package com.schaccs.repository;

import com.schaccs.util.MockData;

/**
 * Load SQLite data if present; otherwise seed MockData once and persist.
 */
public final class AppBootstrap {

    private AppBootstrap() {
    }

    public static void initialize() {
        Database.getInstance(); // ensure schema
        PersistenceService persistence = PersistenceService.getInstance();
        if (persistence.hasData()) {
            persistence.loadAll();
        } else {
            MockData.load();
            persistence.saveAll();
        }
    }

    public static void shutdown() {
        try {
            PersistenceService.getInstance().saveAll();
        } catch (Exception ignored) {
            // best-effort save on exit
        }
        Database.getInstance().close();
    }
}
