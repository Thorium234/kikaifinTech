package com.schaccs.update;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class UpdateScheduler {

    private static final long INITIAL_DELAY_SECONDS = 5;
    private static final long CHECK_INTERVAL_HOURS = 6;

    private final UpdateService updateService;
    private final ScheduledExecutorService executor;

    public UpdateScheduler(UpdateService updateService) {
        this.updateService = updateService;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "update-checker");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        executor.scheduleAtFixedRate(
            () -> updateService.checkForUpdates(false),
            INITIAL_DELAY_SECONDS,
            CHECK_INTERVAL_HOURS * 3600,
            TimeUnit.SECONDS
        );
    }

    public void stop() {
        executor.shutdownNow();
    }
}
