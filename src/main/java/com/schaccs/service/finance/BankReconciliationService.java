package com.schaccs.service.finance;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
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

    public BigDecimal getBookBalance() {
        return ledgerStore.getAccountBalance(com.schaccs.enums.AccountType.SCHOOL_FUND);
    }

    public BankReconciliation createReconciliation(LocalDate statementDate, BigDecimal statementBalance, String notes) {
        BankReconciliation rec = new BankReconciliation();
        rec.setStatementDate(statementDate);
        rec.setStatementBalance(statementBalance);
        rec.setBookBalance(getBookBalance());
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

    public List<BankReconciliation.ReconciliationItem> calculateUnclearedItems(BankReconciliation rec) {
        List<BankReconciliation.ReconciliationItem> items = new ArrayList<>();
        for (var tx : ledgerStore.getTransactions()) {
            if ("FEE_RECEIPT".equals(tx.getType().name())) {
                BankReconciliation.ReconciliationItem item = new BankReconciliation.ReconciliationItem();
                item.setType("DEPOSIT");
                item.setReference(tx.getReference());
                item.setDescription(tx.getDescription());
                item.setAmount(tx.getDebit());
                item.setCleared(false);
                items.add(item);
            } else if ("PAYMENT_VOUCHER".equals(tx.getType().name())) {
                BankReconciliation.ReconciliationItem item = new BankReconciliation.ReconciliationItem();
                item.setType("CHEQUE");
                item.setReference(tx.getReference());
                item.setDescription(tx.getDescription());
                item.setAmount(tx.getCredit());
                item.setCleared(false);
                items.add(item);
            }
        }
        return items;
    }

    public void finalizeReconciliation(BankReconciliation rec) {
        rec.calculate();
        rec.setStatus("RECONCILED");
        rec.setReconciledAt(LocalDateTime.now());
        PersistenceService.getInstance().saveAll();
    }

    public List<FinancialTransaction> getUnclearedTransactions() {
        return ledgerStore.getTransactions().stream()
                .filter(t -> "PAYMENT_VOUCHER".equals(t.getType().name())
                        || "FEE_RECEIPT".equals(t.getType().name()))
                .toList();
    }
}
