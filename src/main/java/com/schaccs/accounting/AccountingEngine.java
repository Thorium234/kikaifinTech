package com.schaccs.accounting;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.TransactionType;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.model.finance.JournalEntry;
import com.schaccs.store.LedgerStore;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Single entry point for posting financial transactions.
 * UI and services must never update balances outside this engine.
 */
public class AccountingEngine {

    private final DoubleEntryEngine doubleEntryEngine;
    private final LedgerStore ledgerStore;

    public AccountingEngine() {
        this(new DoubleEntryEngine(), LedgerStore.getInstance());
    }

    public AccountingEngine(DoubleEntryEngine doubleEntryEngine, LedgerStore ledgerStore) {
        this.doubleEntryEngine = doubleEntryEngine;
        this.ledgerStore = ledgerStore;
    }

    public void postTransaction(JournalEntry journal, TransactionType type, String studentId,
                               String receiptId, String voucherId) {
        String user = AppConfig.getInstance().getCurrentUser();
        doubleEntryEngine.postJournal(journal, user, studentId, receiptId, voucherId, type);
    }

    public void postFeeReceiptLine(String receiptRef, String description, AccountType accountType,
                                    String voteheadCode, BigDecimal amount, String studentId,
                                    String receiptId, String voucherId, LocalDate date) {
        String user = AppConfig.getInstance().getCurrentUser();
        doubleEntryEngine.postFeeReceipt(receiptRef, description, accountType, voteheadCode,
                CurrencyConfig.money(amount), studentId, receiptId, voucherId, user, date);
    }

    /**
     * Post a fee billing entry: Debit AR, Credit Income.
     * Called when fees are charged to a student.
     */
    public void postFeeBillingLine(String ref, String description, AccountType incomeAccount,
                                    String voteheadCode, BigDecimal amount, String studentId, LocalDate date) {
        String user = AppConfig.getInstance().getCurrentUser();
        doubleEntryEngine.postFeeBilling(ref, description, incomeAccount, voteheadCode,
                CurrencyConfig.money(amount), studentId, user, date);
    }

    public BigDecimal accountBalance(AccountType type) {
        return ledgerStore.getAccountBalance(type);
    }

    public java.util.List<FinancialTransaction> recentTransactions() {
        return ledgerStore.getTransactions();
    }
}
