package org.duckcatmine.apocalypse.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.items.ItemRegistry;

/**
 * Identity of blocks placed from scenario items (stations, barricades, traps, base beacons), stored in the chunk's
 * persistent data so it follows the world: a vanilla look-alike never becomes a station or a barricade, breaking one
 * gives back the scenario item, pistons cannot move them. Beacons claim a base for their owner's team.
 */
public final class PlacedBlocks implements Listener {
    public static final int CLAIM_RADIUS = 12;

    public record Placed(String kind, UUID owner, int data) {
        String encode() {
            return kind + ";" + owner + ";" + data;
        }

        static Placed decode(String value) {
            String[] parts = value.split(";", 3);
            return new Placed(parts[0], UUID.fromString(parts[1]), Integer.parseInt(parts[2]));
        }
    }

    public record Claim(String world, int x, int y, int z, UUID owner) {
        public boolean covers(Location location) {
            return location.getWorld().getName().equals(world) && Math.abs(location.getBlockX() - x) <= CLAIM_RADIUS
                    && Math.abs(location.getBlockZ() - z) <= CLAIM_RADIUS;
        }
    }

    private final DuckCatApocalypse plugin;
    private final Map<String, Claim> claims = new HashMap<>();
    private final Map<UUID, Long> trapCooldown = new HashMap<>();

    public PlacedBlocks(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    private NamespacedKey key(Block block) {
        return new NamespacedKey(plugin, "pb_" + (block.getX() & 15) + "_" + block.getY() + "_" + (block.getZ() & 15));
    }

    public Placed get(Block block) {
        if (!plugin.scenario().isScenarioWorld(block.getWorld())) return null;
        String value = block.getChunk().getPersistentDataContainer().get(key(block), PersistentDataType.STRING);
        return value == null ? null : Placed.decode(value);
    }

    public void put(Block block, Placed placed) {
        block.getChunk().getPersistentDataContainer().set(key(block), PersistentDataType.STRING, placed.encode());
        if (ItemRegistry.BEACON.equals(placed.kind())) claims.put(claimKey(block), claim(block, placed));
    }

    public void remove(Block block) {
        block.getChunk().getPersistentDataContainer().remove(key(block));
        claims.remove(claimKey(block));
    }

    private static String claimKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private static Claim claim(Block block, Placed placed) {
        return new Claim(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), placed.owner());
    }

    /** Claims covering a place (only loaded ones: a siege never happens in an unloaded chunk). */
    public List<Claim> claimsAt(Location location) {
        List<Claim> found = new ArrayList<>();
        for (Claim claim : claims.values()) if (claim.covers(location)) found.add(claim);
        return found;
    }

