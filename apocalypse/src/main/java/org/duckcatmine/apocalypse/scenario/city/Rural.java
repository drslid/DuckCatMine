package org.duckcatmine.apocalypse.scenario.city;

import java.util.Random;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

/** Countryside sites: cabins, farms, road checkpoints and the NEXUS laboratory. */
final class Rural {
    private Rural() {
    }

    static void cabin(Frame f, Random r, int y0, CityLayout layout) {
        Structures.flatten(f, layout, y0, 4);
        Frame c = f.sub(2, 2, 7, 7);
        int w = 7, d = 7;
        c.fill(0, y0 - 1, 0, w - 1, y0 - 1, d - 1, B.of(Material.COBBLESTONE));
        c.fill(0, y0, 0, w - 1, y0, d - 1, B.of(Material.SPRUCE_PLANKS));
        c.fill(0, y0 + 1, 0, w - 1, y0 + 3, 0, B.log(Material.SPRUCE_LOG, Axis.X));
        c.fill(0, y0 + 1, d - 1, w - 1, y0 + 3, d - 1, B.log(Material.SPRUCE_LOG, Axis.X));
        c.fill(0, y0 + 1, 0, 0, y0 + 3, d - 1, B.log(Material.SPRUCE_LOG, Axis.Z));
        c.fill(w - 1, y0 + 1, 0, w - 1, y0 + 3, d - 1, B.log(Material.SPRUCE_LOG, Axis.Z));
        Structures.corners(c, w, d, y0 + 1, y0 + 3, B.log(Material.STRIPPED_SPRUCE_LOG, Axis.Y));
        c.fill(1, y0 + 1, 1, w - 2, y0 + 3, d - 2, B.AIR);
        Structures.gable(c, w, d, y0 + 4, Material.SPRUCE_STAIRS, B.of(Material.SPRUCE_PLANKS), B.of(Material.SPRUCE_PLANKS));
        Structures.door(c, r, 3, y0 + 1, Material.SPRUCE_DOOR);
        Structures.window(c, r, 0, y0 + 2, 3, false, Material.SPRUCE_PLANKS);
        Structures.window(c, r, w - 1, y0 + 2, 3, false, Material.SPRUCE_PLANKS);
        Structures.window(c, r, 3, y0 + 2, d - 1, true, Material.SPRUCE_PLANKS);
        int y = y0 + 1;
        Structures.bed(c, 1, y, 5, 4, Structures.bedColor(r));
        c.set(2, y, 5, B.facing(Material.FURNACE, BlockFace.NORTH));
        c.set(1, y, 1, B.of(Material.CRAFTING_TABLE));
        Structures.chest(c, 5, y, 5, BlockFace.NORTH, PlaceType.HOUSE);
        if (r.nextInt(100) < 60) Structures.barrel(c, 5, y, 1, PlaceType.HOUSE);
        c.set(3, y, 3, B.of(Structures.carpet(r)));
        Structures.cobwebs(c, r, w, d, y0 + 3, 1 + r.nextInt(2));
        f.fill(9, y, 3, 9, y, 6, B.log(Material.SPRUCE_LOG, Axis.Z));
        if (r.nextBoolean()) f.fill(9, y + 1, 4, 9, y + 1, 5, B.log(Material.SPRUCE_LOG, Axis.Z));
        f.set(1, y, 1, B.unlit(Material.CAMPFIRE));
        f.set(9, y, 8, B.log(Material.STRIPPED_OAK_LOG, Axis.Y));
    }

