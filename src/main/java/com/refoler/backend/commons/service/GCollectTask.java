package com.refoler.backend.commons.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class GCollectTask {

    public final long INTERVAL_NOT_ENABLED = -1L;

    public interface GCollectable {
        boolean cleanUpCache();
    }

    public GCollectTask() {
        igniteIntervalGcThread();
    }

    private void igniteIntervalGcThread() {
        final long gcInterval = getGcIgniteInterval();
        if (gcInterval == INTERVAL_NOT_ENABLED) {
            return;
        }
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(this::performIntervalGc, gcInterval, gcInterval, TimeUnit.MILLISECONDS);
    }

    public long getGcIgniteInterval() {
        return INTERVAL_NOT_ENABLED;
    }

    public void performIntervalGc() {
        if (getGcIgniteInterval() != INTERVAL_NOT_ENABLED) {
            throw new IllegalStateException("performIntervalGc not Defined!");
        }
    }
}