    public List<Claim> claims() {
        return List.copyOf(claims.values());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.scenario().isScenarioWorld(event.getWorld())) return;
        index(event.getChunk(), true);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!plugin.scenario().isScenarioWorld(event.getWorld())) return;
        index(event.getChunk(), false);
    }

    private void index(Chunk chunk, boolean add) {
        PersistentDataContainer data = chunk.getPersistentDataContainer();
        for (NamespacedKey key : data.getKeys()) {
            if (!key.getNamespace().equals(plugin.getName().toLowerCase(java.util.Locale.ROOT)) || !key.getKey().startsWith("pb_")) continue;
            String value = data.get(key, PersistentDataType.STRING);
            if (value == null || !value.startsWith(ItemRegistry.BEACON + ";")) continue;
            String[] position = key.getKey().substring(3).split("_");
            Block block = chunk.getBlock(Integer.parseInt(position[0]), Integer.parseInt(position[1]), Integer.parseInt(position[2]));
            if (add) claims.put(claimKey(block), claim(block, Placed.decode(value)));
            else claims.remove(claimKey(block));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String id = plugin.items().idOf(event.getItemInHand());
        if (id == null || !ItemRegistry.PLACEABLE.contains(id)) return;
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (!plugin.sessions().isSurvivor(player) || plugin.scenario().refugeArea().near(block.getX(), block.getZ(), 2)) {
            event.setCancelled(true);
            Text.warn(player, "Impossible ici : seulement dans Ground Zero, hors du refuge.");
            return;
        }
        if (ItemRegistry.SPIKE_TRAP.equals(id) && !block.getRelative(BlockFace.DOWN).isSolid()) {
            event.setCancelled(true);
            Text.warn(player, "Le piège doit être posé sur un sol solide.");
            return;
        }
        if (ItemRegistry.BEACON.equals(id) && !claimsAt(block.getLocation()).isEmpty()) {
            event.setCancelled(true);
            Text.warn(player, "Une balise revendique déjà ce lieu.");
            return;
        }
        Integer charges = event.getItemInHand().getPersistentDataContainer().get(plugin.keys().charges, PersistentDataType.INTEGER);
        put(block, new Placed(id, player.getUniqueId(), charges == null ? 0 : charges));
        if (ItemRegistry.BEACON.equals(id))
            Text.good(player, "Base revendiquée (rayon " + CLAIM_RADIUS + ") : aucun siège ici quand toi et ton équipe êtes absents.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Placed placed = get(event.getBlock());
        if (placed == null) return;
        event.setDropItems(false);
        ItemStack item = plugin.items().create(placed.kind(), 1);
        if (item != null && ItemRegistry.SPIKE_TRAP.equals(placed.kind())) {
            if (placed.data() <= 0) item = null;
            else item.editPersistentDataContainer(data -> data.set(plugin.keys().charges, PersistentDataType.INTEGER, placed.data()));
        }
        if (item != null) event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), item);
        remove(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> get(block) != null)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> get(block) != null)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        forgetDestroyed(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        forgetDestroyed(event.blockList());
    }

    private void forgetDestroyed(List<Block> blocks) {
        for (Block block : blocks) if (get(block) != null) remove(block);
    }

    public boolean isStation(Block block, String kind) {
        Placed placed = get(block);
        return placed != null && placed.kind().equals(kind);
    }

    /** Spike traps: an infected stepping on one is hurt (credited to the owner if present) and a charge is used. */
    public void stepOn(Mob mob) {
        Block block = mob.getLocation().getBlock();
        if (block.getType() != Material.POINTED_DRIPSTONE) return;
        Placed placed = get(block);
        if (placed == null || !ItemRegistry.SPIKE_TRAP.equals(placed.kind()) || placed.data() <= 0) return;
        long now = plugin.clock().now();
        Long ready = trapCooldown.get(mob.getUniqueId());
        if (ready != null && ready > now) return;
        trapCooldown.put(mob.getUniqueId(), now + 1000);
        trapCooldown.values().removeIf(until -> until < now - 5000);
        Player owner = plugin.getServer().getPlayer(placed.owner());
        double damage = plugin.settings().play().traps().damage();
        if (owner != null && plugin.sessions().isSurvivor(owner) && owner.getWorld().equals(mob.getWorld()))
            mob.damage(damage, DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(owner).withDirectEntity(owner).build());
        else mob.damage(damage, DamageSource.builder(DamageType.STALAGMITE).build());
        mob.getWorld().playSound(block.getLocation(), Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1f, 0.8f);
        mob.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, mob.getLocation().add(0, 0.5, 0), 3, 0.2, 0.2, 0.2, 0);
        int left = placed.data() - 1;
        if (left <= 0) {
            remove(block);
            block.setType(Material.AIR);
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOOD_BREAK, 1f, 0.9f);
        } else {
            put(block, new Placed(placed.kind(), placed.owner(), left));
        }
    }

    /** Tracked blocks around a place matching a filter, for the self-test and administration. */
    public int count(Chunk chunk, Predicate<Placed> filter) {
        int count = 0;
        PersistentDataContainer data = chunk.getPersistentDataContainer();
        for (NamespacedKey key : data.getKeys()) {
            if (!key.getKey().startsWith("pb_")) continue;
            String value = data.get(key, PersistentDataType.STRING);
            if (value != null && filter.test(Placed.decode(value))) count++;
        }
        return count;
    }
}
