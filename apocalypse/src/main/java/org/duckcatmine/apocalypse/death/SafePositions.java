package org.duckcatmine.apocalypse.death;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.duckcatmine.apocalypse.DuckCatApocalypse;

/** Recent standable positions, used when a death happens in the void, in lava or away from the game. */
public final class SafePositions {
    private final DuckCatApocalypse plugin;
    private final Map<UUID, Location> last = new HashMap<>();

    public SafePositions(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public void sampleAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) sample(player);
    }

    @SuppressWarnings("deprecation")
    private void sample(Player player) {
        if (player.isDead() || !player.isOnGround() || !plugin.sessions().isSurvivor(player)) return;
        Location location = player.getLocation();
        if (usable(location)) last.put(player.getUniqueId(), centered(location));
    }

    /** Standable, not burning, inside the border of the scenario world. */
    public boolean usable(Location location) {
        if (location == null || !plugin.scenario().isScenarioWorld(location.getWorld())) return false;
        World world = location.getWorld();
        if (location.getY() < world.getMinHeight() + 1 || location.getY() > world.getMaxHeight() - 2) return false;
        if (!world.getWorldBorder().isInside(location)) return false;
        Block feet = location.getBlock();
        Material type = feet.getType();
        if (type == Material.LAVA || type == Material.FIRE || type == Material.SOUL_FIRE || type == Material.MAGMA_BLOCK) return false;
        Block below = feet.getRelative(BlockFace.DOWN);
        return below.getType() != Material.LAVA && (below.isSolid() || feet.isLiquid());
    }

    public Location last(UUID id) {
        Location location = last.get(id);
        return location == null ? null : location.clone();
    }

    public void forget(UUID id) {
        last.remove(id);
    }

    public String encoded(UUID id) {
        Location location = last.get(id);
        if (location == null) return null;
        return String.format(Locale.ROOT, "%s;%.2f;%.2f;%.2f", location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
    }

    public static Location decode(String encoded) {
        if (encoded == null) return null;
        String[] parts = encoded.split(";");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Location centered(Location location) {
        return new Location(location.getWorld(), location.getBlockX() + 0.5, location.getBlockY(), location.getBlockZ() + 0.5);
    }
}
