package org.duckcatmine.apocalypse.scenario.city;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/** Street surfaces and furniture: road markings, lamps, trees, parked cars, barricades and rubble. */
public final class Streets {
    private static final Material[] CAR_COLORS = {Material.WHITE_CONCRETE, Material.BLACK_CONCRETE, Material.RED_CONCRETE,
            Material.BLUE_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.GREEN_CONCRETE, Material.YELLOW_CONCRETE,
            Material.CYAN_CONCRETE, Material.GRAY_CONCRETE, Material.BROWN_CONCRETE};
    private static final BlockData ASPHALT = B.of(Material.GRAY_CONCRETE);
    private static final BlockData LINE = B.of(Material.YELLOW_CONCRETE);
    private static final BlockData STRIPE = B.of(Material.WHITE_CONCRETE);
    private static final BlockData POST = B.of(Material.POLISHED_DEEPSLATE_WALL);
    private static final BlockData LAMP = B.lantern(false);

    private Streets() {
    }

    static BlockData road(int x, int z, int rx, int rz, int noise) {
        boolean crossing = rx <= CityLayout.ROAD_HALF && rz <= CityLayout.ROAD_HALF;
        if (!crossing) {
            boolean alongX = rz <= CityLayout.ROAD_HALF;
            int across = alongX ? rz : rx;
            int toCrossing = alongX ? rx : rz;
            int along = alongX ? x : z;
            int side = alongX ? z : x;
            if (toCrossing >= CityLayout.ROAD_HALF + 2 && toCrossing <= CityLayout.ROAD_HALF + 4) {
                if (Math.floorMod(side, 2) == 0 && noise > 120) return STRIPE;
            } else if (across == 0 && Math.floorMod(along, 6) < 3 && noise > 90) {
                return LINE;
            }
        }
        if (noise < 40) return B.of(Material.ANDESITE);
        if (noise < 58) return B.of(Material.COBBLESTONE);
        if (noise < 66) return B.of(Material.GRAVEL);
        if (noise < 72) return B.of(Material.COARSE_DIRT);
        return ASPHALT;
    }

    static BlockData sidewalk(int noise) {
        if (noise < 90) return B.of(Material.STONE_BRICKS);
        if (noise < 130) return B.of(Material.CRACKED_STONE_BRICKS);
        if (noise < 145) return B.of(Material.MOSSY_STONE_BRICKS);
        return B.of(Material.SMOOTH_STONE);
    }

