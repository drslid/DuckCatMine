package org.duckcatmine.apocalypse.spawn;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One shared departure, with frozen membership during preparation and a cancellable async generation. */
public final class DepartureGroup {
    public enum Phase { WAITING, PREPARING, COUNTDOWN }
    private final Set<UUID> members = new LinkedHashSet<>();
    private Phase phase = Phase.WAITING;
    private UUID target;
    private long generation;
    private int seconds;

    public boolean join(UUID player, UUID beside) {
        if (phase != Phase.WAITING || members.size() >= 64 && !members.contains(player)) return false;
        boolean first = members.isEmpty();
        members.add(player);
        if (first || beside != null) target = beside;
        return true;
    }

    public void remove(UUID player) {
        members.remove(player);
        if (members.isEmpty()) cancel();
    }

    public long prepare() {
        if (phase != Phase.WAITING || members.isEmpty()) throw new IllegalStateException("Aucun groupe prêt.");
        phase = Phase.PREPARING;
        return ++generation;
    }

    public boolean countdown(long token, int duration) {
        if (phase != Phase.PREPARING || generation != token || members.isEmpty()) return false;
        if (duration < 1) throw new IllegalArgumentException("Compte à rebours invalide.");
        phase = Phase.COUNTDOWN;
        seconds = duration;
        return true;
    }

    public void elapseSecond() {
        if (phase == Phase.COUNTDOWN && seconds > 0) seconds--;
    }

    public void cancel() {
        members.clear();
        phase = Phase.WAITING;
        target = null;
        seconds = 0;
        generation++;
    }

    public List<UUID> members() { return List.copyOf(members); }
    public UUID target() { return target; }
    public Phase phase() { return phase; }
    public int seconds() { return seconds; }
}
