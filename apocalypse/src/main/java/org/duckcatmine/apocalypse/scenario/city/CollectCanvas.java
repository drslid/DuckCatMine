package org.duckcatmine.apocalypse.scenario.city;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.block.data.BlockData;

/** Replays structures to list where their searchable containers end up (later writes erase earlier ones). */
final class CollectCanvas implements Canvas {
    record Spot(int x, int y, int z) {
    }

    private final Map<Spot, PlaceType> found = new LinkedHashMap<>();

    Map<Spot, PlaceType> found() {
        return found;
    }

    @Override
    public void set(int x, int y, int z, BlockData data) {
        found.remove(new Spot(x, y, z));
    }

    @Override
    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockData data) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2), ay = Math.min(y1, y2), by = Math.max(y1, y2);
        int az = Math.min(z1, z2), bz = Math.max(z1, z2);
        found.keySet().removeIf(s -> s.x() >= ax && s.x() <= bx && s.y() >= ay && s.y() <= by && s.z() >= az && s.z() <= bz);
    }

    @Override
    public void container(int x, int y, int z, BlockData data, PlaceType place) {
        Spot spot = new Spot(x, y, z);
        found.remove(spot);
        found.put(spot, place);
    }
}
