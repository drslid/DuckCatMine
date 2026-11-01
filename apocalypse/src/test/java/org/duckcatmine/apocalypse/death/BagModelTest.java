package org.duckcatmine.apocalypse.death;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BagModelTest {
    private static BagModel bag(int size) {
        SlotState[] states = new SlotState[size];
        java.util.Arrays.fill(states, SlotState.PRESENT);
        return new BagModel(states);
    }

    @Test
    void anItemLeavesTheBagOnlyOnce() {
        BagModel model = bag(2);
        assertTrue(model.beginTake(0));
        assertFalse(model.beginTake(0), "double click");
        model.confirmTake(0);
        assertFalse(model.beginTake(0), "second player after the first");
        assertEquals(SlotState.TAKEN, model.state(0));
        assertFalse(model.beginTake(7));
    }

    @Test
    void partialRecoveryKeepsTheRest() {
        BagModel model = bag(3);
        model.beginTake(1);
        model.confirmTake(1);
        assertTrue(model.hasPresent());
        assertFalse(model.settled());
    }

    @Test
    void expiryPurgesWhatIsLeftButNotAnInFlightWithdrawal() {
        BagModel model = bag(3);
        model.beginTake(0);
        assertEquals(List.of(1, 2), model.expire());
        assertEquals(SlotState.MOVING, model.state(0));
        assertFalse(model.settled());
        model.cancelTake(0, true);
        assertEquals(SlotState.PURGED, model.state(0), "never handed over and the bag is gone");
        assertTrue(model.settled());
    }

    @Test
    void anUnconfirmedWithdrawalReturnsToALiveBag() {
        BagModel model = bag(1);
        model.beginTake(0);
        model.cancelTake(0, false);
        assertEquals(SlotState.PRESENT, model.state(0));
        assertTrue(model.beginTake(0));
    }
}
