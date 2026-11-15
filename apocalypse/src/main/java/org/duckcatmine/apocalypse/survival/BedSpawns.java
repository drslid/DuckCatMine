package org.duckcatmine.apocalypse.survival;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.persistence.Store;

/** A right-click on a Ground Zero bed makes it the next life's starting point, as long as the bed still stands. */
public final class BedSpawns implements Listener {
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private final DuckCatApocalypse plugin;
    private final Map<UUID, int[]> beds = new ConcurrentHashMap<>();

    public BedSpawns(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    private static String key(UUID id) {
        return "bed:" + id;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBed(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || block == null
                || !Tag.BEDS.isTagged(block.getType())) return;
        Player player = event.getPlayer();
        if (!plugin.sessions().isSurvivor(player) || !plugin.scenario().isScenarioWorld(block.getWorld())) return;
        // No sleeping through the night in Ground Zero: the bed only records where the next life starts.
        event.setUseInteractedBlock(Event.Result.DENY);
        if (!plugin.settings().play().survival().bedRespawn()) return;
        Block foot = block.getBlockData() instanceof Bed bed && bed.getPart() == Bed.Part.HEAD ? block.getRelative(bed.getFacing().getOppositeFace()) : block;
        int[] at = {foot.getX(), foot.getY(), foot.getZ()};
        int[] previous = beds.put(player.getUniqueId(), at);
        String value = at[0] + "," + at[1] + "," + at[2];
        UUID id = player.getUniqueId();
        plugin.database().submit(c -> {
            Store.putMeta(c, key(id), value);
            return null;
        });
        if (previous == null || previous[0] != at[0] || previous[1] != at[1] || previous[2] != at[2])
            Text.good(player, "Point de réapparition enregistré : ta prochaine vie commencera près de ce lit.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSleep(PlayerBedEnterEvent event) {
        if (plugin.scenario().isScenarioWorld(event.getBed().getWorld())) event.setCancelled(true);
    }

    /** The recorded bed's standing spot, or null (no bed, destroyed, or option off). Loads the chunk asynchronously. */
    public CompletableFuture<Location> respawnPoint(Player player, World world) {
        if (!plugin.settings().play().survival().bedRespawn()) return CompletableFuture.completedFuture(null);
        int[] at = beds.get(player.getUniqueId());
        if (at == null) at = stored(player.getUniqueId());
        if (at == null) return CompletableFuture.completedFuture(null);
        int[] bed = at;
        return world.getChunkAtAsync(bed[0] >> 4, bed[2] >> 4).thenApply(chunk -> standing(world.getBlockAt(bed[0], bed[1], bed[2])));
    }

    private int[] stored(UUID id) {
        try {
            String value = plugin.database().call(c -> Store.meta(c, key(id)));
            if (value == null) return null;
            String[] parts = value.split(",");
            int[] at = {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
            beds.put(id, at);
            return at;
        } catch (Exception e) {
            plugin.getLogger().warning("Lit de réapparition illisible : " + e.getMessage().toLowerCase(Locale.ROOT));
            return null;
        }
    }

    static Location standing(Block bed) {
        if (!Tag.BEDS.isTagged(bed.getType())) return null;
        for (BlockFace face : SIDES) {
            Block feet = bed.getRelative(face);
            if (feet.isPassable() && !feet.isLiquid() && feet.getRelative(BlockFace.UP).isPassable() && feet.getRelative(BlockFace.DOWN).isSolid())
                return feet.getLocation().add(0.5, 0, 0.5);
        }
        Block above = bed.getRelative(BlockFace.UP);
        return above.isPassable() && above.getRelative(BlockFace.UP).isPassable() ? bed.getLocation().add(0.5, 0.6, 0.5) : null;
    }
}
