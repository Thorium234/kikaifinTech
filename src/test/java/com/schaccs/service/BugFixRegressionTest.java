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
import com.schaccs.model.finance.LedgerEntry;
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
import com.schaccs.repository.PersistenceService;
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
        PersistenceService.getInstance().clearAll();
        FeeStructureStore.getInstance().addVotehead(new Votehead("BOARD", "Boarding", AccountType.SCHOOL_FUND, 1));
        FeeStructureStore.getInstance().addVotehead(new Votehead("ACT", "Activity", AccountType.FSE_OPERATIONS, 2));
        FeeStructureStore.getInstance().addVotehead(new Votehead("TUITION", "Tuition", AccountType.TUITION_FEES, 3));
        AppConfig.getInstance().getSchoolProfile().setNextReceiptNumber(50000);
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
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

        // Seed CASH_AT_BANK balance so negative-cash guard passes
        LedgerEntry seed = new LedgerEntry();
        seed.setDate(LocalDate.now());
        seed.setAccountType(AccountType.CASH_AT_BANK);
        seed.setDebit(CurrencyConfig.money("500000"));
        seed.setCredit(BigDecimal.ZERO);
        seed.setDescription("Seed balance for test");
        LedgerStore.getInstance().addLedgerEntry(seed);

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
        assertEquals(0, ledger.getAdvance().compareTo(CurrencyConfig.money("3000")),
                "Advance should be 3000 after rollover (8000 - 5000 consumed against outstanding)");
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

    // ==========================================
    // BUG-6: Receipt verification hash covers allocation lines
    // ==========================================

    @Test
    void bug6_receiptHashIncludesAllocationLines() {
        Student student = createTestStudent("ADM-HASH");
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("20000"));
        ledger.charge("TUITION", CurrencyConfig.money("10000"));

        ReceiptService service = createTestReceiptService();
        ReceiptService.Result result = service.receivePayment(student, CurrencyConfig.money("15000"),
                PaymentMode.BANK_SLIP, "HASH-REF", LocalDate.now(), null);
        assertTrue(result.isSuccess());

        Receipt receipt = result.getReceipt();
        assertFalse(receipt.getLines().isEmpty(), "Receipt must have allocation lines");
        assertTrue(receipt.isVerified(),
                "Freshly posted receipt must be verifiable (hash computed after lines were added)");

        String baseOnlyHash = sha256Legacy(receipt);
        assertNotEquals(baseOnlyHash, receipt.getVerificationHash(),
                "Hash must cover allocation lines, not just base fields");

        receipt.getLines().getFirst().setAmount(receipt.getLines().getFirst().getAmount().add(CurrencyConfig.money("1")));
        assertFalse(receipt.isVerified(),
                "Changing an allocation line amount must invalidate the integrity hash");
    }

    @Test
    void bug6_legacyV1HashStillValidates() {
        Student student = createTestStudent("ADM-V1");
        StudentStore.getInstance().add(student);

        ReceiptService service = createTestReceiptService();
        ReceiptService.Result result = service.receivePayment(student, CurrencyConfig.money("5000"),
                PaymentMode.MPESA, "V1-REF", LocalDate.now(), null);
        assertTrue(result.isSuccess());

        Receipt receipt = result.getReceipt();
        receipt.getLines().clear();
        receipt.setVerificationHash(sha256Legacy(receipt));

        assertTrue(receipt.isVerified(),
                "Legacy v1 hashes (computed over base fields only) must still validate");
    }

    @Test
    void bug6_reversalKeepsReceiptVerifiable() {
        Student student = createTestStudent("ADM-RVH");
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("20000"));

        ReceiptService service = createTestReceiptService();
        ReceiptService.Result created = service.receivePayment(student, CurrencyConfig.money("8000"),
                PaymentMode.BANK_SLIP, "RVH-REF", LocalDate.now(), null);
        assertTrue(created.isSuccess());

        ReceiptService.Result reversed = service.reverseReceipt(created.getReceipt(), "Verification test");
        assertTrue(reversed.isSuccess());

        Receipt receipt = reversed.getReceipt();
        assertTrue(receipt.isReversed());
        assertTrue(receipt.isVerified(),
                "A reversed receipt must remain verifiable (hash recomputed after reversal fields)");

        receipt.setNotes(receipt.getNotes() + " TAMPERED");
        assertFalse(receipt.isVerified(),
                "Tampering with reversal notes must invalidate the integrity hash");
    }

    private static String sha256Legacy(Receipt receipt) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            String raw = receipt.getReceiptNumber() + "|" + receipt.getDate() + "|" + receipt.getStudentId()
                    + "|" + receipt.getAmount() + "|" + receipt.getPaymentMode() + "|"
                    + receipt.getBankReference() + "|" + receipt.getAmount();
            byte[] bytes = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // ==========================================
    // BUG-8: Reversal rollback must NOT delete the original postings
    // ==========================================

    @Test
    void bug8_reversalRollbackPreservesOriginalLedgerPostings() {
        Student student = createTestStudent("ADM-RB2");
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
                PaymentMode.BANK_SLIP, "RB2-REF", LocalDate.now(), null);
        assertTrue(created.isSuccess());

        int originalTxCount = LedgerStore.getInstance().getTransactions().size();
        assertTrue(originalTxCount > 0, "Receipt posting must create ledger transactions");

        ReceiptService.Result reversed = twoPhaseService.reverseReceipt(created.getReceipt(), "Rollback test");
        assertFalse(reversed.isSuccess());

        assertEquals(originalTxCount, LedgerStore.getInstance().getTransactions().size(),
                "Failed reversal must not delete the original receipt postings from the ledger");
        assertTrue(LedgerStore.getInstance().getTransactions().stream()
                        .noneMatch(t -> t.getReference().startsWith("RCPT-RV-")),
                "Partial reversal contra entries must be rolled back");
        assertTrue(LedgerStore.getInstance().getTransactions().stream()
                        .anyMatch(t -> ("RCPT-" + created.getReceipt().getReceiptNumber()).equals(t.getReference())),
                "Original fee receipt postings must survive a failed reversal");
        assertEquals(CurrencyConfig.money("15000"), LedgerStore.getInstance().getAccountBalance(AccountType.CASH_AT_BANK),
                "Bank balance should reflect the original 15000 receipt after rollback");
    }

    // ==========================================
    // BUG-9: chargeTermFees applies the sibling discount
    // ==========================================

    @Test
    void bug9_chargeTermFeesAppliesSiblingDiscount() {
        AppConfig.getInstance().getSchoolProfile().setSiblingDiscountEnabled(true);
        AppConfig.getInstance().getSchoolProfile().setSiblingDiscountRate(CurrencyConfig.money("0.15"));

        FeeStructure structure = new FeeStructure(AppConfig.getInstance().getAcademicYear(),
                "Form 2", BoardingStatus.BOARDING, "Sibling Test");
        structure.addItem(new FeeStructureItem("BOARD", "Boarding", AcademicTerm.TERM_1,
                BoardingStatus.BOARDING, CurrencyConfig.money("10000")));
        FeeStructureStore.getInstance().addStructure(structure);

        Student first = createTestStudent("ADM-SIB1");
        first.setParentName("Jane Kiptoo");
        Student second = createTestStudent("ADM-SIB2");
        second.setParentName("Jane Kiptoo");
        StudentStore.getInstance().add(first);
        StudentStore.getInstance().add(second);

        com.schaccs.service.fee.FeeCalculationService feeCalc = new com.schaccs.service.fee.FeeCalculationService();

        feeCalc.chargeTermFees(first, AcademicTerm.TERM_1);
        feeCalc.chargeTermFees(second, AcademicTerm.TERM_1);

        StudentFeeLedger firstLedger = StudentStore.getInstance().getLedger(first.getId());
        StudentFeeLedger secondLedger = StudentStore.getInstance().getLedger(second.getId());

        assertEquals(CurrencyConfig.money("10000"), firstLedger.getCharged("BOARD"),
                "First child of a parent pays the full fee");
        assertEquals(CurrencyConfig.money("8500"), secondLedger.getCharged("BOARD"),
                "Second child sharing the parent name must receive the 15% sibling discount");

        Student lone = createTestStudent("ADM-SIB3");
        lone.setParentName("Daniel Otieno");
        StudentStore.getInstance().add(lone);
        feeCalc.chargeTermFees(lone, AcademicTerm.TERM_1);
        assertEquals(CurrencyConfig.money("10000"), StudentStore.getInstance().getLedger(lone.getId()).getCharged("BOARD"),
                "A single child with no siblings pays the full fee");
    }
}
