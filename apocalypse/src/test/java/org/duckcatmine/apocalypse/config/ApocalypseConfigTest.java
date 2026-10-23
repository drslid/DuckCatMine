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
        assertEquals(0.10, config.loot().bonusRollChance(), 1e-9);
        ApocalypseConfig.Archetype walker = config.archetype(ApocalypseConfig.WALKER);
        assertFalse(walker.guaranteed().isEmpty());
        assertEquals(1.0, walker.biteChance());
        assertTrue(walker.speed() < 0.23, "a walker is slower than a vanilla zombie");
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
