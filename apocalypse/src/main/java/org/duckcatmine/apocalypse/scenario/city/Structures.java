package org.duckcatmine.apocalypse.scenario.city;

import java.util.Random;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

/** Dispatcher and shared building kit. Renderers use only their own Random so any canvas sees the same structure. */
final class Structures {
    static final BlockData BARREL = B.facing(Material.BARREL, BlockFace.UP);

    private Structures() {
    }

    static void render(CityLayout.Placement p, Canvas world, CityLayout layout) {
        Frame f = new Frame(world, p.minX(), p.minZ(), p.width(), p.depth(), p.front());
        Random r = new Random(p.seed());
        int y = p.floorY();
        switch (p.kind()) {
            case HOUSE_SMALL -> Homes.house(f, r, y, 1, PlaceType.HOUSE);
            case HOUSE_LARGE -> Homes.house(f, r, y, 2, PlaceType.HOUSE);
            case FARMHOUSE -> Homes.house(f, r, y, 1, PlaceType.FARM);
            case APARTMENT -> Homes.apartment(f, r, y);
            case RUIN -> Homes.ruin(f, r, y);
            case YARD -> Homes.yard(f, r, y);
            case CABIN -> Rural.cabin(f, r, y, layout);
            case SHOP -> Shops.shop(f, r, y);
            case SUPERMARKET -> Shops.supermarket(f, r, y);
            case PHARMACY -> Shops.pharmacy(f, r, y);
            case GARAGE -> Shops.garage(f, r, y);
            case WAREHOUSE -> Shops.warehouse(f, r, y);
            case GAS_STATION -> Shops.gasStation(f, r, y, layout);
            case HOSPITAL -> Civic.hospital(f, r, y);
            case POLICE -> Civic.police(f, r, y);
            case PARK -> Civic.park(f, r, y);
            case PLAZA -> Civic.plaza(f, r, y);
            case FARM -> Rural.farm(f, r, y, layout);
            case LAB -> Rural.lab(f, r, y, layout);
            case CHECKPOINT -> Rural.checkpoint(f, r, y, layout);
        }
    }

    // ---------------------------------------------------------------- kit

    static void walls(Frame f, int w, int d, int y1, int y2, BlockData wall) {
        f.fill(0, y1, 0, w - 1, y2, 0, wall);
        f.fill(0, y1, d - 1, w - 1, y2, d - 1, wall);
        f.fill(0, y1, 0, 0, y2, d - 1, wall);
        f.fill(w - 1, y1, 0, w - 1, y2, d - 1, wall);
    }

    static void corners(Frame f, int w, int d, int y1, int y2, BlockData pillar) {
        f.fill(0, y1, 0, 0, y2, 0, pillar);
        f.fill(w - 1, y1, 0, w - 1, y2, 0, pillar);
        f.fill(0, y1, d - 1, 0, y2, d - 1, pillar);
        f.fill(w - 1, y1, d - 1, w - 1, y2, d - 1, pillar);
    }

    /** One window opening: glass, broken, boarded or barred. */
    static void window(Frame f, Random r, int u, int y, int v, boolean alongU, Material boards) {
        int roll = r.nextInt(100);
        BlockData data = roll < 58 ? B.pane(Material.GLASS_PANE, alongU) : roll < 80 ? B.AIR
                : roll < 93 ? B.of(boards) : B.pane(Material.IRON_BARS, alongU);
        f.set(u, y, v, data);
    }

    static void windows(Frame f, Random r, int w, int d, int y, int skipFrom, int skipTo, Material boards) {
        for (int u = 2; u <= w - 3; u += 2) {
            if (u < skipFrom || u > skipTo) window(f, r, u, y, 0, true, boards);
            window(f, r, u, y, d - 1, true, boards);
        }
        for (int v = 2; v <= d - 3; v += 2) {
            window(f, r, 0, y, v, false, boards);
            window(f, r, w - 1, y, v, false, boards);
        }
    }

