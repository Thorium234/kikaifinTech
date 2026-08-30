package com.schaccs.service.finance;

import com.schaccs.accounting.DoubleEntryEngine;
import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.PaymentMode;
import com.schaccs.enums.TransactionType;
import com.schaccs.model.finance.BankReconciliation;
import com.schaccs.model.finance.BankStatementEntry;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.model.finance.JournalEntry;
import com.schaccs.model.student.Student;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.store.BankReconciliationStore;
import com.schaccs.store.BankStatementStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.StudentStore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * V2 Cashbook Reconciliation Framework.
 *
 * <p>Bridges the internal double-entry general ledger and the external National
 * Bank statement. Every cashbook entry is Unreconciled until it is matched
 * against a bank-statement line; matched items become CLEARED and the period
 * can then be locked as RECONCILED. Handles the three structural anomalies the
 * framework is designed for:
 * <ul>
 *   <li>Unpresented cheques (outflow not yet cleared by the bank) — the
 *       adjusted bank balance subtracts them.</li>
 *   <li>Direct bank credits &amp; debits — this service can raise a receipt
 *       from a statement line or post a bank-charges voucher.</li>
 *   <li>M-Pesa Pay Bill clearing lag — an in-transit clearing journal moves
 *       funds from the M-Pesa Clearing account into the core bank account.</li>
 * </ul>
 *
 * <p>Guardrails (V9): immutable locking once RECONCILED, clearing-date cannot
 * precede posting-date, and a new month's reconciliation is blocked while a
 * previous month still carries an unresolved variance.</p>
 */
public class BankReconciliationService {

    private final BankReconciliationStore store;
    private final LedgerStore ledgerStore;
    private final DoubleEntryEngine engine;

    public BankReconciliationService() {
        this(BankReconciliationStore.getInstance(), LedgerStore.getInstance(), new DoubleEntryEngine());
    }

    public BankReconciliationService(BankReconciliationStore store, LedgerStore ledgerStore,
                                     DoubleEntryEngine engine) {
        this.store = store;
        this.ledgerStore = ledgerStore;
        this.engine = engine;
    }

    private String who() {
        try {
            String u = AppConfig.getInstance().getCurrentUser();
            return u == null || u.isBlank() ? "system" : u;
        } catch (Exception e) {
            return "system";
        }
    }

    public BigDecimal getBookBalance(AccountType accountType) {
        return ledgerStore.getAccountBalance(accountType);
    }

    // =====================================================================
    // V2 — State model
    // =====================================================================

    public BankReconciliation createReconciliation(AccountType accountType, LocalDate statementDate,
                                                   BigDecimal statementBalance, String notes) {
        ensureMonthOpenAllowed(accountType, statementDate);
        BankReconciliation rec = new BankReconciliation();
        rec.setStatementDate(statementDate);
        rec.setStatementBalance(statementBalance);
        rec.setBookBalance(getBookBalance(accountType));
        rec.setBankAccountType(accountType.name());
        rec.setStatus("DRAFT");
        rec.setNotes(notes);
        rec.setCreatedAt(LocalDateTime.now());
        store.add(rec);
        PersistenceService.getInstance().saveAll();
        return rec;
    }

    public void addItem(BankReconciliation rec, BankReconciliation.ReconciliationItem item) {
        ensureNotLocked(rec);
        rec.addItem(item);
        rec.calculate();
    }

