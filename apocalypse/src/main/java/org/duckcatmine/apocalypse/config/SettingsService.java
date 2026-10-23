package org.duckcatmine.apocalypse.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.persistence.Store;

/**
 * Difficulty settings an administrator may change in game or from the web panel: an allow-list of config.yml keys with
 * bounds. A change is checked against the whole configuration before the file is replaced (atomically), then applied
 * like {@code admin reload}; running infections and bags keep their deadlines.
 */
public final class SettingsService {
    public enum Kind { INTEGER, DECIMAL, BOOLEAN }

    public record Setting(String key, String path, Kind kind, double min, double max, String label, String group,
                          Function<ApocalypseConfig, Object> current) {
    }

    public static final List<Setting> CATALOG = List.of(
            new Setting("infection-seconds", "infection.duration-seconds", Kind.INTEGER, 10, 600,
                    "Délai pour finir un antidote après une morsure (s)", "Infection", c -> c.infection().durationMs() / 1000),
            new Setting("bite-multiplier", "infection.bite-multiplier", Kind.DECIMAL, 0, 3,
                    "Risque de morsure (× la chance de chaque infecté)", "Infection", c -> c.infection().biteMultiplier()),
            new Setting("medical-boost", "loot.medical-boost", Kind.DECIMAL, 0.25, 10,
                    "Antidotes, sérums et inhibiteurs trouvés (×)", "Infection", c -> c.loot().medicalBoost()),
            new Setting("bag-seconds", "death.bag-lifetime-seconds", Kind.INTEGER, 30, 3600,
                    "Durée du sac après une mort (s)", "Mort", c -> c.death().bagLifetimeMs() / 1000),
            new Setting("protection-seconds", "respawn.protection-seconds", Kind.INTEGER, 0, 120,
                    "Protection au largage et au retour (s)", "Mort", c -> c.protectionMs() / 1000),
            new Setting("spawning", "spawning.enabled", Kind.BOOLEAN, 0, 1,
                    "Apparition des infectés", "Infectés", c -> c.spawning().enabled()),
            new Setting("density", "spawning.density", Kind.DECIMAL, 0.25, 5,
                    "Densité d'infectés autour des survivants (×)", "Infectés", c -> c.spawning().density()),
            new Setting("per-tick", "spawning.per-tick", Kind.INTEGER, 1, 5,
                    "Renforts par passage (vitesse de repeuplement)", "Infectés", c -> c.spawning().perTick()),
            new Setting("global-cap", "spawning.global-cap", Kind.INTEGER, 0, 400,
                    "Plafond d'infectés dans tout le monde", "Infectés", c -> c.spawning().globalCap()),
            new Setting("night-bonus", "spawning.night-bonus", Kind.INTEGER, 0, 16,
                    "Infectés en plus la nuit, par survivant", "Infectés", c -> c.spawning().nightBonus()),
            new Setting("threat-per-hour", "threat.per-hour", Kind.DECIMAL, 0, 100,
                    "Montée de la menace par heure de jeu", "Infectés", c -> c.play().threat().perHour()),
            new Setting("fauna", "fauna.enabled", Kind.BOOLEAN, 0, 1,
                    "Animaux contaminés", "Faune", c -> c.play().fauna().enabled()),
            new Setting("fauna-share", "fauna.spawn-share", Kind.DECIMAL, 0, 0.6,
                    "Part d'animaux contaminés parmi les nouveaux infectés", "Faune", c -> c.play().fauna().spawnShare()),
            new Setting("fauna-chance", "fauna.infection-chance", Kind.DECIMAL, 0, 1,
                    "Risque qu'un animal mordu soit contaminé", "Faune", c -> c.play().fauna().infectionChance()),
            new Setting("vehicles", "vehicles.enabled", Kind.BOOLEAN, 0, 1,
                    "Voitures conduisibles", "Monde", c -> c.play().vehicles().enabled()),
            new Setting("city-chance", "spawn.city-chance", Kind.DECIMAL, 0, 1,
                    "Part des largages en ville", "Monde", c -> c.play().spawn().cityChance()),
            new Setting("leave-seconds", "session.leave-seconds", Kind.INTEGER, 0, 120,
                    "Délai pour quitter hors du refuge (s)", "Monde", c -> c.play().leaveSeconds()),
            new Setting("intro", "intro.enabled", Kind.BOOLEAN, 0, 1,
                    "Introduction à la première entrée", "Monde", c -> c.play().intro()),
            new Setting("team-size", "teams.max-size", Kind.INTEGER, 2, 16,
                    "Taille maximale d'une équipe", "Monde", c -> c.play().social().teamMaxSize()));

    /** Bundles of values; anything not listed keeps its value. */
    public static final Map<String, Map<String, Object>> PRESETS = presets();

