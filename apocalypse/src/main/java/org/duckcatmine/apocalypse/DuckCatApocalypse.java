package org.duckcatmine.apocalypse;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.duckcatmine.apocalypse.commands.ApocalypseCommand;
import org.duckcatmine.apocalypse.combat.FirearmService;
import org.duckcatmine.apocalypse.crafting.CraftingService;
import org.duckcatmine.apocalypse.crafting.PlacedBlocks;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.config.ConfigException;
import org.duckcatmine.apocalypse.core.SystemClock;
import org.duckcatmine.apocalypse.death.BagService;
import org.duckcatmine.apocalypse.death.DeathService;
import org.duckcatmine.apocalypse.death.SafePositions;
import org.duckcatmine.apocalypse.fauna.FaunaService;
import org.duckcatmine.apocalypse.infection.InfectionService;
import org.duckcatmine.apocalypse.intro.IntroService;
import org.duckcatmine.apocalypse.items.ItemRegistry;
import org.duckcatmine.apocalypse.items.VanillaUseGuard;
import org.duckcatmine.apocalypse.loot.LootService;
import org.duckcatmine.apocalypse.loot.SearchService;
import org.duckcatmine.apocalypse.mobs.MobListener;
import org.duckcatmine.apocalypse.mobs.MobService;
import org.duckcatmine.apocalypse.mobs.SpawnDirector;
import org.duckcatmine.apocalypse.perception.NoiseService;
import org.duckcatmine.apocalypse.persistence.BackupListener;
import org.duckcatmine.apocalypse.persistence.Database;
import org.duckcatmine.apocalypse.persistence.PersistenceException;
import org.duckcatmine.apocalypse.persistence.Store;
import org.duckcatmine.apocalypse.progress.ProgressService;
import org.duckcatmine.apocalypse.scenario.ProtectionListener;
import org.duckcatmine.apocalypse.scenario.ProtectionService;
import org.duckcatmine.apocalypse.scenario.ScenarioService;
import org.duckcatmine.apocalypse.session.BoundaryListener;
import org.duckcatmine.apocalypse.session.SessionService;
import org.duckcatmine.apocalypse.siege.SiegeManager;
import org.duckcatmine.apocalypse.spawn.DropService;
import org.duckcatmine.apocalypse.teams.TeamService;
import org.duckcatmine.apocalypse.threat.ThreatDirector;

public final class DuckCatApocalypse extends JavaPlugin {
    private static final long TICK_PERIOD = 5L;
    private static final long TICKS_PER_SECOND = 20L / TICK_PERIOD;

    private volatile ApocalypseConfig settings;
    private Keys keys;
    private Database database;
    private SystemClock clock;
    private ItemRegistry items;
    private ScenarioService scenario;
    private SessionService sessions;
    private InfectionService infections;
    private DeathService deaths;
    private BagService bags;
    private MobService mobs;
    private LootService loot;
    private SearchService search;
    private NoiseService noise;
    private PlacedBlocks placed;
    private CraftingService crafting;
    private FirearmService firearms;
    private DropService drops;
    private SiegeManager siege;
    private ThreatDirector threat;
    private FaunaService fauna;
    private TeamService teams;
    private ProgressService progress;
    private IntroService intro;
    private ProtectionService protection;
    private SafePositions safePositions;
    private SpawnDirector director;
    private BukkitTask ticker;
    private long ticks;
    private String configProblem;