    public List<BankReconciliation.ReconciliationItem> calculateUnclearedItems(BankReconciliation rec, AccountType accountType) {
        List<BankReconciliation.ReconciliationItem> items = new ArrayList<>();
        LocalDate statementDate = rec.getStatementDate();

        Set<String> reversedIds = ledgerStore.getTransactions().stream()
                .map(FinancialTransaction::getReversalOfId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (var tx : ledgerStore.getTransactions()) {
            if (tx.getAccountType() != accountType) continue;
            if (tx.getDate() != null && tx.getDate().isAfter(statementDate)) continue;
            if (reversedIds.contains(tx.getId())) continue;

            if (tx.getDebit().compareTo(BigDecimal.ZERO) > 0) {
                items.add(buildItem("DEPOSIT", tx));
            } else if (tx.getCredit().compareTo(BigDecimal.ZERO) > 0) {
                items.add(buildItem("CHEQUE", tx));
            }
        }
        return items;
    }

    private BankReconciliation.ReconciliationItem buildItem(String type, FinancialTransaction tx) {
        BankReconciliation.ReconciliationItem item = new BankReconciliation.ReconciliationItem();
        item.setType(type);
        item.setReference(tx.getReference());
        item.setDescription(tx.getDescription());
        item.setPostedDate(tx.getDate());
        item.setSource("CASHBOOK");
        item.setCleared(false);
        if ("DEPOSIT".equals(type)) {
            item.setAmount(tx.getDebit());
        } else {
            item.setAmount(tx.getCredit());
        }
        return item;
    }

    // =====================================================================
    // V3 — Unpresented cheques / deposits in transit
    // =====================================================================

    public BigDecimal getUnpresentedCheques(BankReconciliation rec) {
        BigDecimal total = CurrencyConfig.zero();
        for (var item : rec.getItems()) {
            if (!item.isCleared() && "CHEQUE".equals(item.getType())) {
                total = total.add(item.getAmount());
            }
        }
        return CurrencyConfig.money(total);
    }

    public BigDecimal getDepositsInTransit(BankReconciliation rec) {
        BigDecimal total = CurrencyConfig.zero();
        for (var item : rec.getItems()) {
            if (!item.isCleared() && "DEPOSIT".equals(item.getType())) {
                total = total.add(item.getAmount());
            }
        }
        return CurrencyConfig.money(total);
    }

    // =====================================================================
    // V5 — Auto-match against imported statement
    // =====================================================================

    /**
     * Exact-match pass over the imported statement. A statement entry is
     * matched to an unreconciled cashbook item when the amounts agree and the
     * references share a token (for deposits this is the student admission
     * number embedded in the narration; for withdrawals the cheque / voucher
     * reference). Matched items are assigned CLEARED with the bank date.
     *
     * @return the number of items matched in this pass.
     */
    public int autoMatchFromStatement(BankReconciliation rec, AccountType accountType,
                                      List<BankStatementEntry> statements) {
        if (isLocked(rec)) {
            throw new IllegalStateException("Cannot auto-match: reconciliation is locked (RECONCILED).");
        }
        int matched = 0;
        for (var item : rec.getItems()) {
            if (item.isCleared()) continue;
            boolean isDeposit = "DEPOSIT".equals(item.getType());
            BankStatementEntry best = null;
            for (var e : statements) {
                if (e.isReconciled()) continue;
                boolean amountMatches = isDeposit
                        ? e.getCredit().compareTo(item.getAmount()) == 0
                        : e.getDebit().compareTo(item.getAmount()) == 0;
                if (!amountMatches) continue;
                if (best == null) {
                    best = e;
                } else {
                    // prefer the entry sharing reference tokens
                    int s1 = sharedTokens(item, best);
                    int s2 = sharedTokens(item, e);
                    if (s2 > s1) best = e;
                }
            }
            if (best != null) {
                markItemCleared(rec, item, best.getStatementDate(), best.getReference(),
                        best.getId(), who());
                best.setReconciled(true);
                best.setMatchedItemId(item.getId());
                matched++;
            }
        }
        PersistenceService.getInstance().saveAll();
        return matched;
    }

    private int sharedTokens(BankReconciliation.ReconciliationItem item, BankStatementEntry e) {
        Set<String> itemTokens = tokens(item.getReference()).stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        itemTokens.addAll(tokens(item.getDescription()).stream().map(String::toLowerCase).collect(Collectors.toSet()));
        String stmtText = (nvl(e.getReference()) + " " + nvl(e.getDescription())).toLowerCase(Locale.ROOT);
        int share = 0;
        for (String t : tokens(stmtText)) {
            if (itemTokens.contains(t)) share++;
        }
        return share;
    }

    // =====================================================================
    // V6 — Manual pairing & exceptions
    // =====================================================================

    public void pairItemToStatement(BankReconciliation rec, String itemId, BankStatementEntry statement,
                                    String clearingDate) {
        BankReconciliation.ReconciliationItem item = rec.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cashbook item not found: " + itemId));
        markItemCleared(rec, item, LocalDate.parse(clearingDate), statement.getReference(),
                statement.getId(), who());
        statement.setReconciled(true);
        statement.setMatchedItemId(item.getId());
        PersistenceService.getInstance().saveAll();
    }

    /**
     * Clears an item with an enforced date guardrail: the bank's clearing date
     * cannot be earlier than the cashbook posting date.
     */
    public void markItemCleared(BankReconciliation rec, BankReconciliation.ReconciliationItem item,
                                LocalDate clearingDate, String matchedRef, String statementId, String clearedBy) {
        ensureNotLocked(rec);
        if (clearingDate != null && item.getPostedDate() != null
                && clearingDate.isBefore(item.getPostedDate())) {
            throw new IllegalStateException("Clearing date " + clearingDate
                    + " cannot be earlier than posting date " + item.getPostedDate());
        }
        item.setCleared(true);
        item.setClearingDate(clearingDate);
        item.setMatchedStatementRef(matchedRef);
        item.setSource("BANK_STATEMENT");
        item.setClearedBy(clearedBy);
        rec.calculate();
    }

    /**
     * Direct bank credit: raise a fee receipt straight from an unmatched bank
     * statement line (anomaly B), so the ledger catches up to reality.
     */
    public ReceiptService.Result generateReceiptFromStatement(Student student, BigDecimal amount,
                                                              LocalDate date, String reference, String notes) {
        return new ReceiptService().receivePayment(student, amount, PaymentMode.BANK_SLIP,
                reference, date, notes);
    }

    /**
     * Direct bank debit (anomaly B): post a bank-charges voucher. Debits a
     * general expense and credits the bank account that actually lost the money.
     */
    public void postBankCharges(AccountType bankAccount, LocalDate date, String reference,
                                String narration, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Bank charge amount must be positive");
        }
        JournalEntry journal = new JournalEntry();
        journal.setDate(date);
        journal.setReference(reference);
        journal.setNarration(narration);
        journal.addLine(AccountType.GENERAL_EXPENSES, null, amount, CurrencyConfig.zero(),
                "Bank charges — " + narration);
        journal.addLine(bankAccount, null, CurrencyConfig.zero(), amount,
                "Bank charges (direct debit) — " + narration);
        engine.postJournal(journal, who(), null, null, null, TransactionType.JOURNAL);
    }

