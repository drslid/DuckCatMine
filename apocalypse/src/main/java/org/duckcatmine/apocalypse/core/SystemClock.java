package org.duckcatmine.apocalypse.core;

import java.util.function.LongSupplier;

/**
 * Scenario time used by infections and bags: real elapsed time while the system runs,
 * frozen while the server is stopped or the scenario is suspended. Never tick-based.
 */
public final class SystemClock {
    private final LongSupplier nanoTime;
    private long baseMs;
    private long startedNanos;
    private boolean running;

    public SystemClock(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    public synchronized void start(long fromMs) {
        baseMs = fromMs;
        startedNanos = nanoTime.getAsLong();
        running = true;
    }

    public synchronized long now() {
        return running ? baseMs + (nanoTime.getAsLong() - startedNanos) / 1_000_000L : baseMs;
    }

    public synchronized long pause() {
        baseMs = now();
        running = false;
        return baseMs;
    }

    public synchronized void resume() {
        if (running) return;
        startedNanos = nanoTime.getAsLong();
        running = true;
    }

    public synchronized boolean running() {
        return running;
    }
}
