package org.duckcatmine.apocalypse.scenario;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.session.SessionService;

/** Public refuge rules, start-of-life protection and the guard against command-given effects. */
public final class ProtectionListener implements Listener {
    private final DuckCatApocalypse plugin;

    public ProtectionListener(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public static Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private boolean protectedBlock(Block block, Player player) {
        return plugin.scenario().inRefuge(block.getLocation()) && (player == null || !player.hasPermission(SessionService.BYPASS));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (protectedBlock(event.getBlock(), event.getPlayer())) {
            event.setCancelled(true);
            Text.error(event.getPlayer(), "Le refuge public ne peut pas être modifié.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (protectedBlock(event.getBlock(), event.getPlayer())) {
            event.setCancelled(true);
            Text.error(event.getPlayer(), "Construis ta base hors du refuge public.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (protectedBlock(event.getBlock(), event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (protectedBlock(event.getBlock(), event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.scenario().inRefuge(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.scenario().inRefuge(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (plugin.scenario().inRefuge(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (protectedBlock(event.getBlock(), event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && plugin.scenario().inRefuge(victim.getLocation())
                && event.getCause() != EntityDamageEvent.DamageCause.KILL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && plugin.sessions().isSurvivor(victim)
                && plugin.mobs().isScenarioMob(event.getDamager()) && plugin.protection().isProtected(victim)) {
            event.setCancelled(true);
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null || !plugin.sessions().isSurvivor(attacker)) return;
        if (event.getEntity() instanceof Player && plugin.scenario().inRefuge(attacker.getLocation())) {
            event.setCancelled(true);
            return;
        }
        plugin.protection().endByAction(attacker);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (event.getCause() == EntityPotionEffectEvent.Cause.COMMAND && event.getEntity() instanceof Player player
                && plugin.sessions().isSurvivor(player) && event.getNewEffect() != null) {
            event.setCancelled(true);
        }
    }
}