    @Override
    public void onEnable() {
        migrateConfig();
        saveDefaultConfig();
        reloadConfig();
        keys = new Keys(this);
        try {
            settings = ApocalypseConfig.parse(getConfig());
        } catch (ConfigException e) {
            e.errors().forEach(error -> getLogger().severe("Configuration invalide : " + error));
            try {
                settings = ApocalypseConfig.parse(getConfig().getDefaults());
                configProblem = "config.yml invalide : règles de référence appliquées (voir les erreurs au démarrage).";
                getLogger().severe(configProblem);
            } catch (ConfigException | RuntimeException fatal) {
                enterSafeMode("configuration illisible");
                return;
            }
        }
        items = new ItemRegistry(keys, settings);
        List<String> itemErrors = items.validate(settings);
        if (!itemErrors.isEmpty()) {
            itemErrors.forEach(error -> getLogger().severe("Configuration invalide : " + error));
            enterSafeMode("objets de butin inconnus");
            return;
        }
        try {
            database = Database.open(getDataFolder().toPath().resolve("apocalypse.db"), getLogger());
            clock = new SystemClock(System::nanoTime);
            String stored = database.call(c -> Store.meta(c, "clock_ms"));
            clock.start(stored == null ? 0L : Long.parseLong(stored));
            clock.pause();
            protection = new ProtectionService(this);
            safePositions = new SafePositions(this);
            scenario = new ScenarioService(this);
            sessions = new SessionService(this);
            mobs = new MobService(this);
            loot = new LootService(this);
            infections = new InfectionService(this);
            bags = new BagService(this);
            deaths = new DeathService(this);
            director = new SpawnDirector(this);
            noise = new NoiseService(this);
            placed = new PlacedBlocks(this);
            crafting = new CraftingService(this);
            firearms = new FirearmService(this);
            drops = new DropService(this);
            siege = new SiegeManager(this);
            threat = new ThreatDirector(this);
            fauna = new FaunaService(this);
            teams = new TeamService(this);
            progress = new ProgressService(this);
            intro = new IntroService(this);
            scenario.load();
            if (scenario.isEnabled()) clock.resume();
            infections.load();
            bags.load();
            siege.load();
            threat.load();
            teams.load();
        } catch (PersistenceException e) {
            getLogger().log(Level.SEVERE, "Base du scénario inaccessible", e);
            if (database != null) database.close();
            database = null;
            enterSafeMode("base de données inaccessible");
            return;
        }
        PluginManager events = getServer().getPluginManager();
        Path databaseFile = getDataFolder().toPath().resolve("apocalypse.db");
        events.registerEvents(new BackupListener(database, Database.snapshotPath(databaseFile), getLogger()), this);
        events.registerEvents(new BoundaryListener(this), this);
        events.registerEvents(new ProtectionListener(this), this);
        events.registerEvents(new MobListener(this), this);
        events.registerEvents(new VanillaUseGuard(items), this);
        events.registerEvents(infections, this);
        events.registerEvents(bags, this);
        events.registerEvents(deaths, this);
        events.registerEvents(loot, this);
        search = new SearchService(this);
        events.registerEvents(search, this);
        events.registerEvents(noise, this);
        events.registerEvents(placed, this);
        events.registerEvents(crafting, this);
        events.registerEvents(firearms, this);
        events.registerEvents(siege, this);
        events.registerEvents(fauna, this);
        events.registerEvents(teams, this);
        events.registerEvents(progress, this);
        events.registerEvents(intro, this);
        fauna.resumeLoaded();
        ApocalypseCommand command = new ApocalypseCommand(this);
        PluginCommand registered = getCommand("apocalypse");
        if (registered != null) {
            registered.setExecutor(command);
            registered.setTabCompleter(command);
        }
        for (Player player : getServer().getOnlinePlayers()) sessions.reconcile(player);
        ticker = getServer().getScheduler().runTaskTimer(this, this::tick, TICK_PERIOD, TICK_PERIOD);
        getLogger().info("Ground Zero " + (scenario.isEnabled() ? "ouvert" : "fermé")
                + (scenario.problem() == null ? "" : " — " + scenario.problem()));
    }

