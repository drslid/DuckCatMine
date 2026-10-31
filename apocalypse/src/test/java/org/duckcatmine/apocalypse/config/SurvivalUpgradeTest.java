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
}
