package org.duckcatmine.apocalypse.scenario.city;

import org.bukkit.block.Biome;
import org.bukkit.util.noise.SimplexOctaveGenerator;

/** Height and biome functions: a flat city plateau that blends into hills, forests and lakes. */
public final class Terrain {
    public static final int CITY_Y = 64;
    public static final int WATER_Y = 62;
    static final int BLEND = 160;

    private final SimplexOctaveGenerator hills;
    private final SimplexOctaveGenerator detail;
    private final SimplexOctaveGenerator forest;
    private final SimplexOctaveGenerator moisture;
    private final int cityEdge;

    Terrain(long seed, int cityEdge) {
        this.cityEdge = cityEdge;
        hills = new SimplexOctaveGenerator(seed ^ 0x5DEECE66DL, 4);
        hills.setScale(1 / 260.0);
        detail = new SimplexOctaveGenerator(seed * 31 + 7, 2);
        detail.setScale(1 / 48.0);
        forest = new SimplexOctaveGenerator(seed * 17 + 11, 3);
        forest.setScale(1 / 170.0);
        moisture = new SimplexOctaveGenerator(seed * 13 + 23, 2);
        moisture.setScale(1 / 340.0);
    }

    /** 1 inside the city, 0 in open country, smooth in between. */
    double cityWeight(int x, int z) {
        double distance = Math.max(Math.abs(x), Math.abs(z));
        if (distance <= cityEdge) return 1;
        double t = Math.min(1, (distance - cityEdge) / BLEND);
        return 1 - t * t * (3 - 2 * t);
    }

    double natural(int x, int z) {
        double base = hills.noise(x, z, 0.5, 0.5, true);
        double ridges = 1 - Math.abs(hills.noise(x + 4000, z - 4000, 0.5, 0.5, true));
        return 65 + base * 20 + ridges * ridges * 10 - 5 + detail.noise(x, z, 0.5, 0.5, true) * 2.5;
    }

    /** Y of the top solid block. */
    public int height(int x, int z) {
        double w = cityWeight(x, z);
        if (w >= 1) return CITY_Y;
        return (int) Math.round(natural(x, z) * (1 - w) + CITY_Y * w);
    }

    double forestDensity(int x, int z) {
        return forest.noise(x, z, 0.5, 0.5, true);
    }

    double moisture(int x, int z) {
        return moisture.noise(x, z, 0.5, 0.5, true);
    }

    public Biome biome(int x, int z) {
        if (cityWeight(x, z) > 0.5) return Biome.PLAINS;
        if (height(x, z) <= WATER_Y) return Biome.RIVER;
        double trees = forestDensity(x, z), wet = moisture(x, z);
        if (trees > 0.22) return wet > 0.3 ? Biome.BIRCH_FOREST : wet < -0.35 ? Biome.TAIGA : Biome.FOREST;
        if (wet > 0.5) return Biome.SWAMP;
        return trees < -0.4 ? Biome.MEADOW : Biome.PLAINS;
    }
}
