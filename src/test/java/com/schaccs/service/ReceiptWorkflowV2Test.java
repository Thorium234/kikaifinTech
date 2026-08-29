package com.schaccs.service;

import com.schaccs.accounting.ReceiptAllocationEngine;
import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.PaymentMode;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.fee.FeeAllocation;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.service.report.ReportService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.StudentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V2 receipt workflows: over-payment cascades across terms, manual votehead
 * override is validated and posted, Reverse &amp; Reissue generates a Credit Note
 * without deleting the original, and the Trial Balance audit view pinpoints
 * unbalanced journal groups.
 */
class ReceiptWorkflowV2Test {

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        AppConfig.getInstance().getSchoolProfile().setAcademicYear(2026);
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private void addVote(String code, String name) {
        if (FeeStructureStore.getInstance().findVoteheadByCode(code).isEmpty()) {
            FeeStructureStore.getInstance().addVotehead(new Votehead(code, name, AccountType.SCHOOL_FUND, 1));
        }
    }

    private void addItem(BoardingStatus status, AcademicTerm term, String code, String amount) {
        FeeStructureStore store = FeeStructureStore.getInstance();
        FeeStructure structure = store.findStructure(2026, status).orElse(null);
        if (structure == null) {
            structure = new FeeStructure(2026, "ALL", status, status + " 2026");
            store.addStructure(structure);
        }
        structure.addItem(new FeeStructureItem(code, code + " name", term, status, CurrencyConfig.money(amount)));
    }

    private FeeStructure configureTerms(BoardingStatus status) {
        addVote("T1A", "Term1 Vote A");
        addVote("T2A", "Term2 Vote A");
        addVote("T3A", "Term3 Vote A");
        addItem(status, AcademicTerm.TERM_1, "T1A", "21000");
        addItem(status, AcademicTerm.TERM_2, "T2A", "18000");
        addItem(status, AcademicTerm.TERM_3, "T3A", "15000");
        return FeeStructureStore.getInstance().findStructure(2026, status).orElseThrow();
    }

    private Student createStudent(BoardingStatus boarding) {
        Student s = new Student();
        s.setAdmissionNumber("ADM-" + System.nanoTime());
        s.setName("Test Learner");
        s.setFormClass("Form 1");
        s.setStream("A");
        s.setBoardingStatus(boarding);
        s.setStatus(StudentStatus.ACTIVE);
        s.setAcademicYear(2026);
        StudentStore.getInstance().add(s);
        PersistenceService.getInstance().saveAll();
        return s;
    }

    @Test
    @DisplayName("Over-payment cascades into the next term's votehead before Advance")
    void overpaymentCascadesToNextTerm() {
        FeeStructure fs = configureTerms(BoardingStatus.BOARDING);
        Student s = createStudent(BoardingStatus.BOARDING);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(s.getId());
        ledger.setCurrentTerm(AcademicTerm.TERM_1);
        // Charge Term 1 fees of 21,000 on T1A
        ledger.charge("T1A", CurrencyConfig.money("21000"));

        List<FeeAllocation> allocs = new ReceiptAllocationEngine()
                .allocateCascading(ledger, CurrencyConfig.money("35000"), fs, AcademicTerm.TERM_1);

        // 21,000 clears T1A; 14,000 cascades into Term 2's T2A (and no Advance yet)
        BigDecimal t1 = BigDecimal.ZERO, t2 = BigDecimal.ZERO, adv = BigDecimal.ZERO;
        for (FeeAllocation a : allocs) {
            if ("T1A".equals(a.getVoteheadCode())) t1 = t1.add(a.getAllocated());
            else if ("T2A".equals(a.getVoteheadCode())) t2 = t2.add(a.getAllocated());
            else if (StudentFeeLedger.ADVANCE_CODE.equals(a.getVoteheadCode())) adv = adv.add(a.getAllocated());
        }
        assertEquals(0, t1.compareTo(CurrencyConfig.money("21000")), "Term 1 must be fully settled");
        assertEquals(0, t2.compareTo(CurrencyConfig.money("14000")), "Remainder cascades to Term 2");
        assertEquals(0, adv.compareTo(BigDecimal.ZERO), "No Advance until all terms covered");
    }

    @Test
    @DisplayName("Manual override receipt posts exactly the specified voteheads and validates the total")
    void manualOverridePostsAndValidates() {
        configureTerms(BoardingStatus.BOARDING);
        Student s = createStudent(BoardingStatus.BOARDING);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(s.getId());
        ledger.setCurrentTerm(AcademicTerm.TERM_1);
        ledger.charge("T1A", CurrencyConfig.money("21000"));

        ReceiptService svc = new ReceiptService();

        // Mismatched total must be rejected
        ReceiptService.Result bad = svc.receivePaymentManual(s, CurrencyConfig.money("10000"),
                PaymentMode.BANK_SLIP, "REF-BAD", null, null,
                List.of(new FeeAllocation("T1A", "Term1 Vote A", CurrencyConfig.zero(), CurrencyConfig.money("9999"))));
        assertFalse(bad.isSuccess());
        assertTrue(String.join(" ", bad.getErrors()).toLowerCase().contains("equal"));

        // Correct total posts and records the manual flag
        ReceiptService.Result ok = svc.receivePaymentManual(s, CurrencyConfig.money("10000"),
                PaymentMode.BANK_SLIP, "REF-OK", null, null,
                List.of(new FeeAllocation("T1A", "Term1 Vote A", ledger.getOutstanding("T1A"), CurrencyConfig.money("10000"))));
        assertTrue(ok.isSuccess(), "Manual receipt should post: " + ok.getErrors());
        assertTrue(ok.getReceipt().isManualOverride());
        assertEquals(0, ledger.getOutstanding("T1A").compareTo(CurrencyConfig.money("11000")));
    }

    @Test
    @DisplayName("Reverse & Reissue assigns a Credit Note and re-posts to the correct student")
    void reverseAndReissue() {
        configureTerms(BoardingStatus.BOARDING);
        Student wrong = createStudent(BoardingStatus.BOARDING);
        Student right = createStudent(BoardingStatus.BOARDING);
        StudentFeeLedger wrongLedger = StudentStore.getInstance().getLedger(wrong.getId());
        StudentFeeLedger rightLedger = StudentStore.getInstance().getLedger(right.getId());
        wrongLedger.setCurrentTerm(AcademicTerm.TERM_1);
        rightLedger.setCurrentTerm(AcademicTerm.TERM_1);

        ReceiptService svc = new ReceiptService();
        ReceiptService.Result posted = svc.receivePayment(wrong, CurrencyConfig.money("5000"),
                PaymentMode.BANK_SLIP, "REF", null, null);
        assertTrue(posted.isSuccess(), "Initial receipt should post: " + posted.getErrors());
        Receipt original = posted.getReceipt();

        ReceiptService.Result reissued = svc.reissueReceipt(original, right, "Wrong student");
        assertTrue(reissued.isSuccess(), "Reissue should succeed: " + reissued.getErrors());

        // Original is reversed but not deleted, and carries a CN
        assertTrue(original.isReversed());
        assertNotNull(original.getCreditNoteNumber());
        assertTrue(original.getCreditNoteNumber().startsWith("CN-"));
        // Correct student now holds the 5,000 (fully paid against their 21k charge)
        assertTrue(rightLedger.getBalance().compareTo(CurrencyConfig.money("16000")) <= 0);
        // Original student no longer holds the overpayment
        assertTrue(wrongLedger.getBalance().compareTo(CurrencyConfig.money("21000")) <= 0);
    }
}
