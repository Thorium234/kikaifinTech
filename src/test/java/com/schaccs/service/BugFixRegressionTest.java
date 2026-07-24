package com.schaccs.service;

import com.schaccs.accounting.AccountingEngine;
import com.schaccs.accounting.ReceiptAllocationEngine;
import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.PaymentMode;
import com.schaccs.model.fee.FeeAllocation;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.receipt.ReceiptLine;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentBalance;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.model.voucher.Creditor;
import com.schaccs.model.voucher.Imprest;
import com.schaccs.model.voucher.Invoice;
import com.schaccs.model.voucher.Lpo;
import com.schaccs.service.fee.ArrearsService;
import com.schaccs.service.receipt.ReceiptNumberService;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.service.report.ReportService;
import com.schaccs.service.voucher.PaymentVoucherService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import com.schaccs.store.VoucherStore;
import com.schaccs.validation.ReceiptValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for all bug fixes in the QA audit:
 * - BUG-1: PaymentVoucherService.payVoucher() credits CASH_AT_BANK (not SCHOOL_FUND)
 * - BUG-2: ArrearsService.rolloverAll() nets advance before rollover
 * - BUG-3: Receipt lines carry outstandingBefore; reversal uses it for advance logic
 * - BUG-4: StudentBalance / ReportService.feeBalances() deducts advance from balance
 * - BUG-5: Receipt reversal rolls back in-memory state on persistence failure
 * - BUG-7: ReceiptService.receivePayment() catch block uses outstandingBefore for advance
 */
class BugFixRegressionTest {

    private static ReceiptService createTestReceiptService() {
        return new ReceiptService(
                ReceiptStore.getInstance(), StudentStore.getInstance(), FeeStructureStore.getInstance(),
                new ReceiptValidator(), new ReceiptAllocationEngine(), new AccountingEngine(),
                new ReceiptNumberService(), () -> {});
    }

    @BeforeEach
    void setUp() {
        StudentStore.getInstance().clear();
        ReceiptStore.getInstance().clear();
        LedgerStore.getInstance().clear();
        FeeStructureStore.getInstance().clear();
        VoucherStore.getInstance().clear();
        FeeStructureStore.getInstance().addVotehead(new Votehead("BOARD", "Boarding", AccountType.SCHOOL_FUND, 1));
        FeeStructureStore.getInstance().addVotehead(new Votehead("ACT", "Activity", AccountType.FSE_OPERATIONS, 2));
        FeeStructureStore.getInstance().addVotehead(new Votehead("TUITION", "Tuition", AccountType.TUITION_FEES, 3));
        AppConfig.getInstance().getSchoolProfile().setNextReceiptNumber(50000);
    }

    @AfterEach
    void tearDown() {
        StudentStore.getInstance().clear();
        ReceiptStore.getInstance().clear();
        LedgerStore.getInstance().clear();
        FeeStructureStore.getInstance().clear();
        VoucherStore.getInstance().clear();
    }

    private Student createTestStudent(String adm) {
        Student s = new Student();
        s.setAdmissionNumber(adm);
        s.setName("Test " + adm);
        s.setFormClass("Form 2");
        s.setStream("A");
        s.setBoardingStatus(BoardingStatus.BOARDING);
        s.setPhone("0712345678");
        return s;
    }

    // ==========================================
    // BUG-1: Voucher payment credits CASH_AT_BANK
    // ==========================================

    @Test
    void bug1_payVoucherCreditsCashAtBankNotSchoolFund() {
        Votehead votehead = FeeStructureStore.getInstance().getVoteheads().getFirst();
        Creditor creditor = new Creditor("Supplier A", "0700000000");
        VoucherStore.getInstance().addCreditor(creditor);
        PaymentVoucherService service = new PaymentVoucherService(VoucherStore.getInstance(), new AccountingEngine());

        Lpo lpo = new Lpo();
        lpo.setCreditorId(creditor.getId());
        lpo.setCreditorName(creditor.getName());
        lpo.setVoteheadCode(votehead.getCode());
        lpo.setVoteheadName(votehead.getName());
        lpo.setAmount(CurrencyConfig.money("10000"));
        VoucherStore.getInstance().addLpo(lpo);

        com.schaccs.model.voucher.Invoice invoice = new com.schaccs.model.voucher.Invoice();
        invoice.setLpoId(lpo.getId());
        invoice.setCreditorId(creditor.getId());
        invoice.setCreditorName(creditor.getName());
        invoice.setVoteheadCode(votehead.getCode());
        invoice.setVoteheadName(votehead.getName());
        invoice.setAmount(CurrencyConfig.money("10000"));
        VoucherStore.getInstance().addInvoice(invoice);

        com.schaccs.model.voucher.Commitment commitment = new com.schaccs.model.voucher.Commitment();
        commitment.setCreditorId(creditor.getId());
        commitment.setCreditorName(creditor.getName());
        commitment.setVoteheadCode(votehead.getCode());
        commitment.setVoteheadName(votehead.getName());
        commitment.setAccountType(votehead.getAccountType());
        commitment.setAmount(CurrencyConfig.money("10000"));
        VoucherStore.getInstance().addCommitment(commitment);

        List<String> errors = service.payVoucher(commitment, CurrencyConfig.money("10000"),
                PaymentMode.BANK_SLIP, "REF-001", LocalDate.now(), null);
        assertTrue(errors.isEmpty(), "Payment should succeed: " + errors);

        long schoolFundCredits = LedgerStore.getInstance().getTransactions().stream()
                .filter(t -> t.getAccountType() == AccountType.SCHOOL_FUND && t.getCredit().compareTo(BigDecimal.ZERO) > 0)
                .count();
        assertEquals(0, schoolFundCredits,
                "SCHOOL_FUND must NOT have any credit entries — the BUG was it was credited instead of CASH_AT_BANK");

        long bankCredits = LedgerStore.getInstance().getTransactions().stream()
                .filter(t -> t.getAccountType() == AccountType.CASH_AT_BANK && t.getCredit().compareTo(BigDecimal.ZERO) > 0)
                .count();
        assertTrue(bankCredits > 0, "CASH_AT_BANK must have at least one credit entry (bank payout)");
    }

