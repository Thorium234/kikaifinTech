package com.schaccs.service;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.PaymentMode;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.receipt.ReceiptLine;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.service.school.AcademicCalendarService;
import com.schaccs.service.student.PayPreviewService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.StudentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates continuous fee charging across the full year: Term 1 fees charged
 * at the start, the unpaid balance rolling to arrears at each term end, the
 * next term being billed from the fee structure, and after Term 3 the whole
 * year's unpaid balance accumulating as yearly arrears while the class promotes
 * into the next academic year.
 */
class ContinuousChargingFullYearTest {

    private AcademicCalendarService calendar;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        AppConfig.getInstance().getSchoolProfile().setAcademicYear(2026);
        setupStructures();
        calendar = new AcademicCalendarService();
        calendar.seedIfEmpty();
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private void setupStructures() {
        FeeStructureStore store = FeeStructureStore.getInstance();
        if (store.findVoteheadByCode("TUITION").isEmpty()) {
            store.addVotehead(new Votehead("TUITION", "Tuition", AccountType.SCHOOL_FUND, 1));
        }
        addStructure(store, 2026, BigDecimal.valueOf(1000));
        addStructure(store, 2027, BigDecimal.valueOf(1200));
    }

    private void addStructure(FeeStructureStore store, int year, BigDecimal perTerm) {
        FeeStructure structure = store.findStructure(year, BoardingStatus.DAY).orElse(null);
        if (structure == null) {
            structure = new FeeStructure(year, "ALL", BoardingStatus.DAY, "Day " + year);
            store.addStructure(structure);
        }
        for (AcademicTerm term : AcademicTerm.values()) {
            structure.addItem(new FeeStructureItem("TUITION", "Tuition", term,
                    BoardingStatus.DAY, perTerm));
        }
    }

    private Student createStudent(String form) {
        Student s = new Student();
        s.setAdmissionNumber("ADM-YR-" + System.nanoTime());
        s.setName("Year Cycle Student");
        s.setFormClass(form);
        s.setStream("A");
        s.setBoardingStatus(BoardingStatus.DAY);
        s.setStatus(StudentStatus.ACTIVE);
        s.setAcademicYear(2026);
        StudentStore.getInstance().add(s);
        return s;
    }

    private ReceiptService.Result pay(Student s, String amount, String ref) {
        ReceiptService.Result r = new ReceiptService().receivePayment(
                s, CurrencyConfig.money(amount), PaymentMode.CASH, ref, LocalDate.now(), null);
        assertTrue(r.isSuccess(), () -> "receive failed: " + r.getErrors());
        return r;
    }

