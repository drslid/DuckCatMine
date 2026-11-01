package org.duckcatmine.apocalypse.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.scenario.GroundZeroGenerator;
import org.duckcatmine.apocalypse.scenario.city.CityLayout;
import org.duckcatmine.apocalypse.scenario.city.PlaceType;

/**
 * Searchable containers of the generated city (spec §10): loot shared by the world, rolled once at the first opening
 * and kept through reconnections and chunk reloads; an emptied container is restocked after the configured active time,
 * only once nobody is around (its chunk was unloaded). Deposits and normal player breaking are allowed; non-empty
 * storage is never cleared by a refill. Containers placed by players never become searchable.
 */
public final class SearchService implements Listener {
    private final DuckCatApocalypse plugin;
    private final Random random = new Random();

    public SearchService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    private static boolean candidate(Block block) {
        return block.getType() == Material.CHEST || block.getType() == Material.BARREL;
    }

    /** Place type of a searchable container, identifying a generated one from the city plan when not marked yet. */
    public PlaceType identify(Block block) {
        if (!candidate(block) || !plugin.scenario().isScenarioWorld(block.getWorld())) return null;
        if (!(block.getState(false) instanceof TileState tile)) return null;
        PersistentDataContainer data = tile.getPersistentDataContainer();
        if (data.has(plugin.keys().playerPlaced)) return null;
        String stored = data.get(plugin.keys().searchPlace, PersistentDataType.STRING);
        if (stored != null) return PlaceType.byId(stored);
        if (!(block.getWorld().getGenerator() instanceof GroundZeroGenerator)) return null;
        return CityLayout.of(block.getWorld().getSeed()).placeAt(block.getX(), block.getY(), block.getZ());
    }

    /** Work on physical halves: a player-placed extension must never inherit its neighbour's loot. */
    private static List<Block> blocks(Inventory inventory) {
        List<Block> blocks = new ArrayList<>(2);
        InventoryHolder holder = inventory.getHolder(false);
        if (holder instanceof DoubleChest chest) {
            if (chest.getLeftSide(false) instanceof BlockInventoryHolder left) blocks.add(left.getBlock());
            if (chest.getRightSide(false) instanceof BlockInventoryHolder right) blocks.add(right.getBlock());
        } else if (holder instanceof BlockInventoryHolder block) blocks.add(block.getBlock());
        return blocks;
    }

    public static Inventory localInventory(Container container) {
        return container instanceof Chest chest ? chest.getBlockInventory() : container.getInventory();
    }

    /** Populate each generated half once. Used for normal and double-chest inventory opens. */
    public boolean rollInventory(Inventory inventory) {
        boolean rolled = false;
        for (Block block : blocks(inventory)) rolled |= roll(block);
        return rolled;
    }

    /** Writes the marker through a snapshot; call before filling the live inventory (the update restores the snapshot's items). */
    private void mark(Block block, PlaceType place, Long rolledAt) {
        BlockState snapshot = block.getState();
        if (!(snapshot instanceof TileState tile)) return;
        PersistentDataContainer data = tile.getPersistentDataContainer();
        data.set(plugin.keys().searchPlace, PersistentDataType.STRING, place.id());
        if (rolledAt != null) data.set(new org.bukkit.NamespacedKey(plugin, "stock_v2"), PersistentDataType.BOOLEAN, true);
        if (rolledAt == null) data.remove(plugin.keys().searchRolled);
        else data.set(plugin.keys().searchRolled, PersistentDataType.LONG, rolledAt);
        if (tile instanceof Container container && container.customName() == null) container.customName(Component.text(place == PlaceType.SHOP || place == PlaceType.SUPERMARKET ? "Rayon · " + place.label() : place.label()));
        tile.update(true, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        List<Block> blocks = blocks(event.getInventory());
        Block rename = null;
        for (Block block : blocks) {
            boolean named = block.getState(false) instanceof TileState tile && tile.getPersistentDataContainer().has(plugin.keys().searchPlace);
            if (roll(block) && !named) rename = block;
        }
        if (rename == null || !(event.getPlayer() instanceof Player player)) return;
        Block anchor = rename;
        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && candidate(anchor) && anchor.getState(false) instanceof Container container)
                player.openInventory(container.getInventory());
        });
    }

    /** Rolls the loot of a searchable container that is due (first search or restocked); returns true when it did. */
    public boolean roll(Block block) {
        PlaceType place = identify(block);
        if (place == null || !(block.getState(false) instanceof Container tile)) return false;
        Inventory inventory = localInventory(tile);
        if (tile.getPersistentDataContainer().has(plugin.keys().searchRolled)) {
            if (tile.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "stock_v2"))) return false;
            mark(block, place, plugin.clock().now());
            fill(inventory, place, 3);
            return true;
        }
        mark(block, place, plugin.clock().now());
        fill(inventory, place);
        return true;
    }

    /** Empties a searchable container and makes it due again (administration and self-test). */
    public void reset(Block block) {
        PlaceType place = identify(block);
        if (place == null || !(block.getState(false) instanceof Container container)) return;
        localInventory(container).clear();
        mark(block, place, null);
    }

    private void fill(Inventory inventory, PlaceType place) { fill(inventory, place, -1); }

    private void fill(Inventory inventory, PlaceType place, int fixedRolls) {
        ApocalypseConfig.Place table = plugin.settings().place(place.id());
        if (table == null) return;
        int rolls = fixedRolls >= 0 ? fixedRolls : table.rollsMin() + random.nextInt(table.rollsMax() - table.rollsMin() + 1);
        List<Integer> free = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.isEmpty()) free.add(slot);
        }
        for (int k = 0; k < rolls && !free.isEmpty(); k++) {
            LootTable.Entry entry = table.loot().pick(random.nextDouble());
            ItemStack stack = plugin.items().resolve(entry.item(), entry.amount());
            if (stack != null) inventory.setItem(free.remove(random.nextInt(free.size())), stack);
        }
    }

    /** Restock happens only for empty containers, once their chunk is loaded again (nobody was around). */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk() || !plugin.scenario().isScenarioWorld(event.getWorld())) return;
        long now = plugin.clock().now();
        long refill = plugin.settings().search().refillMs();
        for (BlockState state : event.getChunk().getTileEntities(SearchService::candidate, false)) {
            if (!(state instanceof Container container)) continue;
            PersistentDataContainer data = container.getPersistentDataContainer();
            String place = data.get(plugin.keys().searchPlace, PersistentDataType.STRING);
            Long rolled = data.get(plugin.keys().searchRolled, PersistentDataType.LONG);
            if (place == null || rolled == null || data.has(plugin.keys().playerPlaced) || now - rolled < refill) continue;
            if (!container.getInventory().isEmpty()) continue;
            PlaceType type = PlaceType.byId(place);
            if (type != null) mark(state.getBlock(), type, null);
        }
    }

    /** Native breaking releases the inventory exactly once, including unopened generated loot. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.isDropItems()) roll(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> identify(block) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> identify(block) != null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!candidate(block) && block.getType() != Material.TRAPPED_CHEST) return;
        if (!plugin.scenario().isScenarioWorld(block.getWorld())) return;
        if (!(block.getState() instanceof TileState tile)) return;
        tile.getPersistentDataContainer().set(plugin.keys().playerPlaced, PersistentDataType.BOOLEAN, true);
        tile.update(true, false);
    }
}
