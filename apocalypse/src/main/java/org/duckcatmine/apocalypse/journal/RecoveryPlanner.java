package org.duckcatmine.apocalypse.journal;

/**
 * Decides how a pending operation is resolved when its player is next seen.
 * The player's saved data carries the highest applied sequence: it tells whether the
 * in-game half of the operation reached disk before an incident.
 */
public final class RecoveryPlanner {
    public enum Action { CONFIRM, ROLL_BACK, REAPPLY }

    private RecoveryPlanner() {
    }

    public static Action decide(PlayerOp op, long appliedSeq) {
        boolean applied = appliedSeq >= op.seq();
        return switch (op.type()) {
            case ENTER, LEAVE, BAG_TAKE -> applied ? Action.CONFIRM : Action.ROLL_BACK;
            case DEATH, OFFLINE_DEATH -> applied ? Action.CONFIRM : Action.REAPPLY;
        };
    }
}
