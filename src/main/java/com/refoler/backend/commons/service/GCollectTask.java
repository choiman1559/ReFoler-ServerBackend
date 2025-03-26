package com.refoler.backend.commons.service;

import com.refoler.backend.commons.utils.Log;
import org.jspecify.annotations.NonNull;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class GCollectTask<KeyType> {

    private final static String LogTag = "GCTask";
    public final long INTERVAL_GC_NOT_ENABLED = -1L;
    public final long INTERVAL_ONLY_NOT_ENABLED = -2L;

    public interface GCollectable {
        boolean cleanUpCache();
    }

    public GCollectTask() {
        igniteIntervalGcThread();
    }

    private void igniteIntervalGcThread() {
        final long gcInterval = requireGcIgniteInterval();
        if (gcInterval == INTERVAL_GC_NOT_ENABLED || gcInterval == INTERVAL_ONLY_NOT_ENABLED) {
            return;
        }
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(this::onGCollectPerform, gcInterval, gcInterval, TimeUnit.MILLISECONDS);
    }

    public String requireLogTag() {
        return LogTag;
    }

    public long requireGcIgniteInterval() {
        return INTERVAL_ONLY_NOT_ENABLED;
    }

    @NonNull
    public Set<KeyType> requireKeySet() {
        throw new IllegalStateException("requireKeySet() not implemented!");
    }

    public GCollectable requireCollectableFromKey(KeyType key) {
        throw new IllegalStateException("requireCollectableFromKey() not implemented!");
    }

    public void onGCollected(int collectedCount) {
        if (collectedCount > 0) {
            Log.printDebug(requireLogTag(), "GC Triggered: Cleaned-Up %d in-memory cache(s).".formatted(collectedCount));
        }
    }

    public void onGCollectPerform() {
        if (requireGcIgniteInterval() == INTERVAL_GC_NOT_ENABLED) {
            Log.printDebug(requireLogTag(), "The GC task is not enabled, GC Task abort!");
            return;
        }

        int collectedObjects = 0;
        for (KeyType key : requireKeySet()) {
            GCollectable gCollectable = requireCollectableFromKey(key);
            if (gCollectable.cleanUpCache()) {
                collectedObjects += 1;
            }
        }
        onGCollected(collectedObjects);
    }
}
