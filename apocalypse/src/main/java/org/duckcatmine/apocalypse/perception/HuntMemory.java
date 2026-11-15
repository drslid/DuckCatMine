package org.duckcatmine.apocalypse.perception;

/** One last-known point, never a live reference to a hidden player. Time comes from the scenario clock. */
public final class HuntMemory<P> {
    private P point;
    private long until;
    private boolean confirmed;

    public void see(P point, long now, long durationMs) {
        this.point = point;
        until = now + durationMs;
        confirmed = true;
    }

    /** Infected pounding and screaming may attract idle neighbours, but cannot perpetuate an existing hunt. */
    public void hear(P point, long now, long durationMs, boolean refreshActive) {
        boolean active = goal(now) != null;
        if (active && !refreshActive) return;
        this.point = point;
        until = now + durationMs;
        if (!active) confirmed = false;
    }

    public P goal(long now) {
        if (point != null && now >= until) clear();
        return point;
    }

    public boolean confirmed(long now) { return goal(now) != null && confirmed; }

    public void clear() { point = null; until = 0; confirmed = false; }
}
