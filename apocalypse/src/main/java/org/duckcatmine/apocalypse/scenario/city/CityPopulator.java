package org.duckcatmine.apocalypse.scenario.city;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

/** Countryside trees and underground ore veins; the city keeps only its planted trees. */
public final class CityPopulator extends BlockPopulator {
    private static final Material[] ORES = {Material.COAL_ORE, Material.COAL_ORE, Material.IRON_ORE, Material.IRON_ORE,
            Material.COPPER_ORE, Material.REDSTONE_ORE, Material.GOLD_ORE};

    @Override
    public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        CityLayout layout = CityLayout.of(info.getSeed());
        Terrain terrain = layout.terrain();
        int x0 = chunkX << 4, z0 = chunkZ << 4;
        for (int k = 0; k < 7; k++) {
            int x = x0 + random.nextInt(16), z = z0 + random.nextInt(16), y = info.getMinHeight() + 8 + random.nextInt(100);
            Material ore = ORES[random.nextInt(ORES.length)];
            if (ore == Material.GOLD_ORE && y > 20) continue;
            vein(region, random, x, y, z, ore, 3 + random.nextInt(6));
        }
        double density = terrain.forestDensity(x0 + 8, z0 + 8);
        int trees = density > 0.22 ? 4 + (int) Math.round(density * 8) : density > 0 ? 1 : random.nextInt(5) == 0 ? 1 : 0;
        for (int k = 0; k < trees; k++) {
            int x = x0 + random.nextInt(16), z = z0 + random.nextInt(16);
            if (CityLayout.urban(x, z) || nearRoad(layout, x, z) || nearPlacement(layout, x, z)) continue;
            int y = terrain.height(x, z);
            if (y <= Terrain.WATER_Y || !region.isInRegion(x, y + 1, z) || region.getType(x, y, z) != Material.GRASS_BLOCK) continue;
            region.generateTree(new Location(null, x, y + 1, z), random, treeType(terrain.biome(x, z), random));
        }
    }

    private static boolean nearRoad(CityLayout layout, int x, int z) {
        return layout.countryRoad(x, z) || layout.countryRoad(x + 3, z) || layout.countryRoad(x - 3, z)
                || layout.countryRoad(x, z + 3) || layout.countryRoad(x, z - 3);
    }

    private static boolean nearPlacement(CityLayout layout, int x, int z) {
        for (int dx = -6; dx <= 6; dx += 6) {
            for (int dz = -6; dz <= 6; dz += 6) if (!layout.placementsAt(x + dx, z + dz).isEmpty()) return true;
        }
        return false;
    }

    private static TreeType treeType(Biome biome, Random random) {
        if (biome == Biome.BIRCH_FOREST) return random.nextInt(4) == 0 ? TreeType.TREE : TreeType.BIRCH;
        if (biome == Biome.TAIGA) return random.nextInt(3) == 0 ? TreeType.TALL_REDWOOD : TreeType.REDWOOD;
        if (biome == Biome.SWAMP) return TreeType.SWAMP;
        if (biome == Biome.FOREST) return random.nextInt(5) == 0 ? TreeType.BIRCH : random.nextInt(8) == 0 ? TreeType.BIG_TREE : TreeType.TREE;
        return random.nextInt(6) == 0 ? TreeType.BIG_TREE : TreeType.TREE;
    }

    private static void vein(LimitedRegion region, Random random, int x, int y, int z, Material ore, int size) {
        for (int k = 0; k < size; k++) {
            if (!region.isInRegion(x, y, z)) return;
            Material current = region.getType(x, y, z);
            if (current == Material.STONE) region.setType(x, y, z, ore);
            else if (current == Material.DEEPSLATE) region.setType(x, y, z, deepslate(ore));
            switch (random.nextInt(6)) {
                case 0 -> x++;
                case 1 -> x--;
                case 2 -> y++;
                case 3 -> y--;
                case 4 -> z++;
                default -> z--;
            }
        }
    }

    private static Material deepslate(Material ore) {
        return switch (ore) {
            case COAL_ORE -> Material.DEEPSLATE_COAL_ORE;
            case IRON_ORE -> Material.DEEPSLATE_IRON_ORE;
            case COPPER_ORE -> Material.DEEPSLATE_COPPER_ORE;
            case REDSTONE_ORE -> Material.DEEPSLATE_REDSTONE_ORE;
            case GOLD_ORE -> Material.DEEPSLATE_GOLD_ORE;
            default -> ore;
        };
    }
}
