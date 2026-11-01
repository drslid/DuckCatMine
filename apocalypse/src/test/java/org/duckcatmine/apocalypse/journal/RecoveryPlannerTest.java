package org.duckcatmine.apocalypse.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecoveryPlannerTest {
    private static PlayerOp op(OpType type, long seq) {
        return new PlayerOp(UUID.randomUUID(), seq, type, OpState.PENDING, null);
    }

    @Test
    void savedPlayerFileConfirmsTheOperation() {
        for (OpType type : OpType.values()) {
            assertEquals(RecoveryPlanner.Action.CONFIRM, RecoveryPlanner.decide(op(type, 7), 7), type.name());
            assertEquals(RecoveryPlanner.Action.CONFIRM, RecoveryPlanner.decide(op(type, 7), 9), type.name());
        }
    }

    @Test
    void swapsAndWithdrawalsThatNeverReachedThePlayerAreRolledBack() {
        assertEquals(RecoveryPlanner.Action.ROLL_BACK, RecoveryPlanner.decide(op(OpType.ENTER, 7), 6));
        assertEquals(RecoveryPlanner.Action.ROLL_BACK, RecoveryPlanner.decide(op(OpType.LEAVE, 7), 6));
        assertEquals(RecoveryPlanner.Action.ROLL_BACK, RecoveryPlanner.decide(op(OpType.BAG_TAKE, 7), 0));
    }

    @Test
    void aRecordedDeathIsNeverUndone() {
        assertEquals(RecoveryPlanner.Action.REAPPLY, RecoveryPlanner.decide(op(OpType.DEATH, 7), 6));
        assertEquals(RecoveryPlanner.Action.REAPPLY, RecoveryPlanner.decide(op(OpType.OFFLINE_DEATH, 3), 0));
    }
}
