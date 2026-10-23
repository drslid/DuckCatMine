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
    SEDAN, WRECK;

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