    // =====================================================================
    // V7 — M-Pesa clearing (in-transit)
    // =====================================================================

    /**
     * Sweeps the bulk Pay Bill transfer from the M-Pesa Clearing account into
     * the core bank account. Posts a balancing internal-transfer journal
     * (dr bank account, cr M-Pesa Clearing) per anomaly C.
     *
     * @return the journal reference that records the transfer.
     */
    public String sweepMpesaClearing(AccountType bankAccount, LocalDate date, String reference,
                                     BigDecimal amount, String narration) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("M-Pesa sweep amount must be positive");
        }
        JournalEntry journal = new JournalEntry();
        journal.setDate(date);
        journal.setReference(reference);
        journal.setNarration(narration);
        journal.addLine(bankAccount, null, amount, CurrencyConfig.zero(),
                "In-transit sweep — " + narration);
        journal.addLine(AccountType.MPESA_CLEARING, null, CurrencyConfig.zero(), amount,
                "Clear M-Pesa in-transit — " + narration);
        engine.postJournal(journal, who(), null, null, null, TransactionType.TRANSFER);
        return reference;
    }

    // =====================================================================
    // V9 — Guardrails
    // =====================================================================

    public boolean isLocked(BankReconciliation rec) {
        return "RECONCILED".equals(rec.getStatus());
    }

    private void ensureNotLocked(BankReconciliation rec) {
        if (isLocked(rec)) {
            throw new IllegalStateException("Reconciliation is locked (RECONCILED). "
                    + "Reconciled entries are immutable and cannot be edited, reversed or deleted.");
        }
    }

    /**
     * Strict balance-forward block: a new month cannot open while an earlier
     * month for the same bank account still has an unresolved variance.
     */
    public void ensureMonthOpenAllowed(AccountType accountType, LocalDate statementDate) {
        String bankName = accountType.name();
        for (BankReconciliation existing : store.getReconciliations()) {
            if (!bankName.equals(existing.getBankAccountType())) continue;
            LocalDate existingDate = existing.getStatementDate();
            if (existingDate == null || statementDate == null) continue;
            boolean existingInEarlierMonth = existingDate.getYear() < statementDate.getYear()
                    || (existingDate.getYear() == statementDate.getYear()
                            && existingDate.getMonthValue() < statementDate.getMonthValue());
            if (existingInEarlierMonth && existing.getDifference() != null
                    && existing.getDifference().compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalStateException("Cannot open reconciliation for "
                        + statementDate + ": previous month "
                        + existingDate + " for " + accountType.getDisplayName()
                        + " still has an unresolved variance of "
                        + existing.getDifference());
            }
        }
    }

    public void finalizeReconciliation(BankReconciliation rec) {
        rec.calculate();
        if (rec.getDifference() != null && rec.getDifference().compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Cannot finalize reconciliation: difference must be zero (current: "
                    + rec.getDifference() + ")");
        }
        rec.setPreviousMonthVariance(CurrencyConfig.money(rec.getDifference()));
        rec.setStatus("RECONCILED");
        rec.setReconciledAt(LocalDateTime.now());
        PersistenceService.getInstance().saveAll();
    }

    public List<FinancialTransaction> getUnclearedTransactions(AccountType accountType) {
        return ledgerStore.getTransactions().stream()
                .filter(t -> t.getAccountType() == accountType)
                .filter(t -> t.getReversalOfId() == null)
                .toList();
    }

    /**
     * Latest imported statement rows, ready to drive auto-match and the
     * split-screen exception handler.
     */
    public List<BankStatementEntry> currentStatementEntries() {
        return BankStatementStore.getInstance().getEntries();
    }

    private static Set<String> tokens(String s) {
        if (s == null) return Set.of();
        return java.util.Arrays.stream(s.split("[^\\p{L}\\p{Nd}]+"))
                .filter(t -> !t.isBlank())
                .collect(Collectors.toSet());
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
