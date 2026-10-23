package org.duckcatmine.apocalypse.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.duckcatmine.apocalypse.loot.LootTable;

public record ApocalypseConfig(Scenario scenario, Refuge refuge, Infection infection, Death death, long protectionMs,
                               Loot loot, Spawning spawning, Map<String, Archetype> archetypes, long checkpointMs) {

    public static final String WALKER = "walker";

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

    public record Spawning(boolean enabled, int intervalSeconds, int perPlayerLocalCap, int globalCap,
                           int minDistance, int maxDistance, int despawnDistance) {
    }

    public record Archetype(String id, double health, double damage, double speed, double followRange,
                            double biteChance, LootTable guaranteed, LootTable bonus) {
    }

    public ApocalypseConfig {
        archetypes = Map.copyOf(archetypes);
    }

    public Archetype archetype(String id) {
        return id == null ? null : archetypes.get(id);
    }

    public static ApocalypseConfig parse(ConfigurationSection root) throws ConfigException {
        Reader read = new Reader(root);
        read.integer("config-version", 1, 1, 1);
        Scenario scenario = new Scenario(
                read.text("scenario.id", "ground_zero", "ground_zero"),
                read.text("scenario.world", "ground_zero", "[a-z0-9_]{1,32}"),
                read.integer("scenario.border-size", 768, 128, 8192),
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
                read.decimal("loot.bonus-roll-chance", 0.10, 0, 1));
        Spawning spawning = new Spawning(
                read.bool("spawning.enabled", true),
                read.integer("spawning.interval-seconds", 3, 1, 60),
                read.integer("spawning.per-player-local-cap", 3, 0, 32),
                read.integer("spawning.global-cap", 40, 0, 400),
                read.integer("spawning.min-distance", 20, 8, 64),
                read.integer("spawning.max-distance", 32, 12, 96),
                read.integer("spawning.despawn-distance", 64, 32, 192));
        long checkpointMs = read.integer("clock.checkpoint-seconds", 5, 1, 60) * 1000L;

        Map<String, Archetype> archetypes = new LinkedHashMap<>();
        ConfigurationSection section = root.getConfigurationSection("archetypes");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                if (!id.matches("[a-z_]{1,32}")) {
                    read.errors.add("archetypes." + id + " : identifiant invalide.");
                    continue;
                }
                String base = "archetypes." + id + ".";
                archetypes.put(id, new Archetype(id,
                        read.decimal(base + "health", 20, 1, 1024),
                        read.decimal(base + "damage", 3, 0, 100),
                        read.decimal(base + "speed", 0.2, 0.05, 0.6),
                        read.decimal(base + "follow-range", 24, 4, 64),
                        read.decimal(base + "bite-chance", 1.0, 0, 1),
                        read.table(base + "guaranteed-loot", true),
                        read.table(base + "bonus-loot", false)));
            }
        }
        if (!archetypes.containsKey(WALKER)) read.errors.add("archetypes.walker : archétype obligatoire dans cette version.");
        if (spawning.minDistance() >= spawning.maxDistance())
            read.errors.add("spawning.min-distance doit être inférieure à spawning.max-distance.");
        if (spawning.maxDistance() >= spawning.despawnDistance())
            read.errors.add("spawning.max-distance doit être inférieure à spawning.despawn-distance.");
        int half = scenario.borderSize() / 2;
        if (Math.abs(refuge.centerX()) + refuge.radius() + 16 > half || Math.abs(refuge.centerZ()) + refuge.radius() + 16 > half)
            read.errors.add("refuge : le refuge et ses abords (16 blocs) doivent tenir dans la bordure du monde.");

        if (!read.errors.isEmpty()) throw new ConfigException(read.errors);
        return new ApocalypseConfig(scenario, refuge, infection, death, protectionMs, loot, spawning, archetypes, checkpointMs);
    }

    private static final class Reader {
        private final ConfigurationSection root;
        private final List<String> errors = new ArrayList<>();

        Reader(ConfigurationSection root) {
            this.root = root;
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
            if (required && entries.isEmpty()) errors.add(path + " : au moins un objet est obligatoire (butin garanti).");
            return new LootTable(entries);
        }
    }
}