    @Test
    @DisplayName("Term 1 → 2 → 3 billing with payments, then yearly arrears + promotion")
    void fullYearWithPayments() {
        FeeCalculationService feeCalc = new FeeCalculationService();
        PayPreviewService preview = new PayPreviewService();
        Student s = createStudent("Form 1");
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(s.getId());

        // ── Term 1 ──
        feeCalc.chargeTermFees(s, AcademicTerm.TERM_1);
        assertEquals(0, ledger.getTotalCharged().compareTo(CurrencyConfig.money("1000")));
        pay(s, "600", "SLIP-T1");
        assertEquals(0, ledger.getTotalPaid().compareTo(CurrencyConfig.money("600")));

        // Term 1 ends: unpaid 400 rolls to arrears, Term 2 billed 1000.
        calendar.rolloverIfDue(LocalDate.of(2026, 4, 25));
        assertEquals(AcademicTerm.TERM_2, ledger.getCurrentTerm());
        assertEquals(0, ledger.getArrears().compareTo(CurrencyConfig.money("400")));
        assertEquals(0, ledger.getTotalCharged().compareTo(CurrencyConfig.money("1000")),
                "Term 2 must be billed from the fee structure");
        PayPreviewService.FeeStatus t2 = preview.feeStatus(s);
        assertEquals(0, t2.expectedTerm().compareTo(CurrencyConfig.money("1000")), "Pay shows Term 2 expected");
        assertEquals(0, t2.balance().compareTo(CurrencyConfig.money("1400")),
                "Balance = Term 2 billed (1000) + arrears (400)");

        // ── Term 2: clear arrears + partial term payment ──
        ReceiptService.Result r2 = pay(s, "700", "SLIP-T2");
        List<ReceiptLine> t2Lines = r2.getReceipt().getLines();
        assertEquals(2, t2Lines.size());
        assertTrue(t2Lines.stream().anyMatch(l -> "ARREARS".equals(l.getVoteheadCode())
                && l.getAmount().compareTo(CurrencyConfig.money("400")) == 0));
        assertTrue(t2Lines.stream().anyMatch(l -> "TUITION".equals(l.getVoteheadCode())
                && l.getAmount().compareTo(CurrencyConfig.money("300")) == 0));
        assertEquals(0, ledger.getArrears().compareTo(BigDecimal.ZERO));
        assertEquals(0, ledger.getTotalPaid().compareTo(CurrencyConfig.money("300")));
        assertEquals(0, ledger.getBalance().compareTo(CurrencyConfig.money("700")));

        // Term 2 ends: unpaid 700 rolls to arrears, Term 3 billed 1000.
        calendar.rolloverIfDue(LocalDate.of(2026, 8, 1));
        assertEquals(AcademicTerm.TERM_3, ledger.getCurrentTerm());
        assertEquals(0, ledger.getArrears().compareTo(CurrencyConfig.money("700")));
        assertEquals(0, ledger.getTotalCharged().compareTo(CurrencyConfig.money("1000")),
                "Term 3 must be billed from the fee structure");
        assertEquals(0, preview.feeStatus(s).expectedTerm().compareTo(CurrencyConfig.money("1000")),
                "Pay shows Term 3 expected");

        // ── Term 3: clear arrears + partial term payment ──
        ReceiptService.Result r3 = pay(s, "900", "SLIP-T3");
        List<ReceiptLine> t3Lines = r3.getReceipt().getLines();
        assertEquals(2, t3Lines.size());
        assertTrue(t3Lines.stream().anyMatch(l -> "ARREARS".equals(l.getVoteheadCode())
                && l.getAmount().compareTo(CurrencyConfig.money("700")) == 0));
        assertTrue(t3Lines.stream().anyMatch(l -> "TUITION".equals(l.getVoteheadCode())
                && l.getAmount().compareTo(CurrencyConfig.money("200")) == 0));
        assertEquals(0, ledger.getArrears().compareTo(BigDecimal.ZERO));
        assertEquals(0, ledger.getTotalPaid().compareTo(CurrencyConfig.money("200")),
                "Paid resets per term cycle (T3 payments only)");
        assertEquals(0, ledger.getBalance().compareTo(CurrencyConfig.money("800")));

        // ── Term 3 ends: unpaid 800 → yearly arrears, promoted into 2027 ──
        AcademicCalendarService.RolloverResult roll = calendar.rolloverIfDue(LocalDate.of(2026, 10, 30));
        assertEquals(1, roll.classPromotions());
        assertEquals(AcademicTerm.TERM_1, ledger.getCurrentTerm());
        assertEquals(2027, s.getAcademicYear());
        assertEquals("Form 2", s.getFormClass());
        assertEquals(0, ledger.getArrears().compareTo(CurrencyConfig.money("800")),
                "Term 3's unpaid balance becomes the carried yearly arrears");
        assertEquals(0, ledger.getTotalCharged().compareTo(CurrencyConfig.money("1200")),
                "Term 1 of 2027 is billed at the new year's rate");
        PayPreviewService.FeeStatus y2027 = preview.feeStatus(s);
        assertEquals(0, y2027.expectedTerm().compareTo(CurrencyConfig.money("1200")));
        assertEquals(0, y2027.balance().compareTo(CurrencyConfig.money("2000")),
                "Balance = 2027 Term 1 (1200) + carried arrears (800)");
    }

    @Test
    @DisplayName("No payments across the year: all three terms accumulate into yearly arrears")
    void noPaymentsAccumulateYearlyArrears() {
        FeeCalculationService feeCalc = new FeeCalculationService();
        Student s = createStudent("Form 1");
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(s.getId());

        feeCalc.chargeTermFees(s, AcademicTerm.TERM_1);
        calendar.rolloverIfDue(LocalDate.of(2026, 4, 25));
        assertEquals(AcademicTerm.TERM_2, ledger.getCurrentTerm());
        assertEquals(0, ledger.getArrears().compareTo(CurrencyConfig.money("1000")),
                "Term 1 unpaid → arrears");

        calendar.rolloverIfDue(LocalDate.of(2026, 8, 1));
        assertEquals(AcademicTerm.TERM_3, ledger.getCurrentTerm());
        assertEquals(0, ledger.getArrears().compareTo(CurrencyConfig.money("2000")),
                "Term 1 + Term 2 unpaid → 2000 arrears");

        calendar.rolloverIfDue(LocalDate.of(2026, 10, 30));
        assertEquals(AcademicTerm.TERM_1, ledger.getCurrentTerm());
        assertEquals(2027, s.getAcademicYear());
        assertEquals(0, ledger.getArrears().compareTo(CurrencyConfig.money("3000")),
                "All three terms unpaid → 3000 yearly arrears carried forward");
    }
}
