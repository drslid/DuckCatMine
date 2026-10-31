package org.duckcatmine.apocalypse.combat;

import org.duckcatmine.apocalypse.config.ApocalypseConfig;

/** One wear unit per trigger pull, including the complete shotgun spread. No ammunition inventory. */
public record Gun(String id, String label, double damage, double range, int pellets, double spread,
                  long delayMs, int durability, boolean automatic) {
    public static Gun of(String id, ApocalypseConfig config) {
        if (id == null) return null;
        return switch (id) {
            case "pistol" -> new Gun(id, "Pistolet", config.play().pistol().damage(), config.play().pistol().range(),
                    1, 0, (long) (config.play().pistol().fireDelaySeconds() * 1000), config.play().pistol().durability(), false);
            case "shotgun" -> new Gun(id, "Fusil à pompe", 2.5, 18, 8, .11, 1100, 96, false);
            case "smg" -> new Gun(id, "Mitraillette", 3, 32, 1, .025, 150, 360, true);
            default -> null;
        };
    }
}
