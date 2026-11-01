package org.duckcatmine.apocalypse.scenario.city;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.block.data.BlockData;

/** Final block state of one bounded building. Used only by a maintenance job, never per chunk tick. */
final class BlueprintCanvas implements Canvas {
    record Pos(int x, int y, int z) {}
    final Map<Pos, BlockData> blocks = new LinkedHashMap<>();
    final Map<Pos, PlaceType> containers = new LinkedHashMap<>();
    @Override public void set(int x, int y, int z, BlockData data) {
        Pos pos = new Pos(x, y, z); blocks.put(pos, data); containers.remove(pos);
    }
    @Override public void fill(int ax, int ay, int az, int bx, int by, int bz, BlockData data) {
        for (int x = Math.min(ax, bx); x <= Math.max(ax, bx); x++)
            for (int y = Math.min(ay, by); y <= Math.max(ay, by); y++)
                for (int z = Math.min(az, bz); z <= Math.max(az, bz); z++) set(x, y, z, data);
    }
    @Override public void container(int x, int y, int z, BlockData data, PlaceType place) {
        set(x, y, z, data); containers.put(new Pos(x, y, z), place);
    }
}
