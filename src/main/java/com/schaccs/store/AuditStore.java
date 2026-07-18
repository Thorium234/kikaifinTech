package com.schaccs.store;

import com.schaccs.model.audit.AuditLog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public final class AuditStore {

    private static final AuditStore INSTANCE = new AuditStore();
    private final ObservableList<AuditLog> entries = FXCollections.observableArrayList();

    private AuditStore() {}

    public static AuditStore getInstance() { return INSTANCE; }

    public ObservableList<AuditLog> getEntries() { return entries; }

    public void add(AuditLog entry) {
        entries.add(0, entry);
    }

    public void clear() {
        entries.clear();
    }
}
