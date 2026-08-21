package com.schaccs.accounting;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.TransactionType;
import com.schaccs.model.finance.JournalEntry;
import com.schaccs.store.LedgerStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DoubleEntryEngineTest {

    private LedgerStore ledgerStore;
    private DoubleEntryEngine engine;

    @BeforeEach
    void setUp() {
        ledgerStore = LedgerStore.getInstance();
        ledgerStore.clear();
        engine = new DoubleEntryEngine(ledgerStore);
    }

    @AfterEach
    void tearDown() {
        ledgerStore.clear();
    }

    // --- Balanced journal tests ---

    @Test
    @DisplayName("postJournal posts balanced journal with hash chain")
    void postJournal_balanced() {
        JournalEntry journal = new JournalEntry();
        journal.setDate(LocalDate.of(2026, 3, 15));
        journal.setReference("TEST-001");
        journal.setNarration("Test journal");
        journal.addLine(AccountType.CASH_AT_BANK, null, new BigDecimal("5000"), CurrencyConfig.zero(), "Debit bank");
        journal.addLine(AccountType.TUITION_FEES, null, CurrencyConfig.zero(), new BigDecimal("5000"), "Credit tuition");

        engine.postJournal(journal, "test-user", null, null, null, TransactionType.FEE_RECEIPT);

        assertEquals(2, ledgerStore.getTransactions().size());
        assertEquals(2, ledgerStore.getLedgerEntries().size());
        assertNotNull(ledgerStore.getLedgerEntries().get(0).getHash());
        assertNotNull(ledgerStore.getLedgerEntries().get(1).getHash());
    }

    @Test
    @DisplayName("postJournal rejects unbalanced journal")
    void postJournal_unbalanced_throws() {
        JournalEntry journal = new JournalEntry();
        journal.addLine(AccountType.CASH_AT_BANK, null, new BigDecimal("5000"), CurrencyConfig.zero(), "debit");
        journal.addLine(AccountType.TUITION_FEES, null, CurrencyConfig.zero(), new BigDecimal("3000"), "credit");

        assertThrows(IllegalStateException.class, () ->
                engine.postJournal(journal, "user", null, null, null, TransactionType.FEE_RECEIPT));
    }

    @Test
    @DisplayName("hash chain links entries correctly")
    void hashChain_linksCorrectly() {
        JournalEntry j1 = new JournalEntry();
        j1.setDate(LocalDate.of(2026, 1, 1));
        j1.addLine(AccountType.CASH_AT_BANK, null, new BigDecimal("100"), CurrencyConfig.zero(), "first");
        j1.addLine(AccountType.TUITION_FEES, null, CurrencyConfig.zero(), new BigDecimal("100"), "first");
        engine.postJournal(j1, "user", null, null, null, TransactionType.FEE_RECEIPT);

        JournalEntry j2 = new JournalEntry();
        j2.setDate(LocalDate.of(2026, 1, 2));
        j2.addLine(AccountType.CASH_AT_BANK, null, new BigDecimal("200"), CurrencyConfig.zero(), "second");
        j2.addLine(AccountType.TUITION_FEES, null, CurrencyConfig.zero(), new BigDecimal("200"), "second");
        engine.postJournal(j2, "user", null, null, null, TransactionType.FEE_RECEIPT);

        String hash0 = ledgerStore.getLedgerEntries().get(2).getHash();
        String hash1 = ledgerStore.getLedgerEntries().get(0).getHash();
        assertNotEquals(hash0, hash1);
        assertEquals(hash0, ledgerStore.getLedgerEntries().get(1).getPreviousHash());
    }

    // --- Fee receipt tests ---

    @Test
    @DisplayName("postFeeReceipt debits BANK_TUITION for tuition income")
    void postFeeReceipt_tuitionIncome() {
        engine.postFeeReceipt("R-001", "Tuition payment", AccountType.TUITION_FEES,
                "TUITION", new BigDecimal("15000"), "S1", "R-1", null, "admin", LocalDate.of(2026, 3, 1));

        assertEquals(2, ledgerStore.getTransactions().size());
        assertEquals(AccountType.BANK_TUITION, ledgerStore.getTransactions().get(1).getAccountType());
        assertEquals(AccountType.ACCOUNTS_RECEIVABLE, ledgerStore.getTransactions().get(0).getAccountType());
    }

    @Test
    @DisplayName("postFeeReceipt debits BANK_BOARDING for boarding income")
    void postFeeReceipt_boardingIncome() {
        engine.postFeeReceipt("R-002", "Boarding payment", AccountType.BOARDING_FEES,
                "BOARDING", new BigDecimal("20000"), "S2", "R-2", null, "admin", LocalDate.of(2026, 3, 1));

        assertEquals(AccountType.BANK_BOARDING, ledgerStore.getTransactions().get(1).getAccountType());
    }

    @Test
    @DisplayName("postFeeReceipt debits CASH_AT_BANK for unrestricted income")
    void postFeeReceipt_unrestrictedIncome() {
        engine.postFeeReceipt("R-003", "Misc payment", AccountType.OTHER_INCOME,
                "OTHER", new BigDecimal("5000"), "S3", "R-3", null, "admin", LocalDate.of(2026, 3, 1));

        assertEquals(AccountType.CASH_AT_BANK, ledgerStore.getTransactions().get(1).getAccountType());
    }

    // --- Fee billing tests ---

    @Test
    @DisplayName("postFeeBilling debits AR and credits income account")
    void postFeeBilling() {
        engine.postFeeBilling("FEE-001", "Term 1 tuition", AccountType.TUITION_FEES,
                "TUITION", new BigDecimal("15000"), "S1", "admin", LocalDate.of(2026, 3, 1));

        assertEquals(2, ledgerStore.getTransactions().size());
        assertEquals(AccountType.ACCOUNTS_RECEIVABLE, ledgerStore.getTransactions().get(1).getAccountType());
        assertEquals(AccountType.TUITION_FEES, ledgerStore.getTransactions().get(0).getAccountType());
    }

    @Test
    @DisplayName("postFeeBilling skips zero and negative amounts")
    void postFeeBilling_zeroAmount() {
        engine.postFeeBilling("FEE-002", "Zero", AccountType.TUITION_FEES,
                "TUITION", CurrencyConfig.zero(), "S1", "admin", LocalDate.of(2026, 3, 1));

        assertEquals(0, ledgerStore.getTransactions().size());
    }

    // --- Bank resolution tests ---

    @Test
    @DisplayName("resolveBankForIncome maps GOVT capitation to BANK_TUITION")
    void resolveBankForIncome_govtCapitation() {
        assertEquals(AccountType.BANK_TUITION, DoubleEntryEngine.resolveBankForIncome(AccountType.GOVT_CAPITATION_TUITION));
    }

    @Test
    @DisplayName("resolveBankForIncome maps infrastructure grant to BANK_INFRASTRUCTURE")
    void resolveBankForIncome_infraGrant() {
        assertEquals(AccountType.BANK_INFRASTRUCTURE, DoubleEntryEngine.resolveBankForIncome(AccountType.INFRASTRUCTURE_GRANT));
    }

    @Test
    @DisplayName("resolveBankForIncome maps boarding fees to BANK_BOARDING")
    void resolveBankForIncome_boarding() {
        assertEquals(AccountType.BANK_BOARDING, DoubleEntryEngine.resolveBankForIncome(AccountType.BOARDING_FEES));
    }

    @Test
    @DisplayName("resolveBankForIncome maps null to CASH_AT_BANK")
    void resolveBankForIncome_null() {
        assertEquals(AccountType.CASH_AT_BANK, DoubleEntryEngine.resolveBankForIncome(null));
    }

    @Test
    @DisplayName("resolveBankForExpense maps TLM to BANK_TUITION")
    void resolveBankForExpense_tlm() {
        assertEquals(AccountType.BANK_TUITION, DoubleEntryEngine.resolveBankForExpense(AccountType.TEACHING_LEARNING_MATERIALS));
    }

    @Test
    @DisplayName("resolveBankForExpense maps infrastructure expansion to BANK_INFRASTRUCTURE")
    void resolveBankForExpense_infra() {
        assertEquals(AccountType.BANK_INFRASTRUCTURE, DoubleEntryEngine.resolveBankForExpense(AccountType.INFRASTRUCTURE_EXPANSION));
    }

    @Test
    @DisplayName("resolveBankForExpense maps GOVT group to BANK_TUITION")
    void resolveBankForExpense_govtGroup() {
        AccountType govExpense = AccountType.values()[0];
        for (AccountType at : AccountType.values()) {
            if ("GOVT".equals(at.getRestrictedGroup()) && at.isDebitNormal()) {
                govExpense = at;
                break;
            }
        }
        assertEquals(AccountType.BANK_TUITION, DoubleEntryEngine.resolveBankForExpense(govExpense));
    }

    @Test
    @DisplayName("resolveBankForExpense maps null to CASH_AT_BANK")
    void resolveBankForExpense_null() {
        assertEquals(AccountType.CASH_AT_BANK, DoubleEntryEngine.resolveBankForExpense(null));
    }

    // --- Ledger balance tests ---

    @Test
    @DisplayName("ledger balances update correctly after posting")
    void ledgerBalance_updatesCorrectly() {
        JournalEntry journal = new JournalEntry();
        journal.addLine(AccountType.CASH_AT_BANK, null, new BigDecimal("10000"), CurrencyConfig.zero(), "debit");
        journal.addLine(AccountType.TUITION_FEES, null, CurrencyConfig.zero(), new BigDecimal("10000"), "credit");
        engine.postJournal(journal, "user", null, null, null, TransactionType.FEE_RECEIPT);

        // CASH_AT_BANK is debit-normal: balance should be +10000
        assertEquals(0, ledgerStore.getAccountBalance(AccountType.CASH_AT_BANK).compareTo(new BigDecimal("10000")));
        // TUITION_FEES is credit-normal: balance should be +10000
        assertEquals(0, ledgerStore.getAccountBalance(AccountType.TUITION_FEES).compareTo(new BigDecimal("10000")));
    }

    @Test
    @DisplayName("multiple postings accumulate in ledger balance")
    void ledgerBalance_accumulates() {
        JournalEntry j1 = new JournalEntry();
        j1.addLine(AccountType.CASH_AT_BANK, null, new BigDecimal("5000"), CurrencyConfig.zero(), "one");
        j1.addLine(AccountType.TUITION_FEES, null, CurrencyConfig.zero(), new BigDecimal("5000"), "one");
        engine.postJournal(j1, "user", null, null, null, TransactionType.FEE_RECEIPT);

        JournalEntry j2 = new JournalEntry();
        j2.addLine(AccountType.CASH_AT_BANK, null, new BigDecimal("3000"), CurrencyConfig.zero(), "two");
        j2.addLine(AccountType.TUITION_FEES, null, CurrencyConfig.zero(), new BigDecimal("3000"), "two");
        engine.postJournal(j2, "user", null, null, null, TransactionType.FEE_RECEIPT);

        assertEquals(0, ledgerStore.getAccountBalance(AccountType.CASH_AT_BANK).compareTo(new BigDecimal("8000")));
    }
}
