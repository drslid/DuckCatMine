package org.duckcatmine.apocalypse.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Targeted content migration. Other game rules and custom loot entries are preserved. */
public final class SurvivalUpgrade {
    private SurvivalUpgrade() {}
    public static boolean migrate(YamlConfiguration config, YamlConfiguration defaults) {
        config.setDefaults(defaults);
        int version = config.isSet("survival-content-version") ? config.getInt("survival-content-version") : 1;
        if (version >= 4) return false;
        if (version < 2) {
            for (String id : List.of("shotgun", "smg")) {
                String path = "recipes." + id;
                if (!config.isSet(path)) defaults.getConfigurationSection(path).getValues(true).forEach((key, value) -> {
                    if (!(value instanceof ConfigurationSection)) config.set(path + "." + key, value);
                });
            }
            for (String place : List.of("house", "shop", "supermarket", "police", "warehouse")) {
                String path = "places." + place;
                int oldMin = place.equals("supermarket") ? 2 : 1, oldMax = place.equals("supermarket") ? 4 : 3;
                if (config.getInt(path + ".rolls-min") == oldMin && config.getInt(path + ".rolls-max") == oldMax) {
                    config.set(path + ".rolls-min", defaults.getInt(path + ".rolls-min"));
                    config.set(path + ".rolls-max", defaults.getInt(path + ".rolls-max"));
                }
            }
            List<Map<?, ?>> loot = new ArrayList<>(config.getMapList("places.police.loot"));
            for (Map<?, ?> entry : defaults.getMapList("places.police.loot")) {
                if (!List.of("apocalypse:pistol", "apocalypse:shotgun", "apocalypse:smg").contains(entry.get("item"))) continue;
                if (loot.stream().noneMatch(old -> java.util.Objects.equals(old.get("item"), entry.get("item")))) loot.add(entry);
            }
            config.set("places.police.loot", loot);
        }
        String recipe = "recipes.auto_turret";
        if (version < 3 && !config.isSet(recipe)) defaults.getConfigurationSection(recipe).getValues(true).forEach((key, value) -> {
            if (!(value instanceof ConfigurationSection)) config.set(recipe + "." + key, value);
        });
        for (String path : List.of("noise.walk", "noise.block-break", "noise.block-place"))
            if (!config.isSet(path)) config.set(path, defaults.get(path));
        if (!config.isSet("perception.investigate-seconds") || config.getDouble("perception.investigate-seconds") == 20)
            config.set("perception.investigate-seconds", 30);
        config.set("survival-content-version", 4);
        return true;
    }
}
