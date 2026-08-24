package com.schaccs.service.importer;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.importer.FeesBalanceImportService.ImportContext;
import com.schaccs.service.school.AcademicCalendarService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.StudentStore;
import com.schaccs.store.StudentTermBalanceStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fees-balance batch class handling: the bursar specifies the class after the
 * year (Form N or Grade N), every row whose Form column could not be inferred
 * is filled automatically, and the imported BALANCE is reconciled against the
 * live calendar — current term bills, ended terms become arrears, paid fills
 * the gap, and the closing balance equals the workbook figure exactly.
 */
class FeesBalanceBatchClassTest {

    private FeesBalanceImportService service;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        service = new FeesBalanceImportService();
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private FeesBalanceRow row(String adm, String name, String formClass, String totalFees) {
        FeesBalanceRow row = new FeesBalanceRow("MISC", 2);
        row.setAdmissionNumber(adm);
        row.setName(name);
        row.setFormClass(formClass == null ? "" : formClass);
        row.setStream("");
        row.setBoardingStatus(BoardingStatus.BOARDING);
        row.setTotalFees(CurrencyConfig.money(totalFees));
        return row;
    }

    @Test
    @DisplayName("Batch class fills un-inferred rows and clears the blocking warning")
    void defaultsFillBlanksAndClearCleaning() {
        List<FeesBalanceRow> rows = new ArrayList<>(List.of(
                row("G001", "Ada Grade Ten", "", "12000"),
                row("F002", "Ben Form Two", "Form 2", "5000")));
        service.scrutinize(rows);
        assertTrue(rows.get(0).requiresCleaning(), "Missing class blocks import");
        assertTrue(rows.get(0).getWarnings().stream()
                .anyMatch(w -> w.startsWith("Class not inferred")));

        int changed = service.applyWorkbookDefaults(rows, "Grade 10", "A", false);

        assertEquals(2, changed, "Blank class and blank stream cells both fill");
        assertEquals("Grade 10", rows.get(0).getFormClass());
        assertEquals("A", rows.get(0).getStream());
        assertEquals("Form 2", rows.get(1).getFormClass(), "Existing class kept");
        assertEquals("A", rows.get(1).getStream(), "Blank stream still fills on a classified row");

        service.scrutinize(rows);
        assertFalse(rows.get(0).requiresCleaning(),
                () -> "Row should import now, warnings: " + rows.get(0).getWarningText());
    }

    @Test
    @DisplayName("Overwrite mode replaces existing classes and skips sheet-level pseudo-rows")
    void overwriteReplacesAndSkipsSkippedRows() {
        FeesBalanceRow skipped = row("S000", "Sheet Skip", "", "1000");
        skipped.setInclude(false);
        skipped.setMatchStatus("Skipped");
        List<FeesBalanceRow> rows = new ArrayList<>(List.of(
                row("F002", "Ben Form Two", "Form 2", "5000"),
                skipped));

        int changed = service.applyWorkbookDefaults(rows, "Grade 11", null, true);

        assertEquals(1, changed);
        assertEquals("Grade 11", rows.get(0).getFormClass());
        assertEquals("", skipped.getFormClass(), "Skipped placeholder rows are left alone");
    }

    private static final String T1 = "5500";
    private static final String T2 = "3700";
    private static final String T3 = "1800";

    private void addFeeStructure(int year) {
        FeeStructure fs = new FeeStructure(year, "ALL", BoardingStatus.BOARDING,
                "Boarding " + year);
        fs.addItem(new FeeStructureItem("TUITION", "Tuition", BoardingStatus.BOARDING,
                CurrencyConfig.money(T1), CurrencyConfig.money(T2), CurrencyConfig.money(T3)));
        FeeStructureStore.getInstance().addStructure(fs);
    }

    private BigDecimal termFee(AcademicTerm term) {
        return switch (term) {
            case TERM_1 -> CurrencyConfig.money(T1);
            case TERM_2 -> CurrencyConfig.money(T2);
            case TERM_3 -> CurrencyConfig.money(T3);
        };
    }

    @Test
    @DisplayName("Import reconciles against the live calendar: current term bills, ended terms become arrears")
    void importReconcilesAgainstCurrentCalendarTerm() {
        addFeeStructure(2026);
        AcademicCalendarService calendar = new AcademicCalendarService();
        calendar.ensureYearCalendar(2026);
        AcademicTerm current = calendar.currentTerm(LocalDate.now()).orElseThrow();

        List<FeesBalanceRow> rows = new ArrayList<>(List.of(
                row("N001", "New Billed Student", "Grade 10", "9000")));
        var result = service.apply(rows, ImportContext.of(2026, "TESTER"));

        assertEquals(1, result.getCreated());
        Student student = StudentStore.getInstance().findByAdmissionNumber("N001").orElseThrow();
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());

        BigDecimal billedToDate = BigDecimal.ZERO;
        for (AcademicTerm term : AcademicTerm.values()) {
            if (term.ordinal() > current.ordinal()) {
                break;
            }
            billedToDate = billedToDate.add(termFee(term));
        }
        BigDecimal expectedPaid = billedToDate.subtract(CurrencyConfig.money("9000"))
                .max(BigDecimal.ZERO);

