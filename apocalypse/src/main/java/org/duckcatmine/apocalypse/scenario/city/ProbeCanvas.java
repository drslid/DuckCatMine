package org.duckcatmine.apocalypse.scenario.city;

import org.bukkit.block.data.BlockData;

/** Replays a structure without writing blocks, to learn whether one position holds a searchable container. */
final class ProbeCanvas implements Canvas {
    private final int x;
    private final int y;
    private final int z;
    private PlaceType found;

    ProbeCanvas(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    PlaceType found() {
        return found;
    }

    @Override
    public void set(int x, int y, int z, BlockData data) {
        if (x == this.x && y == this.y && z == this.z) found = null;
    }

    @Override
    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockData data) {
        if (x >= Math.min(x1, x2) && x <= Math.max(x1, x2) && y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
                && z >= Math.min(z1, z2) && z <= Math.max(z1, z2)) found = null;
    }

    @Override
    public void container(int x, int y, int z, BlockData data, PlaceType place) {
        if (x == this.x && y == this.y && z == this.z) found = place;
    }
}
