package org.duckcatmine.apocalypse.session;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.journal.OpState;
import org.duckcatmine.apocalypse.journal.OpType;
import org.duckcatmine.apocalypse.journal.PlayerOp;
import org.duckcatmine.apocalypse.journal.RecoveryPlanner;
import org.duckcatmine.apocalypse.persistence.Database;
import org.duckcatmine.apocalypse.persistence.PersistenceException;
import org.duckcatmine.apocalypse.persistence.Store;
import org.duckcatmine.apocalypse.scenario.ScenarioService;

/**
 * Owns the profile swap between classic survival and Ground Zero. A player always holds exactly one
 * profile in game; the other one lives in the database. Every swap is journaled (see RecoveryPlanner).
 */
public final class SessionService {
    public enum Profile { CLASSIC, APOCALYPSE }

    public static final String BYPASS = "duckcat.apocalypse.bypass";
    static final String CLASSIC_KIND = "CLASSIC";
    static final String SURVIVOR_KIND = "APOCALYPSE";

    private record Entry(long seq, String run, byte[] survivor, boolean inconsistent) {
    }

    private record Exit(long seq, byte[] classic) {
    }

    private final DuckCatApocalypse plugin;
    private final Set<UUID> transitioning = new HashSet<>();
    private final Set<UUID> pendingJoins = new HashSet<>();

    public SessionService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public Profile profile(Player player) {
        String value = player.getPersistentDataContainer().get(plugin.keys().profile, PersistentDataType.STRING);
        return Profile.APOCALYPSE.name().equals(value) ? Profile.APOCALYPSE : Profile.CLASSIC;
    }

    public String runOf(Player player) {
        return player.getPersistentDataContainer().get(plugin.keys().run, PersistentDataType.STRING);
    }

    public long appliedSeq(Player player) {
        Long value = player.getPersistentDataContainer().get(plugin.keys().appliedSeq, PersistentDataType.LONG);
        return value == null ? 0 : value;
    }

    /** Holds the survivor profile and stands in Ground Zero. */
    public boolean isSurvivor(Player player) {
        return profile(player) == Profile.APOCALYPSE && plugin.scenario().isScenarioWorld(player.getWorld());
    }

    public boolean isTransitioning(Player player) {
        return transitioning.contains(player.getUniqueId());
    }

    /** Teleport performed by the scenario itself; the boundary guard lets it through. */
    public boolean teleport(Player player, Location target) {
        transitioning.add(player.getUniqueId());
        try {
            return player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } finally {
            transitioning.remove(player.getUniqueId());
        }
    }

