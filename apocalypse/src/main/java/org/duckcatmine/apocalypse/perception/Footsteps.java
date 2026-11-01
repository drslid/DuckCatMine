package org.duckcatmine.apocalypse.perception;

/** Sampled once per second: turning the camera and teleports are not footsteps. Sneaking is quiet. */
public final class Footsteps {
    public enum Sound { NONE, WALK, SPRINT }
    private Footsteps() {}
    public static Sound sound(double horizontalSquared, boolean onGround, boolean sneaking, boolean sprinting, boolean riding) {
        if (!onGround || sneaking || riding || horizontalSquared < .04 || horizontalSquared > 100) return Sound.NONE;
        return sprinting ? Sound.SPRINT : Sound.WALK;
    }
}
