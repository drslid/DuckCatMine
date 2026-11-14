package org.duckcatmine.apocalypse.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class AmmoTest {
    @Test void foundWeaponsAreLoaded() {
        var random = new SplittableRandom(4);
        for (int n = 0; n < 500; n++) {
            int bullets = Ammo.found("pistol", random), rockets = Ammo.found("rocket_launcher", random);
            assertTrue(bullets >= 30 && bullets <= 100, "balles " + bullets);
            assertTrue(rockets >= 5 && rockets <= 20, "roquettes " + rockets);
        }
    }

    @Test void reloadNeverExceedsCapacityOrStock() {
        assertEquals(40, Ammo.reload(60, 100, 64));
        assertEquals(12, Ammo.reload(0, 100, 12));
        assertEquals(0, Ammo.reload(100, 100, 64));
        assertEquals(0, Ammo.reload(10, 100, 0));
        assertEquals(20, Ammo.reload(-3, 20, 50));
    }

    @Test void legacyLaunchersKeepRemainingRockets() {
        assertEquals(10, Ammo.legacyRockets(null, 0));
        assertEquals(4, Ammo.legacyRockets(4, 0));
        assertEquals(2, Ammo.legacyRockets(100, 8));
        assertEquals(0, Ammo.legacyRockets(10, 999));
    }
}
