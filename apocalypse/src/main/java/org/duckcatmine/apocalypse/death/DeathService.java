package org.duckcatmine.apocalypse.death;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.journal.OpState;
import org.duckcatmine.apocalypse.journal.OpType;
import org.duckcatmine.apocalypse.journal.PlayerOp;
import org.duckcatmine.apocalypse.persistence.PersistenceException;
import org.duckcatmine.apocalypse.persistence.Store;
import org.duckcatmine.apocalypse.session.ProfileCodec;
import org.duckcatmine.apocalypse.session.ProfileSnapshot;
import org.duckcatmine.apocalypse.session.SessionService;

/**
 * Ends a life exactly once (one death per run id) and moves what was carried into one bag.
 * If the death cannot be recorded, vanilla drops are kept so nothing is lost or duplicated.
 */
public final class DeathService implements Listener {
    private record Outcome(long seq, String deathId, Store.BagRow bag) {
    }

    private record Place(String world, double x, double y, double z) {
        static Place of(Location location) {
            return new Place(location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
        }
    }

    private final DuckCatApocalypse plugin;
    private final Set<UUID> infectionKills = new HashSet<>();
    private final Map<UUID, Long> awaitingRespawn = new HashMap<>();
    private final Map<UUID, CompletableFuture<Location>> drops = new HashMap<>();
    private final Set<UUID> dropped = new HashSet<>();

    public DeathService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    /** Deadline reached in game: no heal, shield, resistance or totem can change it. */
    public void killByInfection(Player player) {
        if (player.isDead()) return;
        infectionKills.add(player.getUniqueId());
        player.setHealth(0.0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player && plugin.sessions().isSurvivor(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        boolean infection = infectionKills.remove(player.getUniqueId());
        SessionService sessions = plugin.sessions();
        if (!sessions.isSurvivor(player)) return;
        String run = sessions.runOf(player);
        if (run == null) {
            plugin.getLogger().warning("Mort de " + player.getName() + " sans vie enregistrée : comportement vanilla conservé.");
            return;
        }
        UUID id = player.getUniqueId();
        long now = plugin.clock().now();
        long applied = sessions.appliedSeq(player);
        String name = player.getName();
        Place place = Place.of(bagLocation(player.getLocation(), plugin.safePositions().last(id)));
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) if (drop != null && !drop.isEmpty()) items.add(drop.clone());
        List<byte[]> serialized = items.stream().map(ItemStack::serializeAsBytes).toList();
        String cause = infection ? "INFECTION" : causeOf(player);
        Outcome outcome;
        try {
            outcome = plugin.database().call(c -> record(c, id, name, applied, run, cause, place, now, serialized, OpType.DEATH));
        } catch (PersistenceException e) {
            plugin.getLogger().log(Level.SEVERE, "Mort de " + name + " non enregistrée : objets laissés au sol", e);
            Text.error(player, "Sauvegarde indisponible : tes objets sont restés au sol, sans sac.");
            return;
        }
        event.getDrops().clear();
        event.setKeepInventory(false);
        event.setKeepLevel(false);
        event.setShouldDropExperience(false);
        event.setDroppedExp(0);
        event.setNewExp(0);
        event.setNewLevel(0);
        event.setNewTotalExp(0);
        if (outcome.bag() != null) plugin.bags().register(outcome.bag(), items);
        awaitingRespawn.put(id, outcome.seq());
        drops.put(id, plugin.drops().pick(player.getWorld()));
        plugin.infections().end(id);
        plugin.protection().clear(id);
        plugin.progress().endLife(player);
        plugin.safePositions().forget(id);
        if (infection) event.deathMessage(Component.text(name + " a succombé à NEXUS-13.", NamedTextColor.DARK_RED));
    }

    private Outcome record(Connection c, UUID id, String name, long applied, String run, String cause, Place place, long now,
                           List<byte[]> items, OpType type) throws SQLException {
        Store.ensurePlayer(c, id, name, applied);
        long seq = Store.nextSeq(c, id);
        String deathId = UUID.randomUUID().toString();
        Store.RunRow life = Store.run(c, run);
        Store.insertDeath(c, new Store.DeathRow(deathId, id, run, cause, place.world(), place.x(), place.y(), place.z(), now, null));
        Store.endRun(c, run, now, deathId);
        if (life != null) Store.recordSurvival(c, id, now - life.startedAt());
        Store.resolveInfection(c, run, "INFECTION".equals(cause) ? "FATAL" : "ENDED", now);
        Store.BagRow bag = null;
        if (!items.isEmpty()) {
            bag = new Store.BagRow(UUID.randomUUID().toString(), deathId, id, name, place.world(), place.x(), place.y(), place.z(),
                    now, now + plugin.settings().death().bagLifetimeMs(), "ACTIVE");
            Store.insertBag(c, bag, items);
            Store.attachBag(c, deathId, bag.id());
        }
        Store.insertOp(c, id, seq, type, deathId, now);
        return new Outcome(seq, deathId, bag);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (plugin.sessions().profile(player) == SessionService.Profile.APOCALYPSE) {
            if (!plugin.scenario().isEnabled()) return;
            CompletableFuture<Location> drop = drops.remove(player.getUniqueId());
            Location at = drop != null && drop.isDone() && !drop.isCompletedExceptionally() ? drop.join() : null;
            if (at != null && plugin.scenario().isScenarioWorld(at.getWorld())) {
                event.setRespawnLocation(at);
                dropped.add(player.getUniqueId());
            } else {
                event.setRespawnLocation(plugin.scenario().refugeSpawn());
            }
        } else if (plugin.scenario().isScenarioWorld(event.getRespawnLocation().getWorld())) {
            event.setRespawnLocation(plugin.scenario().fallbackExit());
        }
    }

    @EventHandler
    public void onPostRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        if (plugin.sessions().profile(player) != SessionService.Profile.APOCALYPSE) return;
        Long seq = awaitingRespawn.remove(player.getUniqueId());
        if (seq == null) seq = pendingDeath(player.getUniqueId());
        if (seq != null) startNewLife(player, seq);
        plugin.sessions().enforce(player);
    }

