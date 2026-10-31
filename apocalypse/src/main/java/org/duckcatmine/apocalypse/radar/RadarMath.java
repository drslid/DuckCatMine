package org.duckcatmine.apocalypse.radar;

/** Minecraft yaw: zero faces +Z. Screen top always follows the viewer's gaze. */
public final class RadarMath {
    private RadarMath() {}
    public record Dot(int x, int y, boolean far) {}
    public static Dot project(double dx, double dz, float yaw) {
        double distance = Math.hypot(dx, dz), angle = Math.toRadians(yaw);
        boolean far = distance > 10;
        double scale = far ? 58 / distance : 4.8;
        return new Dot((int) Math.round(64 + (-dx * Math.cos(angle) - dz * Math.sin(angle)) * scale),
                (int) Math.round(64 - (-dx * Math.sin(angle) + dz * Math.cos(angle)) * scale), far);
    }
}
