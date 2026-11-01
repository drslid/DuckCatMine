package org.duckcatmine.apocalypse.config;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurvivalUpgradeTest {
    private YamlConfiguration defaults() { return YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/config.yml"),StandardCharsets.UTF_8)); }
    @Test void upgradesOldContentOnceWithoutReplacingCustomRules() throws Exception {
        var c = defaults(); c.set("survival-content-version",null); c.set("recipes.shotgun",null); c.set("recipes.smg",null);
        c.set("places.house.rolls-min",1); c.set("places.house.rolls-max",3);
        c.set("places.shop.rolls-min",11); c.set("places.shop.rolls-max",14);
        c.set("infection.duration-seconds",77);
        assertTrue(SurvivalUpgrade.migrate(c,defaults()));
        assertEquals(5,c.getInt("places.house.rolls-min")); assertEquals(11,c.getInt("places.shop.rolls-min"));
        assertEquals(77,c.getInt("infection.duration-seconds")); assertTrue(c.isSet("recipes.smg.result.item"));
        String first=c.saveToString(); assertFalse(SurvivalUpgrade.migrate(c,defaults())); assertEquals(first,c.saveToString());
        ApocalypseConfig.parse(c);
    }
    @Test void customWeaponRecipeIsPreserved() {
        var c=defaults(); c.set("survival-content-version",null); c.set("recipes.shotgun.usage","Mon réglage");
        SurvivalUpgrade.migrate(c,defaults()); assertEquals("Mon réglage",c.getString("recipes.shotgun.usage"));
    }
    @Test void partialProductionConfigKeepsDefaultsAndExplicitOverrides() throws Exception {
        var c=new YamlConfiguration(); c.set("config-version",2); c.set("spawning.global-cap",42);
        assertTrue(SurvivalUpgrade.migrate(c,defaults()));
        assertEquals(42,c.getInt("spawning.global-cap"));
        assertTrue(c.getMapList("places.police.loot").size() > 3);
        assertTrue(c.isSet("recipes.shotgun.result.item"));
        ApocalypseConfig.parse(c);
    }
    @Test void versionTwoOnlyAddsTurretRecipeAndKeepsLootAndCustomRecipes() throws Exception {
        var c=defaults(); c.set("survival-content-version",2); c.set("recipes.auto_turret",null);
        c.set("places.house.rolls-min",2); c.set("places.house.rolls-max",4);
        c.set("recipes.pistol.usage","Ma recette conservée");
        String loot=c.getMapList("places.police.loot").toString();
        assertTrue(SurvivalUpgrade.migrate(c,defaults()));
        assertEquals(5,c.getInt("survival-content-version"));
        assertEquals(2,c.getInt("places.house.rolls-min"));
        assertEquals(loot,c.getMapList("places.police.loot").toString());
        assertEquals("Ma recette conservée",c.getString("recipes.pistol.usage"));
        assertEquals("apocalypse:auto_turret",c.getString("recipes.auto_turret.result.item"));
        ApocalypseConfig.parse(c);
    }
    @Test void versionThreeOnlyUpgradesStealthDefaultsAndPreservesCustomRules() throws Exception {
        var c=defaults(); c.set("survival-content-version",3); c.set("perception.investigate-seconds",20);
        c.set("noise.walk",null); c.set("noise.block-break",7); c.set("spawning.global-cap",42);
        assertTrue(SurvivalUpgrade.migrate(c,defaults()));
        assertEquals(30,c.getDouble("perception.investigate-seconds"));
        assertEquals(5,c.getDouble("noise.walk")); assertEquals(7,c.getDouble("noise.block-break"));
        assertEquals(42,c.getInt("spawning.global-cap"));
        c.set("survival-content-version",3); c.set("perception.investigate-seconds",45);
        SurvivalUpgrade.migrate(c,defaults()); assertEquals(45,c.getDouble("perception.investigate-seconds"));
        ApocalypseConfig.parse(c);
    }
    @Test void distantSpawnsAndHorrorAreMigratedOnceWithoutChangingOtherRules() throws Exception {
        var c=defaults();c.set("survival-content-version",4);c.set("spawning.min-distance",20);
        c.set("spawning.max-distance",40);c.set("spawning.despawn-distance",72);c.set("archetypes.breaker.siege-power",14);
        c.set("archetypes.spitter",null);c.set("sectors.residential.mix.spitter",null);
        c.set("sectors.lab.mix.jumper",0);c.set("spawning.global-cap",42);
        assertTrue(SurvivalUpgrade.migrate(c,defaults()));
        var parsed=ApocalypseConfig.parse(c);
        assertEquals(150,parsed.spawning().minDistance());assertEquals(180,parsed.spawning().maxDistance());
        assertEquals(288,parsed.spawning().despawnDistance());assertEquals(28,parsed.archetype("breaker").siegePower());
        assertEquals(42,parsed.spawning().globalCap());assertFalse(parsed.sector("lab").mix().containsKey("jumper"));
        assertNotNull(parsed.archetype("spitter"));assertFalse(SurvivalUpgrade.migrate(c,defaults()));
        assertEquals(28,c.getDouble("archetypes.breaker.siege-power"));
    }
}
