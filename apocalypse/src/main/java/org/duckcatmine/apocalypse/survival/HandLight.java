package org.duckcatmine.apocalypse.survival;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;
import org.duckcatmine.apocalypse.DuckCatApocalypse;

/**
 * A torch or a lantern held in either hand lights the surroundings: a light block, sent to nearby clients only, follows
 * the survivor's head. The world never changes, so a crash cannot leave light blocks behind.
 */
public final class HandLight {
    private static final int VIEW = 64 * 64;
    private final DuckCatApocalypse plugin;
    private final Map<UUID, Location> lit = new HashMap<>();
    private long ticks;

    public HandLight(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    /** Light level given by what the player holds, 0 for none. */
    public static int level(Player player) {
        return Math.max(level(player.getInventory().getItemInMainHand().getType()), level(player.getInventory().getItemInOffHand().getType()));
    }

    static int level(Material type) {
        return switch (type) {
            case TORCH, LANTERN, GLOWSTONE, SEA_LANTERN, JACK_O_LANTERN, SHROOMLIGHT, BEACON, CAMPFIRE -> 14;
            case SOUL_TORCH, SOUL_LANTERN, SOUL_CAMPFIRE -> 10;
            case REDSTONE_TORCH -> 7;
            default -> type.name().contains("COPPER_TORCH") || type.name().contains("COPPER_LANTERN") ? 13 : 0;
        };
    }

    /** Every 5 ticks: move each light with its holder, resend now and then for players who just came close. */
    public void tick() {
        ticks++;
        World world = plugin.scenario().world();
        Set<UUID> holding = new HashSet<>();
        if (world != null) {
            for (Player player : world.getPlayers()) {
                int level = player.isDead() ? 0 : level(player);
                if (level == 0) continue;
                Block block = player.getEyeLocation().getBlock();
                if (!block.getType().isAir()) block = player.getLocation().getBlock();
                if (!block.getType().isAir()) continue;
                holding.add(player.getUniqueId());
                Location at = block.getLocation();
                Location previous = lit.get(player.getUniqueId());
                if (at.equals(previous) && ticks % 8 != 0) continue;
                if (previous != null && !previous.equals(at)) restore(previous);
                Light light = (Light) Material.LIGHT.createBlockData();
                light.setLevel(level);
                for (Player viewer : world.getPlayers()) if (viewer.getLocation().distanceSquared(at) < VIEW) viewer.sendBlockChange(at, light);
                lit.put(player.getUniqueId(), at);
            }
        }
        lit.entrySet().removeIf(entry -> {
            if (holding.contains(entry.getKey())) return false;
            restore(entry.getValue());
            return true;
        });
    }

    private void restore(Location at) {
        World world = at.getWorld();
        if (world == null || !world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return;
        var real = at.getBlock().getBlockData();
        for (Player viewer : world.getPlayers()) if (viewer.getLocation().distanceSquared(at) < VIEW) viewer.sendBlockChange(at, real);
    }

    public void stop() {
        lit.values().forEach(this::restore);
        lit.clear();
    }
}
