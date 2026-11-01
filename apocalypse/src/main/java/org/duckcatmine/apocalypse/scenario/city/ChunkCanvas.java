package org.duckcatmine.apocalypse.scenario.city;

import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;

/** Clips world-coordinate writes to one chunk being generated. */
final class ChunkCanvas implements Canvas {
    private final ChunkGenerator.ChunkData data;
    private final int minX;
    private final int minZ;
    private final int minY;
    private final int maxY;

    ChunkCanvas(ChunkGenerator.ChunkData data, int chunkX, int chunkZ) {
        this.data = data;
        this.minX = chunkX << 4;
        this.minZ = chunkZ << 4;
        this.minY = data.getMinHeight();
        this.maxY = data.getMaxHeight() - 1;
    }

    int minX() {
        return minX;
    }

    int minZ() {
        return minZ;
    }

    boolean touches(int x1, int z1, int x2, int z2) {
        return Math.max(x1, x2) >= minX && Math.min(x1, x2) <= minX + 15 && Math.max(z1, z2) >= minZ && Math.min(z1, z2) <= minZ + 15;
    }

    @Override
    public void set(int x, int y, int z, BlockData block) {
        if (x < minX || x > minX + 15 || z < minZ || z > minZ + 15 || y < minY || y > maxY) return;
        data.setBlock(x - minX, y, z - minZ, block);
    }

    @Override
    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockData block) {
        int ax = Math.max(Math.min(x1, x2), minX), bx = Math.min(Math.max(x1, x2), minX + 15);
        int az = Math.max(Math.min(z1, z2), minZ), bz = Math.min(Math.max(z1, z2), minZ + 15);
        int ay = Math.max(Math.min(y1, y2), minY), by = Math.min(Math.max(y1, y2), maxY);
        if (ax > bx || az > bz || ay > by) return;
        data.setRegion(ax - minX, ay, az - minZ, bx - minX + 1, by + 1, bz - minZ + 1, block);
    }

    @Override
    public void container(int x, int y, int z, BlockData block, PlaceType place) {
        set(x, y, z, block);
    }
}
