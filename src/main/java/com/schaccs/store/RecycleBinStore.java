package com.schaccs.store;

import com.schaccs.model.student.DeletedStudent;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Optional;

/**
 * In-memory store for students moved to the recycle bin.
 */
public final class RecycleBinStore {

    private static final RecycleBinStore INSTANCE = new RecycleBinStore();

    private final ObservableList<DeletedStudent> items = FXCollections.observableArrayList();

    private RecycleBinStore() {
    }

    public static RecycleBinStore getInstance() {
        return INSTANCE;
    }

    public ObservableList<DeletedStudent> getItems() {
        return items;
    }

    public synchronized void add(DeletedStudent item) {
        items.add(item);
    }

    public synchronized void remove(DeletedStudent item) {
        items.remove(item);
    }

    public synchronized Optional<DeletedStudent> findById(String id) {
        return items.stream().filter(d -> d.getId().equals(id)).findFirst();
    }

    public synchronized void clear() {
        items.clear();
    }
}
