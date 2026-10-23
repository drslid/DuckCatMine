package org.duckcatmine.apocalypse.core;

public final class Durations {
    private Durations() {
    }

    /** Rounds up so that a fresh 60-second countdown reads 01:00. */
    public static String clock(long remainingMs) {
        long seconds = Math.max(0, Math.ceilDiv(remainingMs, 1000L));
        return "%02d:%02d".formatted(seconds / 60, seconds % 60);
    }
}
