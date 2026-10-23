package org.duckcatmine.apocalypse.scenario;

public record RefugeArea(int centerX, int centerZ, int radius) {
    public boolean contains(int x, int z) {
        return near(x, z, 0);
    }

    public boolean near(int x, int z, int margin) {
        return Math.abs(x - centerX) <= radius + margin && Math.abs(z - centerZ) <= radius + margin;
    }
}