    static void farm(Frame f, Random r, int y0, CityLayout layout) {
        Structures.flatten(f, layout, y0, 6);
        int w = f.width(), d = f.depth(), mid = w / 2;
        BlockData fence = B.of(Material.OAK_FENCE);
        for (int u = 0; u < w; u++) {
            if (Math.abs(u - mid) > 2 && r.nextInt(100) < 90) f.set(u, y0 + 1, 0, fence);
            if (r.nextInt(100) < 90) f.set(u, y0 + 1, d - 1, fence);
        }
        for (int v = 1; v < d - 1; v++) {
            if (r.nextInt(100) < 90) f.set(0, y0 + 1, v, fence);
            if (r.nextInt(100) < 90) f.set(w - 1, y0 + 1, v, fence);
        }
        f.fill(mid - 1, y0, 0, mid + 1, y0, d - 3, B.of(Material.DIRT_PATH));
        Homes.house(f.sub(3, 4, 9, 9), r, y0, 1, PlaceType.FARM);
        barn(f.sub(w - 18, 3, 15, 12), r, y0);
        Material[] crops = {Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS};
        int fieldStart = 19;
        for (int plot = 0; plot < 2; plot++) {
            int u1 = plot == 0 ? 2 : mid + 3, u2 = plot == 0 ? mid - 3 : w - 3;
            BlockData crop = B.crop(crops[r.nextInt(crops.length)]);
            for (int v = fieldStart; v <= d - 3; v++) {
                boolean channel = (v - fieldStart) % 9 == 4;
                f.fill(u1, y0, v, u2, y0, v, channel ? B.WATER : B.farmland());
                if (!channel) f.fill(u1, y0 + 1, v, u2, y0 + 1, v, r.nextInt(100) < 80 ? crop : B.AIR);
            }
        }
        int su = 6 + r.nextInt(8), sv = fieldStart + 6 + r.nextInt(10);
        if ((sv - fieldStart) % 9 == 4) sv++;
        f.set(su, y0 + 1, sv, B.of(Material.OAK_FENCE));
        f.set(su, y0 + 2, sv, B.of(Material.HAY_BLOCK));
        f.set(su, y0 + 3, sv, B.facing(Material.CARVED_PUMPKIN, BlockFace.NORTH));
        if (r.nextInt(100) < 70) Streets.car(f, mid, fieldStart + 1, false, r.nextLong(), y0 + 1);
        f.set(mid + 3, y0 + 1, 15, B.of(Material.COMPOSTER));
        f.set(mid + 4, y0 + 1, 15, B.of(Material.HAY_BLOCK));
        if (r.nextInt(100) < 50) Structures.barrel(f, mid - 3, y0 + 1, 15, PlaceType.FARM);
    }

    private static void barn(Frame b, Random r, int y0) {
        int w = b.width(), d = b.depth();
        BlockData wall = B.of(Material.RED_TERRACOTTA);
        b.fill(0, y0 + 1, 0, w - 1, y0 + 13, d - 1, B.AIR);
        b.fill(0, y0, 0, w - 1, y0, d - 1, B.of(Material.COARSE_DIRT));
        Structures.walls(b, w, d, y0 + 1, y0 + 5, wall);
        Structures.corners(b, w, d, y0 + 1, y0 + 5, B.log(Material.STRIPPED_OAK_LOG, Axis.Y));
        b.fill(0, y0 + 5, 0, w - 1, y0 + 5, 0, B.log(Material.STRIPPED_OAK_LOG, Axis.X));
        b.fill(w / 2 - 2, y0 + 1, 0, w / 2 + 2, y0 + 4, 0, B.AIR);
        Structures.gable(b, w, d, y0 + 6, Material.DARK_OAK_STAIRS, B.of(Material.DARK_OAK_PLANKS), wall);
        for (int v = 2; v <= d - 3; v += 3) {
            b.set(0, y0 + 3, v, B.AIR);
            b.set(w - 1, y0 + 3, v, B.AIR);
        }
        BlockData hay = B.of(Material.HAY_BLOCK);
        b.fill(1, y0 + 1, d - 3, 4, y0 + 2, d - 2, hay);
        b.fill(1, y0 + 3, d - 2, 3, y0 + 3, d - 2, hay);
        b.fill(w - 5, y0 + 1, 1, w - 2, y0 + 1, 2, hay);
        for (int v = 4; v <= d - 4; v += 3) {
            b.set(w - 4, y0 + 1, v, B.of(Material.OAK_FENCE));
            b.set(w - 3, y0 + 1, v, B.of(Material.OAK_FENCE));
        }
        Structures.chest(b, w - 2, y0 + 1, d - 2, BlockFace.NORTH, PlaceType.FARM);
        Structures.barrel(b, 1, y0 + 1, 2, PlaceType.FARM);
        if (r.nextBoolean()) Structures.chest(b, 5, y0 + 1, d - 2, BlockFace.NORTH, PlaceType.FARM);
        b.set(w / 2, y0 + 1, d - 2, B.of(Material.GRINDSTONE));
        Structures.cobwebs(b, r, w, d, y0 + 5, 2 + r.nextInt(3));
    }