    private Long pendingDeath(UUID id) {
        try {
            for (PlayerOp op : plugin.database().call(c -> Store.pendingOps(c, id))) {
                if (op.type() == OpType.DEATH || op.type() == OpType.OFFLINE_DEATH) return op.seq();
            }
        } catch (PersistenceException e) {
            plugin.getLogger().log(Level.SEVERE, "Lecture des morts en attente impossible", e);
        }
        return null;
    }

    /** A new life with the same identity; the run id is derived from the death operation, so a replay is idempotent. */
    private void startNewLife(Player player, long seq) {
        UUID id = player.getUniqueId();
        long now = plugin.clock().now();
        long applied = plugin.sessions().appliedSeq(player);
        String name = player.getName();
        String scenarioId = plugin.settings().scenario().id();
        String run = UUID.nameUUIDFromBytes((id + ":" + seq).getBytes(StandardCharsets.UTF_8)).toString();
        String previousRun = plugin.sessions().runOf(player);
        try {
            plugin.database().call(c -> {
                Store.ensurePlayer(c, id, name, applied);
                Store.createRun(c, id, run, scenarioId, now);
                return null;
            });
        } catch (PersistenceException e) {
            plugin.getLogger().log(Level.SEVERE, "Nouvelle vie de " + name + " différée", e);
            Text.error(player, "Sauvegarde indisponible : ta nouvelle vie sera finalisée à ta prochaine connexion.");
            return;
        }
        ProfileCodec.resetForNewLife(player);
        if (plugin.scenario().isEnabled() && !dropped.remove(id)) {
            plugin.drops().pick(plugin.scenario().world()).thenAccept(at -> {
                if (player.isOnline() && !player.isDead() && plugin.sessions().isSurvivor(player)) plugin.sessions().teleport(player, at);
            });
        }
        plugin.sessions().markApplied(player, SessionService.Profile.APOCALYPSE, run, seq);
        plugin.database().submit(c -> {
            Store.setOpState(c, id, seq, OpState.DONE);
            return null;
        });
        plugin.protection().start(player);
        Text.warn(player, "Ta vie précédente est terminée. Même nom, même visage, nouvelle vie larguée ailleurs : tout ce que tu portais est dans ton sac.");
        if (!run.equals(previousRun)) plugin.drops().giveKit(player, previousRun);
        plugin.bags().remind(player);
    }