    static void furnish(Canvas canvas, CityLayout layout, int x0, int z0) {
        int y = Terrain.CITY_Y + 1;
        long seed = layout.seed();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx, z = z0 + dz;
                if (!CityLayout.urban(x, z)) continue;
                int rx = CityLayout.roadDistance(x), rz = CityLayout.roadDistance(z);
                long h = CityLayout.mix(seed ^ 0x7A11L, x, z);
                boolean sidewalkX = rz == CityLayout.ROAD_HALF + 2 && rx > CityLayout.ROAD_HALF + 6;
                boolean sidewalkZ = rx == CityLayout.ROAD_HALF + 2 && rz > CityLayout.ROAD_HALF + 6;
                if ((sidewalkX && Math.floorMod(x, 24) == 11) || (sidewalkZ && Math.floorMod(z, 24) == 11)) {
                    canvas.fill(x, y, z, x, y + 2, z, POST);
                    if ((h & 7) > 2) canvas.set(x, y + 3, z, LAMP);
                    continue;
                }
                boolean onRoad = rx <= CityLayout.ROAD_HALF || rz <= CityLayout.ROAD_HALF;
                if (onRoad && (h & 1023) < 4) canvas.set(x, y, z, B.slab(Material.COBBLESTONE_SLAB, false));
            }
        }
        for (List<TreeBlock> tree : streetTrees(layout, x0, z0)) {
            for (TreeBlock block : tree) canvas.set(block.x(), block.y(), block.z(), block.data());
        }
        int half = CityLayout.CELL / 2;
        for (int line = Math.floorDiv(z0 - 4 - half, CityLayout.CELL); line <= Math.floorDiv(z0 + 19 - half, CityLayout.CELL); line++) {
            int zc = line * CityLayout.CELL + half;
            for (int seg = Math.floorDiv(x0 - 40 - half, CityLayout.CELL); seg <= Math.floorDiv(x0 + 40 - half, CityLayout.CELL); seg++)
                segment(canvas, layout, seg * CityLayout.CELL + half, zc, true, y);
        }
        for (int line = Math.floorDiv(x0 - 4 - half, CityLayout.CELL); line <= Math.floorDiv(x0 + 19 - half, CityLayout.CELL); line++) {
            int xc = line * CityLayout.CELL + half;
            for (int seg = Math.floorDiv(z0 - 40 - half, CityLayout.CELL); seg <= Math.floorDiv(z0 + 40 - half, CityLayout.CELL); seg++)
                segment(canvas, layout, xc, seg * CityLayout.CELL + half, false, y);
        }
    }

    /** One road stretch from a crossing at (sx, sz) to the next one along x or z. */
    private static void segment(Canvas canvas, CityLayout layout, int sx, int sz, boolean alongX, int y) {
        int mx = alongX ? sx + CityLayout.CELL / 2 : sx, mz = alongX ? sz : sz + CityLayout.CELL / 2;
        if (!CityLayout.urban(mx, mz) || Math.max(Math.abs(mx), Math.abs(mz)) > CityLayout.CITY_EDGE - 4) return;
        long h = CityLayout.mix(layout.seed() ^ (alongX ? 0xCA5L : 0xCA6L), sx, sz);
        Sector sector = layout.sectorAt(mx, mz);
        int chance = switch (sector) {
            case DOWNTOWN, MEDICAL -> 55;
            case INDUSTRIAL, STARTER -> 35;
            case OUTSKIRTS -> 15;
            default -> 28;
        };
        int roll = (int) Math.floorMod(h, 100L);
        if (roll < chance) {
            int offset = 8 + (int) Math.floorMod(h >>> 8, 28L);
            int lane = ((h >>> 16) & 1) == 0 ? -2 : 1;
            int cx = alongX ? sx + offset : sx + lane, cz = alongX ? sz + lane : sz + offset;
            car(canvas, cx, cz, alongX, h, y);
            if (roll < chance / 3) {
                int second = 8 + (int) Math.floorMod(h >>> 24, 28L);
                if (Math.abs(second - offset) > 5) {
                    int cx2 = alongX ? sx + second : sx - lane - 1, cz2 = alongX ? sz - lane - 1 : sz + second;
                    car(canvas, cx2, cz2, alongX, h >>> 3, y);
                }
            }
        }
        if ((sector == Sector.DOWNTOWN || sector == Sector.MEDICAL) && Math.floorMod(h >>> 40, 100L) < 9) barricade(canvas, sx, sz, alongX, h, y);
    }

    static void car(Canvas canvas, int x, int z, boolean alongX, long h, int y) {
        canvas.vehicle(x, y, z, alongX, h);
    }

    /** Paint of a parked car, from the seed the generator gives it (block cars of older chunks used the same rule). */
    public static Material carPaint(long seed) {
        return CAR_COLORS[(int) Math.floorMod(seed >>> 20, (long) CAR_COLORS.length)];
    }

    public static boolean carBurnt(long seed) {
        return Math.floorMod(seed >>> 30, 7L) == 0;
    }

    /** Blocks the generator used for cars before they became entities, so a loaded old chunk can swap them. */
    public static boolean legacyCarBlock(Material material) {
        if (material == Material.COAL_BLOCK || material == Material.BLACKSTONE || material == Material.GLASS || material == Material.TINTED_GLASS)
            return true;
        for (Material paint : CAR_COLORS) if (paint == material) return true;
        return false;
    }

    /** One block of a street tree, in world coordinates. */
    public record TreeBlock(int x, int y, int z, BlockData data) {
    }

    /**
     * Street trees on the curb side of sidewalks, between lamps and away from crossings, as lists of blocks. Every tree
     * whose canopy reaches the area [x0 - 2, x0 + 17] x [z0 - 2, z0 + 17] is returned.
     */
    public static List<List<TreeBlock>> streetTrees(CityLayout layout, int x0, int z0) {
        List<List<TreeBlock>> trees = new ArrayList<>();
        int half = CityLayout.CELL / 2;
        for (int line = Math.floorDiv(z0 - 8 - half, CityLayout.CELL); line <= Math.floorDiv(z0 + 23 - half, CityLayout.CELL); line++) {
            int zc = line * CityLayout.CELL + half;
            for (int side = -1; side <= 1; side += 2) {
                int tz = zc + side * (CityLayout.ROAD_HALF + 1);
                if (tz < z0 - 4 || tz > z0 + 19) continue;
                for (int tx = x0 - 4; tx <= x0 + 19; tx++) {
                    if (Math.floorMod(tx, 12) == 5) tree(trees, layout, tx, tz, 1, 0, 0, -side);
                }
            }
        }
        for (int line = Math.floorDiv(x0 - 8 - half, CityLayout.CELL); line <= Math.floorDiv(x0 + 23 - half, CityLayout.CELL); line++) {
            int xc = line * CityLayout.CELL + half;
            for (int side = -1; side <= 1; side += 2) {
                int tx = xc + side * (CityLayout.ROAD_HALF + 1);
                if (tx < x0 - 4 || tx > x0 + 19) continue;
                for (int tz = z0 - 4; tz <= z0 + 19; tz++) {
                    if (Math.floorMod(tz, 12) == 5) tree(trees, layout, tx, tz, 0, 1, -side, 0);
                }
            }
        }
        return trees;
    }

    /** {@code (ax, az)} runs along the street, {@code (rx, rz)} points from the trunk towards the road. */
    private static void tree(List<List<TreeBlock>> trees, CityLayout layout, int tx, int tz, int ax, int az, int rx, int rz) {
        int along = ax != 0 ? tx : tz;
        if (CityLayout.roadDistance(along) <= CityLayout.ROAD_HALF + 6 || !CityLayout.urban(tx, tz)) return;
        if (Math.max(Math.abs(tx), Math.abs(tz)) > CityLayout.CITY_EDGE - 2 || Math.max(Math.abs(tx), Math.abs(tz)) <= 40) return;
        long h = CityLayout.mix(layout.seed() ^ 0x7EE5L, tx, tz);
        int keep = switch (layout.sectorAt(tx, tz)) {
            case DOWNTOWN, MEDICAL -> 55;
            case INDUSTRIAL -> 25;
            default -> 80;
        };
        if (Math.floorMod(h, 100L) >= keep) return;
        int species = (int) Math.floorMod(h >>> 8, 20L);
        Material log = species < 10 ? Material.OAK_LOG : species < 15 ? Material.BIRCH_LOG : species < 18 ? Material.DARK_OAK_LOG : Material.SPRUCE_LOG;
        Material leaves = species < 10 ? Material.OAK_LEAVES : species < 15 ? Material.BIRCH_LEAVES : species < 18 ? Material.DARK_OAK_LEAVES : Material.SPRUCE_LEAVES;
        boolean dead = Math.floorMod(h >>> 16, 100L) < 15;
        int height = 4 + (int) Math.floorMod(h >>> 24, 2L);
        int y0 = Terrain.CITY_Y + 1;
        List<TreeBlock> blocks = new ArrayList<>();
        blocks.add(new TreeBlock(tx, Terrain.CITY_Y, tz, B.of(Material.ROOTED_DIRT)));
        BlockData trunk = B.log(log, org.bukkit.Axis.Y);
        for (int y = y0; y < y0 + height; y++) blocks.add(new TreeBlock(tx, y, tz, trunk));
        if (dead) {
            BlockData branch = B.log(log, ax != 0 ? org.bukkit.Axis.X : org.bukkit.Axis.Z);
            blocks.add(new TreeBlock(tx + ax, y0 + height - 2, tz + az, branch));
            blocks.add(new TreeBlock(tx + rx, y0 + height - 1, tz + rz, B.log(log, rx != 0 ? org.bukkit.Axis.X : org.bukkit.Axis.Z)));
            trees.add(blocks);
            return;
        }
        BlockData leaf = B.leaves(leaves);
        for (int layer = 0; layer < 2; layer++) {
            int y = y0 + height - 2 + layer;
            for (int a = -2; a <= 2; a++) {
                for (int b = -1; b <= 2; b++) {
                    if (a == 0 && b == 0) continue;
                    if (Math.abs(a) == 2 && (b == -1 || b == 2)) continue;
                    if (Math.floorMod(CityLayout.mix(h, a * 7 + layer, b), 7L) == 0) continue;
                    blocks.add(new TreeBlock(tx + a * ax + b * rx, y, tz + a * az + b * rz, leaf));
                }
            }
        }
        int top = y0 + height;
        int[][] crown = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}};
        for (int[] c : crown) blocks.add(new TreeBlock(tx + c[0] * ax + c[1] * rx, top, tz + c[0] * az + c[1] * rz, leaf));
        trees.add(blocks);
    }

    private static void barricade(Canvas canvas, int sx, int sz, boolean alongX, long h, int y) {
        int offset = 14 + (int) Math.floorMod(h >>> 45, 18L);
        int gap = (int) Math.floorMod(h >>> 50, 5L) - 2;
        BlockData wall = B.of(Material.COBBLESTONE_WALL);
        BlockData bars = B.pane(Material.IRON_BARS, !alongX);
        BlockData bags = B.of(Material.SANDSTONE_WALL);
        for (int k = -CityLayout.ROAD_HALF; k <= CityLayout.ROAD_HALF; k++) {
            if (k == gap || k == gap + 1) continue;
            int x = alongX ? sx + offset : sx + k, z = alongX ? sz + k : sz + offset;
            canvas.set(x, y, z, Math.floorMod(k, 3) == 0 ? bags : wall);
            if (Math.floorMod(k + offset, 2) == 0) canvas.set(x, y + 1, z, bars);
        }
    }
}