    static void checkpoint(Frame f, Random r, int y0, CityLayout layout) {
        Structures.flatten(f, layout, y0, 6);
        int w = f.width(), d = f.depth(), mid = w / 2;
        f.fill(mid - 2, y0, 0, mid + 2, y0, d - 1, B.GRAVEL);
        f.fill(mid - 1, y0, 0, mid + 1, y0, d - 1, B.of(Material.DIRT_PATH));
        BlockData bags = B.of(Material.SANDSTONE_WALL);
        for (int v : new int[]{4, d - 5}) {
            for (int u = 0; u < w; u++) {
                if (Math.abs(u - mid) <= 1) continue;
                f.set(u, y0 + 1, v, bags);
                if (r.nextInt(3) > 0) f.set(u, y0 + 2, v, bags);
            }
        }
        f.set(mid - 2, y0 + 1, 3, B.open(Material.SPRUCE_FENCE_GATE, BlockFace.NORTH, true));
        Structures.tent(f, 1, y0 + 1, 7, 6, Material.GREEN_WOOL);
        Structures.tent(f, w - 6, y0 + 1, 7, 6, Material.GREEN_WOOL);
        Structures.barrel(f, 3, y0 + 1, 11, PlaceType.MILITARY);
        f.set(2, y0 + 1, 8, B.bed(Material.GREEN_BED, BlockFace.SOUTH, true));
        f.set(2, y0 + 1, 7, B.bed(Material.GREEN_BED, BlockFace.SOUTH, false));
        if (r.nextInt(100) < 65) Structures.chest(f, w - 4, y0 + 1, 11, BlockFace.NORTH, PlaceType.MILITARY);
        Civic.truck(f, mid + 3, 6, false, y0 + 1, r.nextInt(100) < 40);
        Structures.lamp(f, 0, y0 + 1, 5);
        Structures.lamp(f, w - 1, y0 + 1, d - 6);
        if (r.nextBoolean()) Structures.barrel(f, mid - 4, y0 + 1, d - 4, PlaceType.MILITARY);
        f.fill(mid - 3, y0 + 1, 14, mid - 3, y0 + 1, 15, B.of(Material.SPRUCE_PLANKS));
    }

    static void lab(Frame f, Random r, int y0, CityLayout layout) {
        Structures.flatten(f, layout, y0, 8);
        int w = f.width(), d = f.depth(), mid = w / 2;
        f.fill(0, y0, 0, w - 1, y0, d - 1, B.of(Material.POLISHED_ANDESITE));
        for (int k = 0; k < 14; k++) {
            int u = 2 + r.nextInt(w - 6), v = 2 + r.nextInt(d - 6);
            f.fill(u, y0, v, u + 2, y0, v + 1, B.of(r.nextBoolean() ? Material.GRAVEL : Material.COARSE_DIRT));
        }
        f.fill(1, y0 + 1, 1, w - 2, y0 + 8, d - 2, B.AIR);
        BlockData wall = B.of(Material.STONE_BRICKS);
        Structures.walls(f, w, d, y0 + 1, y0 + 3, wall);
        f.fill(0, y0 + 4, 0, w - 1, y0 + 4, 0, B.pane(Material.IRON_BARS, true));
        f.fill(0, y0 + 4, d - 1, w - 1, y0 + 4, d - 1, B.pane(Material.IRON_BARS, true));
        f.fill(0, y0 + 4, 0, 0, y0 + 4, d - 1, B.pane(Material.IRON_BARS, false));
        f.fill(w - 1, y0 + 4, 0, w - 1, y0 + 4, d - 1, B.pane(Material.IRON_BARS, false));
        f.fill(mid - 3, y0 + 1, 0, mid + 2, y0 + 4, 0, B.AIR);
        for (int u : new int[]{mid - 4, mid + 3}) {
            for (int y = y0 + 1; y <= y0 + 4; y++) f.set(u, y, 0, B.of((y & 1) == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE));
        }
        int breach = 6 + r.nextInt(10);
        f.fill(breach, y0 + 1, d - 1, breach + 3, y0 + 4, d - 1, B.AIR);
        for (int k = 0; k < 6; k++) f.set(breach - 1 + r.nextInt(6), y0 + 1, d - 2 - r.nextInt(3), B.of(Material.COBBLESTONE_SLAB));
        Frame booth = f.sub(mid + 5, 2, 5, 5);
        booth.fill(0, y0 + 1, 0, 4, y0 + 3, 4, B.of(Material.WHITE_CONCRETE));
        booth.fill(1, y0 + 1, 1, 3, y0 + 3, 3, B.AIR);
        booth.fill(0, y0 + 4, 0, 4, y0 + 4, 4, B.slab(Material.SMOOTH_STONE_SLAB, false));
        booth.fill(0, y0 + 2, 1, 0, y0 + 2, 3, B.pane(Material.GLASS_PANE, false));
        booth.fill(1, y0 + 2, 0, 3, y0 + 2, 0, B.pane(Material.GLASS_PANE, true));
        booth.fill(2, y0 + 1, 4, 2, y0 + 2, 4, B.AIR);
        Structures.barrel(booth, 3, y0 + 1, 3, PlaceType.MILITARY);
        building(f.sub(17, 18, 30, 22), r, y0);
        int hu = 5, hv = d - 18;
        f.fill(hu, y0, hv, hu + 12, y0, hv + 12, B.of(Material.YELLOW_CONCRETE));
        f.fill(hu + 1, y0, hv + 1, hu + 11, y0, hv + 11, B.of(Material.GRAY_CONCRETE));
        BlockData mark = B.of(Material.WHITE_CONCRETE);
        f.fill(hu + 4, y0, hv + 3, hu + 4, y0, hv + 9, mark);
        f.fill(hu + 8, y0, hv + 3, hu + 8, y0, hv + 9, mark);
        f.fill(hu + 4, y0, hv + 6, hu + 8, y0, hv + 6, mark);
        Civic.truck(f, w - 12, 8, false, y0 + 1, r.nextBoolean());
        Civic.truck(f, w - 8, 9, false, y0 + 1, true);
        Structures.barrel(f, w - 10, y0 + 1, 16, PlaceType.MILITARY);
        if (r.nextBoolean()) Structures.barrel(f, w - 9, y0 + 1, 16, PlaceType.MILITARY);
        for (int[] spot : new int[][]{{3, 3}, {w - 4, 3}, {3, d - 4}, {w - 4, d - 4}}) Structures.lamp(f, spot[0], y0 + 1, spot[1]);
    }

