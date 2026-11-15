package org.duckcatmine.apocalypse.spawn;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DepartureGroupTest {
    @Test void oneCountdownFreezesTheWholeGroupAndFinishesAfterFiveSeconds() {
        var group = new DepartureGroup();
        var a = UUID.randomUUID(); var b = UUID.randomUUID();
        assertTrue(group.join(a, null)); assertTrue(group.join(b, null));
        long token = group.prepare();
        assertFalse(group.join(UUID.randomUUID(), null));
        assertTrue(group.countdown(token, 5));
        for (int second = 5; second > 0; second--) { assertEquals(second, group.seconds()); group.elapseSecond(); }
        assertEquals(0, group.seconds());
        assertEquals(java.util.List.of(a,b), group.members());
    }

    @Test void lateAsyncPreparationCannotLaunchACancelledOrDifferentGroup() {
        var group = new DepartureGroup();
        group.join(UUID.randomUUID(), null);
        long old = group.prepare();
        group.cancel();
        group.join(UUID.randomUUID(), null);
        long current = group.prepare();
        assertFalse(group.countdown(old,5));
        assertTrue(group.countdown(current,5));
        assertFalse(group.countdown(current,5));
    }

    @Test void aDisconnectDoesNotRestartTheOthersAndAnEmptyGroupCancels() {
        var group = new DepartureGroup();
        var a = UUID.randomUUID(); var b = UUID.randomUUID();
        group.join(a,null); group.join(b,null);
        group.countdown(group.prepare(),5); group.elapseSecond();
        group.remove(a);
        assertEquals(4,group.seconds()); assertEquals(java.util.List.of(b),group.members());
        group.remove(b);
        assertEquals(DepartureGroup.Phase.WAITING,group.phase()); assertEquals(0,group.seconds());
    }

    @Test void joiningTheGroupKeepsItsChosenSurvivorAndDuplicatesDoNotAddSeats() {
        var group = new DepartureGroup();
        var a = UUID.randomUUID(); var b = UUID.randomUUID(); var target = UUID.randomUUID();
        group.join(a,target); group.join(b,null); group.join(a,null);
        assertEquals(target,group.target()); assertEquals(2,group.members().size());
        group.cancel(); assertNull(group.target());
        assertThrows(IllegalStateException.class,group::prepare);
    }
}
