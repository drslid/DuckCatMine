package org.duckcatmine.apocalypse.scenario.city;

import java.util.IdentityHashMap;
import java.util.Map;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;

/**
 * Local frame of one structure: u runs along the street, v goes from the front wall (v = 0, facing the street)
 * to the back. Structures are authored with the front facing north; the frame rotates positions and block data.
 */
final class Frame implements Canvas {
    private final Canvas out;
    private final int originX;
    private final int originZ;
    private final int width;
    private final int depth;
    private final BlockFace front;
    private final StructureRotation rotation;
    private final Map<BlockData, BlockData> turned = new IdentityHashMap<>();

    Frame(Canvas out, int minX, int minZ, int width, int depth, BlockFace front) {
        this.out = out;
        this.originX = minX;
        this.originZ = minZ;
        this.width = width;
        this.depth = depth;
        this.front = front;
        this.rotation = switch (front) {
            case EAST -> StructureRotation.CLOCKWISE_90;
            case SOUTH -> StructureRotation.CLOCKWISE_180;
            case WEST -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
    }

    /** World size along x of a footprint {@code width} wide along the street and {@code depth} deep. */
    static int spanX(int width, int depth, BlockFace front) {
        return front == BlockFace.EAST || front == BlockFace.WEST ? depth : width;
    }

    static int spanZ(int width, int depth, BlockFace front) {
        return front == BlockFace.EAST || front == BlockFace.WEST ? width : depth;
    }

    int width() {
        return width;
    }

    int depth() {
        return depth;
    }

    BlockFace front() {
        return front;
    }

    /** Nested frame at local offset (du, dv) with the same orientation, for a building inside a larger site. */
    Frame sub(int du, int dv, int subWidth, int subDepth) {
        Frame parent = this;
        Canvas shifted = new Canvas() {
            @Override
            public void set(int x, int y, int z, BlockData data) {
                parent.set(x + du, y, z + dv, data);
            }

            @Override
            public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockData data) {
                parent.fill(x1 + du, y1, z1 + dv, x2 + du, y2, z2 + dv, data);
            }

            @Override
            public void container(int x, int y, int z, BlockData data, PlaceType place) {
                parent.container(x + du, y, z + dv, data, place);
            }

            @Override
            public void vehicle(int x, int y, int z, boolean alongX, long seed) {
                parent.vehicle(x + du, y, z + dv, alongX, seed);
            }
        };
        return new Frame(shifted, 0, 0, subWidth, subDepth, BlockFace.NORTH);
    }

    int worldX(int u, int v) {
        return switch (front) {
            case EAST -> originX + depth - 1 - v;
            case SOUTH -> originX + width - 1 - u;
            case WEST -> originX + v;
            default -> originX + u;
        };
    }

    int worldZ(int u, int v) {
        return switch (front) {
            case EAST -> originZ + u;
            case SOUTH -> originZ + depth - 1 - v;
            case WEST -> originZ + width - 1 - u;
            default -> originZ + v;
        };
    }

    @Override
    public void set(int u, int y, int v, BlockData data) {
        out.set(worldX(u, v), y, worldZ(u, v), turn(data));
    }

    @Override
    public void fill(int u1, int y1, int v1, int u2, int y2, int v2, BlockData data) {
        out.fill(worldX(u1, v1), y1, worldZ(u1, v1), worldX(u2, v2), y2, worldZ(u2, v2), turn(data));
    }

    @Override
    public void container(int u, int y, int v, BlockData data, PlaceType place) {
        out.container(worldX(u, v), y, worldZ(u, v), turn(data), place);
    }

    @Override
    public void vehicle(int u, int y, int v, boolean alongU, long seed) {
        int x1 = worldX(u, v), z1 = worldZ(u, v);
        int x2 = worldX(u + (alongU ? 3 : 1), v + (alongU ? 1 : 3)), z2 = worldZ(u + (alongU ? 3 : 1), v + (alongU ? 1 : 3));
        out.vehicle(Math.min(x1, x2), y, Math.min(z1, z2), Math.abs(x2 - x1) == 3, seed);
    }

    private BlockData turn(BlockData data) {
        if (rotation == StructureRotation.NONE) return data;
        return turned.computeIfAbsent(data, original -> {
            BlockData copy = original.clone();
            copy.rotate(rotation);
            return copy;
        });
    }
}
