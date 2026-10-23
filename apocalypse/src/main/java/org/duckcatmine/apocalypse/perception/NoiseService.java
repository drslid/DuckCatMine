package org.duckcatmine.apocalypse.perception;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;

/**
 * Server-side noise events (spec §9): a noise draws nearby infected to where it happened, never to the player's current
 * position. Sneaking and darkness make a quiet survivor hard to spot from afar.
 */
public final class NoiseService implements Listener {
    private final DuckCatApocalypse plugin;
    private final Map<UUID, Long> noisyUntil = new HashMap<>();
    private long emitted;

    public NoiseService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public long emitted() {
        return emitted;
    }

    /** A noise of the given reach at a place; {@code source} (optional) stays easy to spot for a few seconds. */
    public void emit(Location at, double radius, Player source) {
        if (radius <= 0 || at.getWorld() == null || !plugin.scenario().isScenarioWorld(at.getWorld())) return;
        emitted++;
        if (source != null) noisyUntil.put(source.getUniqueId(), plugin.clock().now() + 3000);
        plugin.mobs().hear(at, radius);
    }

    /** Whether a quiet player escapes the notice of an infected that has not engaged yet. */
    public boolean concealed(Player player, Mob mob) {
        Long noisy = noisyUntil.get(player.getUniqueId());
        if (noisy != null && noisy > plugin.clock().now()) return false;
        if (player.isSprinting()) return false;
        ApocalypseConfig.Perception perception = plugin.settings().play().perception();
        double distance = player.getLocation().distance(mob.getLocation());
        if (player.isSneaking() && distance > perception.sneakDetection()) return true;
        return light(player.getLocation().getBlock()) <= perception.darkLightLevel() && distance > perception.darkDetection();
    }

    /** Visible light: block light, or sky light dimmed at night (raw sky light does not change with the time of day). */
    public static int light(Block block) {
        int sky = block.getLightFromSky();
        if (!block.getWorld().isDayTime()) sky = Math.max(0, sky - 11);
        return Math.max(block.getLightFromBlocks(), sky);
    }

    /** Once per second: running is heard. */
    public void tick() {
        var world = plugin.scenario().world();
        if (world == null || !plugin.scenario().isEnabled()) return;
        double sprint = plugin.settings().play().noise().sprint();
        for (Player player : world.getPlayers()) {
            if (player.isSprinting() && plugin.sessions().isSurvivor(player) && !plugin.scenario().inRefuge(player.getLocation()))
                emit(player.getLocation(), sprint, player);
        }
        noisyUntil.values().removeIf(until -> until < plugin.clock().now());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (type == Material.GLASS || type == Material.GLASS_PANE || Tag.IMPERMEABLE.isTagged(type) || type.name().endsWith("GLASS_PANE"))
            emit(event.getBlock().getLocation(), plugin.settings().play().noise().glass(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        emit(event.getLocation(), plugin.settings().play().noise().explosion(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        emit(event.getBlock().getLocation(), plugin.settings().play().noise().explosion(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && plugin.mobs().isScenarioMob(event.getEntity()))
            emit(event.getEntity().getLocation(), plugin.settings().play().noise().melee(), player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder(false) instanceof BlockInventoryHolder holder && event.getPlayer() instanceof Player player
                && plugin.sessions().isSurvivor(player))
            emit(holder.getBlock().getLocation(), plugin.settings().play().noise().container(), player);
    }
}
