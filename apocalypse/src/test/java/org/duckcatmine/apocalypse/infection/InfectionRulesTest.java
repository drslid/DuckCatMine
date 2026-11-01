package org.duckcatmine.apocalypse.infection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InfectionRulesTest {
    @Test
    void onlyDamageThatReachesThePlayerInfects() {
        assertFalse(InfectionRules.reachedPlayer(0, 0), "full shield block or zero damage");
        assertTrue(InfectionRules.reachedPlayer(0.5, 0));
        assertTrue(InfectionRules.reachedPlayer(0, 2), "absorption hearts still mean the bite landed");
    }

    @Test
    void referenceBiteChanceAlwaysInfects() {
        assertTrue(InfectionRules.bites(1.0, 0.999_999));
        assertTrue(InfectionRules.bites(0.25, 0.10));
        assertFalse(InfectionRules.bites(0.25, 0.30));
        assertFalse(InfectionRules.bites(0.0, 0.0));
    }

    @Test
    void administrationMustCompleteBeforeTheDeadline() {
        long deadline = 60_000;
        assertTrue(InfectionRules.curable(59_999, deadline));
        assertFalse(InfectionRules.curable(60_000, deadline));
        long startedAtOneSecondLeft = 59_000;
        assertFalse(InfectionRules.curable(startedAtOneSecondLeft + 2_000, deadline), "starting at 00:01 with 2 s to go cannot save");
    }

    @Test
    void warningsFireOnceWhenCrossingThresholds() {
        assertEquals(0, InfectionRules.crossedWarning(Long.MAX_VALUE, 60_000));
        assertEquals(30, InfectionRules.crossedWarning(30_100, 29_900));
        assertEquals(0, InfectionRules.crossedWarning(29_900, 29_650));
        assertEquals(10, InfectionRules.crossedWarning(10_200, 10_000));
        assertEquals(5, InfectionRules.crossedWarning(5_100, 4_900));
        assertEquals(0, InfectionRules.crossedWarning(4_900, 1_000));
    }
}
