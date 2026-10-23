package org.duckcatmine.apocalypse.panel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.config.SettingsService;
import org.duckcatmine.apocalypse.death.BagService;
import org.duckcatmine.apocalypse.items.ItemRegistry;
import org.duckcatmine.apocalypse.persistence.Store;
import org.duckcatmine.apocalypse.scenario.BlueMapHook;
import org.duckcatmine.apocalypse.scenario.GroundZeroGenerator;
import org.duckcatmine.apocalypse.scenario.city.CityLayout;
import org.duckcatmine.apocalypse.scenario.city.Sector;

/**
 * Writes {@code status.json} in the plugin folder every few seconds for the DuckCatMine web panel, which only reads it
 * (read-only mount): state, threat, rules, infected, survivors and teams. Gathered on the main thread, written off it,
 * atomically (temporary file then rename).
 */
public final class StatusWriter {
    public static final int VERSION = 2;
    private static final long PROFILES_EVERY_MS = 60_000;

    private final DuckCatApocalypse plugin;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final AtomicBoolean writing = new AtomicBoolean();
    private int classicProfiles = -1;
    private long profilesCountedAt;
    private JsonArray leaderboard = new JsonArray();
    private long leaderboardAt;
    private com.google.gson.JsonElement mapJson;
    private long mapModified;

