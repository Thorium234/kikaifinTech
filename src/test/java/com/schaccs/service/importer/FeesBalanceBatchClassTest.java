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
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.StudentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fees-balance batch class handling: the bursar specifies the class after the
 * year (Form N or Grade N), every row whose Form column could not be inferred
 * is filled automatically, and students new to the registry get the live
 * term's c/fees charged from the fee structure on top of the imported balance.
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

    private void addFeeStructure(int year, String t1) {
        FeeStructure fs = new FeeStructure(year, "ALL", BoardingStatus.BOARDING,
                "Boarding " + year);
        fs.addItem(new FeeStructureItem("TUITION", "Tuition", BoardingStatus.BOARDING,
                CurrencyConfig.money(t1), CurrencyConfig.zero(), CurrencyConfig.zero()));
        FeeStructureStore.getInstance().addStructure(fs);
    }

    @Test
    @DisplayName("Created students are billed the live term's c/fees from the fee structure")
    void applyWiresTermFeesFromStructure() {
        addFeeStructure(2026, "8500");
        List<FeesBalanceRow> rows = new ArrayList<>(List.of(
                row("N001", "New Billed Student", "Grade 10", "12000")));

        var result = service.apply(rows, ImportContext.of(2026, "TESTER"));

        assertEquals(1, result.getCreated());
        Student student = StudentStore.getInstance().findByAdmissionNumber("N001").orElseThrow();
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(0, CurrencyConfig.money("8500").compareTo(ledger.getTotalCharged()),
                "Term 1 fee structure amount charged to the new account");
        assertEquals(AcademicTerm.TERM_1, ledger.getCurrentTerm());
        assertEquals(0, CurrencyConfig.money("12000").compareTo(ledger.getArrears()),
                "Imported balance lands as arrears on top of the term charge");
    }

    @Test
    @DisplayName("Existing students only get their balance updated, never re-billed")
    void existingStudentsAreNotRebilled() {
        addFeeStructure(2026, "8500");
        Student preExisting = new Student();
        preExisting.setAdmissionNumber("E001");
        preExisting.setName("Existing Student");
        preExisting.setFormClass("Grade 10");
        preExisting.setBoardingStatus(BoardingStatus.BOARDING);
        StudentStore.getInstance().add(preExisting);

        List<FeesBalanceRow> rows = new ArrayList<>(List.of(
                row("E001", "Existing Student", "Grade 10", "3000")));
        var result = service.apply(rows, ImportContext.of(2026, "TESTER"));

        assertEquals(1, result.getExisting());
        assertEquals(0, result.getCreated());
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(preExisting.getId());
        assertEquals(0, BigDecimal.ZERO.compareTo(ledger.getTotalCharged()),
                "No automatic billing for students already in the registry");
        assertEquals(0, CurrencyConfig.money("3000").compareTo(ledger.getArrears()));
    }
}
