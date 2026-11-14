package org.duckcatmine.apocalypse.survival;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.loot.LootTable;

/**
 * Supply drops: a military radio message, then a crate under a parachute lands somewhere near the survivors, every
 * few minutes of play. Unopened, it disappears when its window closes. Shown on the web map as a white square.
 */
public final class SupplyDropService implements Listener {
    public static final int MAX_ACTIVE = 3;
    private static final String[] CALLSIGNS = {"Faucon-6", "Sierra-Lima", "Charlie-Bravo", "Aigle-3", "Tango-Nord"};
    public static final class Drop {
        final String id = UUID.randomUUID().toString();
        final World world;
        final int x, y, z;
        final long expiresAt;
        boolean landed, opened;
        Drop(World world, int x, int y, int z, long expiresAt) {
            this.world = world; this.x = x; this.y = y; this.z = z; this.expiresAt = expiresAt;
        }
        Block block() { return world.getBlockAt(x, y, z); }
    }
    private final DuckCatApocalypse plugin;
    private final Random random = new Random();
    private final List<Drop> drops = new ArrayList<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private long nextAt;
    private boolean preparing;

    public SupplyDropService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public int count() {
        return drops.size();
    }

    private void schedule(long now) {
        var rules = plugin.settings().play().survival();
        double min = Math.min(rules.dropMinMinutes(), rules.dropMaxMinutes()), max = Math.max(rules.dropMinMinutes(), rules.dropMaxMinutes());
        nextAt = now + (long) ((min + random.nextDouble() * (max - min)) * 60_000);
    }

    /** Seconds before the next radio call (only counts down while someone plays outside the refuge). */
    public long secondsToNext() {
        return nextAt == 0 ? -1 : Math.max(0, (nextAt - plugin.clock().now()) / 1000);
    }

