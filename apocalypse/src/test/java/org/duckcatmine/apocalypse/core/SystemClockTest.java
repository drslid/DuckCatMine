package org.duckcatmine.apocalypse.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SystemClockTest {
    private final AtomicLong nanos = new AtomicLong(5_000_000_000L);
    private final SystemClock clock = new SystemClock(nanos::get);

    private void advanceMs(long ms) {
        nanos.addAndGet(ms * 1_000_000L);
    }

    @Test
    void resumesFromThePersistedValue() {
        clock.start(42_000);
        advanceMs(1_500);
        assertEquals(43_500, clock.now());
    }

    @Test
    void stoppedTimeIsNotCounted() {
        clock.start(0);
        advanceMs(10_000);
        assertEquals(10_000, clock.pause());
        advanceMs(3_600_000);
        assertEquals(10_000, clock.now());
        assertFalse(clock.running());
        clock.resume();
        advanceMs(2_000);
        assertEquals(12_000, clock.now());
    }

    @Test
    void resumeTwiceDoesNotSkipTime() {
        clock.start(0);
        advanceMs(1_000);
        clock.resume();
        advanceMs(1_000);
        assertEquals(2_000, clock.now());
    }

    @Test
    void displayedCountdownStartsAtOneMinuteAndNeverGoesNegative() {
        assertEquals("01:00", Durations.clock(60_000));
        assertEquals("01:00", Durations.clock(59_001));
        assertEquals("00:59", Durations.clock(59_000));
        assertEquals("00:01", Durations.clock(1));
        assertEquals("00:00", Durations.clock(0));
        assertEquals("00:00", Durations.clock(-250));
        assertEquals("05:00", Durations.clock(300_000));
    }
}
