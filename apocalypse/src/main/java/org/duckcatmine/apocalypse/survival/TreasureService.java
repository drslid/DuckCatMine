package org.duckcatmine.apocalypse.survival;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.items.ItemRegistry;
import org.duckcatmine.apocalypse.loot.LootTable;
import org.duckcatmine.apocalypse.persistence.Store;
import org.duckcatmine.apocalypse.scenario.city.Sector;

/**
 * Treasure maps: three fragments found while searching form a map of a cache hidden in the countryside. Holding the
 * map shows direction and distance; the cache holds a laser turret (silver, rarely gold) and strong loot.
 */
public final class TreasureService implements Listener {
    public static final int FRAGMENTS = 3;
    public static final double GOLD_CHANCE = 0.15;
    private final DuckCatApocalypse plugin;
    private final Random random = new Random();
    private final NamespacedKey mapId, mapX, mapZ;
    private final java.util.Set<String> placed = new java.util.HashSet<>();

    public TreasureService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
        mapId = new NamespacedKey(plugin, "treasure_id");
        mapX = new NamespacedKey(plugin, "treasure_x");
        mapZ = new NamespacedKey(plugin, "treasure_z");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAssemble(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Player player = event.getPlayer();
        if (!plugin.items().is(event.getItem(), ItemRegistry.TREASURE_FRAGMENT) || !plugin.sessions().isSurvivor(player)) return;
        event.setCancelled(true);
        int have = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) if (plugin.items().is(item, ItemRegistry.TREASURE_FRAGMENT)) have += item.getAmount();
        if (have < FRAGMENTS) {
            Text.info(player, "Fragments : " + have + " / " + FRAGMENTS + ". Il en faut trois pour reconstituer la carte.");
            return;
        }
        World world = player.getWorld();
        int[] spot = destination(world);
        if (spot == null) { Text.warn(player, "Les fragments ne correspondent à rien… réessaie plus tard."); return; }
        int left = FRAGMENTS;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (left == 0 || !plugin.items().is(item, ItemRegistry.TREASURE_FRAGMENT)) continue;
            int used = Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - used);
            left -= used;
        }
        ItemStack map = plugin.items().create(ItemRegistry.TREASURE_MAP, 1);
        String id = UUID.randomUUID().toString();
        map.editPersistentDataContainer(data -> {
            data.set(mapId, PersistentDataType.STRING, id);
            data.set(mapX, PersistentDataType.INTEGER, spot[0]);
            data.set(mapZ, PersistentDataType.INTEGER, spot[1]);
        });
        player.getInventory().addItem(map).values().forEach(rest -> world.dropItemNaturally(player.getLocation(), rest));
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.8f);
        Text.good(player, "Carte au trésor reconstituée ! Tiens-la en main pour suivre la piste.");
        plugin.events().add("treasure", player.getName() + " a reconstitué une carte au trésor");
    }

    /** A countryside place 300 to 1000 blocks from the refuge, inside the border. */
    private int[] destination(World world) {
        var refuge = plugin.settings().refuge();
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2, distance = 300 + random.nextDouble() * 700;
            int x = (int) (refuge.centerX() + Math.cos(angle) * distance), z = (int) (refuge.centerZ() + Math.sin(angle) * distance);
            Location at = new Location(world, x, 64, z);
            if (!world.getWorldBorder().isInside(at) || plugin.director().sectorAt(at) != Sector.COUNTRYSIDE) continue;
            return new int[] {x, z};
        }
        return null;
    }

    /** Every second: guidance for map holders, and the cache appears once its chunk is loaded near the holder. */
    public void tick() {
        World world = plugin.scenario().world();
        if (world == null || !plugin.scenario().isEnabled()) return;
        for (Player player : world.getPlayers()) {
            ItemStack map = player.getInventory().getItemInMainHand();
            if (!plugin.items().is(map, ItemRegistry.TREASURE_MAP) || !plugin.sessions().isSurvivor(player)) continue;
            var data = map.getPersistentDataContainer();
            Integer x = data.get(mapX, PersistentDataType.INTEGER), z = data.get(mapZ, PersistentDataType.INTEGER);
            String id = data.get(mapId, PersistentDataType.STRING);
            if (x == null || z == null || id == null) continue;
            double dx = x + 0.5 - player.getX(), dz = z + 0.5 - player.getZ(), distance = Math.hypot(dx, dz);
            player.sendActionBar(Component.text(distance < 6 ? "La cache est ici !" : "Carte au trésor · " + Math.round(distance) + " m "
                    + SupplyDropService.direction(dx, dz), NamedTextColor.GOLD));
            if (distance < 48 && world.isChunkLoaded(x >> 4, z >> 4)) place(world, x, z, id);
        }
    }

    private void place(World world, int x, int z, String id) {
        String key = "treasure:" + id;
        if (!placed.add(id)) return;
        try {
            if (plugin.database().call(c -> Store.meta(c, key)) != null) return;
        } catch (Exception e) {
            return;
        }
        Block top = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Block chestBlock = top.getRelative(0, 1, 0);
        chestBlock.setType(Material.CHEST);
        for (int[] d : new int[][] {{1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {0, 0}})
            if (d[0] != 0 || d[1] != 0) {
                Block mark = top.getRelative(d[0], 0, d[1]);
                if (mark.getType().isSolid()) mark.setType(Material.RED_CONCRETE);
            }
        if (chestBlock.getState() instanceof Chest chest) {
            chest.customName(Component.text("Cache au trésor", NamedTextColor.GOLD));
            chest.update(true, false);
            fill(((Chest) chestBlock.getState()).getBlockInventory());
        }
        plugin.database().submit(c -> { Store.putMeta(c, key, chestBlock.getX() + "," + chestBlock.getY() + "," + chestBlock.getZ()); return null; });
        world.playSound(chestBlock.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 0.6f);
    }

    private void fill(Inventory inventory) {
        List<Integer> free = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) free.add(slot);
        inventory.setItem(free.remove(random.nextInt(free.size())),
                plugin.items().create(random.nextDouble() < GOLD_CHANCE ? ItemRegistry.LASER_TURRET_GOLD : ItemRegistry.LASER_TURRET, 1));
        var place = plugin.settings().place("cache");
        if (place == null) return;
        int rolls = place.rollsMin() + random.nextInt(place.rollsMax() - place.rollsMin() + 1);
        for (int n = 0; n < rolls && !free.isEmpty(); n++) {
            LootTable.Entry entry = place.loot().pick(random.nextDouble());
            ItemStack stack = plugin.items().loot(entry.item(), entry.amount());
            if (stack != null) inventory.setItem(free.remove(random.nextInt(free.size())), stack);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Chest chest) || !(event.getPlayer() instanceof Player player)) return;
        ItemStack map = player.getInventory().getItemInMainHand();
        if (!plugin.items().is(map, ItemRegistry.TREASURE_MAP)) return;
        Integer x = map.getPersistentDataContainer().get(mapX, PersistentDataType.INTEGER), z = map.getPersistentDataContainer().get(mapZ, PersistentDataType.INTEGER);
        if (x == null || z == null || Math.abs(chest.getX() - x) > 2 || Math.abs(chest.getZ() - z) > 2) return;
        player.getInventory().setItemInMainHand(null);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        Text.good(player, "Cache trouvée ! La carte est accomplie.");
        plugin.events().add("treasure", player.getName() + " a trouvé une cache au trésor");
    }
}
