package org.duckcatmine.apocalypse.combat;

import java.util.random.RandomGenerator;

/** Rounds stored in a weapon: a found weapon is already loaded, a reload takes rounds from the inventory. */
public final class Ammo {
    public static final int CAPACITY = 100, ROCKET_CAPACITY = 20, LAUNCHER_DURABILITY = 60;
    public static final int FOUND_MIN = 30, FOUND_MAX = 100, ROCKETS_FOUND_MIN = 5, ROCKETS_FOUND_MAX = 20;
    private Ammo() {}

    public static int found(String gunId, RandomGenerator random) {
        return "rocket_launcher".equals(gunId) ? random.nextInt(ROCKETS_FOUND_MIN, ROCKETS_FOUND_MAX + 1)
                : random.nextInt(FOUND_MIN, FOUND_MAX + 1);
    }

    /** Rounds moved from the inventory into the weapon. */
    public static int reload(int loaded, int capacity, int available) {
        return Math.max(0, Math.min(capacity - Math.max(0, loaded), available));
    }

    /** Former ten-shot launchers keep their remaining rockets as loaded rounds. */
    public static int legacyRockets(Integer stored, int wear) {
        return Math.max(0, Math.min(10 - Math.max(0, wear), stored == null ? 10 : stored));
    }
}
