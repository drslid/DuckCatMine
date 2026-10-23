package org.duckcatmine.apocalypse.scenario;

import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/** Flat ground with a street grid; buildings are placed by code, never by vanilla structures. */
public final class GroundZeroGenerator extends ChunkGenerator {
    public static final int SURFACE = 64;
    private static final int BLOCK = 64;
    private static final int ROAD = 5;
    private static final int ROAD_OFFSET = 30;

    @Override
    public void generateSurface(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        int min = info.getMinHeight();
        data.setRegion(0, min, 0, 16, min + 1, 16, Material.BEDROCK);
        data.setRegion(0, min + 1, 0, 16, SURFACE - 3, 16, Material.STONE);
        data.setRegion(0, SURFACE - 3, 0, 16, SURFACE, 16, Material.DIRT);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                boolean road = isRoad((chunkX << 4) + x, (chunkZ << 4) + z);
                data.setBlock(x, SURFACE, z, road ? Material.GRAY_CONCRETE : Material.GRASS_BLOCK);
            }
        }
    }

    static boolean isRoad(int x, int z) {
        return Math.floorMod(x - ROAD_OFFSET, BLOCK) < ROAD || Math.floorMod(z - ROAD_OFFSET, BLOCK) < ROAD;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return Biome.PLAINS;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(Biome.PLAINS);
            }
        };
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, SURFACE + 1, 0.5);
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