    /** Writes the in-game half of an operation to the player file, which then proves it happened. */
    public void markApplied(Player player, Profile profile, String run, long seq) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.set(plugin.keys().profile, PersistentDataType.STRING, profile.name());
        if (run == null) data.remove(plugin.keys().run);
        else data.set(plugin.keys().run, PersistentDataType.STRING, run);
        data.set(plugin.keys().appliedSeq, PersistentDataType.LONG, Math.max(seq, appliedSeq(player)));
        player.saveData();
    }

    public void join(Player player) {
        ScenarioService scenario = plugin.scenario();
        if (!scenario.isEnabled() || scenario.world() == null) {
            Text.error(player, "Ground Zero est fermé pour le moment.");
            return;
        }
        if (scenario.isScenarioWorld(player.getWorld())) {
            Text.info(player, "Tu es déjà dans Ground Zero.");
            return;
        }
        if (profile(player) == Profile.APOCALYPSE) {
            enforce(player);
            return;
        }
        if (player.isDead()) return;
        if (!plugin.database().healthy()) {
            Text.error(player, "Sauvegarde du scénario indisponible : entrée refusée pour protéger tes objets.");
            return;
        }
        UUID id = player.getUniqueId();
        if (!pendingJoins.add(id)) return;
        var refuge = plugin.settings().refuge();
        Text.info(player, "Préparation de Ground Zero…");
        scenario.world().getChunkAtAsync(refuge.centerX() >> 4, refuge.centerZ() >> 4).whenComplete((chunk, error) -> {
            if (!plugin.isEnabled()) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                pendingJoins.remove(id);
                if (player.isOnline()) enter(player);
            });
        });
    }

    private void enter(Player player) {
        ScenarioService scenario = plugin.scenario();
        if (!scenario.isEnabled() || scenario.world() == null || scenario.isScenarioWorld(player.getWorld())
                || profile(player) != Profile.CLASSIC || player.isDead()) return;
        UUID id = player.getUniqueId();
        player.closeInventory();
        if (player.isInsideVehicle()) player.leaveVehicle();
        ProfileSnapshot classic = ProfileCodec.capture(player);
        long now = plugin.clock().now();
        long applied = appliedSeq(player);
        String name = player.getName();
        String scenarioId = plugin.settings().scenario().id();
        Entry entry;
        try {
            entry = plugin.database().call(c -> {
                Store.ensurePlayer(c, id, name, applied);
                long seq = Store.nextSeq(c, id);
                Store.insertOp(c, id, seq, OpType.ENTER, null, now);
                Store.putProfile(c, id, CLASSIC_KIND, null, classic.encode(), now, seq);
                byte[] stored = Store.profile(c, id, SURVIVOR_KIND);
                Store.PlayerRow row = Store.player(c, id);
                Store.RunRow run = row.currentRun() == null ? null : Store.run(c, row.currentRun());
                if (stored != null && run != null && run.alive()) return new Entry(seq, run.id(), stored, false);
                if (stored != null) return new Entry(seq, null, null, true);
                String fresh = UUID.randomUUID().toString();
                Store.createRun(c, id, fresh, scenarioId, now);
                return new Entry(seq, fresh, null, false);
            });
        } catch (PersistenceException e) {
            plugin.getLogger().log(Level.SEVERE, "Entrée de " + name + " refusée : écriture impossible", e);
            Text.error(player, "Entrée impossible : sauvegarde indisponible. Rien n'a changé.");
            return;
        }
        if (entry.inconsistent()) {
            plugin.getLogger().severe("Profil de survie de " + name + " rattaché à une vie terminée : entrée bloquée pour examen.");
            rollbackSwap(id, entry.seq(), CLASSIC_KIND);
            Text.error(player, "Ta sauvegarde de survie demande une vérification : préviens un administrateur.");
            return;
        }
        ProfileSnapshot survivor = entry.survivor() == null ? null : ProfileSnapshot.decode(entry.survivor());
        Location target = survivor == null ? null : ProfileCodec.location(survivor);
        if (target == null || !scenario.isScenarioWorld(target.getWorld())) target = scenario.refugeSpawn();
        if (!teleport(player, target)) {
            rollbackSwap(id, entry.seq(), CLASSIC_KIND);
            Text.error(player, "Téléportation refusée : rien n'a changé.");
            return;
        }
        if (survivor == null) ProfileCodec.resetForNewLife(player);
        else ProfileCodec.apply(player, survivor);
        player.setGameMode(GameMode.SURVIVAL);
        markApplied(player, Profile.APOCALYPSE, entry.run(), entry.seq());
        long seq = entry.seq();
        plugin.database().submit(c -> {
            Store.setOpState(c, id, seq, OpState.DONE);
            Store.deleteProfileBefore(c, id, SURVIVOR_KIND, seq);
            return null;
        });
        if (survivor == null) {
            Text.warn(player, "Une nouvelle vie commence au refuge public. Tu n'as rien : fouille, fabrique, survis.");
            Text.info(player, "Une morsure laisse 60 secondes pour finir un antidote. À ta mort, tout ce que tu portes"
                    + " reste 5 minutes dans ton sac, là où tu es tombé. Tes constructions restent dans le monde.");
            plugin.protection().start(player);
        } else {
            Text.good(player, "Retour dans Ground Zero : ta survie reprend où tu l'avais laissée.");
        }
        plugin.infections().show(player);
        plugin.bags().remind(player);
    }

    /** Voluntary exits are only possible from the refuge and never with an active bite. */
    public boolean leave(Player player, boolean forced, boolean move) {
        UUID id = player.getUniqueId();
        if (profile(player) != Profile.APOCALYPSE) {
            if (!forced) Text.info(player, "Tu n'es pas dans Ground Zero.");
            return false;
        }
        if (player.isDead()) return false;
        if (!forced) {
            if (!plugin.scenario().isScenarioWorld(player.getWorld())) return false;
            if (plugin.infections().isInfected(id)) {
                Text.error(player, "Impossible de partir avec une morsure : l'antidote d'abord.");
                return false;
            }
            if (!plugin.scenario().inRefuge(player.getLocation())) {
                Text.error(player, "Rejoins le refuge pour quitter Ground Zero.");
                return false;
            }
        }
        player.closeInventory();
        if (player.isInsideVehicle()) player.leaveVehicle();
        ProfileSnapshot survivor = ProfileCodec.capture(player);
        long now = plugin.clock().now();
        long applied = appliedSeq(player);
        String run = runOf(player);
        String name = player.getName();
        Exit exit;
        try {
            exit = plugin.database().call(c -> {
                Store.ensurePlayer(c, id, name, applied);
                long seq = Store.nextSeq(c, id);
                Store.insertOp(c, id, seq, OpType.LEAVE, null, now);
                Store.putProfile(c, id, SURVIVOR_KIND, run, survivor.encode(), now, seq);
                return new Exit(seq, Store.profile(c, id, CLASSIC_KIND));
            });
        } catch (PersistenceException e) {
            plugin.getLogger().log(Level.SEVERE, "Sortie de " + name + " impossible : écriture refusée", e);
            if (!forced) Text.error(player, "Sortie impossible : sauvegarde indisponible. Rien n'a changé.");
            return false;
        }
        ProfileSnapshot classic = exit.classic() == null ? null : ProfileSnapshot.decode(exit.classic());
        if (move) {
            Location target = classic == null ? null : ProfileCodec.location(classic);
            if (target == null || plugin.scenario().isScenarioWorld(target.getWorld())) target = plugin.scenario().fallbackExit();
            if (!teleport(player, target)) {
                rollbackSwap(id, exit.seq(), SURVIVOR_KIND);
                if (!forced) Text.error(player, "Téléportation refusée : rien n'a changé.");
                return false;
            }
        }
        if (classic != null) {
            ProfileCodec.apply(player, classic);
        } else {
            plugin.getLogger().warning("Aucun profil classique enregistré pour " + name + " : inventaire classique vide.");
            ProfileCodec.clearCarried(player);
        }
        markApplied(player, Profile.CLASSIC, null, exit.seq());
        long seq = exit.seq();
        plugin.database().submit(c -> {
            Store.setOpState(c, id, seq, OpState.DONE);
            Store.deleteProfileBefore(c, id, CLASSIC_KIND, seq);
            return null;
        });
        plugin.infections().hide(player);
        plugin.protection().clear(id);
        Text.good(player, forced ? "Ground Zero est fermé : tu as retrouvé ta survie habituelle. Ta vie Apocalypse est sauvegardée."
                : "Tu as quitté Ground Zero. Ta vie y reste sauvegardée.");
        return true;
    }

    private void rollbackSwap(UUID id, long seq, String writtenKind) {
        try {
            plugin.database().call(c -> {
                Store.deleteProfileWrittenBy(c, id, writtenKind, seq);
                Store.setOpState(c, id, seq, OpState.ROLLED_BACK);
                return null;
            });
        } catch (PersistenceException e) {
            plugin.getLogger().log(Level.SEVERE, "Annulation différée à la prochaine connexion (opération " + seq + ")", e);
        }
    }

    /** Runs before anything else when a player connects: resolves interrupted operations, then the invariants. */
    public void reconcile(Player player) {
        UUID id = player.getUniqueId();
        long applied = appliedSeq(player);
        List<PlayerOp> pending;
        try {
            pending = plugin.database().call(c -> Store.pendingOps(c, id));
        } catch (PersistenceException e) {
            plugin.getLogger().log(Level.SEVERE, "Vérification de " + player.getName() + " impossible", e);
            return;
        }
        for (PlayerOp op : pending) {
            boolean confirmed = RecoveryPlanner.decide(op, applied) == RecoveryPlanner.Action.CONFIRM;
            try {
                switch (op.type()) {
                    case ENTER -> resolveSwap(op, confirmed, SURVIVOR_KIND, CLASSIC_KIND);
                    case LEAVE -> resolveSwap(op, confirmed, CLASSIC_KIND, SURVIVOR_KIND);
                    case BAG_TAKE -> plugin.bags().resolveTake(op, confirmed);
                    case DEATH, OFFLINE_DEATH -> {
                        if (confirmed) plugin.database().call(c -> {
                            Store.setOpState(c, id, op.seq(), OpState.DONE);
                            return null;
                        });
                        else if (!player.isDead()) plugin.deaths().reapply(player, op);
                    }
                }
                plugin.getLogger().info("Reprise de l'opération " + op.type() + " #" + op.seq() + " de " + player.getName()
                        + (confirmed ? " : confirmée." : " : rejouée ou annulée."));
            } catch (PersistenceException e) {
                plugin.getLogger().log(Level.SEVERE, "Reprise de l'opération " + op.seq() + " de " + player.getName() + " impossible", e);
            }
        }
        enforce(player);
    }

    private void resolveSwap(PlayerOp op, boolean confirmed, String consumedKind, String writtenKind) {
        plugin.database().call(c -> {
            if (confirmed) Store.deleteProfileBefore(c, op.player(), consumedKind, op.seq());
            else Store.deleteProfileWrittenBy(c, op.player(), writtenKind, op.seq());
            Store.setOpState(c, op.player(), op.seq(), confirmed ? OpState.DONE : OpState.ROLLED_BACK);
            return null;
        });
    }

    /** A classic profile never stays in Ground Zero, and a survivor profile never leaves it. */
    public void enforce(Player player) {
        if (player.isDead() || isTransitioning(player)) return;
        ScenarioService scenario = plugin.scenario();
        boolean inside = scenario.isScenarioWorld(player.getWorld());
        if (profile(player) == Profile.CLASSIC) {
            if (inside && !player.hasPermission(BYPASS)) {
                teleport(player, scenario.fallbackExit());
                Text.info(player, "Tu as été raccompagné hors de Ground Zero. Utilise /apocalypse join pour y entrer.");
            }
            return;
        }
        if (!inside) {
            leave(player, true, false);
            return;
        }
        if (!scenario.isEnabled()) {
            leave(player, true, true);
            return;
        }
        if (player.getGameMode() != GameMode.SURVIVAL) player.setGameMode(GameMode.SURVIVAL);
        plugin.infections().show(player);
        plugin.bags().remind(player);
    }

    public void evacuateAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (profile(player) == Profile.APOCALYPSE && !player.isDead()) leave(player, true, true);
        }
    }

    /** Last safe position, used for a bag when a death happens away from the game. */
    public void persistPresence(Player player, boolean blocking) {
        if (!isSurvivor(player)) return;
        String encoded = plugin.safePositions().encoded(player.getUniqueId());
        if (encoded == null) return;
        UUID id = player.getUniqueId();
        Database.Work<Void> work = c -> {
            Store.setLastSafe(c, id, encoded);
            return null;
        };
        if (blocking) plugin.database().call(work);
        else plugin.database().submit(work);
    }

    public void forget(Player player) {
        pendingJoins.remove(player.getUniqueId());
        transitioning.remove(player.getUniqueId());
    }
}
