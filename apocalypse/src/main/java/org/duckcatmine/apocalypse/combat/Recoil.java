package org.duckcatmine.apocalypse.combat;

/** Camera kick per successful shot; no position change and no delayed mouse override. */
public final class Recoil {
    private Recoil() {}
    public record Kick(float yaw, float pitch) {}
    public static Kick shot(String weapon, float currentPitch, boolean crouched, double variation) {
        float up = switch (weapon) {
            case "pistol" -> 1.15f;
            case "shotgun" -> 3.8f;
            case "smg" -> .65f;
            case "rocket_launcher" -> 5.2f;
            default -> 0;
        };
        float lateral = switch (weapon) {
            case "pistol" -> .35f;
            case "shotgun" -> .65f;
            case "smg" -> .28f;
            case "rocket_launcher" -> .9f;
            default -> 0;
        };
        float stability = crouched ? .65f : 1f;
        float pitch = Math.max(-85f, currentPitch - up * stability) - currentPitch;
        // A player already looking above the limit must never be pulled down by a shot.
        return new Kick((float)Math.max(-1, Math.min(1, variation)) * lateral * stability, Math.min(0, pitch));
    }
}
