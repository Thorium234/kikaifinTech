package com.schaccs.store;

import com.schaccs.model.CleanDataEntry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * In-memory store for import rows held back for cleaning (the Clean Data list).
 * Rows stay here until they are fixed (auto-importing into complete data) or
 * discarded, and are persisted so a later session can continue where a user
 * left off — the same recycle-bin pattern.
 */
public final class CleanDataStore {

    private static final CleanDataStore INSTANCE = new CleanDataStore();

    private final ObservableList<CleanDataEntry> items = FXCollections.observableArrayList();

    private CleanDataStore() {
    }

    public static CleanDataStore getInstance() {
        return INSTANCE;
    }

    public ObservableList<CleanDataEntry> getItems() {
        return items;
    }

    public synchronized List<CleanDataEntry> forType(CleanDataEntry.Type type) {
        return items.stream().filter(e -> e.getType() == type).toList();
    }

    public synchronized List<Map<String, String>> rowsFor(CleanDataEntry.Type type) {
        return items.stream().filter(e -> e.getType() == type)
                .map(e -> Map.copyOf(e.getFields()))
                .toList();
    }

    public synchronized void add(CleanDataEntry entry) {
        items.add(entry);
    }

    public synchronized void remove(CleanDataEntry entry) {
        items.remove(entry);
    }

    public synchronized void clear() {
        items.clear();
    }

    /**
     * Make the held rows of a type exactly the given rows. Used after a Clean
     * Data fix session: resolved rows are gone (they imported), the rows still
     * held are re-stored.
     */
    public synchronized void replaceRows(CleanDataEntry.Type type, List<Map<String, String>> rows) {
        items.removeIf(e -> e.getType() == type);
        for (Map<String, String> fields : rows) {
            items.add(CleanDataEntry.create(type, fields));
        }
    }

    /**
     * Add rows of a type, replacing an identical existing row so repeated
     * imports of the same broken file do not pile up duplicates.
     */
    public synchronized void addRows(CleanDataEntry.Type type, List<Map<String, String>> rows) {
        for (Map<String, String> fields : rows) {
            String fingerprint = fingerprint(type, fields);
            items.removeIf(e -> e.getType() == type
                    && fingerprint.equals(fingerprint(e.getType(), e.getFields())));
            items.add(CleanDataEntry.create(type, fields));
        }
    }

    private static String fingerprint(CleanDataEntry.Type type, Map<String, String> fields) {
        return type.name()
                + "|" + normalize(first(fields, "admissionnumber", "admissionNumber"))
                + "|" + normalize(first(fields, "fullname", "studentname", "name"))
                + "|" + normalize(first(fields, "formclass", "formClass"))
                + "|" + normalize(first(fields, "stream"));
    }

    private static String first(Map<String, String> m, String... keys) {
        for (String key : keys) {
            String value = m.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