    public StatusWriter(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public Path file() {
        return plugin.getDataFolder().toPath().resolve("status.json");
    }

    public void tick() {
        JsonObject status;
        try {
            status = snapshot();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "État pour le panneau non calculé", e);
            return;
        }
        String text = gson.toJson(status);
        if (!writing.compareAndSet(false, true)) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                write(text);
            } catch (IOException e) {
                plugin.getLogger().warning("status.json non écrit : " + e.getMessage());
            } finally {
                writing.set(false);
            }
        });
    }

    /** Last state written when the plugin stops, so the panel does not show stale survivors as present. */
    public void writeStopped() {
        try {
            JsonObject status = snapshot();
            status.addProperty("running", false);
            status.add("survivors", new JsonArray());
            write(gson.toJson(status));
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("status.json final non écrit : " + e.getMessage());
        }
    }

    private void write(String text) throws IOException {
        Path target = file();
        Path temp = target.resolveSibling("status.json.tmp");
        Files.createDirectories(target.getParent());
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    JsonObject snapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("generatedAt", System.currentTimeMillis());
        root.addProperty("running", true);
        var scenario = plugin.scenario();
        World world = scenario.world();
        root.addProperty("enabled", scenario.isEnabled());
        root.addProperty("world", world == null ? null : world.getName());
        root.addProperty("epoch", scenario.epoch());
        root.addProperty("problem", scenario.problem());
        root.addProperty("configProblem", plugin.configProblem());
        root.addProperty("database", plugin.database().healthy());
        root.addProperty("clockSeconds", plugin.clock().now() / 1000);
        if (world != null) {
            JsonObject time = new JsonObject();
            time.addProperty("ticks", world.getTime());
            time.addProperty("night", !world.isDayTime());
            time.addProperty("day", world.getFullTime() / 24000);
            root.add("time", time);
        }

        var threat = plugin.threat();
        JsonObject threatJson = new JsonObject();
        threatJson.addProperty("level", Math.round(threat.level() * 100) / 100.0);
        threatJson.addProperty("phase", threat.phase().name().toLowerCase(java.util.Locale.ROOT));
        threatJson.addProperty("phaseLabel", threat.phase().label());
        threatJson.addProperty("secondsLeft", threat.secondsLeft());
        threatJson.addProperty("hordes", threat.hordes());
        root.add("threat", threatJson);

        JsonObject rules = new JsonObject();
        rules.addProperty("pvp", scenario.pvp());
        rules.addProperty("destruction", plugin.siege().enabled());
        rules.addProperty("globalCap", plugin.settings().spawning().globalCap());
        root.add("rules", rules);

        JsonObject infected = new JsonObject();
        JsonObject labels = new JsonObject();
        int total = 0;
        for (Map.Entry<String, Integer> entry : plugin.mobs().countByArchetype().entrySet()) {
            infected.addProperty(entry.getKey(), entry.getValue());
            total += entry.getValue();
        }
        for (ApocalypseConfig.Archetype archetype : plugin.settings().archetypes().values()) labels.addProperty(archetype.id(), archetype.name());
        root.add("infected", infected);
        root.add("archetypeLabels", labels);
        root.addProperty("infectedTotal", total);
        root.addProperty("incubatingAnimals", plugin.fauna().incubatingCount());
        root.addProperty("activeInfections", plugin.infections().count());
        root.addProperty("activeBags", plugin.bags().count());
        root.addProperty("damagedBlocks", plugin.siege().damagedBlocks());

        JsonArray survivors = new JsonArray();
        if (world != null) {
            for (Player player : world.getPlayers()) {
                if (!plugin.sessions().isSurvivor(player)) continue;
                JsonObject survivor = new JsonObject();
                Location at = player.getLocation();
                survivor.addProperty("name", player.getName());
                survivor.addProperty("uuid", player.getUniqueId().toString());
                survivor.addProperty("x", at.getBlockX());
                survivor.addProperty("y", at.getBlockY());
                survivor.addProperty("z", at.getBlockZ());
                Sector sector = plugin.director().sectorAt(at);
                survivor.addProperty("sector", sector.label());
                survivor.addProperty("inRefuge", scenario.inRefuge(at));
                survivor.addProperty("health", Math.round(player.getHealth() * 10) / 10.0);
                Long remaining = plugin.infections().remaining(player.getUniqueId());
                survivor.addProperty("infectionSeconds", remaining == null ? null : remaining / 1000);
                var team = plugin.teams().teamOf(player.getUniqueId());
                survivor.addProperty("team", team == null ? null : team.name());
                survivor.addProperty("points", plugin.progress().points(player));
                survivor.addProperty("lifeSeconds", plugin.progress().lifeMillis(player) / 1000);
                JsonObject skills = new JsonObject();
                plugin.progress().ranks(player).forEach(skills::addProperty);
                survivor.add("skills", skills);
                survivor.addProperty("inVehicle", plugin.vehicles().isRiding(player));
                survivors.add(survivor);
            }
        }
        root.add("survivors", survivors);

        JsonArray teams = new JsonArray();
        for (Map.Entry<String, List<String>> team : plugin.teams().listing()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", team.getKey());
            JsonArray members = new JsonArray();
            team.getValue().forEach(members::add);
            entry.add("members", members);
            teams.add(entry);
        }
        root.add("teams", teams);

        long now = System.currentTimeMillis();
        if (classicProfiles < 0 || now - profilesCountedAt > PROFILES_EVERY_MS) {
            try {
                classicProfiles = plugin.database().call(c -> Store.countProfiles(c, "CLASSIC"));
                profilesCountedAt = now;
            } catch (RuntimeException e) {
                classicProfiles = -1;
            }
        }
        root.addProperty("classicProfilesHeld", classicProfiles);
        var refuge = plugin.settings().refuge();
        JsonObject refugeJson = new JsonObject();
        refugeJson.addProperty("x", refuge.centerX());
        refugeJson.addProperty("z", refuge.centerZ());
        refugeJson.addProperty("radius", refuge.radius());
        root.add("refuge", refugeJson);
        root.addProperty("borderSize", plugin.settings().scenario().borderSize());
        Path map = plugin.getDataFolder().toPath().resolve("map.json");
        try {
            long modified = Files.isRegularFile(map) ? Files.getLastModifiedTime(map).toMillis() : 0;
            if (modified != mapModified) {
                mapJson = modified == 0 ? null : JsonParser.parseString(Files.readString(map));
                mapModified = modified;
            }
        } catch (IOException | RuntimeException e) {
            mapModified = -1;
        }
        if (mapJson != null) root.add("map", mapJson);
        extras(root, world, now);
        return root;
    }

    /** Everything the panel needs to steer a game: settings, journal, board, bags, items, places, cars. */
    private void extras(JsonObject root, World world, long now) {
        JsonObject settings = new JsonObject();
        plugin.tuning().values().forEach((key, value) -> {
            if (value instanceof Boolean bool) settings.addProperty(key, bool);
            else if (value instanceof Number number) settings.addProperty(key, number);
        });
        root.add("settings", settings);
        JsonArray catalog = new JsonArray();
        for (SettingsService.Setting setting : SettingsService.CATALOG) {
            JsonObject entry = new JsonObject();
            entry.addProperty("key", setting.key());
            entry.addProperty("label", setting.label());
            entry.addProperty("group", setting.group());
            entry.addProperty("kind", setting.kind().name().toLowerCase(java.util.Locale.ROOT));
            entry.addProperty("min", setting.min());
            entry.addProperty("max", setting.max());
            catalog.add(entry);
        }
        root.add("settingsCatalog", catalog);
        JsonObject presets = new JsonObject();
        SettingsService.PRESETS.forEach((name, values) -> {
            JsonObject preset = new JsonObject();
            preset.addProperty("label", SettingsService.PRESET_LABELS.getOrDefault(name, name));
            JsonObject entries = new JsonObject();
            values.forEach((key, value) -> entries.addProperty(key, (Number) value));
            preset.add("values", entries);
            presets.add(name, preset);
        });
        root.add("presets", presets);
        root.addProperty("activePreset", plugin.tuning().activePreset());

        JsonArray events = new JsonArray();
        for (EventLog.Event event : plugin.events().entries()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("at", event.at());
            entry.addProperty("kind", event.kind());
            entry.addProperty("text", event.text());
            events.add(entry);
        }
        root.add("events", events);

        if (now - leaderboardAt > PROFILES_EVERY_MS) {
            leaderboardAt = now;
            try {
                JsonArray board = new JsonArray();
                long clock = plugin.clock().now();
                for (Store.Ranking row : plugin.database().call(c -> Store.leaderboard(c, 10))) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("name", row.name());
                    entry.addProperty("alive", row.alive());
                    entry.addProperty("points", row.points());
                    entry.addProperty("kills", row.kills());
                    entry.addProperty("lifeSeconds", row.alive() ? Math.max(0, clock - row.startedAt()) / 1000 : 0);
                    entry.addProperty("careerPoints", row.careerPoints());
                    entry.addProperty("bestSurvivalSeconds", row.bestSurvivalMs() / 1000);
                    board.add(entry);
                }
                leaderboard = board;
            } catch (RuntimeException e) {
                plugin.getLogger().fine("Classement non calculé : " + e.getMessage());
            }
        }
        root.add("leaderboard", leaderboard);

        JsonArray bags = new JsonArray();
        for (BagService.Info bag : plugin.bags().list()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("owner", bag.owner());
            entry.addProperty("x", bag.x());
            entry.addProperty("z", bag.z());
            entry.addProperty("secondsLeft", bag.secondsLeft());
            entry.addProperty("items", bag.items());
            bags.add(entry);
        }
        root.add("bags", bags);

        JsonArray items = new JsonArray();
        for (String id : plugin.items().ids()) {
            if (ItemRegistry.NOTE.equals(id)) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id);
            entry.addProperty("name", plugin.items().label(id));
            items.add(entry);
        }
        root.add("items", items);

        JsonArray landmarks = new JsonArray();
        if (world != null && world.getGenerator() instanceof GroundZeroGenerator) {
            for (CityLayout.Landmark landmark : CityLayout.of(world.getSeed()).landmarks()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("kind", landmark.kind());
                entry.addProperty("label", landmark.label());
                entry.addProperty("x", landmark.x());
                entry.addProperty("z", landmark.z());
                landmarks.add(entry);
            }
        }
        root.add("landmarks", landmarks);

        JsonObject vehicles = new JsonObject();
        vehicles.addProperty("loaded", plugin.vehicles().loaded());
        vehicles.addProperty("driven", plugin.vehicles().driven());
        vehicles.addProperty("enabled", plugin.settings().play().vehicles().enabled());
        root.add("vehicles", vehicles);

        JsonObject blueMap = new JsonObject();
        blueMap.addProperty("available", world != null && BlueMapHook.installed());
        blueMap.addProperty("map", world == null ? null : world.getName());
        root.add("blueMap", blueMap);
    }
}
