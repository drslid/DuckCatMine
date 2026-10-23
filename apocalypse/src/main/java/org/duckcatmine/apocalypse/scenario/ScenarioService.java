package org.duckcatmine.apocalypse.scenario;

import java.io.File;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.persistence.PersistentDataType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Keys;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.persistence.Store;

/** Binding between the scenario and its dedicated world. Never deletes, replaces or regenerates a world. */
public final class ScenarioService {
    private static final Set<String> PROTECTED_WORLDS = Set.of("world", "world_nether", "world_the_end", "world_the_nether");

    private final DuckCatApocalypse plugin;
    private World world;
    private boolean enabled;
    private String problem = "Aucun monde associé : /apocalypse admin world create";
    private Location spawn;
    private int epoch = 1;
    private boolean pvp;

    public ScenarioService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public boolean pvp() {
        return pvp;
    }

    /** PvP between survivors (never in the refuge, never between teammates); announced to everyone inside. */
    public void setPvp(boolean value, String actor) {
        long now = plugin.clock().now();
        plugin.database().call(c -> {
            Store.putMeta(c, "pvp", Boolean.toString(value));
            Store.audit(c, now, actor, value ? "pvp_on" : "pvp_off", null);
            return null;
        });
        pvp = value;
        if (world == null) return;
        world.setGameRule(GameRules.PVP, value);
        for (org.bukkit.entity.Player player : world.getPlayers()) org.duckcatmine.apocalypse.Text.warn(player, rulesLine());
    }

    /** Looting and PvP rules, shown when entering and when they change. */
    public String rulesLine() {
        return pvp ? "Règles : PvP ACTIVÉ hors du refuge (jamais entre coéquipiers). Les sacs restent réservés à leur propriétaire"
                + " et à son équipe si elle l'autorise ; les bases avec balise sont protégées."
                : "Règles : PvP désactivé. Les sacs restent réservés à leur propriétaire et à son équipe si elle l'autorise ;"
                + " les bases avec balise sont protégées.";
    }

    public void load() {
        String name = plugin.database().call(c -> Store.meta(c, "world"));
        String expectedId = plugin.database().call(c -> Store.meta(c, "world_id"));
        String storedEpoch = plugin.database().call(c -> Store.meta(c, "world_epoch"));
        epoch = storedEpoch == null ? 1 : Integer.parseInt(storedEpoch);
        boolean wanted = "true".equals(plugin.database().call(c -> Store.meta(c, "enabled")));
        pvp = "true".equals(plugin.database().call(c -> Store.meta(c, "pvp")));
        world = null;
        enabled = false;
        spawn = null;
        if (name == null) return;
        World loaded = Bukkit.getWorld(name);
        if (loaded == null) {
            if (!storageExists(name)) {
                problem = "Le monde " + name + " est introuvable ; il n'est jamais recréé automatiquement (reconstruction volontaire : "
                        + "/apocalypse admin world recreate confirm).";
                return;
            }
            loaded = creator(name).createWorld();
        }
        if (loaded == null) {
            problem = "Chargement du monde " + name + " impossible.";
            return;
        }
        String actualId = loaded.getPersistentDataContainer().get(plugin.keys().worldId, PersistentDataType.STRING);
        if (expectedId == null || !expectedId.equals(actualId)) {
            problem = "Le monde " + name + " ne correspond pas à la base du scénario (restauration partielle ?) : scénario suspendu.";
            return;
        }
        world = loaded;
        applyRules(world);
        problem = null;
        enabled = wanted;
        BlueMapHook.ensure(plugin, world, false);
    }