    /** Replays a death whose in-game half never reached the player file. */
    public void reapply(Player player, PlayerOp op) {
        UUID id = player.getUniqueId();
        Store.DeathRow death = plugin.database().call(c -> Store.death(c, op.payload()));
        if (death == null) {
            plugin.getLogger().severe("Mort " + op.payload() + " introuvable pour l'opération " + op.seq() + " : annulée.");
            plugin.database().call(c -> {
                Store.setOpState(c, id, op.seq(), OpState.ROLLED_BACK);
                return null;
            });
            return;
        }
        if (op.type() == OpType.OFFLINE_DEATH && death.bagId() == null) {
            List<ItemStack> items = carried(player);
            long now = plugin.clock().now();
            long expires = death.at() + plugin.settings().death().bagLifetimeMs();
            if (!items.isEmpty() && now < expires) {
                List<byte[]> serialized = items.stream().map(ItemStack::serializeAsBytes).toList();
                Store.BagRow bag = new Store.BagRow(UUID.randomUUID().toString(), death.id(), id, player.getName(), death.world(),
                        death.x(), death.y(), death.z(), death.at(), expires, "ACTIVE");
                plugin.database().call(c -> {
                    Store.insertBag(c, bag, serialized);
                    Store.attachBag(c, death.id(), bag.id());
                    return null;
                });
                plugin.bags().register(bag, items);
            } else if (!items.isEmpty()) {
                plugin.database().call(c -> {
                    Store.audit(c, now, "system", "bag_expired_offline", death.id() + " items=" + items.size());
                    return null;
                });
            }
        }
        ProfileCodec.clearCarried(player);
        Text.error(player, op.type() == OpType.OFFLINE_DEATH ? "Tu as succombé à NEXUS-13 pendant ton absence."
                : "Ta mort a été finalisée après un incident du serveur.");
        startNewLife(player, op.seq());
    }

    /** Infection deadline reached while the survivor profile is not held in game. */
    public void offlineInfectionDeath(UUID id, String run, long at) {
        try {
            byte[] stored = plugin.database().call(c -> Store.profile(c, id, "APOCALYPSE"));
            Store.PlayerRow row = plugin.database().call(c -> Store.player(c, id));
            String name = row == null ? id.toString() : row.name();
            long applied = row == null ? 0 : row.opSeq();
            if (stored != null) {
                ProfileSnapshot survivor = ProfileSnapshot.decode(stored);
                List<ItemStack> items = new ArrayList<>();
                for (ItemStack item : ProfileCodec.items(survivor)) if (item != null && !item.isEmpty()) items.add(item);
                List<byte[]> serialized = items.stream().map(ItemStack::serializeAsBytes).toList();
                Place place = Place.of(bagLocation(ProfileCodec.location(survivor), null));
                Outcome outcome = plugin.database().call(c -> {
                    Outcome result = record(c, id, name, applied, run, "INFECTION", place, at, serialized, OpType.OFFLINE_DEATH);
                    Store.deleteProfile(c, id, "APOCALYPSE");
                    Store.setOpState(c, id, result.seq(), OpState.DONE);
                    return result;
                });
                if (outcome.bag() != null) plugin.bags().register(outcome.bag(), items);
            } else {
                Place place = Place.of(bagLocation(null, SafePositions.decode(row == null ? null : row.lastSafe())));
                plugin.database().call(c -> record(c, id, name, applied, run, "INFECTION", place, at, List.of(), OpType.OFFLINE_DEATH));
            }
            plugin.getLogger().info("Infection mortelle hors jeu enregistrée pour " + name + ".");
        } catch (PersistenceException | IllegalStateException e) {
            plugin.getLogger().log(Level.SEVERE, "Mort hors jeu non enregistrée ; nouvel essai à la prochaine échéance", e);
            throw e;
        }
    }

    private Location bagLocation(Location death, Location lastSafe) {
        SafePositions safe = plugin.safePositions();
        if (safe.usable(death)) return SafePositions.centered(death);
        if (safe.usable(lastSafe)) return lastSafe;
        return plugin.scenario().bagFallback();
    }

    private static List<ItemStack> carried(Player player) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) if (item != null && !item.isEmpty()) items.add(item.clone());
        ItemStack cursor = player.getItemOnCursor();
        if (!cursor.isEmpty()) items.add(cursor.clone());
        return items;
    }

    private static String causeOf(Player player) {
        EntityDamageEvent last = player.getLastDamageCause();
        return last == null ? "UNKNOWN" : last.getCause().name();
    }
}
