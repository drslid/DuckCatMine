package org.duckcatmine.apocalypse.mobs;

import java.util.Random;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;

/**
 * Minimal first director: keeps a bounded number of walkers around each active survivor, out of sight
 * when possible, never in the refuge, never in unloaded chunks. Hordes and tension cycles come later.
 */
public final class SpawnDirector {
    private static final int LOCAL_RADIUS = 40;
    private static final int ATTEMPTS = 6;

    private final DuckCatApocalypse plugin;
    private final Random random = new Random();

    public SpawnDirector(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        ApocalypseConfig.Spawning spawning = plugin.settings().spawning();
        World world = plugin.scenario().world();
        if (!plugin.scenario().isEnabled() || world == null || !spawning.enabled()) return;
        despawnFar(world, spawning.despawnDistance());
        int total = plugin.mobs().count();
        for (Player player : world.getPlayers()) {
            if (total >= spawning.globalCap()) return;
            if (player.isDead() || !plugin.sessions().isSurvivor(player) || plugin.scenario().inRefuge(player.getLocation())) continue;
            if (plugin.mobs().countNear(player.getLocation(), LOCAL_RADIUS) >= spawning.perPlayerLocalCap()) continue;
            Location spot = findSpot(world, player, spawning);
            if (spot == null) continue;
            plugin.mobs().spawnWalker(spot);
            total++;
        }
    }

    private Location findSpot(World world, Player player, ApocalypseConfig.Spawning spawning) {
        Location origin = player.getLocation();
        double facing = Math.toRadians(origin.getYaw() + 90);
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = attempt < ATTEMPTS / 2 ? facing + Math.PI + (random.nextDouble() - 0.5) * Math.PI / 1.5
                    : random.nextDouble() * Math.PI * 2;
            double distance = spawning.minDistance() + random.nextDouble() * (spawning.maxDistance() - spawning.minDistance());
            int x = (int) Math.floor(origin.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(origin.getZ() + Math.sin(angle) * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4) || plugin.scenario().refugeArea().near(x, z, 8)) continue;
            Location spot = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
            if (!world.getWorldBorder().isInside(spot)) continue;
            Block feet = spot.getBlock();
            Block below = feet.getRelative(BlockFace.DOWN);
            if (!feet.isPassable() || !feet.getRelative(BlockFace.UP).isPassable() || !below.isSolid() || below.isLiquid()) continue;
            if (nearAnySurvivor(world, spot, spawning.minDistance())) continue;
            return spot;
        }
        return null;
    }

    private boolean nearAnySurvivor(World world, Location spot, double distance) {
        double squared = distance * distance;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(spot) < squared) return true;
        }
        return false;
    }

    private void despawnFar(World world, int distance) {
        double squared = (double) distance * distance;
        for (UUID id : plugin.mobs().trackedIds()) {
            Entity entity = plugin.getServer().getEntity(id);
            if (!(entity instanceof Mob mob) || !mob.getWorld().equals(world) || mob.getTarget() != null) continue;
            boolean near = false;
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(mob.getLocation()) <= squared) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                mob.remove();
                plugin.mobs().forget(id);
            }
        }
    }
}
