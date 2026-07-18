package com.schaccs.store;

import com.schaccs.model.finance.BankReconciliation;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public final class BankReconciliationStore {

    private static final BankReconciliationStore INSTANCE = new BankReconciliationStore();
    private final ObservableList<BankReconciliation> reconciliations = FXCollections.observableArrayList();

    private BankReconciliationStore() {}

    public static BankReconciliationStore getInstance() { return INSTANCE; }
    public ObservableList<BankReconciliation> getReconciliations() { return reconciliations; }

    public void add(BankReconciliation r) {
        reconciliations.add(0, r);
    }

    public void clear() {
        reconciliations.clear();
    }
}