    /** Main NEXUS building: two floors of labs around a corridor, roof access by ladder. */
    private static void building(Frame b, Random r, int y0) {
        int w = b.width(), d = b.depth(), h = 5, top = y0 + 2 * h, mid = w / 2;
        BlockData wall = B.of(Material.WHITE_CONCRETE);
        BlockData base = B.of(Material.GRAY_CONCRETE);
        BlockData floor = B.of(Material.POLISHED_DIORITE);
        b.fill(0, y0 + 1, 0, w - 1, top + 2, d - 1, B.AIR);
        b.fill(0, y0, 0, w - 1, y0, d - 1, floor);
        Structures.walls(b, w, d, y0 + 1, top - 1, wall);
        Structures.walls(b, w, d, y0 + 1, y0 + 1, base);
        b.fill(0, y0 + h, 0, w - 1, y0 + h, d - 1, base);
        b.fill(1, y0 + h, 1, w - 2, y0 + h, d - 2, floor);
        int c1 = 9, c2 = 12;
        for (int level = 0; level < 2; level++) {
            int fy = y0 + level * h, y = fy + 1, ceiling = fy + h - 1;
            for (int u = 2; u <= w - 3; u += 2) {
                for (int yy = fy + 2; yy <= fy + 3; yy++) {
                    labWindow(b, r, u, yy, 0, true);
                    labWindow(b, r, u, yy, d - 1, true);
                }
            }
            for (int v = 2; v <= d - 3; v += 3) {
                if (v >= c1 && v <= c2) continue;
                for (int yy = fy + 2; yy <= fy + 3; yy++) {
                    labWindow(b, r, 0, yy, v, false);
                    labWindow(b, r, w - 1, yy, v, false);
                }
            }
            b.fill(1, y, c1, w - 2, ceiling, c1, wall);
            b.fill(1, y, c2, w - 2, ceiling, c2, wall);
            for (int pu : new int[]{10, 19}) {
                b.fill(pu, y, 1, pu, ceiling, c1 - 1, wall);
                b.fill(pu, y, c2 + 1, pu, ceiling, d - 2, wall);
            }
            int[][] rooms = {{1, 9}, {11, 18}, {20, w - 2}};
            for (int k = 0; k < rooms.length; k++) {
                int u1 = rooms[k][0], u2 = rooms[k][1], door = (u1 + u2) / 2;
                b.fill(door, y, c1, door, y + 1, c1, B.AIR);
                b.fill(door, y, c2, door, y + 1, c2, B.AIR);
                if (level == 0 && k == 1) continue;
                labRoom(b, r, u1, u2, y, 1, c1 - 1, true, level);
                labRoom(b, r, u1, u2, y, c2 + 1, d - 2, false, level);
            }
            Structures.cobwebs(b, r, w, d, ceiling, 3 + r.nextInt(4));
            for (int k = 0; k < 4; k++) if (r.nextInt(2) == 0) b.set(2 + r.nextInt(w - 4), y, c1 + 1 + r.nextInt(2), B.of(Material.RED_CARPET));
        }
        b.fill(mid - 2, y0 + 1, 0, mid + 1, y0 + 3, 0, B.AIR);
        for (int y = y0 + 1; y <= y0 + 4; y++) {
            BlockData stripe = B.of((y & 1) == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE);
            b.set(mid - 3, y, 0, stripe);
            b.set(mid + 2, y, 0, stripe);
        }
        b.fill(mid - 2, y0 + 4, 0, mid + 1, y0 + 4, 0, B.of(Material.YELLOW_CONCRETE));
        b.fill(mid - 2, y0 + 1, 4, mid + 1, y0 + 2, 4, B.pane(Material.IRON_BARS, true));
        b.set(mid - 1, y0 + 1, 4, B.AIR);
        b.set(mid - 1, y0 + 2, 4, B.AIR);
        b.set(mid, y0 + 1, 4, B.AIR);
        Structures.chest(b, 12, y0 + 1, 1, BlockFace.SOUTH, PlaceType.LAB);
        Structures.decay(b, r, w, d, y0 + 2, top - 1, 3 + r.nextInt(4));
        b.fill(0, y0 + 1, c1 + 1, 0, top - 1, c1 + 1, wall);
        Structures.flatRoof(b, w, d, top, B.of(Material.SMOOTH_STONE), B.of(Material.ANDESITE_WALL));
        Structures.ladder(b, 1, y0 + 1, top, c1 + 1, BlockFace.EAST);
        b.fill(w - 6, top + 1, d - 6, w - 3, top + 2, d - 3, B.of(Material.IRON_BLOCK));
        b.fill(4, top + 1, 4, 5, top + 1, 5, B.of(Material.LIGHT_GRAY_CONCRETE));
    }

