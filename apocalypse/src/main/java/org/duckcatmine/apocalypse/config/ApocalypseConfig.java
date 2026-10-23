package org.duckcatmine.apocalypse.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.duckcatmine.apocalypse.loot.LootTable;
import org.duckcatmine.apocalypse.mobs.Appearance;
import org.duckcatmine.apocalypse.scenario.city.PlaceType;
import org.duckcatmine.apocalypse.scenario.city.Sector;

public record ApocalypseConfig(Scenario scenario, Refuge refuge, Infection infection, Death death, long protectionMs,
                               Loot loot, Search search, Spawning spawning, Map<String, SectorRule> sectors,
                               Map<String, Archetype> archetypes, Map<String, Place> places, long checkpointMs) {

    public static final int VERSION = 2;
    public static final String WALKER = "walker";
    /** Stable archetype IDs: each has its own behaviour in code; config tunes their numbers. */
    public static final Set<String> KNOWN_ARCHETYPES = Set.of(WALKER, "pursuer", "runner", "howler", "breaker", "abomination");

    public record Scenario(String id, String world, int borderSize, String difficulty) {
    }

    public record Refuge(int centerX, int centerZ, int radius) {
    }

    public record Infection(long durationMs, boolean repeatedBitesResetTimer, float antidoteUseSeconds,
                            boolean antidoteAllowSprint, float antidoteSpeedMultiplier) {
    }

    public record Death(long bagLifetimeMs, double bagAccessRadius) {
    }

    public record Loot(long contributionWindowMs, double bonusRollChance) {
    }

    public record Search(long refillMs) {
    }

    public record Spawning(boolean enabled, int intervalSeconds, int globalCap, int minDistance, int maxDistance,
                           int despawnDistance, int nightBonus, double indoorChance) {
    }

    public record SectorRule(String id, int localCap, Map<String, Double> mix) {
        public SectorRule {
            mix = Map.copyOf(mix);
        }
    }

    public record Archetype(String id, String name, double health, double damage, double speed, double speedVariation,
                            double followRange, double biteChance, double armor, double knockbackResistance, double scale,
                            int maxAlive, List<String> appearances, Map<String, Double> abilities,
                            LootTable guaranteed, LootTable bonus) {
        public Archetype {
            appearances = List.copyOf(appearances);
            abilities = Map.copyOf(abilities);
        }

        public double ability(String key, double fallback) {
            return abilities.getOrDefault(key, fallback);
        }
    }

    public record Place(String id, int rollsMin, int rollsMax, LootTable loot) {
    }

    public ApocalypseConfig {
        sectors = Map.copyOf(sectors);
        archetypes = Map.copyOf(archetypes);
        places = Map.copyOf(places);
    }

    public Archetype archetype(String id) {
        return id == null ? null : archetypes.get(id);
    }

    public SectorRule sector(String id) {
        return id == null ? null : sectors.get(id);
    }

    public Place place(String id) {
        return id == null ? null : places.get(id);
    }

    public static ApocalypseConfig parse(ConfigurationSection root) throws ConfigException {
        Reader read = new Reader(root);
        read.integer("config-version", VERSION, VERSION, VERSION);
        Scenario scenario = new Scenario(
                read.text("scenario.id", "ground_zero", "ground_zero"),
                read.text("scenario.world", "ground_zero", "[a-z0-9_]{1,32}"),
                read.integer("scenario.border-size", 3072, 128, 8192),
                read.text("scenario.difficulty", "NORMAL", "EASY|NORMAL|HARD"));
        Refuge refuge = new Refuge(
                read.integer("refuge.center-x", 0, -100_000, 100_000),
                read.integer("refuge.center-z", 0, -100_000, 100_000),
                read.integer("refuge.radius", 12, 6, 48));
        Infection infection = new Infection(
                read.integer("infection.duration-seconds", 60, 10, 600) * 1000L,
                read.bool("infection.repeated-bites-reset-timer", false),
                (float) read.decimal("infection.antidote-use-seconds", 2.0, 0.5, 10),
                read.bool("infection.antidote-allow-sprint", true),
                (float) read.decimal("infection.antidote-speed-multiplier", 1.0, 0.1, 1.0));
        Death death = new Death(
                read.integer("death.bag-lifetime-seconds", 300, 30, 3600) * 1000L,
                read.decimal("death.bag-access-radius", 4.5, 2, 8));
        long protectionMs = read.integer("respawn.protection-seconds", 30, 0, 120) * 1000L;
        Loot loot = new Loot(
                read.integer("loot.contribution-window-seconds", 30, 5, 300) * 1000L,
                read.decimal("loot.bonus-roll-chance", 0.12, 0, 1));
        Search search = new Search(read.integer("search.refill-minutes", 30, 1, 1440) * 60_000L);
        Spawning spawning = new Spawning(
                read.bool("spawning.enabled", true),
                read.integer("spawning.interval-seconds", 3, 1, 60),
                read.integer("spawning.global-cap", 60, 0, 400),
                read.integer("spawning.min-distance", 20, 8, 64),
                read.integer("spawning.max-distance", 40, 12, 96),
                read.integer("spawning.despawn-distance", 72, 32, 192),
                read.integer("spawning.night-bonus", 2, 0, 16),
                read.decimal("spawning.indoor-chance", 0.25, 0, 1));
        long checkpointMs = read.integer("clock.checkpoint-seconds", 5, 1, 60) * 1000L;

        Map<String, Archetype> archetypes = new LinkedHashMap<>();
        for (String id : read.keys("archetypes")) {
            if (!KNOWN_ARCHETYPES.contains(id)) {
                read.errors.add("archetypes." + id + " : archétype inconnu (connus : " + String.join(", ", KNOWN_ARCHETYPES.stream().sorted().toList()) + ").");
                continue;
            }
            String base = "archetypes." + id + ".";
            List<String> appearances = read.appearances(base + "appearances");
            archetypes.put(id, new Archetype(id,
                    read.text(base + "name", id, ".{1,32}"),
                    read.decimal(base + "health", 20, 1, 1024),
                    read.decimal(base + "damage", 3, 0, 100),
                    read.decimal(base + "speed", 0.2, 0.05, 0.6),
                    read.decimal(base + "speed-variation", 0, 0, 0.05),
                    read.decimal(base + "follow-range", 24, 4, 64),
                    read.decimal(base + "bite-chance", 0.33, 0, 1),
                    read.decimal(base + "armor", 0, 0, 20),
                    read.decimal(base + "knockback-resistance", 0, 0, 1),
                    read.decimal(base + "scale", 1, 0.5, 2),
                    read.integer(base + "max-alive", 0, 0, 50),
                    appearances,
                    read.numbers(base + "abilities", 0, 10_000),
                    read.table(base + "guaranteed-loot", true),
                    read.table(base + "bonus-loot", false)));
        }
        if (!archetypes.containsKey(WALKER)) read.errors.add("archetypes.walker : archétype obligatoire.");

        Map<String, SectorRule> sectors = new LinkedHashMap<>();
        for (String id : read.keys("sectors")) {
            boolean known = false;
            for (Sector sector : Sector.values()) known |= sector != Sector.REFUGE && sector.id().equals(id);
            if (!known) {
                read.errors.add("sectors." + id + " : secteur inconnu.");
                continue;
            }
            Map<String, Double> mix = read.numbers("sectors." + id + ".mix", 0, 1_000_000);
            mix.entrySet().removeIf(entry -> {
                if (archetypes.containsKey(entry.getKey()) && entry.getValue() > 0) return false;
                read.errors.add("sectors." + id + ".mix." + entry.getKey() + " : archétype non configuré ou poids nul.");
                return true;
            });
            sectors.put(id, new SectorRule(id, read.integer("sectors." + id + ".local-cap", 3, 0, 32), mix));
        }

        Map<String, Place> places = new LinkedHashMap<>();
        for (String id : read.keys("places")) {
            if (PlaceType.byId(id) == null) {
                read.errors.add("places." + id + " : type de lieu inconnu.");
                continue;
            }
            String base = "places." + id + ".";
            int min = read.integer(base + "rolls-min", 1, 0, 27);
            int max = read.integer(base + "rolls-max", 3, 1, 27);
            if (min > max) read.errors.add(base + "rolls-min doit être inférieur ou égal à rolls-max.");
            places.put(id, new Place(id, min, max, read.table(base + "loot", true)));
        }

        if (spawning.minDistance() >= spawning.maxDistance())
            read.errors.add("spawning.min-distance doit être inférieure à spawning.max-distance.");
        if (spawning.maxDistance() >= spawning.despawnDistance())
            read.errors.add("spawning.max-distance doit être inférieure à spawning.despawn-distance.");
        int half = scenario.borderSize() / 2;
        if (Math.abs(refuge.centerX()) + refuge.radius() + 16 > half || Math.abs(refuge.centerZ()) + refuge.radius() + 16 > half)
            read.errors.add("refuge : le refuge et ses abords (16 blocs) doivent tenir dans la bordure du monde.");

        if (!read.errors.isEmpty()) throw new ConfigException(read.errors);
        return new ApocalypseConfig(scenario, refuge, infection, death, protectionMs, loot, search, spawning, sectors,
                archetypes, places, checkpointMs);
    }

    private static final class Reader {
        private final ConfigurationSection root;
        private final List<String> errors = new ArrayList<>();

        Reader(ConfigurationSection root) {
            this.root = root;
        }

        Set<String> keys(String path) {
            ConfigurationSection section = root.getConfigurationSection(path);
            return section == null ? Set.of() : section.getKeys(false);
        }

        int integer(String path, int fallback, int min, int max) {
            Object value = root.get(path);
            if (value == null) return fallback;
            if (!(value instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
                errors.add(path + " : nombre entier attendu.");
                return fallback;
            }
            long result = number.longValue();
            if (result < min || result > max) {
                errors.add(path + " : " + result + " hors limites [" + min + " ; " + max + "].");
                return fallback;
            }
            return (int) result;
        }

        double decimal(String path, double fallback, double min, double max) {
            Object value = root.get(path);
            if (value == null) return fallback;
            if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                errors.add(path + " : nombre attendu.");
                return fallback;
            }
            double result = number.doubleValue();
            if (result < min || result > max) {
                errors.add(path + " : " + result + " hors limites [" + min + " ; " + max + "].");
                return fallback;
            }
            return result;
        }

        boolean bool(String path, boolean fallback) {
            Object value = root.get(path);
            if (value == null) return fallback;
            if (value instanceof Boolean result) return result;
            errors.add(path + " : true ou false attendu.");
            return fallback;
        }

        String text(String path, String fallback, String pattern) {
            Object value = root.get(path);
            if (value == null) return fallback;
            if (!(value instanceof String result) || !result.matches(pattern)) {
                errors.add(path + " : valeur invalide (" + pattern + ").");
                return fallback;
            }
            return result;
        }

        /** Section of named numbers, e.g. a sector mix or archetype abilities. */
        Map<String, Double> numbers(String path, double min, double max) {
            Map<String, Double> result = new LinkedHashMap<>();
            ConfigurationSection section = root.getConfigurationSection(path);
            if (section == null) {
                if (root.get(path) != null) errors.add(path + " : section attendue.");
                return result;
            }
            for (String key : section.getKeys(false)) {
                if (!key.matches("[a-z_-]{1,32}")) {
                    errors.add(path + "." + key + " : nom invalide.");
                    continue;
                }
                result.put(key, decimal(path + "." + key, 0, min, max));
            }
            return result;
        }

        List<String> appearances(String path) {
            List<String> result = new ArrayList<>();
            List<?> raw = root.getList(path);
            if (raw == null) return List.of(Appearance.CITIZEN.id());
            for (Object value : raw) {
                if (value instanceof String id && Appearance.byId(id) != null) result.add(id);
                else errors.add(path + " : apparence inconnue " + value + " (connues : " + Appearance.ids() + ").");
            }
            if (result.isEmpty()) {
                errors.add(path + " : au moins une apparence est obligatoire.");
                result.add(Appearance.CITIZEN.id());
            }
            return result;
        }

        LootTable table(String path, boolean required) {
            List<LootTable.Entry> entries = new ArrayList<>();
            List<?> raw = root.getList(path);
            if (raw == null) raw = List.of();
            for (int index = 0; index < raw.size(); index++) {
                String where = path + "[" + index + "]";
                if (!(raw.get(index) instanceof Map<?, ?> map)) {
                    errors.add(where + " : objet attendu.");
                    continue;
                }
                Object item = map.get("item");
                Object weight = map.get("weight");
                Object amount = map.containsKey("amount") ? map.get("amount") : 1;
                if (!(item instanceof String id) || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                    errors.add(where + ".item : identifiant namespacé attendu (ex. apocalypse:scrap).");
                    continue;
                }
                if (!(weight instanceof Number w) || !(w.doubleValue() > 0) || w.doubleValue() > 1_000_000) {
                    errors.add(where + ".weight : poids strictement positif attendu.");
                    continue;
                }
                if (!(amount instanceof Number a) || a.doubleValue() != Math.rint(a.doubleValue()) || a.intValue() < 1 || a.intValue() > 64) {
                    errors.add(where + ".amount : quantité entière entre 1 et 64 attendue.");
                    continue;
                }
                entries.add(new LootTable.Entry(id, w.doubleValue(), a.intValue()));
            }
            if (required && entries.isEmpty()) errors.add(path + " : au moins un objet est obligatoire.");
            return new LootTable(entries);
        }
    }
}