    // ==========================================
    // BUG-2: Arrears rollover nets advance before moving to arrears
    // ==========================================

    @Test
    void bug2_rolloverNetsAdvanceAgainstOutstanding() {
        Student student = createTestStudent("ADM-ARR1");
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("20000"));
        ledger.pay("BOARD", CurrencyConfig.money("15000"));
        ledger.addAdvance(CurrencyConfig.money("8000"));

        ArrearsService arrearsService = new ArrearsService(StudentStore.getInstance());
        arrearsService.rolloverAll();

        BigDecimal arrears = arrearsService.getArrears(student);
        assertEquals(0, arrears.compareTo(BigDecimal.ZERO),
                "Rollover should net advance (8000) against outstanding (5000), producing 0 arrears, but got " + arrears);
        assertEquals(0, ledger.getAdvance().compareTo(BigDecimal.ZERO),
                "Advance should be cleared after rollover");
    }

    @Test
    void bug2_rolloverPreservesAdvancePartiallyWhenAdvanceExceedsOutstanding() {
        Student student = createTestStudent("ADM-ARR2");
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("20000"));
        ledger.pay("BOARD", CurrencyConfig.money("18000"));
        ledger.addAdvance(CurrencyConfig.money("5000"));

        ArrearsService arrearsService = new ArrearsService(StudentStore.getInstance());
        arrearsService.rolloverAll();

        BigDecimal arrears = arrearsService.getArrears(student);
        assertEquals(0, arrears.compareTo(BigDecimal.ZERO),
                "Outstanding is 2000, advance is 5000 — net is 0 arrears, but got " + arrears);
    }

    @Test
    void bug2_rolloverMovesUncoveredOutstandingToArrears() {
        Student student = createTestStudent("ADM-ARR3");
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("20000"));
        ledger.pay("BOARD", CurrencyConfig.money("10000"));
        ledger.addAdvance(CurrencyConfig.money("3000"));

        ArrearsService arrearsService = new ArrearsService(StudentStore.getInstance());
        arrearsService.rolloverAll();

        BigDecimal arrears = arrearsService.getArrears(student);
        assertEquals(0, arrears.compareTo(CurrencyConfig.money("7000")),
                "Outstanding 10000 - advance 3000 = 7000 should be rolled to arrears, but got " + arrears);
    }

    // ==========================================
    // BUG-3 + BUG-7: ReceiptLine carries outstandingBefore; advance handling correct
    // ==========================================

    @Test
    void bug3_receiptLineCarriesOutstandingBefore() {
        Student student = createTestStudent("ADM-OB");
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("10000"));

        ReceiptService service = createTestReceiptService();
        ReceiptService.Result result = service.receivePayment(student, CurrencyConfig.money("5000"),
                PaymentMode.MPESA, "OB-REF", LocalDate.now(), null);
        assertTrue(result.isSuccess());

        for (ReceiptLine line : result.getReceipt().getLines()) {
            assertNotNull(line.getOutstandingBefore(),
                    "ReceiptLine must carry outstandingBefore value");
            assertEquals(CurrencyConfig.money("10000"), line.getOutstandingBefore(),
                    "Outstanding before BOARD payment should be 10000, got " + line.getOutstandingBefore());
        }
    }

    @Test
    void bug3_reversalUsesOutstandingBeforeForAdvanceConsume() {
        Student student = createTestStudent("ADM-ADV");
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("5000"));

        ReceiptService service = createTestReceiptService();
        ReceiptService.Result overpay = service.receivePayment(student, CurrencyConfig.money("8000"),
                PaymentMode.MPESA, "ADV-REF", LocalDate.now(), null);
        assertTrue(overpay.isSuccess());
        assertEquals(CurrencyConfig.money("3000"), ledger.getAdvance());

        ReceiptService.Result reversed = service.reverseReceipt(overpay.getReceipt(), "Test");
        assertTrue(reversed.isSuccess());
        assertEquals(0, ledger.getAdvance().compareTo(BigDecimal.ZERO),
                "Advance should be fully consumed back after reversal, got " + ledger.getAdvance());
        assertEquals(CurrencyConfig.money("5000"), ledger.getOutstanding("BOARD"),
                "BOARD should return to fully outstanding after reversal");
    }

    @Test
    void bug3_reversalRestoreNormalPaymentAdvance() {
        Student student = createTestStudent("ADM-ADV2");
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("10000"));
        ledger.addAdvance(CurrencyConfig.money("2000"));

        ReceiptService service = createTestReceiptService();
        ReceiptService.Result partial = service.receivePayment(student, CurrencyConfig.money("5000"),
                PaymentMode.MPESA, "ADV2-REF", LocalDate.now(), null);
        assertTrue(partial.isSuccess());

        ReceiptService.Result reversed = service.reverseReceipt(partial.getReceipt(), "Test");
        assertTrue(reversed.isSuccess());
        assertEquals(CurrencyConfig.money("2000"), ledger.getAdvance(),
                "Advance should return to 2000 after reversal");
        assertEquals(CurrencyConfig.zero(), ledger.getPaid("BOARD"),
                "BOARD paid should be 0 after reversal");
    }

    // ==========================================
    // BUG-4: ReportService.feeBalances() deducts advance
    // ==========================================

    @Test
    void bug4_feeBalancesDeductsAdvanceFromBalance() {
        Student student = createTestStudent("ADM-BAL");
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("20000"));
        ledger.pay("BOARD", CurrencyConfig.money("15000"));
        ledger.addAdvance(CurrencyConfig.money("8000"));

        ReportService reportService = new ReportService(StudentStore.getInstance(),
                ReceiptStore.getInstance(), FeeStructureStore.getInstance(), LedgerStore.getInstance());
        List<StudentBalance> balances = reportService.feeBalances();

        StudentBalance bal = balances.stream()
                .filter(b -> "ADM-BAL".equals(b.getAdmissionNumber()))
                .findFirst().orElse(null);
        assertNotNull(bal, "Student balance should be in report");
        assertEquals(0, bal.getBalance().compareTo(CurrencyConfig.money("-3000")),
                "Balance should be charged(20000) + arrears(0) - paid(15000) - advance(8000) = -3000, but got " + bal.getBalance());
    }

    @Test
    void bug4_feeBalancesHandlesStudentWithNoAdvance() {
        Student student = createTestStudent("ADM-BAL2");
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("20000"));
        ledger.pay("BOARD", CurrencyConfig.money("10000"));

        ReportService reportService = new ReportService(StudentStore.getInstance(),
                ReceiptStore.getInstance(), FeeStructureStore.getInstance(), LedgerStore.getInstance());
        List<StudentBalance> balances = reportService.feeBalances();

        StudentBalance bal = balances.stream()
                .filter(b -> "ADM-BAL2".equals(b.getAdmissionNumber()))
                .findFirst().orElse(null);
        assertNotNull(bal);
        assertEquals(0, bal.getBalance().compareTo(CurrencyConfig.money("10000")),
                "Balance without advance should be 20000 - 10000 = 10000, got " + bal.getBalance());
    }

    // ==========================================
    // BUG-5: Reversal rollback on persistence failure
    // ==========================================

    @Test
    void bug5_reversalRollsBackLedgerOnPersistenceFailure() {
        Student student = createTestStudent("ADM-RB");
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("20000"));

        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        ReceiptService twoPhaseService = new ReceiptService(
                ReceiptStore.getInstance(), StudentStore.getInstance(), FeeStructureStore.getInstance(),
                new ReceiptValidator(), new ReceiptAllocationEngine(), new AccountingEngine(),
                new ReceiptNumberService(), () -> {
                    if (callCount.incrementAndGet() > 1) {
                        throw new RuntimeException("Simulated persistence failure on reversal");
                    }
                });

        ReceiptService.Result created = twoPhaseService.receivePayment(student, CurrencyConfig.money("15000"),
                PaymentMode.BANK_SLIP, "RB-REF", LocalDate.now(), null);
        assertTrue(created.isSuccess());

        BigDecimal paidBefore = ledger.getPaid("BOARD");
        assertEquals(CurrencyConfig.money("15000"), paidBefore);

        ReceiptService.Result reversed = twoPhaseService.reverseReceipt(created.getReceipt(), "Rollback test");
        assertFalse(reversed.isSuccess());
        assertTrue(reversed.getErrors().getFirst().contains("Failed to reverse"));

        assertEquals(CurrencyConfig.money("15000"), ledger.getPaid("BOARD"),
                "Ledger must be rolled back — paid should remain 15000");
        assertFalse(created.getReceipt().isReversed(),
                "Receipt.reversed flag must be rolled back to false");
    }
}
