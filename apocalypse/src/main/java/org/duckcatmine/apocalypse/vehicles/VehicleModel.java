package org.duckcatmine.apocalypse.vehicles;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Car bodies built from block displays, all placed at the car centre (ground level) and offset by their transformation,
 * so one yaw turns the whole model. Local axes: x towards the left side, y up, z towards the front.
 */
public enum VehicleModel {
    SEDAN, WRECK, MOTORBIKE, VAN;

    /** Infected heavy enough to reach someone sitting in a car or a van. */
    public static final java.util.Set<String> HEAVY_INFECTED = java.util.Set.of("breaker", "abomination", "nemesis");
    private static final double[][] CAR_SEATS = {{0.42, -0.05}, {-0.42, -0.05}, {0.42, -0.90}, {-0.42, -0.90}};
    private static final double[][] VAN_SEATS = {{0.45, 1.05}, {-0.45, 1.05}, {0.45, -0.40}, {-0.45, -0.40}};
    private static final double[][] BIKE_SEATS = {{0, -0.15}, {0, -0.75}};

    public boolean drivable() { return this != WRECK; }
    public boolean shields() { return this == SEDAN || this == VAN; }
    /** Cars take a driver and a passenger, vans four people; the first one in drives. */
    public int seatCount() { return this == VAN ? 4 : 2; }
    public double[] seat(int index) { return (this == MOTORBIKE ? BIKE_SEATS : this == VAN ? VAN_SEATS : CAR_SEATS)[index]; }
    /** Motorbikes: faster and frugal, small tank. Cars burn half the configured rate. Vans: slower, big tank. */
    public double speedFactor() { return this == MOTORBIKE ? 1.25 : this == VAN ? 0.85 : 1; }
    public double tankFactor() { return this == MOTORBIKE ? 0.35 : this == VAN ? 1.6 : 1; }
    public double fuelFactor() { return this == MOTORBIKE ? 0.5 : this == VAN ? 0.7 : 0.5; }
    public float hitboxWidth() { return this == MOTORBIKE ? 1.1f : this == VAN ? 2.6f : 2.3f; }
    public float hitboxHeight() { return this == MOTORBIKE ? 1.3f : this == VAN ? 2.4f : 1.75f; }
    public double halfWidth() { return this == MOTORBIKE ? 0.2 : this == VAN ? 0.85 : 0.75; }
    public double halfLength() { return this == MOTORBIKE ? 0.85 : this == VAN ? 2.3 : 1.85; }
    public String label() { return this == MOTORBIKE ? "moto" : this == VAN ? "camionnette" : "voiture"; }

    public record Part(String role, BlockData data, Transformation transformation, boolean glow) {
    }

    static final String BODY = "body";
    static final String HEADLIGHT = "headlight";
    static final String TAILLIGHT = "taillight";

