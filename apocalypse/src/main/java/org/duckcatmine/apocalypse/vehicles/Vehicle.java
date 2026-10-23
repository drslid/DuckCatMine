package org.duckcatmine.apocalypse.vehicles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;

/** Live state of one car; the body display keeps a copy (see {@link #encode()}) so it survives restarts. */
final class Vehicle {
    static final int SEATS = 4;

    final UUID id;
    final World world;
    VehicleModel model;
    Material paint;
    double x;
    double y;
    double z;
    float yaw;
    double speed;
    double fall;
    double fuel;
    double health;
    boolean salvaged;
    boolean engineOn;
    final List<UUID> parts = new ArrayList<>();
    UUID body;
    UUID interaction;
    final UUID[] seats = new UUID[SEATS];
    final UUID[] riders = new UUID[SEATS];
    final List<int[]> barriers = new ArrayList<>();
    final Map<UUID, Long> hits = new HashMap<>();
    long ticks;
    long lastHorn;

    Vehicle(UUID id, World world, VehicleModel model, Material paint, double x, double y, double z, float yaw, double fuel, double health) {
        this.id = id;
        this.world = world;
        this.model = model;
        this.paint = paint;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.fuel = fuel;
        this.health = health;
    }

    boolean occupied() {
        for (UUID rider : riders) if (rider != null) return true;
        return false;
    }

    int seatOf(UUID player) {
        for (int i = 0; i < SEATS; i++) if (player.equals(riders[i])) return i;
        return -1;
    }

    String encode() {
        StringBuilder out = new StringBuilder("1;").append(model.name()).append(';').append(paint.name()).append(';')
                .append(String.format(Locale.ROOT, "%.2f;%.1f;", fuel, health)).append(salvaged ? 1 : 0).append(';');
        for (int k = 0; k < barriers.size(); k++) {
            int[] b = barriers.get(k);
            if (k > 0) out.append('|');
            out.append(b[0]).append(',').append(b[1]).append(',').append(b[2]);
        }
        return out.toString();
    }

    /** Restores the saved fields; position and yaw come from the body entity. */
    static Vehicle decode(UUID id, World world, String state, double x, double y, double z, float yaw) {
        String[] fields = state == null ? new String[0] : state.split(";", -1);
        VehicleModel model = VehicleModel.SEDAN;
        Material paint = Material.WHITE_CONCRETE;
        double fuel = 0, health = 100;
        boolean salvaged = false;
        try {
            if (fields.length > 1) model = VehicleModel.valueOf(fields[1]);
            if (fields.length > 2 && Material.matchMaterial(fields[2]) != null) paint = Material.matchMaterial(fields[2]);
            if (fields.length > 3) fuel = Double.parseDouble(fields[3]);
            if (fields.length > 4) health = Double.parseDouble(fields[4]);
            if (fields.length > 5) salvaged = "1".equals(fields[5]);
        } catch (IllegalArgumentException ignored) {
            // A damaged state keeps the defaults: the car stays usable.
        }
        Vehicle vehicle = new Vehicle(id, world, model, paint, x, y, z, yaw, fuel, health);
        vehicle.salvaged = salvaged;
        if (fields.length > 6 && !fields[6].isEmpty()) {
            for (String block : fields[6].split("\\|")) {
                String[] xyz = block.split(",");
                if (xyz.length != 3) continue;
                try {
                    vehicle.barriers.add(new int[]{Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])});
                } catch (NumberFormatException ignored) {
                    // Skip one unreadable cell rather than lose the car.
                }
            }
        }
        return vehicle;
    }
}
