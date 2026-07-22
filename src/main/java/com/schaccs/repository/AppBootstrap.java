package com.schaccs.repository;

import com.schaccs.store.AccountStore;
import com.schaccs.util.DisasterRecoveryEngine;
import com.schaccs.util.MockData;

/**
 * Load SQLite data if present; otherwise seed MockData once and persist.
 */
public final class AppBootstrap {

    private AppBootstrap() {
    }

    private static DisasterRecoveryEngine disasterRecoveryEngine;

    public static void initialize() {
        Database.getInstance(); // ensure schema
        PersistenceService persistence = PersistenceService.getInstance();
        if (persistence.hasData()) {
            persistence.loadAll();
        } else {
            MockData.load();
            persistence.saveAll();
        }
        AccountStore.getInstance().seedDefaultAccounts();
        disasterRecoveryEngine = new DisasterRecoveryEngine();
        disasterRecoveryEngine.start();
    }

    public static void shutdown() {
        if (disasterRecoveryEngine != null) {
            disasterRecoveryEngine.onAppShutdown();
        }
        try {
            PersistenceService.getInstance().saveAll();
        } catch (Exception ignored) {
            // best-effort save on exit
        }
        Database.getInstance().close();
    }
}