    /** Front door at v = 0: closed, open or missing. */
    static void door(Frame f, Random r, int u, int y, Material door) {
        int roll = r.nextInt(100);
        if (roll < 25) {
            f.fill(u, y, 0, u, y + 1, 0, B.AIR);
            return;
        }
        boolean open = roll < 45;
        f.set(u, y, 0, B.door(door, BlockFace.NORTH, false, open));
        f.set(u, y + 1, 0, B.door(door, BlockFace.NORTH, true, open));
    }

    /** Gable roof with its ridge along u, starting on top of the walls at {@code y}. */
    static void gable(Frame f, int w, int d, int y, Material stairs, BlockData ridge, BlockData gableWall) {
        for (int k = 0; ; k++) {
            int front = k, back = d - 1 - k, level = y + k;
            if (front > back) break;
            if (front == back) {
                f.fill(0, level, front, w - 1, level, front, ridge);
                break;
            }
            f.fill(0, level, front, w - 1, level, front, B.stairs(stairs, BlockFace.SOUTH, false));
            f.fill(0, level, back, w - 1, level, back, B.stairs(stairs, BlockFace.NORTH, false));
            if (back - front > 1) {
                f.fill(0, level, front + 1, 0, level, back - 1, gableWall);
                f.fill(w - 1, level, front + 1, w - 1, level, back - 1, gableWall);
            }
        }
    }

    static void flatRoof(Frame f, int w, int d, int y, BlockData roof, BlockData parapet) {
        f.fill(0, y, 0, w - 1, y, d - 1, roof);
        if (parapet != null) walls(f, w, d, y + 1, y + 1, parapet);
    }

    static void chest(Frame f, int u, int y, int v, BlockFace facing, PlaceType place) {
        f.container(u, y, v, B.facing(Material.CHEST, facing), place);
    }

    static void barrel(Frame f, int u, int y, int v, PlaceType place) {
        f.container(u, y, v, BARREL, place);
    }

    /** Ladder against a wall; {@code facing} points away from that wall. */
    static void ladder(Frame f, int u, int y1, int y2, int v, BlockFace facing) {
        f.fill(u, y1, v, u, y2, v, B.facing(Material.LADDER, facing));
    }

    static void table(Frame f, Random r, int u, int y, int v) {
        f.set(u, y, v, B.of(Material.OAK_FENCE));
        f.set(u, y + 1, v, r.nextBoolean() ? B.of(Material.OAK_PRESSURE_PLATE) : B.of(Material.WHITE_CARPET));
        if (r.nextBoolean()) f.set(u - 1, y, v, B.stairs(Material.OAK_STAIRS, BlockFace.WEST, false));
        if (r.nextBoolean()) f.set(u + 1, y, v, B.stairs(Material.OAK_STAIRS, BlockFace.EAST, false));
    }

    static void bed(Frame f, int u, int y, int headV, int footV, Material color) {
        BlockFace facing = headV < footV ? BlockFace.NORTH : BlockFace.SOUTH;
        f.set(u, y, headV, B.bed(color, facing, true));
        f.set(u, y, footV, B.bed(color, facing, false));
    }

    /** Webs under a ceiling at {@code y}. */
    static void cobwebs(Frame f, Random r, int w, int d, int y, int count) {
        for (int k = 0; k < count; k++) {
            int u = 1 + r.nextInt(Math.max(1, w - 2)), v = 1 + r.nextInt(Math.max(1, d - 2));
            f.set(u, y, v, B.COBWEB);
        }
    }

    /** Holes and scorch marks on walls; never touches corners. */
    static void decay(Frame f, Random r, int w, int d, int y1, int y2, int holes) {
        boolean burnt = r.nextInt(100) < 12;
        int marks = holes + (burnt ? 6 : 0);
        for (int k = 0; k < marks; k++) {
            int y = y1 + r.nextInt(Math.max(1, y2 - y1 + 1));
            int side = r.nextInt(4);
            int u, v;
            if (side < 2) {
                u = 2 + r.nextInt(Math.max(1, w - 4));
                v = side == 0 ? d - 1 : 0;
            } else {
                v = 2 + r.nextInt(Math.max(1, d - 4));
                u = side == 2 ? 0 : w - 1;
            }
            if (Math.abs(u - w / 2) <= 1 && (v == 0 || v == d - 1)) continue;
            f.set(u, y, v, k < holes ? B.AIR : B.of(r.nextBoolean() ? Material.BLACKSTONE : Material.COAL_BLOCK));
        }
    }

