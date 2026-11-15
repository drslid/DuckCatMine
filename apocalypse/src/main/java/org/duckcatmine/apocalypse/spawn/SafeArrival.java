package org.duckcatmine.apocalypse.spawn;

import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Adjacent standing spots in already loaded chunks. Never changes blocks or silently falls back to an unsafe spot. */
public final class SafeArrival {
    private static final Set<Material> DANGER = Set.of(Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.POINTED_DRIPSTONE, Material.SWEET_BERRY_BUSH, Material.FIRE,
            Material.SOUL_FIRE, Material.POWDER_SNOW, Material.NETHER_PORTAL, Material.END_PORTAL, Material.BARRIER);

    private SafeArrival() {}

    public static Location near(Location center, Set<String> reserved) {
        World world = center.getWorld();
        for (int radius = 2; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = center.getBlockX() + dx, z = center.getBlockZ() + dz;
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    for (int dy : new int[]{0, 1, -1, 2, -2}) {
                        int y = center.getBlockY() + dy;
                        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) continue;
                        String key = x + ":" + y + ":" + z;
                        if (reserved.contains(key)) continue;
                        Location at = new Location(world, x + .5, y, z + .5, center.getYaw(), center.getPitch());
                        if (!world.getWorldBorder().isInside(at)) continue;
                        Block feet = world.getBlockAt(x, y, z), head = world.getBlockAt(x, y + 1, z), below = world.getBlockAt(x, y - 1, z);
                        if (!feet.isPassable() || !head.isPassable() || feet.isLiquid() || head.isLiquid() || !below.isSolid()
                                || DANGER.contains(feet.getType()) || DANGER.contains(head.getType()) || DANGER.contains(below.getType())) continue;
                        if (world.getNearbyEntities(at.clone().add(0,.9,0),.4,.8,.4).stream()
                                .anyMatch(entity -> entity instanceof org.bukkit.entity.Player || entity instanceof org.bukkit.entity.Interaction)) continue;
                        reserved.add(key);
                        return at;
                    }
                }
            }
        }
        return null;
    }
}
