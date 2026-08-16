package com.schaccs.repository;

import com.schaccs.service.Services;
import com.schaccs.service.school.AcademicCalendarService;
import com.schaccs.store.AccountStore;
import com.schaccs.util.DisasterRecoveryEngine;
import com.schaccs.util.MockData;

import java.time.LocalDate;
import java.util.logging.Logger;

/**
 * Load SQLite data if present; otherwise seed MockData once and persist.
 * Also seeds the sample academic calendar and runs the automatic end-of-term
 * transition when a configured term has already ended.
 */
public final class AppBootstrap {

    private static final Logger LOG = Logger.getLogger(AppBootstrap.class.getName());

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

        AcademicCalendarService calendar = Services.getInstance().academicCalendar();
        boolean seededNow = calendar.seedIfEmpty();
        LocalDate today = LocalDate.now();
        if (!seededNow) {
            // The calendar is already configured and persisted — apply any ended-term
            // transition automatically (status reconcile + arrears rollover + completion).
            calendar.rolloverIfDue(today);
        } else {
            calendar.reconcileStatuses(today);
            LOG.info("Academic calendar sample data seeded; skipping automatic rollover on first run.");
        }

        disasterRecoveryEngine = DisasterRecoveryEngine.getInstance();
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
