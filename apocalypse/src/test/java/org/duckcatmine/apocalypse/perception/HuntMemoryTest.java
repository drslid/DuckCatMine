package org.duckcatmine.apocalypse.perception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HuntMemoryTest {
    @Test void abandonsExactlyThirtySecondsAfterTheLastSight() {
        var memory = new HuntMemory<String>();
        memory.see("last visible street", 1000, 30000);
        assertEquals("last visible street", memory.goal(30999));
        assertTrue(memory.confirmed(30999));
        assertNull(memory.goal(31000));
        assertFalse(memory.confirmed(31000));
    }
    @Test void visibleStationaryPlayerKeepsTheChaseAlive() {
        var memory = new HuntMemory<String>();
        memory.see("same window", 0, 30000);
        memory.see("same window", 29000, 30000);
        assertEquals("same window", memory.goal(58000));
    }
    @Test void realNoiseUpdatesOnlyTheHeardPointAndRestartsTheDeadline() {
        var memory = new HuntMemory<String>();
        memory.see("old street", 0, 30000);
        memory.hear("broken window", 25000, 30000, true);
        assertEquals("broken window", memory.goal(54000));
        assertTrue(memory.confirmed(54000));
        assertNull(memory.goal(55000));
    }
    @Test void infectedNoiseCannotCreateAnEndlessSiege() {
        var memory = new HuntMemory<String>();
        memory.see("hidden survivor", 0, 30000);
        for (int second=1; second<30; second++) memory.hear("pounding door", second*1000, 30000, false);
        assertEquals("hidden survivor", memory.goal(29999));
        assertNull(memory.goal(30000));
        memory.hear("neighbour howler", 31000, 30000, false);
        assertEquals("neighbour howler", memory.goal(31000));
        assertFalse(memory.confirmed(31000));
    }
    @Test void hearingAloneDoesNotGrantVisualConfirmation() {
        var memory = new HuntMemory<String>();
        memory.hear("footstep", 0, 30000, true);
        assertFalse(memory.confirmed(1000));
        memory.see("survivor", 2000, 30000);
        assertTrue(memory.confirmed(2000));
        memory.hear("new noise after forgetting", 40000, 30000, true);
        assertFalse(memory.confirmed(40000));
    }
}