    /** Every second. */
    public void tick() {
        World world = plugin.scenario().world();
        var rules = plugin.settings().play().survival();
        long now = plugin.clock().now();
        if (world == null || !plugin.scenario().isEnabled()) return;
        for (Drop drop : new ArrayList<>(drops)) {
            if (!drop.opened && now >= drop.expiresAt) { vanish(drop, true); continue; }
            if (drop.landed && !drop.opened && drop.world.isChunkLoaded(drop.x >> 4, drop.z >> 4)) {
                Location top = new Location(drop.world, drop.x + 0.5, drop.y + 1.2, drop.z + 0.5);
                drop.world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, top, 2, 0.1, 0.2, 0.1, 0.02);
                drop.world.spawnParticle(Particle.END_ROD, top.clone().add(0, 2, 0), 3, 0.05, 2, 0.05, 0);
            }
        }
        if (!rules.supplyDrops()) return;
        List<Player> active = new ArrayList<>();
        for (Player p : world.getPlayers())
            if (plugin.sessions().isSurvivor(p) && !p.isDead() && !plugin.scenario().inRefuge(p.getLocation())) active.add(p);
        if (nextAt == 0) schedule(now);
        if (active.isEmpty()) { nextAt += 1000; return; }
        if (now < nextAt || preparing || drops.size() >= MAX_ACTIVE) return;
        schedule(now);
        launch(active.get(random.nextInt(active.size())), 0);
    }

    /** Administrator command and normal schedule: a drop between 60 and 150 blocks from the survivor. */
    public void launch(Player near, int tries) {
        if (tries >= 8) { preparing = false; return; }
        preparing = true;
        World world = near.getWorld();
        double angle = random.nextDouble() * Math.PI * 2, distance = 60 + random.nextDouble() * 90;
        int x = (int) Math.floor(near.getX() + Math.cos(angle) * distance), z = (int) Math.floor(near.getZ() + Math.sin(angle) * distance);
        if (!world.getWorldBorder().isInside(new Location(world, x, 64, z)) || plugin.scenario().refugeArea().near(x, z, 24)) { launch(near, tries + 1); return; }
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            Location spot = plugin.director().outdoorSpot(world, x, z);
            if (spot == null || !near.isOnline()) { launch(near, tries + 1); return; }
            preparing = false;
            Drop drop = new Drop(world, spot.getBlockX(), spot.getBlockY(), spot.getBlockZ(),
                    plugin.clock().now() + (long) (plugin.settings().play().survival().dropLifetimeMinutes() * 60_000));
            drops.add(drop);
            radio(drop);
            fall(drop);
            plugin.events().add("drop", "Largage de ravitaillement en X " + drop.x + ", Z " + drop.z);
        });
    }

    private void radio(Drop drop) {
        String callsign = CALLSIGNS[random.nextInt(CALLSIGNS.length)];
        long minutes = Math.round(plugin.settings().play().survival().dropLifetimeMinutes());
        for (Player player : drop.world.getPlayers()) {
            if (!plugin.sessions().isSurvivor(player)) continue;
            player.sendMessage(Component.text("📻 RADIO · RÉGIMENT DE SECOURS ", NamedTextColor.DARK_GREEN, TextDecoration.BOLD)
                    .append(Component.text("*grésillements*", NamedTextColor.GRAY, TextDecoration.ITALIC).decoration(TextDecoration.BOLD, false)));
            player.sendMessage(Component.text("« Ici " + callsign + " à tous les survivants. Largage de vivres et d'armes effectué. »", NamedTextColor.GREEN));
            player.sendMessage(Component.text("« Coordonnées X " + drop.x + ", Z " + drop.z + ". Fenêtre de récupération : " + minutes
                    + " minutes. Terminé. »", NamedTextColor.GREEN));
            player.sendMessage(Component.text("→ " + bearing(player.getLocation(), drop), NamedTextColor.WHITE));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f, 0.6f);
            player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_USE, 1f, 0.5f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 1f, 0.8f);
        }
    }

    static String bearing(Location from, Drop drop) {
        double dx = drop.x + 0.5 - from.getX(), dz = drop.z + 0.5 - from.getZ();
        return Math.round(Math.hypot(dx, dz)) + " m " + direction(dx, dz);
    }

    /** French compass point of a horizontal offset (north is negative Z). */
    public static String direction(double dx, double dz) {
        String[] names = {"au sud", "au sud-ouest", "à l'ouest", "au nord-ouest", "au nord", "au nord-est", "à l'est", "au sud-est"};
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        int index = (int) Math.floorMod(Math.round(angle / 45.0), 8);
        return names[index];
    }

    private void fall(Drop drop) {
        Location target = new Location(drop.world, drop.x, drop.y, drop.z);
        Location start = target.clone().add(0, 40, 0);
        BlockDisplay crate = drop.world.spawn(start, BlockDisplay.class, e -> {
            e.setPersistent(false); e.setBlock(Material.BARREL.createBlockData()); e.setTeleportDuration(2);
        });
        BlockDisplay chute = drop.world.spawn(start.clone().add(0, 2.2, 0), BlockDisplay.class, e -> {
            e.setPersistent(false); e.setBlock(Material.WHITE_WOOL.createBlockData()); e.setTeleportDuration(2);
            e.setTransformation(new Transformation(new Vector3f(-1.5f, 0, -1.5f), new Quaternionf(), new Vector3f(4f, 0.25f, 4f), new Quaternionf()));
        });
        BukkitTask[] task = new BukkitTask[1];
        double[] height = {40};
        task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            height[0] -= 0.8;
            if (height[0] <= 0 || !drops.contains(drop) || !crate.isValid()) {
                task[0].cancel();
                crate.remove();
                chute.remove();
                if (drops.contains(drop)) land(drop);
                return;
            }
            crate.teleport(target.clone().add(0, height[0], 0));
            chute.teleport(target.clone().add(0, height[0] + 2.2, 0));
            if ((int) height[0] % 4 == 0) drop.world.spawnParticle(Particle.CLOUD, target.clone().add(0.5, height[0] + 2.4, 0.5), 2, 0.8, 0.1, 0.8, 0);
        }, 2L, 2L);
    }

    private void land(Drop drop) {
        Block block = drop.block();
        block.setType(Material.CHEST);
        if (block.getState() instanceof Chest chest) {
            chest.customName(Component.text("Caisse de ravitaillement", NamedTextColor.GOLD));
            chest.update(true, false);
            fill(((Chest) block.getState()).getBlockInventory());
        }
        drop.landed = true;
        drop.world.playSound(block.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
        drop.world.spawnParticle(Particle.CLOUD, block.getLocation().add(0.5, 0.5, 0.5), 20, 0.8, 0.3, 0.8, 0.02);
        plugin.noise().emit(block.getLocation(), 40, null);
    }

    /** Self-test only: a landed crate filled from the airdrop table; returns the number of stacks. */
    public int trialFill(Block block) {
        block.setType(Material.CHEST);
        if (!(block.getState() instanceof Chest chest)) return 0;
        fill(chest.getBlockInventory());
        int stacks = 0;
        for (ItemStack item : ((Chest) block.getState()).getBlockInventory().getContents()) if (item != null && !item.isEmpty()) stacks++;
        ((Chest) block.getState()).getBlockInventory().clear();
        block.setType(Material.AIR);
        return stacks;
    }

    private void fill(Inventory inventory) {
        var place = plugin.settings().place("airdrop");
        if (place == null) return;
        int rolls = place.rollsMin() + random.nextInt(place.rollsMax() - place.rollsMin() + 1);
        List<Integer> free = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) free.add(slot);
        for (int n = 0; n < rolls && !free.isEmpty(); n++) {
            LootTable.Entry entry = place.loot().pick(random.nextDouble());
            ItemStack stack = plugin.items().loot(entry.item(), entry.amount());
            if (stack != null) inventory.setItem(free.remove(random.nextInt(free.size())), stack);
        }
    }

    private void vanish(Drop drop, boolean announce) {
        drops.remove(drop);
        if (drop.landed) {
            Runnable remove = () -> {
                Block block = drop.block();
                if (block.getState() instanceof Chest chest) chest.getBlockInventory().clear();
                if (block.getType() == Material.CHEST) block.setType(Material.AIR);
                drop.world.spawnParticle(Particle.LARGE_SMOKE, block.getLocation().add(0.5, 0.5, 0.5), 15, 0.4, 0.4, 0.4, 0.02);
            };
            if (drop.world.isChunkLoaded(drop.x >> 4, drop.z >> 4) || !plugin.isEnabled()) remove.run();
            else drop.world.getChunkAtAsync(drop.x >> 4, drop.z >> 4).thenAccept(chunk -> remove.run());
        }
        if (announce) for (Player player : drop.world.getPlayers())
            if (plugin.sessions().isSurvivor(player))
                player.sendMessage(Component.text("📻 « Fenêtre de récupération fermée. Le largage est perdu. Terminé. »", NamedTextColor.DARK_GREEN));
    }

    private Drop at(Block block) {
        for (Drop drop : drops)
            if (drop.landed && drop.world.equals(block.getWorld()) && drop.x == block.getX() && drop.y == block.getY() && drop.z == block.getZ()) return drop;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Chest chest) || !(event.getPlayer() instanceof Player player)) return;
        Drop drop = at(chest.getBlock());
        if (drop == null || drop.opened) return;
        claimed(drop, player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Drop drop = at(event.getBlock());
        if (drop != null && !drop.opened) claimed(drop, event.getPlayer());
    }

    private void claimed(Drop drop, Player player) {
        drop.opened = true;
        drops.remove(drop);
        plugin.events().add("drop", player.getName() + " a récupéré un largage");
        for (Player other : drop.world.getPlayers())
            if (plugin.sessions().isSurvivor(other))
                other.sendActionBar(Component.text(player.getName() + " a récupéré le largage.", NamedTextColor.GOLD));
    }

    private void updateBars(World world, long now) {
        for (Player player : world.getPlayers()) {
            BossBar bar = bars.get(player.getUniqueId());
            Drop nearest = null;
            double best = Double.MAX_VALUE;
            if (plugin.sessions().isSurvivor(player))
                for (Drop drop : drops) {
                    double d = Math.hypot(drop.x - player.getX(), drop.z - player.getZ());
                    if (d < best) { best = d; nearest = drop; }
                }
            if (nearest == null) {
                if (bar != null) { player.hideBossBar(bar); bars.remove(player.getUniqueId()); }
                continue;
            }
            long left = Math.max(0, (nearest.expiresAt - now) / 1000);
            float lifetime = (float) (plugin.settings().play().survival().dropLifetimeMinutes() * 60);
            Component title = Component.text("📦 Largage · " + left / 60 + ":" + String.format("%02d", left % 60) + " · " + bearing(player.getLocation(), nearest), NamedTextColor.WHITE);
            if (bar == null) {
                bar = BossBar.bossBar(title, 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                bars.put(player.getUniqueId(), bar);
                player.showBossBar(bar);
            }
            bar.name(title);
            bar.progress(Math.max(0f, Math.min(1f, left / Math.max(1f, lifetime))));
        }
        bars.keySet().removeIf(id -> {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null && p.getWorld().equals(world)) return false;
            if (p != null) p.hideBossBar(bars.get(id));
            return true;
        });
    }

    /** For the web map: position, remaining seconds and whether the crate is still falling. */
    public List<Map<String, Object>> snapshot() {
        long now = plugin.clock().now();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Drop drop : drops)
            list.add(Map.of("id", drop.id, "x", drop.x, "y", drop.y, "z", drop.z,
                    "secondsLeft", Math.max(0, (drop.expiresAt - now) / 1000), "landed", drop.landed));
        return list;
    }

    public void stop() {
        for (Drop drop : new ArrayList<>(drops)) vanish(drop, false);
        for (var entry : bars.entrySet()) {
            Player p = plugin.getServer().getPlayer(entry.getKey());
            if (p != null) p.hideBossBar(entry.getValue());
        }
        bars.clear();
    }
}
