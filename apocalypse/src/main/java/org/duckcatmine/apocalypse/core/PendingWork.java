package org.duckcatmine.apocalypse.core;

import java.util.Set;
import java.util.function.Predicate;

/** Bounded deduplicated work. Callbacks may synchronously enqueue more work (e.g. chunk-load events). */
public final class PendingWork {
    private PendingWork() {}
    public static <T> void drain(Set<T> pending, int budget, int scanLimit, Predicate<T> work) {
        // Finish iteration before invoking callbacks; an iterator across a chunk load is unsafe even on one thread.
        for (T key : pending.stream().limit(scanLimit).toList()) {
            if (budget <= 0) break;
            if (!pending.remove(key)) continue;
            if (work.test(key)) budget--;
        }
    }
}
