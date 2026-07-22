package com.schaccs.store;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.model.finance.LedgerEntry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class LedgerStore {

    private static final LedgerStore INSTANCE = new LedgerStore();

    private final ObservableList<FinancialTransaction> transactions = FXCollections.observableArrayList();
    private final ObservableList<LedgerEntry> ledgerEntries = FXCollections.observableArrayList();
    private final Map<AccountType, BigDecimal> accountBalances = new EnumMap<>(AccountType.class);

    private LedgerStore() {
        for (AccountType type : AccountType.values()) {
            accountBalances.put(type, CurrencyConfig.zero());
        }
    }

    public static LedgerStore getInstance() {
        return INSTANCE;
    }

    public ObservableList<FinancialTransaction> getTransactions() {
        return transactions;
    }

    public ObservableList<LedgerEntry> getLedgerEntries() {
        return ledgerEntries;
    }

    public void addTransaction(FinancialTransaction tx) {
        transactions.add(0, tx);
    }

    public void addLedgerEntry(LedgerEntry entry) {
        ledgerEntries.add(0, entry);
        AccountType type = entry.getAccountType();
        if (type != null) {
            BigDecimal current = accountBalances.getOrDefault(type, CurrencyConfig.zero());
            BigDecimal next;
            if (type.isDebitNormal()) {
                // Debit-normal: debit increases, credit decreases
                next = current.add(entry.getDebit()).subtract(entry.getCredit());
            } else {
                // Credit-normal: credit increases, debit decreases
                next = current.add(entry.getCredit()).subtract(entry.getDebit());
            }
            accountBalances.put(type, CurrencyConfig.money(next));
            entry.setBalance(accountBalances.get(type));
        }
    }

    public BigDecimal getAccountBalance(AccountType type) {
        return accountBalances.getOrDefault(type, CurrencyConfig.zero());
    }

    public Map<AccountType, BigDecimal> getAccountBalances() {
        return accountBalances;
    }

    public List<FinancialTransaction> forStudent(String studentId) {
        return transactions.stream()
                .filter(t -> studentId.equals(t.getStudentId()))
                .collect(Collectors.toList());
    }

    public void clear() {
        transactions.clear();
        ledgerEntries.clear();
        for (AccountType type : AccountType.values()) {
            accountBalances.put(type, CurrencyConfig.zero());
        }
    }
}
