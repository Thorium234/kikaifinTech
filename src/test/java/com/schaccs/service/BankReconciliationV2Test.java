package com.schaccs.service;

import com.schaccs.accounting.DoubleEntryEngine;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.finance.BankReconciliation;
import com.schaccs.model.finance.BankStatementEntry;
import com.schaccs.model.finance.JournalEntry;
import com.schaccs.model.student.Student;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.finance.BankReconciliationService;
import com.schaccs.service.finance.BankStatementImportService;
import com.schaccs.store.BankReconciliationStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.StudentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BankReconciliationV2Test {

    private static final AccountType BANK = AccountType.BANK_BOARDING;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private DoubleEntryEngine engine() {
        return new DoubleEntryEngine(LedgerStore.getInstance());
    }

    private BankReconciliationService service() {
        return new BankReconciliationService(BankReconciliationStore.getInstance(),
                LedgerStore.getInstance(), engine());
    }

    private void postDeposit(String ref, String narration, BigDecimal amt, LocalDate date) {
        JournalEntry j = new JournalEntry();
        j.setDate(date);
        j.setReference(ref);
        j.setNarration(narration);
        j.addLine(BANK, null, amt, CurrencyConfig.zero(), narration);
        j.addLine(AccountType.ACCOUNTS_RECEIVABLE, null, CurrencyConfig.zero(), amt, narration);
        engine().postJournal(j, "system", null, null, null,
                com.schaccs.enums.TransactionType.FEE_RECEIPT);
    }

    private void postCheque(String ref, String narration, BigDecimal amt, LocalDate date) {
        JournalEntry j = new JournalEntry();
        j.setDate(date);
        j.setReference(ref);
        j.setNarration(narration);
        j.addLine(AccountType.ACCOUNTS_PAYABLE, null, amt, CurrencyConfig.zero(), narration);
        j.addLine(BANK, null, CurrencyConfig.zero(), amt, narration);
        engine().postJournal(j, "system", null, null, null,
                com.schaccs.enums.TransactionType.PAYMENT_VOUCHER);
    }

    private BankReconciliation seededRecon(LocalDate date, BigDecimal stmtBalance) {
        BankReconciliationService s = service();
        BankReconciliation rec = s.createReconciliation(BANK, date, stmtBalance, null);
        rec.getItems().addAll(s.calculateUnclearedItems(rec, BANK));
        return rec;
    }

    // =====================================================================
    // V2 — state model
    // =====================================================================

    @Test
    void createReconciliationTagsBankAccountAndBookBalance() {
        postDeposit("RCPT-1", "ADM2026001 fees", new BigDecimal("50000"), LocalDate.of(2026, 6, 1));
        BankReconciliation rec = service().createReconciliation(BANK, LocalDate.of(2026, 6, 30),
                new BigDecimal("50000"), null);
        assertEquals(BANK.name(), rec.getBankAccountType());
        assertEquals(new BigDecimal("50000.00"), rec.getBookBalance());
        assertEquals("DRAFT", rec.getStatus());
    }

    // =====================================================================
    // V3 — unpresented cheques / deposits in transit
    // =====================================================================

    @Test
    void unpresentedChequesAndDepositsInTransitAreDerived() {
        postDeposit("RCPT-1", "ADM2026001", new BigDecimal("50000"), LocalDate.of(2026, 6, 1));
        postCheque("PV-99", "mazao", new BigDecimal("20000"), LocalDate.of(2026, 6, 5));
        BankReconciliation rec = seededRecon(LocalDate.of(2026, 6, 30), BigDecimal.ZERO);
        assertEquals(new BigDecimal("20000.00"), service().getUnpresentedCheques(rec));
        assertEquals(new BigDecimal("50000.00"), service().getDepositsInTransit(rec));
        // adjusted balance = statement balance + deposits in transit - unpresented cheques
        rec.setStatementBalance(new BigDecimal("100000"));
        rec.calculate();
        assertEquals(new BigDecimal("130000.00"), rec.getAdjustedBalance());
    }

    // =====================================================================
    // V5 — auto-match against imported statement
    // =====================================================================

    @Test
    void autoMatchClearsDepositsAndChequesAgainstStatement() {
        postDeposit("RCPT-1", "ADM2026001 fees deposit", new BigDecimal("50000"), LocalDate.of(2026, 6, 1));
        postCheque("PV-99", "maize supplier serial 000123", new BigDecimal("20000"), LocalDate.of(2026, 6, 5));
        BankReconciliation rec = seededRecon(LocalDate.of(2026, 6, 30), BigDecimal.ZERO);

        BankStatementEntry dep = entry(LocalDate.of(2026, 6, 2), "payment ADM2026001", "STMT-D1",
                BigDecimal.ZERO, new BigDecimal("50000"), new BigDecimal("150000"));
        BankStatementEntry chq = entry(LocalDate.of(2026, 6, 9), "cheque 000123 cashed", "STMT-C1",
                new BigDecimal("20000"), BigDecimal.ZERO, new BigDecimal("130000"));

        int matched = service().autoMatchFromStatement(rec, BANK, List.of(dep, chq));
        assertEquals(2, matched);
        BankReconciliation.ReconciliationItem depItem = byAmount(rec, new BigDecimal("50000"));
        BankReconciliation.ReconciliationItem chqItem = byAmount(rec, new BigDecimal("20000"));
        assertTrue(depItem.isCleared());
        assertTrue(chqItem.isCleared());
        assertEquals(LocalDate.of(2026, 6, 2), depItem.getClearingDate());
        assertEquals("STMT-D1", depItem.getMatchedStatementRef());
        assertEquals(LocalDate.of(2026, 6, 9), chqItem.getClearingDate());
        assertTrue(dep.isReconciled());
        assertTrue(chq.isReconciled());
    }

    private BankReconciliation.ReconciliationItem byAmount(BankReconciliation rec, BigDecimal amt) {
        return rec.getItems().stream()
                .filter(i -> i.getAmount().compareTo(amt) == 0)
                .findFirst().orElseThrow(() -> new AssertionError("no item of " + amt));
    }

    // =====================================================================
    // V6 — manual pairing & direct bank credit/debit
    // =====================================================================

    @Test
    void manualPairMarksItemClearedWithStatementRef() {
        postDeposit("RCPT-1", "orphan deposit", new BigDecimal("50000"), LocalDate.of(2026, 6, 1));
        BankReconciliation rec = seededRecon(LocalDate.of(2026, 6, 30), BigDecimal.ZERO);
        BankStatementEntry e = entry(LocalDate.of(2026, 6, 3), "agent deposit", "STMT-X",
                BigDecimal.ZERO, new BigDecimal("50000"), new BigDecimal("150000"));
        service().pairItemToStatement(rec, rec.getItems().get(0).getId(), e, "2026-06-03");
        assertTrue(rec.getItems().get(0).isCleared());
        assertEquals("STMT-X", rec.getItems().get(0).getMatchedStatementRef());
    }

    @Test
    void generateReceiptFromStatementPostsAReceipt() {
        Student s = new Student();
        s.setAdmissionNumber("ADM2026001");
        s.setName("Test Student");
        s.setFormClass("Form 3");
        s.setStream("A");
        s.setBoardingStatus(BoardingStatus.DAY);
        s.setGender("M");
        s.setPhone("0700000000");
        s.setStatus(StudentStatus.ACTIVE);
        s.setAcademicYear(2026);
        StudentStore.getInstance().add(s);

        var result = service().generateReceiptFromStatement(s, new BigDecimal("20000"),
                LocalDate.of(2026, 6, 5), "STMT-D3", "direct bank credit");
        assertTrue(result.isSuccess(), "Receipt should be raised from the statement line");
    }

    @Test
    void postBankChargesCreditsTheBankAccount() {
        BigDecimal before = service().getBookBalance(BANK);
        service().postBankCharges(BANK, LocalDate.of(2026, 6, 30), "STMT-BCH", "ledger fees",
                new BigDecimal("1200"));
        BigDecimal after = service().getBookBalance(BANK);
        assertEquals(before.subtract(new BigDecimal("1200")), after);
        assertTrue(ledgerBalances(), "Ledger must remain in balance after bank charges");
    }

    // =====================================================================
    // V7 — M-Pesa in-transit clearing
    // =====================================================================

    @Test
    void sweepMpesaClearingMovesFundsFromClearingToBank() {
        BigDecimal bankBefore = service().getBookBalance(BANK);
        // seed an in-transit credit standing in the clearing account
        JournalEntry j = new JournalEntry();
        j.setDate(LocalDate.of(2026, 6, 30));
        j.setReference("MPESA-IMB");
        j.setNarration("mpesa pending");
        j.addLine(AccountType.MPESA_CLEARING, null, new BigDecimal("150000"), CurrencyConfig.zero(), "pending");
        j.addLine(AccountType.ACCOUNTS_RECEIVABLE, null, CurrencyConfig.zero(), new BigDecimal("150000"), "pending");
        engine().postJournal(j, "system", null, null, null,
                com.schaccs.enums.TransactionType.FEE_RECEIPT);

        service().sweepMpesaClearing(BANK, LocalDate.of(2026, 6, 30), "SWEEP-1",
                new BigDecimal("150000"), "bulk paybill transfer");

        assertEquals(bankBefore.add(new BigDecimal("150000")), service().getBookBalance(BANK));
        assertEquals(BigDecimal.ZERO.setScale(2), service().getBookBalance(AccountType.MPESA_CLEARING));
        assertTrue(ledgerBalances(), "Transfer must keep the ledger balanced");
    }

    // =====================================================================
    // V9 — guardrails
    // =====================================================================

    @Test
    void clearingDateCannotPrecedePostingDate() {
        postDeposit("RCPT-1", "ADM2026001", new BigDecimal("50000"), LocalDate.of(2026, 6, 10));
        BankReconciliation rec = seededRecon(LocalDate.of(2026, 6, 30), BigDecimal.ZERO);
        assertThrows(IllegalStateException.class, () -> service().markItemCleared(rec,
                rec.getItems().get(0), LocalDate.of(2026, 6, 1), "STMT-D1", "STMT-D1", "system"));
        // a valid clearing date is accepted
        service().markItemCleared(rec, rec.getItems().get(0), LocalDate.of(2026, 6, 12),
                "STMT-D1", "STMT-D1", "system");
        assertTrue(rec.getItems().get(0).isCleared());
    }

    @Test
    void reconciledPeriodIsImmutable() {
        postDeposit("RCPT-1", "ADM2026001", new BigDecimal("50000"), LocalDate.of(2026, 6, 1));
        BankReconciliation rec = seededRecon(LocalDate.of(2026, 6, 30), new BigDecimal("50000"));
        // clear to make difference zero, then finalize
        service().markItemCleared(rec, rec.getItems().get(0), LocalDate.of(2026, 6, 15),
                "STMT-D1", "STMT-D1", "system");
        service().finalizeReconciliation(rec);
        assertEquals("RECONCILED", rec.getStatus());
        assertThrows(IllegalStateException.class, () -> service().markItemCleared(rec,
                rec.getItems().get(0), LocalDate.of(2026, 6, 16), "STMT-D2", "STMT-D2", "system"));
        assertThrows(IllegalStateException.class,
                () -> service().addItem(rec, new BankReconciliation.ReconciliationItem()));
    }

    @Test
    void newMonthBlockedWhilePreviousMonthHasUnresolvedVariance() {
        postCheque("PV-99", "uncleared", new BigDecimal("20000"), LocalDate.of(2026, 5, 20));
        // May reconciliation intentionally left with variance (difference != 0)
        BankReconciliation may = service().createReconciliation(BANK, LocalDate.of(2026, 5, 31),
                new BigDecimal("30000"), null);
        may.getItems().addAll(service().calculateUnclearedItems(may, BANK));
        may.setStatementBalance(new BigDecimal("10000")); // mismatch -> variance
        may.calculate();
        assertTrue(may.getDifference().compareTo(BigDecimal.ZERO) != 0);

        // Opening June must be blocked
        assertThrows(IllegalStateException.class,
                () -> service().createReconciliation(BANK, LocalDate.of(2026, 6, 30), BigDecimal.ZERO, null));
        // Same-month reconciliation is allowed
        assertDoesNotThrow(() -> service().createReconciliation(BANK, LocalDate.of(2026, 5, 30),
                BigDecimal.ZERO, null));
    }

    private boolean ledgerBalances() {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (var e : LedgerStore.getInstance().getLedgerEntries()) {
            debit = debit.add(e.getDebit());
            credit = credit.add(e.getCredit());
        }
        return debit.compareTo(credit) == 0;
    }

    @Test
    void finalizeReconciliationRequiresZeroDifference() {
        // A transaction posted AFTER the statement date is excluded from uncleared
        // items but still raises the book balance, forcing a non-zero variance.
        postDeposit("RCPT-1", "ADM2026001", new BigDecimal("50000"), LocalDate.of(2026, 7, 1));
        BankReconciliation rec = service().createReconciliation(BANK, LocalDate.of(2026, 6, 30),
                BigDecimal.ZERO, null);
        rec.getItems().addAll(service().calculateUnclearedItems(rec, BANK));
        rec.calculate();
        assertTrue(rec.getDifference().compareTo(BigDecimal.ZERO) != 0,
                "Difference should be non-zero so finalize is blocked");
        assertThrows(IllegalStateException.class, () -> service().finalizeReconciliation(rec));
    }

    // =====================================================================
    // V5 — statement import parsing (.csv)
    // =====================================================================

    @Test
    void csvStatementImportParsesRows() throws Exception {
        Path csv = Files.createTempFile("stmt", ".csv");
        Files.writeString(csv, "Date,Narration,Reference,Withdrawal,Deposit,Balance\n"
                + "01/06/2026,payment ADM2026001,STMT-D1,,50000.00,150000.00\n"
                + "09/06/2026,cheque 123 cashed,STMT-C1,20000.00,,130000.00\n");
        List<BankStatementEntry> entries = new BankStatementImportService().importFile(csv);
        assertEquals(2, entries.size());
        assertEquals(LocalDate.of(2026, 6, 1), entries.get(0).getStatementDate());
        assertEquals(0, entries.get(0).getCredit().compareTo(new BigDecimal("50000")));
        assertEquals(0, entries.get(1).getDebit().compareTo(new BigDecimal("20000")));
        Files.deleteIfExists(csv);
    }

    private BankStatementEntry entry(LocalDate date, String desc, String ref,
                                     BigDecimal debit, BigDecimal credit, BigDecimal balance) {
        BankStatementEntry e = new BankStatementEntry();
        e.setStatementDate(date);
        e.setDescription(desc);
        e.setReference(ref);
        e.setDebit(debit);
        e.setCredit(credit);
        e.setBalance(balance);
        return e;
    }
}