    static void tree(Frame f, Random r, int u, int y, int v, Material log, Material leaves) {
        int height = 4 + r.nextInt(2);
        f.fill(u, y, v, u, y + height - 1, v, B.log(log, Axis.Y));
        BlockData leaf = B.leaves(leaves);
        for (int du = -2; du <= 2; du++) {
            for (int dv = -2; dv <= 2; dv++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (Math.abs(du) + Math.abs(dv) + Math.abs(dy) > 3 || (du == 0 && dv == 0 && dy < 1)) continue;
                    f.set(u + du, y + height - 1 + dy, v + dv, leaf);
                }
            }
        }
        f.set(u, y + height + 1, v, leaf);
    }

    /** A-frame tent five wide along u and {@code length} deep along v, open at its front end. */
    static void tent(Frame f, int u, int y, int v, int length, Material wool) {
        BlockData cloth = B.of(wool);
        int back = v + length - 1;
        f.fill(u, y, v, u, y, back, cloth);
        f.fill(u + 4, y, v, u + 4, y, back, cloth);
        f.fill(u + 1, y + 1, v, u + 1, y + 1, back, cloth);
        f.fill(u + 3, y + 1, v, u + 3, y + 1, back, cloth);
        f.fill(u + 2, y + 2, v, u + 2, y + 2, back, cloth);
        f.fill(u + 1, y, back, u + 3, y, back, cloth);
        f.set(u + 2, y + 1, back, cloth);
    }

    static void lamp(Frame f, int u, int y, int v) {
        f.fill(u, y, v, u, y + 2, v, B.of(Material.POLISHED_DEEPSLATE_WALL));
        f.set(u, y + 3, v, B.lantern(false));
    }

    static Material wool(Random r) {
        Material[] colors = {Material.RED_WOOL, Material.BLUE_WOOL, Material.GREEN_WOOL, Material.YELLOW_WOOL,
                Material.ORANGE_WOOL, Material.WHITE_WOOL, Material.CYAN_WOOL};
        return colors[r.nextInt(colors.length)];
    }

    static Material bedColor(Random r) {
        Material[] colors = {Material.RED_BED, Material.BLUE_BED, Material.WHITE_BED, Material.GREEN_BED,
                Material.LIGHT_GRAY_BED, Material.BROWN_BED};
        return colors[r.nextInt(colors.length)];
    }

    static Material carpet(Random r) {
        Material[] colors = {Material.RED_CARPET, Material.BLUE_CARPET, Material.BROWN_CARPET, Material.GRAY_CARPET,
                Material.GREEN_CARPET, Material.LIGHT_GRAY_CARPET};
        return colors[r.nextInt(colors.length)];
    }

    /** Levels a site: columns inside get {@code y} as ground, a margin ramps back to the natural terrain. */
    static void flatten(Frame f, CityLayout layout, int y, int margin) {
        Terrain terrain = layout.terrain();
        int w = f.width(), d = f.depth();
        for (int u = -margin; u < w + margin; u++) {
            for (int v = -margin; v < d + margin; v++) {
                int outside = Math.max(Math.max(-u, u - w + 1), Math.max(-v, v - d + 1));
                int natural = terrain.height(f.worldX(u, v), f.worldZ(u, v));
                int target = outside <= 0 ? y : (int) Math.round(y + (natural - y) * (outside / (double) (margin + 1)));
                if (natural > target) f.fill(u, target + 1, v, u, natural + 1, v, B.AIR);
                if (natural < target) f.fill(u, natural - 3, v, u, target - 1, v, B.DIRT);
                boolean road = outside > 0 && layout.countryRoad(f.worldX(u, v), f.worldZ(u, v));
                f.set(u, target, v, road ? B.of(Material.DIRT_PATH) : target <= Terrain.WATER_Y ? B.SAND : B.GRASS);
                f.fill(u, target + 1, v, u, target + 2, v, B.AIR);
            }
        }
    }
}
