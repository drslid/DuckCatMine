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
    void newTunablesHaveSafeDefaults() throws Exception {
        ApocalypseConfig config = ApocalypseConfig.parse(bundled());
        assertEquals(1.0, config.infection().biteMultiplier(), 1e-9);
        assertEquals(1.5, config.spawning().density(), 1e-9);
        assertEquals(2, config.spawning().perTick());
        assertEquals(10, config.play().leaveSeconds());
        assertEquals(0.15, config.play().fauna().spawnShare(), 1e-9);
        assertTrue(config.play().vehicles().enabled());
        assertTrue(config.play().vehicles().maxSpeedRoad() > config.play().vehicles().maxSpeedOffroad());
        assertEquals(3.5, config.play().explosives().fuseSeconds(), 1e-9);
        for (String recipe : java.util.List.of("pipe_bomb", "molotov", "fire_axe", "bow", "arrows", "jerrycan"))
            assertTrue(config.play().recipes().containsKey(recipe), recipe + " can be crafted");
        assertTrue(config.play().weapons().get("fire_axe").damage() > config.play().weapons().get("heavy_club").damage());
    }

    @Test
    void medicalBoostMultipliesOnlyAntidoteParts() throws Exception {
        YamlConfiguration plain = bundled();
        plain.set("loot.medical-boost", 1.0);
        YamlConfiguration boosted = bundled();
        boosted.set("loot.medical-boost", 3.0);
        var before = ApocalypseConfig.parse(plain).place("pharmacy").loot().entries();
        var after = ApocalypseConfig.parse(boosted).place("pharmacy").loot().entries();
        for (int k = 0; k < before.size(); k++) {
            double factor = ApocalypseConfig.MEDICAL_ITEMS.contains(before.get(k).item()) ? 3 : 1;
            assertEquals(before.get(k).weight() * factor, after.get(k).weight(), 1e-9, before.get(k).item());
        }
    }

    @Test
    void outOfRangeTunablesAreRefused() throws Exception {
        YamlConfiguration broken = bundled();
        broken.set("spawning.density", 9);
        broken.set("infection.bite-multiplier", -1);
        broken.set("session.leave-seconds", 500);
        String errors = String.join("\n", assertThrows(ConfigException.class, () -> ApocalypseConfig.parse(broken)).errors());
        assertTrue(errors.contains("spawning.density"), errors);
        assertTrue(errors.contains("infection.bite-multiplier"), errors);
        assertTrue(errors.contains("session.leave-seconds"), errors);
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
    void anOlderFileKeepsItsValuesAndGainsNewSections() throws Exception {
        YamlConfiguration old = yaml("""
                config-version: 2
                archetypes:
                  walker:
                    bite-chance: 0.2
                """);
        old.setDefaults(bundled());
        ApocalypseConfig config = ApocalypseConfig.parse(old);
        assertEquals(0.2, config.archetype("walker").biteChance(), 1e-9);
        assertFalse(config.archetype("walker").guaranteed().isEmpty(), "missing keys come from the bundled defaults");
        assertTrue(config.archetypes().containsKey(ApocalypseConfig.INFECTED_WOLF), "archetypes added by an update are kept");
        assertEquals(bundledRecipes(), config.play().recipes().keySet());
        assertEquals(3, config.play().recipes().get("ammo").ingredients().size());
        assertEquals(7, config.play().siege().resistance().size(), "siege resistances come from the defaults");
        assertEquals(5, config.play().threat().cycleSeconds().size());
        assertTrue(config.play().fauna().species().contains("wolf"));
    }

    @Test
    void siegeThreatAndFaunaRules() throws Exception {
        ApocalypseConfig config = ApocalypseConfig.parse(bundled());
        var siege = config.play().siege();
        assertTrue(siege.resistance().get("reinforced") > siege.resistance().get("barricade"), "a reinforced barricade holds longer");
        assertTrue(siege.resistance().get("stone") > siege.resistance().get("wood"));
        assertTrue(siege.maxBlocksPerSecond() > 0 && siege.maxAttackersPerBlock() > 0);
        var threat = config.play().threat();
        assertTrue(threat.cycleFactors().get("relief") < 1 && threat.cycleFactors().get("peak") > 1, "relief eases, the peak presses");
        assertTrue(threat.hordeMin() <= threat.hordeMax());
        var fauna = config.play().fauna();
        assertTrue(fauna.incubationMinSeconds() >= 30 && fauna.incubationMaxSeconds() <= 60, "visible incubation of 30 to 60 s");
        assertTrue(config.archetype(ApocalypseConfig.INFECTED_WOLF).siegePower() < config.archetype("breaker").siegePower());

        YamlConfiguration broken = yaml("""
                config-version: 2
                threat:
                  horde-min: 9
                  horde-max: 2
                fauna:
                  species: [wolf, dragon]
                """);
        broken.setDefaults(bundled());
        String errors = String.join("\n", assertThrows(ConfigException.class, () -> ApocalypseConfig.parse(broken)).errors());
        assertTrue(errors.contains("horde-min"), errors);
        assertTrue(errors.contains("dragon"), errors);
    }

    private static java.util.Set<String> bundledRecipes() throws Exception {
        return ApocalypseConfig.parse(bundled()).play().recipes().keySet();
    }

    @Test
    void walkerIsRequired() {
        ConfigException error = assertThrows(ConfigException.class, () -> ApocalypseConfig.parse(yaml("archetypes: {}\n")));
        assertTrue(String.join("\n", error.errors()).contains("archetypes.walker"));
    }

    @Test
    void craftingCatalogueCoversTheMinimalRecipes() throws Exception {
        ApocalypseConfig config = ApocalypseConfig.parse(bundled());
        var recipes = config.play().recipes();
        for (String id : java.util.List.of("club", "blade", "spear", "heavy_club", "workshop", "medical_station", "pistol", "ammo",
                "bandage", "antidote", "barricade", "barricade_reinforced", "decoy", "spike_trap"))
            assertTrue(recipes.containsKey(id), id);
        assertEquals("workshop", recipes.get("pistol").station());
        assertEquals("medical", recipes.get("antidote").station());
        assertEquals(6, config.play().pistol().magazine());
        assertEquals(2.5, config.play().pistol().reloadSeconds(), 1e-9);
        assertTrue(config.play().weapons().get("spear").reach() > 3, "the spear reaches further");
        for (var kit : config.play().kit().weaponKits().entrySet()) {
            var recipe = recipes.get(kit.getKey());
            assertEquals("workbench", recipe.station(), "a start-kit weapon is made on a plain workbench");
            for (var ingredient : recipe.ingredients().entrySet()) {
                int have = kit.getValue().stream().filter(stack -> stack.item().equals(ingredient.getKey())
                        || ingredient.getKey().equals("#planks") && stack.item().endsWith("_planks")).mapToInt(ApocalypseConfig.Stack::amount).sum();
                assertTrue(have >= ingredient.getValue(), kit.getKey() + " kit lacks " + ingredient.getKey());
            }
        }
        for (var entry : config.play().kit().loot().entries())
            assertFalse(ApocalypseConfig.RARE_ITEMS.contains(entry.item()), entry.item() + " is rare");
        assertTrue(config.play().kit().minPreviousLifeMs() > 0, "dying on purpose does not stack start kits");
    }

    @Test
    void rareItemsAreRefusedInTheStartKit() {
        ConfigException error = assertThrows(ConfigException.class, () -> ApocalypseConfig.parse(yaml("""
                archetypes:
                  walker:
                    guaranteed-loot:
                      - {item: apocalypse:scrap, weight: 1}
                start-kit:
                  loot:
                    - {item: apocalypse:antidote, weight: 1}
                recipes:
                  broken:
                    station: garage
                    result: {item: apocalypse:club}
                    ingredients: {"bad id": 2}
                """)));
        String all = String.join("\n", error.errors());
        assertTrue(all.contains("objet rare"), all);
        assertTrue(all.contains("recipes.broken.station"), all);
        assertTrue(all.contains("ingrédient invalide"), all);
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
