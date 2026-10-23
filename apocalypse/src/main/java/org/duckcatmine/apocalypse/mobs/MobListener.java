package org.duckcatmine.apocalypse.mobs;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.duckcatmine.apocalypse.DuckCatApocalypse;

public final class MobListener implements Listener {
    private final DuckCatApocalypse plugin;

    public MobListener(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    /** Only the director spawns hostile creatures in Ground Zero; administrators keep commands and eggs. */
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Enemy) || !plugin.scenario().isScenarioWorld(event.getLocation().getWorld())) return;
        switch (event.getSpawnReason()) {
            case CUSTOM, COMMAND, SPAWNER_EGG -> {
            }
            default -> event.setCancelled(true);
        }
    }

    /** Drowned, husk or villager conversions would leave the registry and its rules. */
    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (plugin.mobs().isScenarioMob(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!plugin.mobs().isScenarioMob(event.getEntity()) || !(event.getTarget() instanceof Player player)) return;
        if (!plugin.sessions().isSurvivor(player) || plugin.scenario().inRefuge(player.getLocation())
                || plugin.protection().isProtected(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!plugin.mobs().isScenarioMob(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        plugin.loot().reward(event);
        plugin.mobs().forget(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onRemove(EntityRemoveFromWorldEvent event) {
        plugin.mobs().forget(event.getEntity().getUniqueId());
        plugin.loot().forget(event.getEntity().getUniqueId());
    }
}
