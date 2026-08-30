package com.schaccs.store;

import com.schaccs.model.finance.BankStatementEntry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Holds the latest imported batch of bank-statement rows until they are matched
 * and persisted against a reconciliation.
 */
public final class BankStatementStore {

    private static final BankStatementStore INSTANCE = new BankStatementStore();
    private final ObservableList<BankStatementEntry> entries = FXCollections.observableArrayList();

    private BankStatementStore() {}

    public static BankStatementStore getInstance() { return INSTANCE; }
    public ObservableList<BankStatementEntry> getEntries() { return entries; }

    public synchronized void add(BankStatementEntry e) {
        entries.add(e);
    }

    public synchronized void replaceAll(java.util.List<BankStatementEntry> list) {
        entries.clear();
        entries.addAll(list);
    }

    public synchronized void clear() {
        entries.clear();
    }
}
