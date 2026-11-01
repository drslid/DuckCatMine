package org.duckcatmine.apocalypse.radar;

/** Minecraft yaw: zero faces +Z. Screen top always follows the viewer's gaze. */
public final class RadarMath {
    public static final int RANGE = 100;
    private RadarMath() {}
    public record Dot(int x, int y, boolean far) {}
    public static Dot project(double dx, double dz, float yaw) {
        return project(dx,dz,yaw,RANGE);
    }
    public static Dot project(double dx, double dz, float yaw, int range) {
        if (range <= 0) throw new IllegalArgumentException("Portée positive attendue");
        double distance = Math.hypot(dx, dz), angle = Math.toRadians(yaw);
        boolean far = distance > range;
        double scale = far ? 58 / distance : 48.0 / range;
        return new Dot((int) Math.round(64 + (-dx * Math.cos(angle) - dz * Math.sin(angle)) * scale),
                (int) Math.round(64 - (-dx * Math.sin(angle) + dz * Math.cos(angle)) * scale), far);
    }
}
