package com.schaccs.store;

import com.schaccs.model.payroll.StatutoryConfig;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Optional;

/**
 * In-memory store for runtime {@link StatutoryConfig} instances. The payroll
 * calculation engine loads the active config from here every run so statutory
 * rate changes take effect without a code change.
 */
public final class StatutoryConfigStore {

    private static final StatutoryConfigStore INSTANCE = new StatutoryConfigStore();

    private final ObservableList<StatutoryConfig> configs = FXCollections.observableArrayList();

    private StatutoryConfigStore() {}

    public static StatutoryConfigStore getInstance() { return INSTANCE; }

    public ObservableList<StatutoryConfig> getConfigs() { return configs; }

    /** Returns the single active config, or the first one, or null. */
    public Optional<StatutoryConfig> findActive() {
        return configs.stream().filter(StatutoryConfig::isActive).findFirst()
                .or(() -> configs.stream().findFirst());
    }

    public synchronized void clear() {
        configs.clear();
    }
}