    private static void labWindow(Frame b, Random r, int u, int y, int v, boolean alongU) {
        int roll = r.nextInt(100);
        b.set(u, y, v, roll < 45 ? B.of(Material.TINTED_GLASS) : roll < 70 ? B.pane(Material.GLASS_PANE, alongU) : roll < 90 ? B.AIR
                : B.pane(Material.IRON_BARS, alongU));
    }

    private static void labRoom(Frame b, Random r, int u1, int u2, int y, int v1, int v2, boolean front, int level) {
        int wallV = front ? v1 : v2;
        BlockData counter = B.of(Material.WHITE_CONCRETE);
        BlockData top = B.slab(Material.SMOOTH_QUARTZ_SLAB, false);
        for (int u = u1; u <= u2; u++) {
            if (r.nextInt(100) < 75) {
                b.set(u, y, wallV, counter);
                b.set(u, y + 1, wallV, top);
            }
        }
        b.set(u1 + 1, y + 1, wallV, B.of(Material.BREWING_STAND));
        if (r.nextBoolean()) b.set(u2 - 1, y + 1, wallV, B.of(Material.BREWING_STAND));
        int midV = (v1 + v2) / 2;
        b.set(u1, y, midV, B.of(Material.CAULDRON));
        if (r.nextInt(3) == 0) b.set(u2, y, midV, B.of(level == 0 ? Material.BLUE_ICE : Material.BOOKSHELF));
        int roll = r.nextInt(100);
        if (roll < 55) Structures.barrel(b, u2, y, front ? v2 : v1, PlaceType.LAB);
        else if (roll < 80) Structures.chest(b, u2, y, front ? v2 : v1, BlockFace.WEST, PlaceType.LAB);
        if (r.nextInt(3) == 0) b.set(u1 + 2, y, midV, B.of(Material.RED_CARPET));
        if (r.nextInt(4) == 0) b.set(u1 + 3, y, midV + 1, B.of(Material.IRON_BARS));
    }
}
