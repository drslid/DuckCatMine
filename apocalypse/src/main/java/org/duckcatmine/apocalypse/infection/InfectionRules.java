package org.duckcatmine.apocalypse.infection;

public final class InfectionRules {
    static final int[] WARNINGS = {30, 10, 5};

    private InfectionRules() {
    }

    /** Health or absorption actually lost: a cancelled hit or a full shield block never infects. */
    public static boolean reachedPlayer(double finalDamage, double absorbed) {
        return finalDamage > 0 || absorbed > 0;
    }

    public static boolean bites(double chance, double roll) {
        return chance >= 1.0 || roll < chance;
    }

    /** Administration must complete strictly before the deadline. */
    public static boolean curable(long now, long deadline) {
        return now < deadline;
    }

    /** Returns 30, 10 or 5 when that threshold has just been crossed, otherwise 0. */
    public static int crossedWarning(long previousRemaining, long remaining) {
        for (int seconds : WARNINGS) {
            long threshold = seconds * 1000L;
            if (previousRemaining > threshold && remaining <= threshold) return seconds;
        }
        return 0;
    }
}
