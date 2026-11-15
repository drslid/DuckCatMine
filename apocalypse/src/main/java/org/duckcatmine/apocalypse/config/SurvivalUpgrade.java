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
        if (version >= 9) return false;
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
        if(version<4){
            for (String path : List.of("noise.walk", "noise.block-break", "noise.block-place"))
                if (!config.isSet(path)) config.set(path, defaults.get(path));
            if (!config.isSet("perception.investigate-seconds") || config.getDouble("perception.investigate-seconds") == 20)
                config.set("perception.investigate-seconds", 30);
        }
        if(version<5){
            int min=Math.max(150,config.getInt("spawning.min-distance",150));
            int max=Math.max(min+16,Math.max(180,config.getInt("spawning.max-distance",180)));
            config.set("spawning.min-distance",min);config.set("spawning.max-distance",max);
            config.set("spawning.despawn-distance",Math.max(288,Math.max(max+64,config.getInt("spawning.despawn-distance",288))));
            double power=config.isSet("archetypes.breaker.siege-power")?config.getDouble("archetypes.breaker.siege-power"):14;
            config.set("archetypes.breaker.siege-power",Math.min(500,power*2));
            for(String id:List.of("spitter","jumper","crawler","stalker","bloated")){
                String path="archetypes."+id;
                if(!config.isSet(path))copy(config,defaults,path);
            }
            // Relative existing weights stay intact. Explicitly disabled types (weight 0) stay disabled.
            for(String sector:List.of("residential","downtown","industrial","medical","outskirts","countryside","lab"))
                for(String id:List.of("spitter","jumper","crawler","stalker","bloated")){
                    String path="sectors."+sector+".mix."+id;
                    if(!config.isSet(path))config.set(path,defaults.getDouble(path));
                }
        }
        if(version<6){
            if(!config.isSet("recipes.rocket_launcher"))copy(config,defaults,"recipes.rocket_launcher");
            for(String place:List.of("military","lab")){
                String path="places."+place+".loot";List<Map<?,?>> loot=new ArrayList<>(config.getMapList(path));
                if(loot.stream().noneMatch(e->"apocalypse:rocket_launcher".equals(e.get("item"))))loot.add(Map.of("item","apocalypse:rocket_launcher","weight",1));
                config.set(path,loot);
            }
        }
        if(version<7){
            config.set("spawning.min-distance",110);config.set("spawning.max-distance",190);config.set("spawning.despawn-distance",200);
            if(config.getDouble("spawning.indoor-chance")==0)config.set("spawning.indoor-chance",.3);
            if(config.getInt("spawning.global-cap")==60||config.getInt("spawning.global-cap")==80)config.set("spawning.global-cap",120);
            if(config.getDouble("spawning.density")==1.5)config.set("spawning.density",2.5);
            if(config.getInt("spawning.per-tick")==2)config.set("spawning.per-tick",3);
        }
        if(version<8){
            // More infected and the stealth rules asked for: 20 s of silence, walking heard nearby, running heard from far away.
            // Only the previous reference values move; an administrator's own tuning stays.
            if(config.getDouble("spawning.density")==2.5)config.set("spawning.density",3.5);
            if(config.getInt("spawning.global-cap")==120)config.set("spawning.global-cap",200);
            if(config.getInt("spawning.per-tick")==3)config.set("spawning.per-tick",4);
            if(config.getDouble("perception.investigate-seconds")==30)config.set("perception.investigate-seconds",20);
            if(config.getDouble("noise.walk")==5)config.set("noise.walk",8);
            if(config.getDouble("noise.sprint")==10)config.set("noise.sprint",32);
            if(config.getDouble("pistol.range")==40)config.set("pistol.range",80);
            for(String place:List.of("house","shop","supermarket","police","garage","warehouse","gas_station","farm","lab","military")){
                String path="places."+place+".loot";
                if(!config.isSet(path))continue;
                List<Map<?,?>> loot=new ArrayList<>(config.getMapList(path));
                for(Map<?,?> entry:defaults.getMapList(path))
                    if(loot.stream().noneMatch(old->java.util.Objects.equals(old.get("item"),entry.get("item"))))loot.add(entry);
                config.set(path,loot);
            }
            for(String path:List.of("places.armory","places.airdrop","places.cache","recipes.rocket","archetypes.nemesis","archetypes.smiler"))
                if(!config.isSet(path))copy(config,defaults,path);
            for(String id:List.of("pistol","shotgun","smg","rocket_launcher","ammo")){
                String usage=config.getString("recipes."+id+".usage","");
                if(usage.contains("ans munitions")||usage.startsWith("10 roquettes")||usage.startsWith("Petit lot"))
                    config.set("recipes."+id+".usage",defaults.getString("recipes."+id+".usage"));
            }
            if(config.getInt("recipes.ammo.result.amount",8)==8)config.set("recipes.ammo.result.amount",12);
            for(String id:List.of("walker","pursuer")){
                String path="archetypes."+id+".appearances";
                if(!config.isSet(path))continue;
                List<String> looks=new ArrayList<>(config.getStringList(path));
                for(String look:List.of("engineer","scientist","guard","explorer"))if(!looks.contains(look))looks.add(look);
                config.set(path,looks);
            }
            for(String sector:List.of("residential","downtown","industrial","medical","outskirts","countryside","lab")){
                String path="sectors."+sector+".mix.smiler";
                if(!config.isSet(path)&&defaults.isSet(path))config.set(path,defaults.getDouble(path));
            }
        }
        if (version < 9) {
            if (config.getInt("death.bag-lifetime-seconds", 300) == 300) config.set("death.bag-lifetime-seconds", 600);
            if (config.getDouble("perception.investigate-seconds", 20) == 20) config.set("perception.investigate-seconds", 30);
        }
        config.set("survival-content-version", 9);
        return true;
    }
    private static void copy(YamlConfiguration target,YamlConfiguration defaults,String path){
        if(!defaults.isConfigurationSection(path))return;
        defaults.getConfigurationSection(path).getValues(true).forEach((key,value)->{
            if(!(value instanceof ConfigurationSection))target.set(path+"."+key,value);
        });
    }
}
