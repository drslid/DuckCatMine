package org.duckcatmine.apocalypse.scenario;

import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.duckcatmine.apocalypse.scenario.city.CityLayout;
import org.duckcatmine.apocalypse.scenario.city.CityPopulator;
import org.duckcatmine.apocalypse.scenario.city.Terrain;

/** Ground Zero: a ruined city around the refuge, then hills, forests, farms and the NEXUS site. Never vanilla structures. */
public final class GroundZeroGenerator extends ChunkGenerator {
    public static final int SURFACE = Terrain.CITY_Y;

    @Override
    public void generateSurface(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        CityLayout.of(info.getSeed()).generate(data, chunkX, chunkZ);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        Terrain terrain = CityLayout.of(info.getSeed()).terrain();
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return terrain.biome(x, z);
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(Biome.PLAINS, Biome.RIVER, Biome.FOREST, Biome.BIRCH_FOREST, Biome.TAIGA, Biome.SWAMP, Biome.MEADOW);
            }
        };
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(new CityPopulator());
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
