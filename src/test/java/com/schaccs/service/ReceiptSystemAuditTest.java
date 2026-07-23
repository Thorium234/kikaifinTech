package com.schaccs.service;

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
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import com.schaccs.validation.ReceiptValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Receipt System audit tests covering:
 * - Receipt creation and accounting
 * - Receipt reversal and ledger restoration
 * - Receipt verification (hash integrity)
 * - Allocation engine edge cases
 * - LedgerStore consistency
 * - Double-entry balance
 */
class ReceiptSystemAuditTest {

    @BeforeEach
    void setUp() {
        StudentStore.getInstance().clear();
        ReceiptStore.getInstance().clear();
        LedgerStore.getInstance().clear();
        FeeStructureStore.getInstance().clear();
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
    }

    private Student createTestStudent(String adm, BoardingStatus boarding) {
        Student s = new Student();
        s.setAdmissionNumber(adm);
        s.setName("Test Student " + adm);
        s.setFormClass("Form 2");
        s.setStream("A");
        s.setBoardingStatus(boarding);
        s.setPhone("0712345678");
        return s;
    }

    private void addFeeStructure(BoardingStatus boarding) {
        FeeStructure fs = new FeeStructure(2026, "ALL", boarding, "Test Structure");
        fs.addItem(new FeeStructureItem("BOARD", "Boarding", AcademicTerm.TERM_1, boarding, CurrencyConfig.money(20000)));
        fs.addItem(new FeeStructureItem("ACT", "Activity", AcademicTerm.TERM_1, boarding, CurrencyConfig.money(5000)));
        fs.addItem(new FeeStructureItem("TUITION", "Tuition", AcademicTerm.TERM_1, boarding, CurrencyConfig.money(15000)));
        FeeStructureStore.getInstance().addStructure(fs);
    }

    // ========================================
    // TEST: Receipt creation + accounting
    // ========================================

    @Test
    void receiptCreationPostsBalancedDoubleEntry() {
        addFeeStructure(BoardingStatus.BOARDING);
        Student student = createTestStudent("ADM-001", BoardingStatus.BOARDING);
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("20000"));
        ledger.charge("ACT", CurrencyConfig.money("5000"));
        ledger.charge("TUITION", CurrencyConfig.money("15000"));

        ReceiptService service = new ReceiptService();
        ReceiptService.Result result = service.receivePayment(student, CurrencyConfig.money("25000"),
                PaymentMode.MPESA, "MPESA-REF-001", LocalDate.now(), "Full payment test");

        assertTrue(result.isSuccess());
        assertNotNull(result.getReceipt());
        assertEquals(CurrencyConfig.money("25000"), result.getReceipt().getAmount());

        // Verify double-entry balance
        BigDecimal totalDebits = LedgerStore.getInstance().getTransactions().stream()
                .map(t -> t.getDebit())
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        BigDecimal totalCredits = LedgerStore.getInstance().getTransactions().stream()
                .map(t -> t.getCredit())
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        assertEquals(0, totalDebits.compareTo(totalCredits),
                "Debit total must equal credit total for balanced double-entry");