    /** Creates the dedicated world once; refuses if any world or folder already uses the name. */
    public String create() {
        if (plugin.database().call(c -> Store.meta(c, "world")) != null) return "Un monde est déjà associé au scénario.";
        String name = plugin.settings().scenario().world();
        if (Bukkit.getWorld(name) != null || storageExists(name))
            return "Le monde " + name + " existe déjà : rien n'a été modifié. Utilise /apocalypse admin world bind " + name + " confirm.";
        World created = creator(name).createWorld();
        if (created == null) return "Création du monde impossible.";
        String id = mark(created);
        applyRules(created);
        RefugeBuilder.build(created, refugeArea(), GroundZeroGenerator.SURFACE);
        ApocalypseConfig.Refuge refuge = plugin.settings().refuge();
        created.setSpawnLocation(refuge.centerX(), GroundZeroGenerator.SURFACE + 1, refuge.centerZ(), 0f);
        created.save();
        bind(created, id);
        BlueMapHook.ensure(plugin, created, false);
        return null;
    }

    /**
     * Rebuilds the bound world once an administrator has moved its folder aside, server stopped. Deletes nothing:
     * profiles, lives and held classic inventories stay in the database; survivors of the previous generation resume
     * at the refuge with what they carry.
     */
    public String recreate() {
        String name = plugin.database().call(c -> Store.meta(c, "world"));
        if (name == null) return "Aucun monde associé : utilise /apocalypse admin world create.";
        if (Bukkit.getWorld(name) != null || storageExists(name))
            return "Le monde " + name + " existe encore : arrête le serveur, mets son dossier de côté, puis relance cette commande.";
        World created = creator(name).createWorld();
        if (created == null) return "Création du monde impossible.";
        String id = mark(created);
        applyRules(created);
        RefugeBuilder.build(created, refugeArea(), GroundZeroGenerator.SURFACE);
        ApocalypseConfig.Refuge refuge = plugin.settings().refuge();
        created.setSpawnLocation(refuge.centerX(), GroundZeroGenerator.SURFACE + 1, refuge.centerZ(), 0f);
        created.save();
        int next = epoch + 1;
        long now = plugin.clock().now();
        plugin.database().call(c -> {
            Store.putMeta(c, "world_id", id);
            Store.putMeta(c, "world_epoch", Integer.toString(next));
            Store.putMeta(c, "enabled", "false");
            Store.audit(c, now, "console", "world_recreate", name + " " + id + " epoch " + next);
            return null;
        });
        world = created;
        epoch = next;
        enabled = false;
        spawn = null;
        problem = null;
        BlueMapHook.ensure(plugin, created, true);
        plugin.events().add("admin", "Ground Zero reconstruit (génération " + next + ")");
        return null;
    }

    /** Generation number of the bound world; positions saved in an older generation are not trusted. */
    public int epoch() {
        return epoch;
    }

    /** Associates an existing, already loaded world; the main worlds are refused. */
    public String bindExisting(String name) {
        if (plugin.database().call(c -> Store.meta(c, "world")) != null) return "Un monde est déjà associé au scénario.";
        World target = Bukkit.getWorld(name);
        if (target == null) return "Monde non chargé : " + name + ".";
        if (PROTECTED_WORLDS.contains(name) || target.getUID().equals(Bukkit.getWorlds().getFirst().getUID())
                || target.getEnvironment() != World.Environment.NORMAL)
            return "Ce monde ne peut pas devenir Ground Zero : seuls les mondes dédiés de type normal sont acceptés.";
        String id = mark(target);
        applyRules(target);
        bind(target, id);
        return null;
    }

    private String mark(World target) {
        String id = target.getPersistentDataContainer().get(plugin.keys().worldId, PersistentDataType.STRING);
        if (id == null) id = UUID.randomUUID().toString();
        target.getPersistentDataContainer().set(Keys.SCENARIO, PersistentDataType.STRING, plugin.settings().scenario().id());
        target.getPersistentDataContainer().set(plugin.keys().worldId, PersistentDataType.STRING, id);
        return id;
    }

    private void bind(World target, String id) {
        String name = target.getName();
        long now = plugin.clock().now();
        plugin.database().call(c -> {
            Store.putMeta(c, "world", name);
            Store.putMeta(c, "world_id", id);
            Store.putMeta(c, "enabled", "false");
            Store.audit(c, now, "console", "world_bind", name + " " + id);
            return null;
        });
        world = target;
        enabled = false;
        spawn = null;
        problem = null;
    }

