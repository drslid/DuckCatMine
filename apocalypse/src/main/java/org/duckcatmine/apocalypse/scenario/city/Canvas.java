package org.duckcatmine.apocalypse.scenario.city;

import org.bukkit.block.data.BlockData;

/**
 * Write-only target for generated structures. Renderers never read back, so the same code can build a chunk
 * or only report where its searchable containers are.
 */
interface Canvas {
    void set(int x, int y, int z, BlockData data);

    /** Inclusive box, corners in any order. */
    void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockData data);

    void container(int x, int y, int z, BlockData data, PlaceType place);

    /**
     * A parked car on a 4 x 2 footprint starting at (x, z), long side along x or z, standing at {@code y}. Cars are
     * entities added when the chunk is first loaded, so only canvases that list cars implement this.
     */
    default void vehicle(int x, int y, int z, boolean alongX, long seed) {
    }
}
