package org.duckcatmine.apocalypse.combat;

import org.duckcatmine.apocalypse.config.ApocalypseConfig;

/** One wear unit and one round per trigger pull, including the complete shotgun spread. Rounds are stored in the weapon. */
public record Gun(String id, String label, double damage, double range, int pellets, double spread,
                  long delayMs, int durability, boolean automatic, String ammo, int capacity, long reloadMs) {
    public static final String BULLETS = "ammo", ROCKETS = "rocket";
    public static Gun of(String id, ApocalypseConfig config) {
        if (id == null) return null;
        var pistol = config.play().pistol();
        return switch (id) {
            case "pistol" -> new Gun(id, "Pistolet", pistol.damage(), pistol.range(), 1, 0, (long) (pistol.fireDelaySeconds() * 1000),
                    pistol.durability(), false, BULLETS, Ammo.CAPACITY, (long) (pistol.reloadSeconds() * 1000));
            case "shotgun" -> new Gun(id, "Fusil à pompe", 2.5, 36, 8, .11, 1100, 96, false, BULLETS, Ammo.CAPACITY, 3000);
            case "smg" -> new Gun(id, "Mitraillette", 3, 64, 1, .025, 150, 360, true, BULLETS, Ammo.CAPACITY, 2500);
            case "rocket_launcher" -> new Gun(id, "Lance-roquettes", RocketService.DAMAGE, RocketService.RANGE, 1, 0, 2000,
                    Ammo.LAUNCHER_DURABILITY, false, ROCKETS, Ammo.ROCKET_CAPACITY, 3500);
            default -> null;
        };
    }
}
