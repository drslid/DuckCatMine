package org.duckcatmine.apocalypse.session;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.persistence.PersistenceException;
import org.duckcatmine.apocalypse.persistence.Store;

/**
 * The only way in or out of Ground Zero is the scenario's own transition. Guarding teleport events,
 * not command names, also covers plugin menus, warps, homes and portals.
 */
public final class BoundaryListener implements Listener {
    private final DuckCatApocalypse plugin;

    public BoundaryListener(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.sessions().reconcile(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) plugin.sessions().enforce(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        try {
            plugin.sessions().persistPresence(player, false);
        } catch (PersistenceException e) {
            plugin.getLogger().warning("Position sûre de " + player.getName() + " non enregistrée : " + e.getMessage());
        }
        plugin.infections().hide(player);
        plugin.sessions().forget(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        guard(event, event.getPlayer(), event.getFrom().getWorld(), event.getTo().getWorld());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (event.getTo() == null) return;
        guard(event, event.getPlayer(), event.getFrom().getWorld(), event.getTo().getWorld());
    }

    private void guard(PlayerTeleportEvent event, Player player, World from, World to) {
        if (plugin.sessions().isTransitioning(player)) return;
        boolean fromScenario = plugin.scenario().isScenarioWorld(from);
        boolean toScenario = plugin.scenario().isScenarioWorld(to);
        if (!fromScenario && !toScenario) return;
        if (player.hasPermission(SessionService.BYPASS) && plugin.sessions().profile(player) == SessionService.Profile.CLASSIC) {
            if (fromScenario != toScenario) audit(player, toScenario ? "bypass_enter" : "bypass_exit");
            return;
        }
        if (fromScenario != toScenario) {
            event.setCancelled(true);
            Text.error(player, toScenario ? "Entre dans Ground Zero avec /apocalypse join."
                    : "Pour quitter Ground Zero, rejoins le refuge puis tape /apocalypse leave.");
            return;
        }
        switch (event.getCause()) {
            case ENDER_PEARL, CONSUMABLE_EFFECT, EXIT_BED, DISMOUNT, UNKNOWN -> {
            }
            default -> {
                event.setCancelled(true);
                Text.error(player, "Pas de téléportation dans Ground Zero : il faut marcher.");
            }
        }
    }

    private void audit(Player player, String action) {
        long now = plugin.clock().now();
        String detail = player.getName() + " " + player.getUniqueId();
        plugin.getLogger().info("Traversée administrateur de Ground Zero (" + action + ") : " + detail);
        plugin.database().submit(c -> {
            Store.audit(c, now, player.getName(), action, detail);
            return null;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (plugin.scenario().isScenarioWorld(event.getFrom().getWorld())
                || (event.getTo() != null && plugin.scenario().isScenarioWorld(event.getTo().getWorld()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (plugin.scenario().isScenarioWorld(event.getWorld())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnderChest(InventoryOpenEvent event) {
        if (event.getInventory().getType() != InventoryType.ENDER_CHEST || !(event.getPlayer() instanceof Player player)) return;
        if (plugin.scenario().isScenarioWorld(player.getWorld()) && !player.hasPermission(SessionService.BYPASS)) {
            event.setCancelled(true);
            Text.error(player, "Le coffre de l'Ender ne fonctionne pas dans Ground Zero : stocke dans les coffres de ta base.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSetSpawn(PlayerSetSpawnEvent event) {
        if (plugin.scenario().isScenarioWorld(event.getPlayer().getWorld())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (plugin.sessions().isSurvivor(event.getPlayer()) && event.getNewGameMode() != org.bukkit.GameMode.SURVIVAL) {
            event.setCancelled(true);
            Text.error(event.getPlayer(), "Ground Zero se joue en Survie : quitte le scénario avant de changer de mode.");
        }
    }

    /** Safety net for a world change the guard could not see (another plugin, a forced move). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.sessions().isTransitioning(player)) return;
        boolean was = plugin.scenario().isScenarioWorld(event.getFrom());
        boolean now = plugin.scenario().isScenarioWorld(player.getWorld());
        if (was == now) return;
        if (player.hasPermission(SessionService.BYPASS) && plugin.sessions().profile(player) == SessionService.Profile.CLASSIC) return;
        plugin.getLogger().warning("Changement de monde non autorisé pour " + player.getName() + " : correction appliquée.");
        plugin.sessions().enforce(player);
    }
}