    public String setEnabled(boolean value, String actor) {
        if (value && world == null) return problem;
        long now = plugin.clock().now();
        plugin.database().call(c -> {
            Store.putMeta(c, "enabled", Boolean.toString(value));
            Store.audit(c, now, actor, value ? "enable" : "disable", null);
            return null;
        });
        if (enabled == value) return null;
        enabled = value;
        if (value) {
            plugin.clock().resume();
        } else {
            plugin.sessions().evacuateAll();
            plugin.mobs().removeAll();
            plugin.bags().hideAll();
            long paused = plugin.clock().pause();
            plugin.database().call(c -> {
                Store.putMeta(c, "clock_ms", Long.toString(paused));
                return null;
            });
        }
        return null;
    }

    private void applyRules(World target) {
        target.setGameRule(GameRules.KEEP_INVENTORY, false);
        target.setGameRule(GameRules.PVP, pvp);
        target.setGameRule(GameRules.LOCATOR_BAR, false);
        target.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 101);
        target.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        target.setGameRule(GameRules.SPAWN_PATROLS, false);
        target.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        target.setGameRule(GameRules.SPAWN_WARDENS, false);
        target.setGameRule(GameRules.SPAWN_MOBS, false);
        target.setGameRule(GameRules.MOB_GRIEFING, false);
        target.setGameRule(GameRules.IMMEDIATE_RESPAWN, false);
        target.setDifficulty(Difficulty.valueOf(plugin.settings().scenario().difficulty()));
        ApocalypseConfig.Refuge refuge = plugin.settings().refuge();
        WorldBorder border = target.getWorldBorder();
        border.setCenter(refuge.centerX() + 0.5, refuge.centerZ() + 0.5);
        border.setSize(plugin.settings().scenario().borderSize());
    }

    private static WorldCreator creator(String name) {
        return new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .generator(new GroundZeroGenerator())
                .generateStructures(false);
    }

    /** Minecraft 26.x keeps extra dimensions below the main world folder. */
    private static boolean storageExists(String name) {
        File container = Bukkit.getWorldContainer();
        if (new File(container, name).exists()) return true;
        File[] roots = container.listFiles(File::isDirectory);
        if (roots == null) return false;
        for (File root : roots) {
            File[] namespaces = new File(root, "dimensions").listFiles(File::isDirectory);
            if (namespaces == null) continue;
            for (File namespace : namespaces) if (new File(namespace, name).exists()) return true;
        }
        return false;
    }

    public World world() {
        return world;
    }

    public boolean isEnabled() {
        return enabled && world != null;
    }

    public String problem() {
        return problem;
    }

    public boolean isScenarioWorld(World candidate) {
        return candidate != null && world != null && candidate.getUID().equals(world.getUID());
    }

    public RefugeArea refugeArea() {
        ApocalypseConfig.Refuge refuge = plugin.settings().refuge();
        return new RefugeArea(refuge.centerX(), refuge.centerZ(), refuge.radius());
    }

    public boolean inRefuge(Location location) {
        return location != null && isScenarioWorld(location.getWorld())
                && refugeArea().contains(location.getBlockX(), location.getBlockZ());
    }

    public Location refugeSpawn() {
        if (spawn == null || !isScenarioWorld(spawn.getWorld())) {
            ApocalypseConfig.Refuge refuge = plugin.settings().refuge();
            int y = world.getHighestBlockYAt(refuge.centerX(), refuge.centerZ()) + 1;
            spawn = new Location(world, refuge.centerX() + 0.5, y, refuge.centerZ() + 0.5, 0f, 0f);
        }
        return spawn.clone();
    }

    /** Recovery point outside the gate, used when a death position is not usable. */
    public Location bagFallback() {
        ApocalypseConfig.Refuge refuge = plugin.settings().refuge();
        int x = refuge.centerX(), z = refuge.centerZ() + refuge.radius() + 3;
        return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
    }

    public Location fallbackExit() {
        return Bukkit.getWorlds().getFirst().getSpawnLocation();
    }
}