    /**
     * Without its database the scenario cannot swap profiles. Survivors are kept out rather than let
     * them carry Ground Zero items into the classic world; everyone else plays normally.
     */
    private void enterSafeMode(String reason) {
        getLogger().severe("Mode sûr (" + reason + ") : les joueurs porteurs d'un profil Ground Zero ne peuvent pas se connecter.");
        Component message = Component.text("Ground Zero est en maintenance : ta survie est conservée. Reviens plus tard.");
        Listener guard = new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onJoin(PlayerJoinEvent event) {
                Player player = event.getPlayer();
                if ("APOCALYPSE".equals(player.getPersistentDataContainer().get(keys.profile, PersistentDataType.STRING)))
                    player.kick(message);
            }
        };
        getServer().getPluginManager().registerEvents(guard, this);
        for (Player player : getServer().getOnlinePlayers()) {
            if ("APOCALYPSE".equals(player.getPersistentDataContainer().get(keys.profile, PersistentDataType.STRING))) player.kick(message);
        }
    }

    /** A config.yml of an older version is set aside (never deleted) so the new defaults are written. */
    private void migrateConfig() {
        File file = new File(getDataFolder(), "config.yml");
        if (!file.isFile()) return;
        int version = YamlConfiguration.loadConfiguration(file).getInt("config-version", 1);
        if (version >= ApocalypseConfig.VERSION) return;
        File aside = new File(getDataFolder(), "config.v" + version + ".yml");
        for (int n = 2; aside.exists(); n++) aside = new File(getDataFolder(), "config.v" + version + "-" + n + ".yml");
        if (file.renameTo(aside)) getLogger().warning("config.yml version " + version + " mise de côté (" + aside.getName()
                + ") : nouvelle configuration version " + ApocalypseConfig.VERSION + " écrite. Reporte tes réglages à la main si besoin.");
        else getLogger().severe("Impossible de mettre de côté l'ancienne config.yml : règles de référence appliquées.");
    }

    public String configProblem() {
        return configProblem;
    }

    private void tick() {
        ticks++;
        try {
            infections.tick();
            mobs.tick();
            firearms.tick();
            if (ticks % TICKS_PER_SECOND == 0) {
                noise.tick();
                siege.tick();
                threat.tick();
                fauna.tick();
                bags.tick();
                protection.tick();
                safePositions.sampleAll();
                mobs.sweep();
            }
            if (ticks % (TICKS_PER_SECOND * settings.spawning().intervalSeconds()) == 0) director.tick();
            if (ticks % (TICKS_PER_SECOND * settings.checkpointMs() / 1000) == 0 && clock.running()) persistClock();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Erreur dans la boucle du scénario", e);
        }
    }

    private void persistClock() {
        long now = clock.now();
        database.submit(c -> {
            Store.putMeta(c, "clock_ms", Long.toString(now));
            return null;
        });
    }

    /** Keeps the last valid configuration when the new one is invalid. */
    public List<String> reloadSettings() {
        YamlConfiguration candidate = YamlConfiguration.loadConfiguration(getDataFolder().toPath().resolve("config.yml").toFile());
        candidate.setDefaults(getConfig().getDefaults() == null ? new YamlConfiguration() : getConfig().getDefaults());
        try {
            ApocalypseConfig parsed = ApocalypseConfig.parse(candidate);
            List<String> errors = new ArrayList<>(items.validate(parsed));
            if (!errors.isEmpty()) return errors;
            configProblem = null;
            settings = parsed;
            items.reload(parsed);
            reloadConfig();
            return List.of();
        } catch (ConfigException e) {
            return e.errors();
        }
    }

    @Override
    public void onDisable() {
        if (ticker != null) ticker.cancel();
        if (database == null) return;
        if (sessions != null && safePositions != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                try {
                    sessions.persistPresence(player, true);
                } catch (PersistenceException e) {
                    getLogger().warning("Position sûre de " + player.getName() + " non enregistrée.");
                }
            }
        }
        if (bags != null) bags.hideAll();
        if (siege != null) siege.clear();
        if (mobs != null) mobs.removeAll();
        if (threat != null) {
            try {
                threat.persist();
            } catch (PersistenceException e) {
                getLogger().warning("Niveau de menace non enregistré (tolérance : 30 s de jeu).");
            }
        }
        if (infections != null) infections.hideAll();
        if (clock != null) {
            long now = clock.pause();
            try {
                database.call(c -> {
                    Store.putMeta(c, "clock_ms", Long.toString(now));
                    return null;
                });
            } catch (PersistenceException e) {
                getLogger().log(Level.SEVERE, "Horloge du scénario non enregistrée (tolérance : un intervalle de sauvegarde)", e);
            }
        }
        database.close();
    }

    public ApocalypseConfig settings() {
        return settings;
    }

    public Keys keys() {
        return keys;
    }

    public Database database() {
        return database;
    }

    public SystemClock clock() {
        return clock;
    }

    public ItemRegistry items() {
        return items;
    }

    public ScenarioService scenario() {
        return scenario;
    }

    public SessionService sessions() {
        return sessions;
    }

    public InfectionService infections() {
        return infections;
    }

    public DeathService deaths() {
        return deaths;
    }

    public BagService bags() {
        return bags;
    }

    public MobService mobs() {
        return mobs;
    }

    public SpawnDirector director() {
        return director;
    }

    public LootService loot() {
        return loot;
    }

    public SearchService search() {
        return search;
    }

    public NoiseService noise() {
        return noise;
    }

    public PlacedBlocks placed() {
        return placed;
    }

    public CraftingService crafting() {
        return crafting;
    }

    public FirearmService firearms() {
        return firearms;
    }

    public DropService drops() {
        return drops;
    }

    public SiegeManager siege() {
        return siege;
    }

    public ThreatDirector threat() {
        return threat;
    }

    public FaunaService fauna() {
        return fauna;
    }

    public TeamService teams() {
        return teams;
    }

    public ProgressService progress() {
        return progress;
    }

    public IntroService intro() {
        return intro;
    }

    public ProtectionService protection() {
        return protection;
    }

    public SafePositions safePositions() {
        return safePositions;
    }
}
