package com.schaccs.accounting;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.TransactionType;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.model.finance.JournalEntry;
import com.schaccs.model.finance.LedgerEntry;
import com.schaccs.store.LedgerStore;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Posts balanced journal entries into the ledger store.
 */
public class DoubleEntryEngine {

    private final LedgerStore ledgerStore;

    public DoubleEntryEngine() {
        this(LedgerStore.getInstance());
    }

    public DoubleEntryEngine(LedgerStore ledgerStore) {
        this.ledgerStore = ledgerStore;
    }

    public void postJournal(JournalEntry journal, String createdBy, String studentId, String receiptId,
                             String voucherId, TransactionType type) {
        if (!journal.isBalanced()) {
            throw new IllegalStateException("Journal is not balanced: debits="
                    + journal.totalDebits() + " credits=" + journal.totalCredits());
        }

        for (JournalEntry.JournalLine line : journal.getLines()) {
            FinancialTransaction tx = new FinancialTransaction();
            tx.setDate(journal.getDate());
            tx.setType(type);
            tx.setAccountType(line.getAccountType());
            tx.setVoteheadCode(line.getVoteheadCode());
            tx.setReference(journal.getReference());
            tx.setDescription(line.getDescription() != null ? line.getDescription() : journal.getNarration());
            tx.setDebit(line.getDebit());
            tx.setCredit(line.getCredit());
            tx.setStudentId(studentId);
            tx.setReceiptId(receiptId);
            tx.setVoucherId(voucherId);
            tx.setCreatedBy(createdBy);
            tx.setCreatedAt(LocalDateTime.now());
            ledgerStore.addTransaction(tx);

            LedgerEntry entry = new LedgerEntry();
            entry.setDate(journal.getDate());
            entry.setAccountType(line.getAccountType());
            entry.setVoteheadCode(line.getVoteheadCode());
            entry.setReference(journal.getReference());
            entry.setDescription(tx.getDescription());
            entry.setDebit(line.getDebit());
            entry.setCredit(line.getCredit());
            entry.setTransactionId(tx.getId());
            ledgerStore.addLedgerEntry(entry);
        }
    }

    /**
     * Fee receipt: Debit Bank/Cash (School Fund), Credit income voteheads.
     * Simplified single-account model for V1 school fund collections.
     */
    public void postFeeReceipt(String reference, String narration, AccountType incomeAccount,
                                String voteheadCode, BigDecimal amount, String studentId,
                                String receiptId, String voucherId, String createdBy, java.time.LocalDate date) {
        JournalEntry journal = new JournalEntry();
        journal.setDate(date);
        journal.setReference(reference);
        journal.setNarration(narration);
        // Debit cash/bank under School Fund
        journal.addLine(AccountType.SCHOOL_FUND, "CASH_BANK", amount, CurrencyConfig.zero(),
                "Cash/Bank — " + narration);
        // Credit income
        journal.addLine(incomeAccount, voteheadCode, CurrencyConfig.zero(), amount, narration);
        postJournal(journal, createdBy, studentId, receiptId, voucherId, TransactionType.FEE_RECEIPT);
    }
}
