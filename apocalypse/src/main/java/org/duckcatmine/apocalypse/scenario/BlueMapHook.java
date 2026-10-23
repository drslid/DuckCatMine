package org.duckcatmine.apocalypse.scenario;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.duckcatmine.apocalypse.DuckCatApocalypse;

/**
 * Shows Ground Zero on the server's BlueMap (the 3D map of the web panel) when BlueMap is installed: one map
 * configuration written once, then BlueMap reloads it. A rebuilt world purges the old render.
 */
public final class BlueMapHook {
    private BlueMapHook() {
    }

    public static boolean installed() {
        return Bukkit.getPluginManager().getPlugin("BlueMap") != null;
    }

    public static void ensure(DuckCatApocalypse plugin, World world, boolean rebuilt) {
        if (world == null || !installed() || !world.getName().matches("[a-z0-9_-]{1,32}")) return;
        File maps = new File(plugin.getDataFolder().getParentFile(), "BlueMap/maps");
        if (!maps.isDirectory()) return;
        File file = new File(maps, world.getName() + ".conf");
        boolean created = false;
        if (!file.exists()) {
            var refuge = plugin.settings().refuge();
            String conf = String.join("\n",
                    "# Ground Zero (DuckCatApocalypse), écrit une fois par le plugin ; modifiable.",
                    "world: \"" + Bukkit.getWorlds().getFirst().getName() + "\"",
                    "dimension: \"" + world.getKey().asString() + "\"",
                    "name: \"Ground Zero\"",
                    "sorting: 50",
                    "start-pos: { x: " + refuge.centerX() + ", z: " + refuge.centerZ() + " }",
                    "sky-color: \"#8e98a4\"",
                    "void-color: \"#000000\"",
                    "sky-light: 1",
                    "ambient-light: 0.1",
                    "remove-caves-below-y: 55",
                    "cave-detection-ocean-floor: -5",
                    "cave-detection-uses-block-light: false",
                    "min-inhabited-time: 0",
                    "render-mask: [",
                    "  {",
                    "  }",
                    "]",
                    "render-edges: true",
                    "edge-light-strength: 8",
                    "enable-perspective-view: true",
                    "enable-flat-view: true",
                    "enable-free-flight-view: true",
                    "enable-hires: true",
                    "storage: \"file\"",
                    "ignore-missing-light-data: false",
                    "marker-sets: {",
                    "}",
                    "");
            try {
                Files.writeString(file.toPath(), conf, StandardCharsets.UTF_8);
                created = true;
                plugin.getLogger().info("Carte BlueMap de Ground Zero ajoutée : " + file.getPath());
            } catch (IOException e) {
                plugin.getLogger().warning("Carte BlueMap non ajoutée : " + e.getMessage());
                return;
            }
        }
        String map = world.getName();
        if (rebuilt) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> console("bluemap purge " + map), 400L);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> console("bluemap update " + map), 600L);
        } else if (created) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> console("bluemap reload light"), 600L);
        }
    }

    private static void console(String command) {
        if (installed()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