        // Verify account balances
        assertTrue(LedgerStore.getInstance().getAccountBalance(AccountType.CASH_AT_BANK).compareTo(BigDecimal.ZERO) > 0,
                "Cash at Bank should be debited (positive)");
    }

    @Test
    void receiptCreationUpdatesStudentLedgerCorrectly() {
        addFeeStructure(BoardingStatus.BOARDING);
        Student student = createTestStudent("ADM-002", BoardingStatus.BOARDING);
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("20000"));

        ReceiptService service = new ReceiptService();
        service.receivePayment(student, CurrencyConfig.money("10000"),
                PaymentMode.BANK_SLIP, "SLIP-001", LocalDate.now(), null);

        assertEquals(CurrencyConfig.money("10000"), ledger.getPaid("BOARD"),
                "Student ledger should reflect payment of 10000 for BOARD");
        assertEquals(CurrencyConfig.money("10000"), ledger.getOutstanding("BOARD"),
                "Outstanding should be 10000 after partial payment");
    }

    // ========================================
    // TEST: Receipt reversal
    // ========================================

    @Test
    void receiptReversalRestoresStudentLedger() {
        addFeeStructure(BoardingStatus.BOARDING);
        Student student = createTestStudent("ADM-003", BoardingStatus.BOARDING);
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("20000"));

        ReceiptService service = new ReceiptService();
        ReceiptService.Result created = service.receivePayment(student, CurrencyConfig.money("15000"),
                PaymentMode.BANK_SLIP, "SLIP-002", LocalDate.now(), null);
        assertTrue(created.isSuccess());
        assertEquals(CurrencyConfig.money("15000"), ledger.getPaid("BOARD"));

        ReceiptService.Result reversed = service.reverseReceipt(created.getReceipt(), "Audit test");
        assertTrue(reversed.isSuccess());

        assertEquals(CurrencyConfig.zero(), ledger.getPaid("BOARD"),
                "After reversal, paid amount must be zero");
        assertEquals(CurrencyConfig.money("20000"), ledger.getOutstanding("BOARD"),
                "After reversal, full amount must be outstanding");
        assertTrue(created.getReceipt().isReversed());
    }

    @Test
    void receiptReversalPostsContraDoubleEntry() {
        addFeeStructure(BoardingStatus.BOARDING);
        Student student = createTestStudent("ADM-004", BoardingStatus.BOARDING);
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("20000"));

        ReceiptService service = new ReceiptService();
        ReceiptService.Result created = service.receivePayment(student, CurrencyConfig.money("20000"),
                PaymentMode.MPESA, "MPESA-004", LocalDate.now(), null);

        int txCountBefore = LedgerStore.getInstance().getTransactions().size();
        ReceiptService.Result reversed = service.reverseReceipt(created.getReceipt(), "Test reversal");
        assertTrue(reversed.isSuccess());
        int txCountAfter = LedgerStore.getInstance().getTransactions().size();

        assertEquals(txCountBefore + 2, txCountAfter,
                "Reversal must post 2 contra entries (bank credit + income debit)");

        // Double-entry still balanced
        BigDecimal totalDebits = LedgerStore.getInstance().getTransactions().stream()
                .map(t -> t.getDebit())
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        BigDecimal totalCredits = LedgerStore.getInstance().getTransactions().stream()
                .map(t -> t.getCredit())
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        assertEquals(0, totalDebits.compareTo(totalCredits),
                "Double-entry must remain balanced after reversal");
    }

    @Test
    void reversalIsIdempotent() {
        addFeeStructure(BoardingStatus.BOARDING);
        Student student = createTestStudent("ADM-005", BoardingStatus.BOARDING);
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("20000"));

        ReceiptService service = new ReceiptService();
        ReceiptService.Result created = service.receivePayment(student, CurrencyConfig.money("5000"),
                PaymentMode.BANK_SLIP, "SLIP-005", LocalDate.now(), null);
        ReceiptService.Result reversed = service.reverseReceipt(created.getReceipt(), "First");
        assertTrue(reversed.isSuccess());

        ReceiptService.Result doubleReversed = service.reverseReceipt(created.getReceipt(), "Second");
        assertFalse(doubleReversed.isSuccess());
        assertTrue(doubleReversed.getErrors().getFirst().contains("already reversed"));
    }

    @Test
    void reversalRejectsNullReceipt() {
        ReceiptService service = new ReceiptService();
        ReceiptService.Result result = service.reverseReceipt(null, "reason");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().getFirst().contains("No receipt selected"));
    }

    // ========================================
    // TEST: Receipt verification (hash integrity)
    // ========================================

    @Test
    void receiptVerificationDoesNotMutateState() {
        Receipt receipt = new Receipt();
        receipt.setReceiptNumber(99999);
        receipt.setDate(LocalDate.of(2026, 6, 15));
        receipt.setStudentId("S1");
        receipt.setAmount(CurrencyConfig.money("5000"));
        receipt.setPaymentMode(PaymentMode.BANK_SLIP);
        receipt.setBankReference("REF-VERIFY");
        receipt.computeVerificationHash();

        String hashBefore = receipt.getVerificationHash();
        boolean verified1 = receipt.isVerified();
        String hashAfter = receipt.isVerified() ? receipt.getVerificationHash() : hashBefore;

        assertTrue(verified1, "Receipt should be verified with unchanged data");
        assertEquals(hashBefore, hashAfter, "isVerified() must not modify the stored hash");
    }

    @Test
    void receiptVerificationFailsWhenDataChanged() {
        Receipt receipt = new Receipt();
        receipt.setReceiptNumber(88888);
        receipt.setDate(LocalDate.of(2026, 6, 15));
        receipt.setStudentId("S1");
        receipt.setAmount(CurrencyConfig.money("3000"));
        receipt.setPaymentMode(PaymentMode.CHEQUE);
        receipt.computeVerificationHash();

        receipt.setAmount(CurrencyConfig.money("9999"));
        assertFalse(receipt.isVerified(), "Verification must fail after data mutation");
    }

    @Test
    void receiptLinesTotalMatchesAmount() {
        Receipt receipt = new Receipt();
        receipt.setAmount(CurrencyConfig.money("10000"));
        receipt.addLine(new ReceiptLine("BOARD", "Boarding", CurrencyConfig.money("7000")));
        receipt.addLine(new ReceiptLine("ACT", "Activity", CurrencyConfig.money("3000")));

        assertEquals(CurrencyConfig.money("10000"), receipt.linesTotal(),
                "Sum of receipt lines must equal receipt total");
    }

    // ========================================
    // TEST: Allocation engine edge cases
    // ========================================

    @Test
    void allocationDistributesEquallyAcrossVoteheads() {
        StudentFeeLedger ledger = new StudentFeeLedger("S-EQ");
        ledger.charge("BOARD", CurrencyConfig.money("10000"));
        ledger.charge("ACT", CurrencyConfig.money("10000"));
        ledger.charge("TUITION", CurrencyConfig.money("10000"));

        ReceiptAllocationEngine engine = new ReceiptAllocationEngine(FeeStructureStore.getInstance());
        List<FeeAllocation> allocs = engine.allocate(ledger, CurrencyConfig.money("30000"));

        long voteheadAllocs = allocs.stream()
                .filter(a -> !"ADVANCE".equals(a.getVoteheadCode()) && !"ARREARS".equals(a.getVoteheadCode()))
                .count();
        assertEquals(3, voteheadAllocs, "Should allocate to 3 voteheads equally");

        BigDecimal totalAllocated = allocs.stream()
                .map(FeeAllocation::getAllocated)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        assertEquals(CurrencyConfig.money("30000"), totalAllocated,
                "Total allocated must equal payment amount");
    }

    @Test
    void allocationOverpaymentGoesToAdvance() {
        StudentFeeLedger ledger = new StudentFeeLedger("S-OVR");
        ledger.charge("BOARD", CurrencyConfig.money("5000"));

        ReceiptAllocationEngine engine = new ReceiptAllocationEngine(FeeStructureStore.getInstance());
        List<FeeAllocation> allocs = engine.allocate(ledger, CurrencyConfig.money("8000"));

        long advanceCount = allocs.stream()
                .filter(a -> StudentFeeLedger.ADVANCE_CODE.equals(a.getVoteheadCode()))
                .count();
        assertEquals(1, advanceCount, "Overpayment should create an ADVANCE allocation");

        FeeAllocation advance = allocs.stream()
                .filter(a -> StudentFeeLedger.ADVANCE_CODE.equals(a.getVoteheadCode()))
                .findFirst().orElseThrow();
        assertEquals(CurrencyConfig.money("3000"), advance.getAllocated(),
                "Advance should be the overpayment amount");
    }

    @Test
    void allocationClearsArrearsBeforeCurrentCharges() {
        StudentFeeLedger ledger = new StudentFeeLedger("S-ARR");
        ledger.setArrears(CurrencyConfig.money("5000"));
        ledger.charge("BOARD", CurrencyConfig.money("10000"));

        ReceiptAllocationEngine engine = new ReceiptAllocationEngine(FeeStructureStore.getInstance());
        List<FeeAllocation> allocs = engine.allocate(ledger, CurrencyConfig.money("12000"));

        assertEquals("ARREARS", allocs.get(0).getVoteheadCode(),
                "First allocation must clear arrears");
        assertEquals(CurrencyConfig.money("5000"), allocs.get(0).getAllocated(),
                "Arrears allocation must match full arrears amount");
    }

    @Test
    void allocationWithZeroOutstandingDoesNotInfiniteLoop() {
        StudentFeeLedger ledger = new StudentFeeLedger("S-ZERO");
        ledger.charge("BOARD", CurrencyConfig.money("5000"));
        ledger.pay("BOARD", CurrencyConfig.money("5000"));

        FeeStructure fs = new FeeStructure(2026, "ALL", BoardingStatus.BOARDING, "Test");
        fs.addItem(new FeeStructureItem("BOARD", "Boarding", AcademicTerm.TERM_1, BoardingStatus.BOARDING, CurrencyConfig.money("5000")));
        FeeStructureStore.getInstance().addStructure(fs);

        ReceiptAllocationEngine engine = new ReceiptAllocationEngine(FeeStructureStore.getInstance());
        List<FeeAllocation> allocs = engine.allocate(ledger, CurrencyConfig.money("1000"), fs, AcademicTerm.TERM_1);

        BigDecimal total = allocs.stream().map(FeeAllocation::getAllocated)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        assertEquals(CurrencyConfig.money("1000"), total,
                "Fully paid student's payment should go to advance");
    }

    @Test
    void allocationHandlesPartialPaymentWithMultipleVoteheads() {
        StudentFeeLedger ledger = new StudentFeeLedger("S-PARTIAL");
        ledger.charge("BOARD", CurrencyConfig.money("10000"));
        ledger.charge("ACT", CurrencyConfig.money("5000"));

        ReceiptAllocationEngine engine = new ReceiptAllocationEngine(FeeStructureStore.getInstance());
        List<FeeAllocation> allocs = engine.allocate(ledger, CurrencyConfig.money("3000"));

        BigDecimal totalAllocated = allocs.stream()
                .map(FeeAllocation::getAllocated)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        assertEquals(CurrencyConfig.money("3000"), totalAllocated,
                "Partial payment total must equal payment amount");
    }

    // ========================================
    // TEST: LedgerStore consistency
    // ========================================

    @Test
    void ledgerStoreRemoveByReceiptIdCleansUp() {
        addFeeStructure(BoardingStatus.BOARDING);
        Student student = createTestStudent("ADM-LED1", BoardingStatus.BOARDING);
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("10000"));

        ReceiptService service = new ReceiptService();
        ReceiptService.Result created = service.receivePayment(student, CurrencyConfig.money("10000"),
                PaymentMode.BANK_SLIP, "LED1-REF", LocalDate.now(), null);
        assertTrue(created.isSuccess());

        int txCount = LedgerStore.getInstance().getTransactions().size();
        assertTrue(txCount > 0, "Transactions should exist after receipt");

        LedgerStore.getInstance().removeByReceiptId(created.getReceipt().getId());

        assertEquals(0, LedgerStore.getInstance().getTransactions().size(),
                "All transactions should be removed");
        assertEquals(0, LedgerStore.getInstance().getLedgerEntries().size(),
                "All ledger entries should be removed");

        for (AccountType type : AccountType.values()) {
            assertEquals(CurrencyConfig.zero(), LedgerStore.getInstance().getAccountBalance(type),
                    "Account balance for " + type + " should be zero after removal");
        }
    }

    @Test
    void ledgerStoreRecalculateBalancesIsCorrect() {
        addFeeStructure(BoardingStatus.BOARDING);
        Student student = createTestStudent("ADM-LED2", BoardingStatus.BOARDING);
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("5000"));

        ReceiptService service = new ReceiptService();
        service.receivePayment(student, CurrencyConfig.money("5000"),
                PaymentMode.MPESA, "LED2-REF", LocalDate.now(), null);

        BigDecimal cashBefore = LedgerStore.getInstance().getAccountBalance(AccountType.CASH_AT_BANK);

        LedgerStore.getInstance().recalculateBalances();
        BigDecimal cashAfter = LedgerStore.getInstance().getAccountBalance(AccountType.CASH_AT_BANK);

        assertEquals(0, cashBefore.compareTo(cashAfter),
                "recalculateBalances must produce the same result as incremental updates");
    }

    // ========================================
    // TEST: Receipt validation
    // ========================================

    @Test
    void validatorRejectsNullStudent() {
        ReceiptValidator validator = new ReceiptValidator();
        List<String> errors = validator.validate(null, CurrencyConfig.money("1000"),
                PaymentMode.BANK_SLIP, "REF");
        assertFalse(errors.isEmpty(), "Should reject null student");
    }

    @Test
    void validatorRejectsZeroAmount() {
        Student student = createTestStudent("ADM-V0", BoardingStatus.BOARDING);
        ReceiptValidator validator = new ReceiptValidator();
        List<String> errors = validator.validate(student, CurrencyConfig.zero(),
                PaymentMode.BANK_SLIP, "REF");
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("amount")),
                "Should reject zero amount");
    }

    @Test
    void validatorRejectsNegativeAmount() {
        Student student = createTestStudent("ADM-V1", BoardingStatus.BOARDING);
        ReceiptValidator validator = new ReceiptValidator();
        List<String> errors = validator.validate(student, CurrencyConfig.money("-100"),
                PaymentMode.BANK_SLIP, "REF");
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("amount")),
                "Should reject negative amount");
    }

    // ========================================
    // TEST: Full lifecycle
    // ========================================

    @Test
    void fullReceiptLifecycleCreateVerifyReverse() {
        addFeeStructure(BoardingStatus.BOARDING);
        Student student = createTestStudent("ADM-LC", BoardingStatus.BOARDING);
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("20000"));

        ReceiptService service = new ReceiptService();

        // 1. Create receipt
        ReceiptService.Result created = service.receivePayment(student, CurrencyConfig.money("10000"),
                PaymentMode.MPESA, "LC-REF-001", LocalDate.now(), "Lifecycle test");
        assertTrue(created.isSuccess());
        assertEquals(CurrencyConfig.money("10000"), ledger.getPaid("BOARD"));

        // 2. Verify receipt
        assertTrue(service.verifyReceipt(created.getReceipt()), "Receipt should verify");

        // 3. Reverse receipt
        ReceiptService.Result reversed = service.reverseReceipt(created.getReceipt(), "Lifecycle reversal");
        assertTrue(reversed.isSuccess());
        assertEquals(CurrencyConfig.zero(), ledger.getPaid("BOARD"));

        // 4. Verify reversed receipt is marked
        assertTrue(created.getReceipt().isReversed());

        // 5. Double-entry balanced throughout
        BigDecimal totalDebits = LedgerStore.getInstance().getTransactions().stream()
                .map(t -> t.getDebit())
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        BigDecimal totalCredits = LedgerStore.getInstance().getTransactions().stream()
                .map(t -> t.getCredit())
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        assertEquals(0, totalDebits.compareTo(totalCredits),
                "Double-entry must remain balanced through full lifecycle");
    }

    @Test
    void multipleReceiptsAndPartialReversalsMaintainBalance() {
        addFeeStructure(BoardingStatus.BOARDING);
        Student student = createTestStudent("ADM-MULTI", BoardingStatus.BOARDING);
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("30000"));

        ReceiptService service = new ReceiptService();

        // Receipt 1: 10000
        ReceiptService.Result r1 = service.receivePayment(student, CurrencyConfig.money("10000"),
                PaymentMode.MPESA, "MULTI-1", LocalDate.now(), null);
        assertTrue(r1.isSuccess());

        // Receipt 2: 15000
        ReceiptService.Result r2 = service.receivePayment(student, CurrencyConfig.money("15000"),
                PaymentMode.BANK_SLIP, "MULTI-2", LocalDate.now(), null);
        assertTrue(r2.isSuccess());

        assertEquals(CurrencyConfig.money("25000"), ledger.getPaid("BOARD"));

        // Reverse receipt 1
        ReceiptService.Result rv1 = service.reverseReceipt(r1.getReceipt(), "Partial reversal");
        assertTrue(rv1.isSuccess());
        assertEquals(CurrencyConfig.money("15000"), ledger.getPaid("BOARD"));

        // Reverse receipt 2
        ReceiptService.Result rv2 = service.reverseReceipt(r2.getReceipt(), "Full reversal");
        assertTrue(rv2.isSuccess());
        assertEquals(CurrencyConfig.zero(), ledger.getPaid("BOARD"));

        // Ledger fully restored
        assertEquals(CurrencyConfig.money("30000"), ledger.getOutstanding("BOARD"));
    }

    // ========================================
    // TEST: Receipt number allocation
    // ========================================

    @Test
    void receiptNumbersAreSequential() {
        addFeeStructure(BoardingStatus.BOARDING);
        Student student = createTestStudent("ADM-SEQ", BoardingStatus.BOARDING);
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("50000"));

        ReceiptService service = new ReceiptService();
        ReceiptService.Result r1 = service.receivePayment(student, CurrencyConfig.money("10000"),
                PaymentMode.MPESA, "SEQ-1", LocalDate.now(), null);
        ReceiptService.Result r2 = service.receivePayment(student, CurrencyConfig.money("10000"),
                PaymentMode.MPESA, "SEQ-2", LocalDate.now(), null);
        ReceiptService.Result r3 = service.receivePayment(student, CurrencyConfig.money("10000"),
                PaymentMode.MPESA, "SEQ-3", LocalDate.now(), null);

        assertTrue(r1.isSuccess() && r2.isSuccess() && r3.isSuccess());
        long n1 = r1.getReceipt().getReceiptNumber();
        long n2 = r2.getReceipt().getReceiptNumber();
        long n3 = r3.getReceipt().getReceiptNumber();

        assertEquals(n1 + 1, n2, "Receipt numbers must be sequential");
        assertEquals(n2 + 1, n3, "Receipt numbers must be sequential");
    }
}
