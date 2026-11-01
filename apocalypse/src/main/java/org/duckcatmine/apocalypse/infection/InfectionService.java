package org.duckcatmine.apocalypse.infection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.core.Durations;
import org.duckcatmine.apocalypse.items.ItemRegistry;
import org.duckcatmine.apocalypse.persistence.PersistenceException;
import org.duckcatmine.apocalypse.persistence.Store;

/** NEXUS-13 bites: one countdown per life, measured on the scenario clock, never on server ticks. */
public final class InfectionService implements Listener {
    private static final class Active {
        final UUID player;
        final String run;
        final long startedAt;
        long deadline;
        long lastRemaining = Long.MAX_VALUE;
        long shownSecond = -1;
        boolean dying;
        final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);

        Active(UUID player, String run, long startedAt, long deadline) {
            this.player = player;
            this.run = run;
            this.startedAt = startedAt;
            this.deadline = deadline;
        }
    }

    private final DuckCatApocalypse plugin;
    private final Map<UUID, Active> active = new HashMap<>();
    private final Random random = new Random();

    public InfectionService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public void load() {
        for (Store.InfectionRow row : plugin.database().call(Store::activeInfections)) {
            active.put(row.player(), new Active(row.player(), row.runId(), row.startedAt(), row.deadline()));
        }
    }

    public int count() {
        return active.size();
    }

    public boolean isInfected(UUID id) {
        return active.containsKey(id);
    }

    public Long remaining(UUID id) {
        Active infection = active.get(id);
        return infection == null ? null : Math.max(0, infection.deadline - plugin.clock().now());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        ApocalypseConfig.Archetype archetype = plugin.settings().archetype(plugin.mobs().archetypeOf(event.getDamager()));
        if (archetype == null || plugin.mobs().slamming() || player.isDead() || !plugin.sessions().isSurvivor(player)) return;
        double absorbed = event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)
                ? -event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION) : 0;
        if (!InfectionRules.reachedPlayer(event.getFinalDamage(), absorbed)) return;
        if (!InfectionRules.bites(Math.min(1, archetype.biteChance() * plugin.settings().infection().biteMultiplier()), random.nextDouble())) return;
        bite(player);
    }

    private void bite(Player player) {
        UUID id = player.getUniqueId();
        String run = plugin.sessions().runOf(player);
        if (run == null) return;
        long now = plugin.clock().now();
        long duration = plugin.settings().infection().durationMs();
        Active current = active.get(id);
        if (current != null && current.run.equals(run)) {
            if (plugin.settings().infection().repeatedBitesResetTimer()) {
                current.deadline = now + duration;
                long deadline = current.deadline;
                plugin.database().submit(c -> {
                    Store.updateDeadline(c, run, deadline);
                    return null;
                });
            }
            return;
        }
        if (current != null) player.hideBossBar(current.bar);
        Active infection = new Active(id, run, now, now + duration);
        active.put(id, infection);
        long deadline = infection.deadline;
        plugin.database().submit(c -> {
            Store.insertInfection(c, run, id, now, deadline);
            return null;
        });
        display(player, infection, deadline - now);
        player.showBossBar(infection.bar);
        plugin.events().add("bite", player.getName() + " a été mordu");
        player.sendActionBar(Component.text("Morsure : trouve un antidote avant la fin du compte à rebours.", NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.7f, 0.8f);
        Text.warn(player, "Tu as été mordu : termine un antidote NEXUS avant la fin du compte à rebours, sinon cette vie s'achève.");
        Text.info(player, "Antidotes : pharmacies, hôpital, laboratoire, ou poste médical (sérum + inhibiteur + seringue)."
                + " /apocalypse guide indique la pharmacie la plus proche.");
    }

    public void tick() {
        if (!plugin.clock().running()) return;
        long now = plugin.clock().now();
        for (Active infection : List.copyOf(active.values())) {
            long remaining = infection.deadline - now;
            Player player = plugin.getServer().getPlayer(infection.player);
            boolean held = player != null && plugin.sessions().isSurvivor(player);
            if (held && !infection.run.equals(plugin.sessions().runOf(player))) {
                active.remove(infection.player);
                player.hideBossBar(infection.bar);
                continue;
            }
            if (remaining <= 0) {
                expire(infection, player, held);
                continue;
            }
            if (held) display(player, infection, remaining);
            infection.lastRemaining = remaining;
        }
    }

    private void expire(Active infection, Player player, boolean held) {
        if (held) {
            if (!player.isDead()) plugin.deaths().killByInfection(player);
            return;
        }
        if (infection.dying) return;
        infection.dying = true;
        try {
            plugin.deaths().offlineInfectionDeath(infection.player, infection.run, infection.deadline);
            active.remove(infection.player);
            if (player != null) player.hideBossBar(infection.bar);
        } catch (PersistenceException | IllegalStateException e) {
            infection.dying = false;
            plugin.getLogger().log(Level.WARNING, "Échéance hors jeu reportée", e);
        }
    }

    private void display(Player player, Active infection, long remaining) {
        long second = Math.ceilDiv(remaining, 1000L);
        if (second != infection.shownSecond) {
            infection.shownSecond = second;
            infection.bar.name(Component.text("MORSURE — ANTIDOTE : " + Durations.clock(remaining), NamedTextColor.RED));
            float total = Math.max(1, infection.deadline - infection.startedAt);
            infection.bar.progress(Math.max(0f, Math.min(1f, remaining / total)));
        }
        int warning = InfectionRules.crossedWarning(infection.lastRemaining, remaining);
        if (warning == 0) return;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.45f, warning == 30 ? 0.8f : 1.4f);
        player.sendActionBar(Component.text("Morsure : plus que " + warning + " secondes pour l'antidote", NamedTextColor.RED));
    }

    /** One dose, consumed only when the cure is validated before the deadline. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!ItemRegistry.ANTIDOTE.equals(plugin.items().idOf(event.getItem()))) return;
        Player player = event.getPlayer();
        if (!plugin.sessions().isSurvivor(player)) {
            event.setCancelled(true);
            return;
        }
        Active infection = active.get(player.getUniqueId());
        if (infection == null || !infection.run.equals(plugin.sessions().runOf(player))) {
            event.setCancelled(true);
            Text.info(player, "Tu n'es pas infecté : la dose est conservée. L'antidote ne protège pas à l'avance.");
            return;
        }
        if (!cure(player)) {
            event.setCancelled(true);
            return;
        }
        Text.good(player, "Antidote administré à temps : l'infection est stoppée. Tes blessures, elles, restent à soigner.");
    }

    /** Validated cure of the current life's infection, before its deadline; false when there is nothing (left) to cure. */
    public boolean cure(Player player) {
        Active infection = active.get(player.getUniqueId());
        if (infection == null || !infection.run.equals(plugin.sessions().runOf(player))) return false;
        long now = plugin.clock().now();
        if (!InfectionRules.curable(now, infection.deadline)) return false;
        active.remove(player.getUniqueId());
        player.hideBossBar(infection.bar);
        String run = infection.run;
        plugin.database().submit(c -> {
            Store.resolveInfection(c, run, "CURED", now);
            return null;
        });
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        plugin.events().add("cure", player.getName() + " a pris l'antidote à temps");
        return true;
    }

    public void show(Player player) {
        Active infection = active.get(player.getUniqueId());
        if (infection == null || !plugin.sessions().isSurvivor(player)) return;
        infection.shownSecond = -1;
        display(player, infection, Math.max(0, infection.deadline - plugin.clock().now()));
        player.showBossBar(infection.bar);
    }

    public void hide(Player player) {
        Active infection = active.get(player.getUniqueId());
        if (infection != null) player.hideBossBar(infection.bar);
    }

    public void hideAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) hide(player);
    }

    /** The life ended: the infection ends with it and the next life starts healthy. */
    public void end(UUID id) {
        Active infection = active.remove(id);
        Player player = plugin.getServer().getPlayer(id);
        if (infection != null && player != null) player.hideBossBar(infection.bar);
    }

    /** New game: the lives ended in the database, the timers stop here. */
    public void clearAll() {
        for (UUID id : java.util.List.copyOf(active.keySet())) end(id);
    }

    /** Remaining seconds of every running bite, by player. */
    public Map<UUID, Long> remainingAll() {
        Map<UUID, Long> result = new HashMap<>();
        long now = plugin.clock().now();
        active.forEach((id, infection) -> result.put(id, Math.max(0, infection.deadline - now) / 1000));
        return result;
    }
}
