package org.duckcatmine.apocalypse.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ApocalypseConfigTest {
    private static YamlConfiguration bundled() throws Exception {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(ApocalypseConfigTest.class.getResourceAsStream("/config.yml")),
                StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(text);
        return configuration;
    }

    @Test
    void shippedConfigurationUsesTheReferenceRules() throws Exception {
        ApocalypseConfig config = ApocalypseConfig.parse(bundled());
        assertEquals(60_000, config.infection().durationMs());
        assertFalse(config.infection().repeatedBitesResetTimer());
        assertEquals(2.0f, config.infection().antidoteUseSeconds());
        assertEquals(300_000, config.death().bagLifetimeMs());
        assertEquals(30_000, config.protectionMs());
        assertEquals(0.12, config.loot().bonusRollChance(), 1e-9);
        assertEquals(30 * 60_000L, config.search().refillMs());
        assertEquals(3072, config.scenario().borderSize());
        ApocalypseConfig.Archetype walker = config.archetype(ApocalypseConfig.WALKER);
        assertFalse(walker.guaranteed().isEmpty());
        assertTrue(walker.speed() < 0.23, "a walker is slower than a vanilla zombie");
        assertEquals(ApocalypseConfig.KNOWN_ARCHETYPES, config.archetypes().keySet(), "every known archetype is configured");
        double sum = 0;
        for (ApocalypseConfig.Archetype archetype : config.archetypes().values()) {
            assertTrue(archetype.biteChance() >= 0.2 && archetype.biteChance() <= 0.5, archetype.id() + " bite chance");
            assertFalse(archetype.guaranteed().isEmpty(), archetype.id() + " guaranteed loot");
            sum += archetype.biteChance();
        }
        assertEquals(0.33, sum / config.archetypes().size(), 0.04, "about one bite in three on average");
        assertTrue(config.archetype("runner").ability("sprint-speed", 0) > config.archetype("runner").speed());
        assertEquals(1, config.archetype("abomination").maxAlive());
        for (var place : org.duckcatmine.apocalypse.scenario.city.PlaceType.values())
            assertFalse(config.place(place.id()) == null || config.place(place.id()).loot().isEmpty(), place.id() + " has a loot table");
        for (var sector : org.duckcatmine.apocalypse.scenario.city.Sector.values()) {
            if (sector == org.duckcatmine.apocalypse.scenario.city.Sector.REFUGE) continue;
            assertTrue(config.sector(sector.id()) != null && !config.sector(sector.id()).mix().isEmpty(), sector.id() + " has a mix");
        }
        assertTrue(config.sector("starter").localCap() <= 3, "the starting district stays gentle");
        assertFalse(config.sector("starter").mix().containsKey("abomination"));
    }

    @Test
    void invalidValuesAreAllReported() throws Exception {
        ConfigException error = assertThrows(ConfigException.class, () -> ApocalypseConfig.parse(yaml("""
                infection:
                  duration-seconds: 5
                death:
                  bag-lifetime-seconds: '300'
                spawning:
                  min-distance: 40
                  max-distance: 32
                archetypes:
                  walker:
                    guaranteed-loot:
                      - {item: apocalypse:scrap, weight: -1}
                """)));
        String all = String.join("\n", error.errors());
        assertTrue(all.contains("infection.duration-seconds"), all);
        assertTrue(all.contains("death.bag-lifetime-seconds"), all);
        assertTrue(all.contains("spawning.min-distance"), all);
        assertTrue(all.contains("weight"), all);
        assertTrue(all.contains("guaranteed-loot : au moins un objet"), all);
    }

    @Test
    void walkerIsRequired() {
        ConfigException error = assertThrows(ConfigException.class, () -> ApocalypseConfig.parse(yaml("archetypes: {}\n")));
        assertTrue(String.join("\n", error.errors()).contains("archetypes.walker"));
    }

    @Test
    void unknownReferencesAreRejected() {
        ConfigException error = assertThrows(ConfigException.class, () -> ApocalypseConfig.parse(yaml("""
                archetypes:
                  walker:
                    appearances: [citizen, martian]
                    guaranteed-loot:
                      - {item: apocalypse:scrap, weight: 1}
                  dragon:
                    guaranteed-loot:
                      - {item: apocalypse:scrap, weight: 1}
                sectors:
                  downtown:
                    mix: {walker: 10, runner: 5}
                  moon:
                    mix: {walker: 1}
                places:
                  castle:
                    loot:
                      - {item: minecraft:bread, weight: 1}
                  pharmacy:
                    rolls-min: 4
                    rolls-max: 2
                    loot: []
                """)));
        String all = String.join("\n", error.errors());
        assertTrue(all.contains("martian"), all);
        assertTrue(all.contains("archetypes.dragon"), all);
        assertTrue(all.contains("sectors.downtown.mix.runner"), all);
        assertTrue(all.contains("sectors.moon"), all);
        assertTrue(all.contains("places.castle"), all);
        assertTrue(all.contains("rolls-min"), all);
        assertTrue(all.contains("places.pharmacy.loot"), all);
    }

    @Test
    void olderVersionsAreRefused() {
        ConfigException error = assertThrows(ConfigException.class, () -> ApocalypseConfig.parse(yaml("""
                config-version: 1
                archetypes:
                  walker:
                    guaranteed-loot:
                      - {item: apocalypse:scrap, weight: 1}
                """)));
        assertTrue(String.join("\n", error.errors()).contains("config-version"));
    }

    @Test
    void refugeMustFitInsideTheBorder() {
        ConfigException error = assertThrows(ConfigException.class, () -> ApocalypseConfig.parse(yaml("""
                scenario:
                  border-size: 128
                refuge:
                  center-x: 50
                archetypes:
                  walker:
                    guaranteed-loot:
                      - {item: apocalypse:scrap, weight: 1}
                """)));
        assertTrue(String.join("\n", error.errors()).contains("refuge"));
    }
}