    private static Map<String, Map<String, Object>> presets() {
        Map<String, Map<String, Object>> presets = new LinkedHashMap<>();
        presets.put("relaxed", Map.ofEntries(Map.entry("infection-seconds", 120), Map.entry("bite-multiplier", 0.6),
                Map.entry("medical-boost", 4.0), Map.entry("bag-seconds", 600), Map.entry("protection-seconds", 45),
                Map.entry("density", 1.0), Map.entry("per-tick", 1), Map.entry("global-cap", 50), Map.entry("night-bonus", 1),
                Map.entry("threat-per-hour", 1.5), Map.entry("fauna-share", 0.1)));
        presets.put("normal", Map.ofEntries(Map.entry("infection-seconds", 60), Map.entry("bite-multiplier", 1.0),
                Map.entry("medical-boost", 3.0), Map.entry("bag-seconds", 300), Map.entry("protection-seconds", 30),
                Map.entry("density", 1.5), Map.entry("per-tick", 2), Map.entry("global-cap", 80), Map.entry("night-bonus", 2),
                Map.entry("threat-per-hour", 3.0), Map.entry("fauna-share", 0.15)));
        presets.put("nightmare", Map.ofEntries(Map.entry("infection-seconds", 45), Map.entry("bite-multiplier", 1.5),
                Map.entry("medical-boost", 1.5), Map.entry("bag-seconds", 180), Map.entry("protection-seconds", 15),
                Map.entry("density", 2.5), Map.entry("per-tick", 3), Map.entry("global-cap", 150), Map.entry("night-bonus", 4),
                Map.entry("threat-per-hour", 6.0), Map.entry("fauna-share", 0.25)));
        return presets;
    }

    public static final Map<String, String> PRESET_LABELS = Map.of("relaxed", "Détendu", "normal", "Normal", "nightmare", "Cauchemar");

    private final DuckCatApocalypse plugin;

    public SettingsService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public static Setting find(String key) {
        for (Setting setting : CATALOG) if (setting.key().equals(key)) return setting;
        return null;
    }

    /** Current effective values (file, else bundled defaults). */
    public Map<String, Object> values() {
        Map<String, Object> values = new LinkedHashMap<>();
        ApocalypseConfig config = plugin.settings();
        for (Setting setting : CATALOG) values.put(setting.key(), setting.current().apply(config));
        return values;
    }

    /** The preset whose every value matches, or null. */
    public String activePreset() {
        Map<String, Object> values = values();
        for (Map.Entry<String, Map<String, Object>> preset : PRESETS.entrySet()) {
            boolean all = true;
            for (Map.Entry<String, Object> entry : preset.getValue().entrySet()) {
                Object current = values.get(entry.getKey());
                if (!(current instanceof Number number) || Math.abs(number.doubleValue() - ((Number) entry.getValue()).doubleValue()) > 1e-6) {
                    all = false;
                    break;
                }
            }
            if (all) return preset.getKey();
        }
        return null;
    }

    static Object parse(Setting setting, String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(',', '.');
        if (setting.kind() == Kind.BOOLEAN) {
            if (List.of("on", "true", "oui", "1").contains(text)) return true;
            if (List.of("off", "false", "non", "0").contains(text)) return false;
            return null;
        }
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
        if (!(value >= setting.min() && value <= setting.max())) return null;
        if (setting.kind() == Kind.INTEGER) {
            if (value != Math.rint(value)) return null;
            return (int) value;
        }
        return Math.round(value * 1000) / 1000.0;
    }

    /** Changes one setting; returns problems (empty when applied). */
    public List<String> set(String key, String raw, String actor) {
        Setting setting = find(key);
        if (setting == null) return List.of("Réglage inconnu : " + key + ".");
        Object value = parse(setting, raw);
        if (value == null) return List.of(setting.kind() == Kind.BOOLEAN ? "Valeur attendue : on ou off."
                : String.format(Locale.ROOT, "Valeur attendue entre %s et %s%s.", number(setting.min()), number(setting.max()),
                setting.kind() == Kind.INTEGER ? " (nombre entier)" : ""));
        return apply(Map.of(setting.path(), value), actor, "admin_set", key + "=" + value);
    }

    public List<String> preset(String name, String actor) {
        Map<String, Object> preset = PRESETS.get(name);
        if (preset == null) return List.of("Préréglage inconnu : " + name + " (relaxed, normal, nightmare).");
        Map<String, Object> changes = new LinkedHashMap<>();
        preset.forEach((key, value) -> changes.put(find(key).path(), value));
        return apply(changes, actor, "admin_preset", name);
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private List<String> apply(Map<String, Object> changes, String actor, String action, String detail) {
        Path file = plugin.getDataFolder().toPath().resolve("config.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        String before;
        try {
            before = Files.readString(file, StandardCharsets.UTF_8);
            yaml.loadFromString(before);
        } catch (IOException | InvalidConfigurationException e) {
            return List.of("config.yml illisible : " + e.getMessage());
        }
        changes.forEach(yaml::set);
        String after = yaml.saveToString();
        YamlConfiguration candidate = new YamlConfiguration();
        try {
            candidate.loadFromString(after);
        } catch (InvalidConfigurationException e) {
            return List.of("Écriture refusée : " + e.getMessage());
        }
        if (plugin.getConfig().getDefaults() != null) candidate.setDefaults(plugin.getConfig().getDefaults());
        try {
            ApocalypseConfig.parse(candidate);
        } catch (ConfigException e) {
            return e.errors();
        }
        try {
            replace(file, after);
        } catch (IOException e) {
            return List.of("config.yml non écrit : " + e.getMessage());
        }
        List<String> errors = plugin.reloadSettings();
        if (!errors.isEmpty()) {
            try {
                replace(file, before);
            } catch (IOException e) {
                plugin.getLogger().severe("Retour à l'ancienne config.yml impossible : " + e.getMessage());
            }
            return errors;
        }
        long now = plugin.clock().now();
        plugin.database().submit(c -> {
            Store.audit(c, now, actor, action, detail);
            return null;
        });
        plugin.getLogger().info("Réglage (" + actor + ") : " + detail);
        plugin.events().add("admin", "Réglage modifié par " + actor + " : " + detail);
        return List.of();
    }

    private static void replace(Path file, String text) throws IOException {
        Path temp = file.resolveSibling("config.yml.tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
