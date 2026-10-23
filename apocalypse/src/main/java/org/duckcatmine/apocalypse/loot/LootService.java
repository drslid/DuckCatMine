package org.duckcatmine.apocalypse.loot;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.persistence.Store;
import org.duckcatmine.apocalypse.scenario.ProtectionListener;

/**
 * Every eligible kill drops at least one item: a registered infected, really dead, with at least one living
 * survivor who hit it within the contribution window. Rewards are dropped once, never per participant.
 */
public final class LootService implements Listener {
    private final DuckCatApocalypse plugin;
    private final Map<UUID, Map<UUID, Long>> contributions = new HashMap<>();
    private final Random random = new Random();

    public LootService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!plugin.mobs().isScenarioMob(event.getEntity())) return;
        Player attacker = ProtectionListener.attacker(event.getDamager());
        if (attacker == null || !plugin.sessions().isSurvivor(attacker)) return;
        contributions.computeIfAbsent(event.getEntity().getUniqueId(), id -> new HashMap<>())
                .put(attacker.getUniqueId(), plugin.clock().now());
    }

    public void forget(UUID mob) {
        contributions.remove(mob);
    }

    public void reward(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        Map<UUID, Long> hits = contributions.remove(mob.getUniqueId());
        ApocalypseConfig.Archetype archetype = plugin.settings().archetype(plugin.mobs().archetypeOf(mob));
        if (archetype == null || hits == null || !plugin.scenario().isScenarioWorld(mob.getWorld())) return;
        Player credited = contributor(hits);
        if (credited == null) return;
        for (LootTable.Entry entry : LootRules.roll(archetype.guaranteed(), archetype.bonus(), plugin.settings().loot().bonusRollChance(), random)) {
            ItemStack stack = plugin.items().resolve(entry.item(), entry.amount());
            if (stack != null) event.getDrops().add(stack);
        }
        String run = plugin.sessions().runOf(credited);
        if (run != null) plugin.database().submit(c -> {
            Store.addKill(c, run);
            return null;
        });
    }

    private Player contributor(Map<UUID, Long> hits) {
        long now = plugin.clock().now();
        long window = plugin.settings().loot().contributionWindowMs();
        Player best = null;
        long latest = Long.MIN_VALUE;
        for (Map.Entry<UUID, Long> hit : hits.entrySet()) {
            Player player = plugin.getServer().getPlayer(hit.getKey());
            if (player == null || player.isDead() || !plugin.sessions().isSurvivor(player) || now - hit.getValue() > window) continue;
            if (hit.getValue() > latest) {
                latest = hit.getValue();
                best = player;
            }
        }
        return best;
    }
}
