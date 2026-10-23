package org.duckcatmine.apocalypse.scenario.city;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/** Street surfaces and furniture: road markings, lamps, abandoned cars, barricades and rubble. */
final class Streets {
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

    static void furnish(ChunkCanvas canvas, CityLayout layout, int x0, int z0) {
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
    private static void segment(ChunkCanvas canvas, CityLayout layout, int sx, int sz, boolean alongX, int y) {
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
        boolean burnt = Math.floorMod(h >>> 30, 7L) == 0;
        BlockData body = B.of(burnt ? Material.BLACKSTONE : CAR_COLORS[(int) Math.floorMod(h >>> 20, (long) CAR_COLORS.length)]);
        BlockData glass = burnt ? B.AIR : B.of(Math.floorMod(h >>> 33, 3L) == 0 ? Material.TINTED_GLASS : Material.GLASS);
        BlockData wheel = B.of(Material.COAL_BLOCK);
        for (int a = 0; a < 4; a++) {
            for (int b = 0; b < 2; b++) {
                int xx = alongX ? x + a : x + b, zz = alongX ? z + b : z + a;
                canvas.set(xx, y, zz, (a == 0 || a == 3) && !burnt ? wheel : body);
                canvas.set(xx, y + 1, zz, a == 1 || a == 2 ? glass : body);
                if (a == 1 || a == 2) canvas.set(xx, y + 2, zz, body);
            }
        }
    }

    private static void barricade(ChunkCanvas canvas, int sx, int sz, boolean alongX, long h, int y) {
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