    public List<Part> parts(Material paint) {
        List<Part> parts = new ArrayList<>();
        if (this == WRECK) {
            parts.add(part(BODY, Material.BLACKSTONE, -0.95, 0.10, -2.10, 1.90, 0.60, 4.20));
            parts.add(part("roof", Material.COAL_BLOCK, -0.86, 1.22, -1.15, 1.72, 0.08, 1.80));
            for (double x : new double[]{-0.86, 0.78})
                for (double z : new double[]{-1.15, 0.57}) parts.add(part("pillar", Material.POLISHED_BLACKSTONE, x, 0.70, z, 0.08, 0.52, 0.08));
            parts.add(part("rust", Material.BROWN_TERRACOTTA, -0.80, 0.70, 0.95, 1.60, 0.04, 1.00));
            for (double x : new double[]{-0.97, 0.72})
                for (double z : new double[]{-1.55, 1.00}) parts.add(part("rim", Material.DEEPSLATE, x, 0.0, z, 0.25, 0.32, 0.55));
            return parts;
        }
        if (this == MOTORBIKE) {
            parts.add(part(BODY, paint, -0.20, 0.55, -0.35, 0.40, 0.30, 0.95));                    // tank and frame
            parts.add(part("seat", Material.BLACK_WOOL, -0.18, 0.80, -0.95, 0.36, 0.10, 0.75));
            parts.add(part("engine", Material.GRAY_CONCRETE, -0.17, 0.25, -0.35, 0.34, 0.30, 0.55));
            parts.add(part("exhaust", Material.LIGHT_GRAY_CONCRETE, 0.17, 0.25, -1.15, 0.10, 0.10, 0.85));
            parts.add(part("fork", Material.LIGHT_GRAY_CONCRETE, -0.07, 0.30, 0.70, 0.14, 0.75, 0.10));
            parts.add(part("handlebar", Material.BLACK_CONCRETE, -0.38, 1.02, 0.62, 0.76, 0.06, 0.08));
            parts.add(part("wheel", Material.BLACK_CONCRETE, -0.08, 0.0, 0.55, 0.16, 0.58, 0.58));
            parts.add(part("wheel", Material.BLACK_CONCRETE, -0.08, 0.0, -1.20, 0.16, 0.58, 0.58));
            parts.add(part("fender", paint, -0.11, 0.56, -1.25, 0.22, 0.06, 0.62));
            parts.add(light(HEADLIGHT, false, -0.10, 0.86, 0.80, 0.20, 0.16, 0.06));
            parts.add(light(TAILLIGHT, false, -0.08, 0.80, -1.35, 0.16, 0.08, 0.04));
            return parts;
        }
        if (this == VAN) {
            parts.add(part(BODY, paint, -1.00, 0.35, -2.45, 2.00, 1.75, 4.20));                    // cargo box and cabin
            parts.add(part("hood", paint, -1.00, 0.35, 1.75, 2.00, 0.85, 0.70));
            parts.add(part("glass", Material.BLACK_STAINED_GLASS, -0.92, 1.25, 1.70, 1.84, 0.75, 0.06));  // windscreen
            for (double x : new double[]{-1.01, 0.99}) parts.add(part("glass", Material.BLACK_STAINED_GLASS, x, 1.25, 0.70, 0.02, 0.62, 0.95));
            parts.add(part("roof", Material.WHITE_CONCRETE, -0.98, 2.10, -2.40, 1.96, 0.05, 4.10));
            parts.add(part("stripe", Material.ORANGE_CONCRETE, -1.01, 0.95, -2.40, 2.02, 0.12, 4.05));
            for (double x : new double[]{-1.03, 0.73})
                for (double z : new double[]{-1.85, 1.35}) parts.add(part("wheel", Material.BLACK_CONCRETE, x, 0.0, z, 0.30, 0.66, 0.66));
            parts.add(light(HEADLIGHT, false, 0.50, 0.75, 2.44, 0.36, 0.18, 0.04));
            parts.add(light(HEADLIGHT, false, -0.86, 0.75, 2.44, 0.36, 0.18, 0.04));
            parts.add(light(TAILLIGHT, false, -0.95, 0.80, -2.48, 1.90, 0.12, 0.04));
            parts.add(part("bumper", Material.GRAY_CONCRETE, -1.02, 0.30, 2.42, 2.04, 0.18, 0.10));
            parts.add(part("bumper", Material.GRAY_CONCRETE, -1.02, 0.30, -2.52, 2.04, 0.18, 0.10));
            parts.add(part("doors", Material.GRAY_CONCRETE, -0.55, 0.45, -2.47, 0.02, 1.55, 0.02));
            return parts;
        }
        parts.add(part(BODY, paint, -0.95, 0.30, -2.10, 1.90, 0.60, 4.20));
        parts.add(part("glass", Material.BLACK_STAINED_GLASS, -0.86, 0.90, -1.30, 1.72, 0.70, 2.15));
        parts.add(part("roof", paint, -0.88, 1.60, -1.20, 1.76, 0.10, 1.95));
        // Seen from inside only: a negative scale turns its faces inwards.
        parts.add(part("headliner", Material.LIGHT_GRAY_WOOL, -0.84, 1.595, -1.15, 1.68, -0.02, 1.85));
        for (double x : new double[]{-0.97, 0.67})
            for (double z : new double[]{-1.62, 0.98}) parts.add(part("wheel", Material.BLACK_CONCRETE, x, 0.0, z, 0.30, 0.62, 0.64));
        parts.add(light(HEADLIGHT, false, 0.48, 0.62, 2.08, 0.36, 0.16, 0.04));
        parts.add(light(HEADLIGHT, false, -0.84, 0.62, 2.08, 0.36, 0.16, 0.04));
        parts.add(light(TAILLIGHT, false, -0.85, 0.66, -2.12, 1.70, 0.12, 0.04));
        parts.add(part("bumper", Material.LIGHT_GRAY_CONCRETE, -0.97, 0.28, 2.06, 1.94, 0.16, 0.10));
        parts.add(part("bumper", Material.LIGHT_GRAY_CONCRETE, -0.97, 0.28, -2.16, 1.94, 0.16, 0.10));
        parts.add(part("grille", Material.BLACK_CONCRETE, -0.40, 0.46, 2.09, 0.80, 0.12, 0.03));
        parts.add(part("plate", Material.WHITE_CONCRETE, -0.25, 0.40, -2.14, 0.50, 0.11, 0.02));
        return parts;
    }

    /** A headlight or the rear lights, lit while the engine runs. */
    static Part light(String role, boolean lit, double tx, double ty, double tz, double sx, double sy, double sz) {
        Material material = HEADLIGHT.equals(role) ? (lit ? Material.SEA_LANTERN : Material.WHITE_STAINED_GLASS)
                : (lit ? Material.RED_CONCRETE : Material.RED_STAINED_GLASS);
        Part part = part(role, material, tx, ty, tz, sx, sy, sz);
        return new Part(role, part.data(), part.transformation(), lit);
    }

    static BlockData lightData(String role, boolean lit) {
        return (HEADLIGHT.equals(role) ? (lit ? Material.SEA_LANTERN : Material.WHITE_STAINED_GLASS)
                : (lit ? Material.RED_CONCRETE : Material.RED_STAINED_GLASS)).createBlockData();
    }

    private static Part part(String role, Material material, double tx, double ty, double tz, double sx, double sy, double sz) {
        Transformation transformation = new Transformation(new Vector3f((float) tx, (float) ty, (float) tz), new Quaternionf(),
                new Vector3f((float) sx, (float) sy, (float) sz), new Quaternionf());
        return new Part(role, material.createBlockData(), transformation, false);
    }
}
