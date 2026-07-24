package com.schaccs.service.finance;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.model.finance.BankReconciliation;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.repository.PersistenceService;
import com.schaccs.store.BankReconciliationStore;
import com.schaccs.store.LedgerStore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class BankReconciliationService {

    private final BankReconciliationStore store;
    private final LedgerStore ledgerStore;

    public BankReconciliationService() {
        this(BankReconciliationStore.getInstance(), LedgerStore.getInstance());
    }

    public BankReconciliationService(BankReconciliationStore store, LedgerStore ledgerStore) {
        this.store = store;
        this.ledgerStore = ledgerStore;
    }

    public BigDecimal getBookBalance(AccountType accountType) {
        return ledgerStore.getAccountBalance(accountType);
    }

    public BankReconciliation createReconciliation(AccountType accountType, LocalDate statementDate, BigDecimal statementBalance, String notes) {
        BankReconciliation rec = new BankReconciliation();
        rec.setStatementDate(statementDate);
        rec.setStatementBalance(statementBalance);
        rec.setBookBalance(getBookBalance(accountType));
        rec.setStatus("DRAFT");
        rec.setNotes(notes);
        rec.setCreatedAt(LocalDateTime.now());
        store.add(rec);
        PersistenceService.getInstance().saveAll();
        return rec;
    }

    public void addItem(BankReconciliation rec, BankReconciliation.ReconciliationItem item) {
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

            BankReconciliation.ReconciliationItem item = new BankReconciliation.ReconciliationItem();
            item.setReference(tx.getReference());
            item.setDescription(tx.getDescription());
            item.setCleared(false);

            if (tx.getDebit().compareTo(BigDecimal.ZERO) > 0) {
                item.setType("DEPOSIT");
                item.setAmount(tx.getDebit());
            } else if (tx.getCredit().compareTo(BigDecimal.ZERO) > 0) {
                item.setType("CHEQUE");
                item.setAmount(tx.getCredit());
            } else {
                continue;
            }
            items.add(item);
        }
        return items;
    }

    public void finalizeReconciliation(BankReconciliation rec) {
        rec.calculate();
        if (rec.getDifference() != null && rec.getDifference().compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Cannot finalize reconciliation: difference must be zero (current: "
                    + rec.getDifference() + ")");
        }
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
}