        assertEquals(current, ledger.getCurrentTerm(),
                "The ledger must know the term the school is actually in");
        assertEquals(0, termFee(current).compareTo(ledger.getTotalCharged()),
                "Charged is the CURRENT term's fee from the structure");
        assertEquals(0, billedToDate.subtract(termFee(current)).compareTo(ledger.getArrears()),
                "Arrears are the fees of the terms that already ended");
        assertEquals(0, expectedPaid.compareTo(ledger.getTotalPaid()),
                "Paid is billed-to-date minus the imported balance");
        assertEquals(0, CurrencyConfig.money("9000").compareTo(ledger.getBalance()),
                "The closing balance equals the workbook figure exactly");

        var snapshots = StudentTermBalanceStore.getInstance().findByStudent(student.getId());
        assertEquals(current.getNumber(), snapshots.size(), "One snapshot per term elapsed");
        var latest = StudentTermBalanceStore.getInstance()
                .find(student.getId(), 2026, current).orElseThrow();
        assertEquals(0, CurrencyConfig.money("9000").compareTo(latest.getClosingBalance()),
                "The current term closes at the imported balance");
    }

    @Test
    @DisplayName("A credit balance settles all billing and lands as advance")
    void creditBalanceLandsAsAdvance() {
        addFeeStructure(2026);
        AcademicCalendarService calendar = new AcademicCalendarService();
        calendar.ensureYearCalendar(2026);
        AcademicTerm current = calendar.currentTerm(LocalDate.now()).orElseThrow();

        List<FeesBalanceRow> rows = new ArrayList<>(List.of(
                row("N002", "Credit Student", "Grade 10", "-1000")));
        service.apply(rows, ImportContext.of(2026, "TESTER"));

        Student student = StudentStore.getInstance().findByAdmissionNumber("N002").orElseThrow();
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());

        BigDecimal billedToDate = BigDecimal.ZERO;
        for (AcademicTerm term : AcademicTerm.values()) {
            if (term.ordinal() > current.ordinal()) {
                break;
            }
            billedToDate = billedToDate.add(termFee(term));
        }
        assertEquals(0, termFee(current).compareTo(ledger.getTotalCharged()));
        assertEquals(0, termFee(current).compareTo(ledger.getTotalPaid()),
                "The current term's billing is fully settled");
        assertEquals(0, BigDecimal.ZERO.compareTo(ledger.getArrears()),
                "Ended terms are settled too - the payment surplus clears arrears");
        assertEquals(0, CurrencyConfig.money("1000").compareTo(ledger.getAdvance()));
        assertEquals(0, CurrencyConfig.money("-1000").compareTo(ledger.getBalance()));
    }

    @Test
    @DisplayName("Without a calendar period or fee structure the balance lands as plain arrears")
    void fallsBackToPlainArrearsWithoutCalendar() {
        List<FeesBalanceRow> rows = new ArrayList<>(List.of(
                row("N003", "No Calendar Student", "Grade 10", "9000")));

        service.apply(rows, ImportContext.of(2026, "TESTER"));

        Student student = StudentStore.getInstance().findByAdmissionNumber("N003").orElseThrow();
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(0, BigDecimal.ZERO.compareTo(ledger.getTotalCharged()),
                "Nothing to reconcile against - no billing is posted");
        assertEquals(0, CurrencyConfig.money("9000").compareTo(ledger.getArrears()));
        assertEquals(0, CurrencyConfig.money("9000").compareTo(ledger.getBalance()));
    }

    @Test
    @DisplayName("Existing students keep their history: only arrears move so the balance matches")
    void existingStudentsKeepHistoryAndMatchImportedBalance() {
        addFeeStructure(2026);
        new AcademicCalendarService().ensureYearCalendar(2026);
        Student preExisting = new Student();
        preExisting.setAdmissionNumber("E001");
        preExisting.setName("Existing Student");
        preExisting.setFormClass("Grade 10");
        preExisting.setBoardingStatus(BoardingStatus.BOARDING);
        preExisting.setAcademicYear(2026);
        StudentStore.getInstance().add(preExisting);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(preExisting.getId());
        ledger.charge("TUITION", CurrencyConfig.money("1000"));
        ledger.pay("TUITION", CurrencyConfig.money("400"));

        List<FeesBalanceRow> rows = new ArrayList<>(List.of(
                row("E001", "Existing Student", "Grade 10", "3000")));
        var result = service.apply(rows, ImportContext.of(2026, "TESTER"));

        assertEquals(1, result.getExisting());
        assertEquals(0, result.getCreated());
        assertEquals(0, CurrencyConfig.money("1000").compareTo(ledger.getTotalCharged()),
                "No automatic re-billing for students already in the registry");
        assertEquals(0, CurrencyConfig.money("3000").compareTo(ledger.getBalance()),
                "Only arrears move so the closing balance equals the import");
        assertEquals(0, CurrencyConfig.money("2400").compareTo(ledger.getArrears()));
    }
}
