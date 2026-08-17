package com.schaccs.accounting;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.TransactionType;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.model.finance.JournalEntry;
import com.schaccs.model.finance.LedgerEntry;
import com.schaccs.store.LedgerStore;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Posts balanced journal entries into the ledger store.
 */
public class DoubleEntryEngine {

    private static final String GENESIS_HASH = "0".repeat(64);

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

        String prevHash = ledgerStore.lastHash();

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
            tx.setCreatedAt(journal.getDate().atStartOfDay());
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
            entry.setPreviousHash(prevHash);

            String hashPayload = prevHash
                    + "|" + (line.getAccountType() != null ? line.getAccountType().name() : "")
                    + "|" + nvl(line.getVoteheadCode())
                    + "|" + nvl(journal.getReference())
                    + "|" + line.getDebit().toPlainString()
                    + "|" + line.getCredit().toPlainString()
                    + "|" + journal.getDate();
            String hash = sha256(hashPayload);
            entry.setHash(hash);
            prevHash = hash;

            ledgerStore.addLedgerEntry(entry);
        }
    }

    /**
     * Fee receipt: Debit Cash at Bank, Credit Accounts Receivable.
     * Revenue was already recognized at billing time via {@link #postFeeBilling}.
     */
    public void postFeeReceipt(String reference, String narration, AccountType incomeAccount,
                                String voteheadCode, BigDecimal amount, String studentId,
                                String receiptId, String voucherId, String createdBy, java.time.LocalDate date) {
        JournalEntry journal = new JournalEntry();
        journal.setDate(date);
        journal.setReference(reference);
        journal.setNarration(narration);
        // Debit cash at bank (asset)
        journal.addLine(AccountType.CASH_AT_BANK, "CASH_BANK", amount, CurrencyConfig.zero(),
                "Cash/Bank — " + narration);
        // Credit accounts receivable (settle student's outstanding balance)
        journal.addLine(AccountType.ACCOUNTS_RECEIVABLE, voteheadCode, CurrencyConfig.zero(), amount,
                "AR settlement — " + narration);
        postJournal(journal, createdBy, studentId, receiptId, voucherId, TransactionType.FEE_RECEIPT);
    }

    /**
     * Fee billing: Debit Accounts Receivable, Credit income votehead.
     * Recognizes revenue when the fee is charged to the student.
     */
    public void postFeeBilling(String reference, String narration, AccountType incomeAccount,
                                String voteheadCode, BigDecimal amount, String studentId,
                                String createdBy, java.time.LocalDate date) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        JournalEntry journal = new JournalEntry();
        journal.setDate(date);
        journal.setReference(reference);
        journal.setNarration(narration);
        // Debit accounts receivable (asset — student owes this)
        journal.addLine(AccountType.ACCOUNTS_RECEIVABLE, voteheadCode, amount, CurrencyConfig.zero(),
                "AR — " + narration);
        // Credit income votehead (revenue recognized)
        journal.addLine(incomeAccount, voteheadCode, CurrencyConfig.zero(), amount, narration);
        postJournal(journal, createdBy, studentId, null, null, TransactionType.FEE_CHARGE);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
