package org.duckcatmine.apocalypse.scenario.city;

import java.util.Random;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

/** Hospital, police station, parks and the refuge square. */
final class Civic {
    private Civic() {
    }

    static void hospital(Frame f, Random r, int y0) {
        int w = f.width(), d = f.depth();
        BlockData wall = B.of(Material.WHITE_CONCRETE);
        BlockData stripe = B.of(Material.LIGHT_BLUE_CONCRETE);
        BlockData floor = B.of(Material.SMOOTH_QUARTZ);
        int floors = 3, h = 5, top = y0 + floors * h;
        int corridor = d / 2;
        int[] starts = {1, 8, 14, 20, 26};
        int[] ends = {6, 12, 18, 24, w - 2};
        f.fill(0, y0 + 1, 0, w - 1, top + 2, d - 1, B.AIR);
        f.fill(0, y0, 0, w - 1, y0, d - 1, floor);
        Structures.walls(f, w, d, y0 + 1, top - 1, wall);
        for (int level = 0; level < floors; level++) {
            int fy = y0 + level * h, y = fy + 1, ceiling = fy + h - 1;
            if (level > 0) {
                f.fill(0, fy, 0, w - 1, fy, d - 1, stripe);
                f.fill(1, fy, 1, w - 2, fy, d - 2, floor);
            }
            for (int u = 2; u <= w - 3; u += 3) {
                for (int yy = fy + 2; yy <= fy + 3; yy++) {
                    Structures.window(f, r, u, yy, 0, true, Material.WHITE_CONCRETE);
                    Structures.window(f, r, u, yy, d - 1, true, Material.WHITE_CONCRETE);
                }
            }
            for (int v = 2; v <= d - 3; v += 3) {
                for (int yy = fy + 2; yy <= fy + 3; yy++) {
                    Structures.window(f, r, 0, yy, v, false, Material.WHITE_CONCRETE);
                    Structures.window(f, r, w - 1, yy, v, false, Material.WHITE_CONCRETE);
                }
            }
            f.fill(1, y, corridor - 2, w - 2, ceiling, corridor - 2, wall);
            f.fill(1, y, corridor + 2, w - 2, ceiling, corridor + 2, wall);
            for (int k = 1; k < starts.length; k++) {
                int pu = starts[k] - 1;
                f.fill(pu, y, 1, pu, ceiling, corridor - 3, wall);
                f.fill(pu, y, corridor + 3, pu, ceiling, d - 2, wall);
            }
            for (int k = 0; k < starts.length; k++) {
                int door = (starts[k] + ends[k]) / 2;
                f.fill(door, y, corridor - 2, door, y + 1, corridor - 2, B.AIR);
                f.fill(door, y, corridor + 2, door, y + 1, corridor + 2, B.AIR);
                if (level > 0 || k == 0 || k == starts.length - 1) ward(f, r, starts[k], ends[k], y, 1, corridor - 3, true);
                if (level == 0 && k == 2) storage(f, r, starts[k], ends[k], y, corridor + 3, d - 2);
                else ward(f, r, starts[k], ends[k], y, corridor + 3, d - 2, false);
            }
            if (r.nextBoolean()) f.set(w / 2, ceiling, corridor, B.lantern(true));
            Structures.cobwebs(f, r, w, d, ceiling, 2 + r.nextInt(4));
            for (int k = 0; k < 3; k++) if (r.nextInt(3) == 0) f.set(2 + r.nextInt(w - 4), y, corridor - 1 + r.nextInt(3), B.of(Material.RED_CARPET));
        }
        int y = y0 + 1;
        f.fill(starts[1], y, 1, ends[3], y0 + h - 1, corridor - 2, B.AIR);
        f.fill(starts[1] + 3, y, 6, ends[3] - 3, y, 6, B.of(Material.QUARTZ_BLOCK));
        f.fill(starts[1] + 3, y + 1, 6, ends[3] - 3, y + 1, 6, B.slab(Material.QUARTZ_SLAB, false));
        f.set(w / 2, y, 6, B.AIR);
        f.set(w / 2, y + 1, 6, B.AIR);
        Structures.chest(f, starts[1] + 4, y, 8, BlockFace.NORTH, PlaceType.HOSPITAL);
        for (int u = starts[1] + 1; u <= ends[3] - 1; u += 4) f.set(u, y, 3, B.stairs(Material.QUARTZ_STAIRS, BlockFace.NORTH, false));
        f.fill(w / 2 - 1, y, 0, w / 2 + 1, y + 2, 0, B.AIR);
        BlockData cross = B.of(Material.RED_CONCRETE);
        f.fill(w / 2, top - 6, 0, w / 2, top - 2, 0, cross);
        f.fill(w / 2 - 2, top - 4, 0, w / 2 + 2, top - 4, 0, cross);
        Structures.decay(f, r, w, d, y0 + 1, top - 1, 2 + r.nextInt(4));
        f.fill(0, y0 + 1, corridor - 1, 0, top - 1, corridor - 1, wall);
        f.fill(w - 1, y0 + 1, corridor - 1, w - 1, top - 1, corridor - 1, wall);
        Structures.flatRoof(f, w, d, top, B.of(Material.SMOOTH_STONE), B.of(Material.DIORITE_WALL));
        Structures.ladder(f, 1, y0 + 1, top, corridor - 1, BlockFace.EAST);
        Structures.ladder(f, w - 2, y0 + 1, top, corridor - 1, BlockFace.WEST);
        int cu = w / 2, cv = corridor;
        f.fill(cu - 5, top, cv - 5, cu + 5, top, cv + 5, B.of(Material.YELLOW_CONCRETE));
        f.fill(cu - 4, top, cv - 4, cu + 4, top, cv + 4, B.of(Material.GRAY_CONCRETE));
        BlockData mark = B.of(Material.WHITE_CONCRETE);
        f.fill(cu - 2, top, cv - 2, cu - 2, top, cv + 2, mark);
        f.fill(cu + 2, top, cv - 2, cu + 2, top, cv + 2, mark);
        f.fill(cu - 2, top, cv, cu + 2, top, cv, mark);
        if (r.nextInt(100) < 70) Structures.chest(f, cu + 4, top + 1, cv + 4, BlockFace.WEST, PlaceType.HOSPITAL);
    }

    /** Patient room between u1..u2; the bed head touches the outer wall (v1 when {@code front}, else v2). */
    private static void ward(Frame f, Random r, int u1, int u2, int y, int v1, int v2, boolean front) {
        int outer = front ? v1 : v2, inner = front ? v1 + 1 : v2 - 1;
        f.set(u1 + 1, y, outer, B.bed(Material.WHITE_BED, front ? BlockFace.NORTH : BlockFace.SOUTH, true));
        f.set(u1 + 1, y, inner, B.bed(Material.WHITE_BED, front ? BlockFace.NORTH : BlockFace.SOUTH, false));
        if (u2 - u1 >= 5 && r.nextBoolean()) {
            f.set(u2 - 1, y, outer, B.bed(Material.LIGHT_BLUE_BED, front ? BlockFace.NORTH : BlockFace.SOUTH, true));
            f.set(u2 - 1, y, inner, B.bed(Material.LIGHT_BLUE_BED, front ? BlockFace.NORTH : BlockFace.SOUTH, false));
        }
        int roll = r.nextInt(100);
        int mid = (v1 + v2) / 2;
        if (roll < 34) Structures.barrel(f, u2, y, outer, PlaceType.HOSPITAL);
        else if (roll < 50) f.set(u2, y, mid, B.of(Material.CAULDRON));
        else if (roll < 62) f.set(u2, y, mid, B.of(Material.BREWING_STAND));
        if (r.nextInt(4) == 0) f.set(u1 + 2 + r.nextInt(Math.max(1, u2 - u1 - 2)), y, mid, B.of(Material.RED_CARPET));
        if (r.nextInt(5) == 0) f.set(u1, y, mid + (front ? 2 : -2), B.of(Material.IRON_BARS));
    }

    private static void storage(Frame f, Random r, int u1, int u2, int y, int v1, int v2) {
        for (int u = u1; u <= u2; u++) {
            f.set(u, y, v2, B.of(Material.WHITE_CONCRETE));
            f.set(u, y + 1, v2, B.slab(Material.SMOOTH_QUARTZ_SLAB, false));
        }
        Structures.chest(f, u1, y, v2 - 2, BlockFace.EAST, PlaceType.HOSPITAL);
        Structures.barrel(f, u2, y, v2 - 2, PlaceType.HOSPITAL);
        if (r.nextBoolean()) Structures.barrel(f, u2, y, v1 + 1, PlaceType.HOSPITAL);
        f.set(u1, y, v1 + 1, B.of(Material.BLUE_ICE));
        f.fill(u1 + 1, y, v2 - 1, u2 - 1, y, v2 - 1, B.of(Material.WHITE_CARPET));
    }

    static void police(Frame f, Random r, int y0) {
        int w = f.width(), d = f.depth();
        int front = 3, top = y0 + 5, mid = w / 2;
        BlockData wall = B.of(Material.STONE_BRICKS);
        BlockData floor = B.of(Material.POLISHED_ANDESITE);
        f.fill(0, y0 + 1, 0, w - 1, top + 2, d - 1, B.AIR);
        f.fill(0, y0, 0, w - 1, y0, d - 1, floor);
        Frame b = f.sub(0, front, w, d - front);
        int bd = d - front;
        Structures.walls(b, w, bd, y0 + 1, top - 1, wall);
        Structures.corners(b, w, bd, y0 + 1, top - 1, B.of(Material.POLISHED_DEEPSLATE));
        b.fill(0, y0 + 4, 0, w - 1, y0 + 4, 0, B.of(Material.BLUE_CONCRETE));
        for (int u = 2; u <= w - 3; u += 2) {
            if (Math.abs(u - mid) <= 1) continue;
            b.set(u, y0 + 2, 0, B.pane(Material.IRON_BARS, true));
            b.set(u, y0 + 2, bd - 1, B.pane(Material.IRON_BARS, true));
        }
        Structures.flatRoof(b, w, bd, top, B.of(Material.SMOOTH_STONE), B.of(Material.STONE_BRICK_WALL));
        b.fill(mid, y0 + 1, 0, mid, y0 + 2, 0, B.AIR);
        if (r.nextBoolean()) {
            b.set(mid, y0 + 1, 0, B.door(Material.DARK_OAK_DOOR, BlockFace.NORTH, false, true));
            b.set(mid, y0 + 2, 0, B.door(Material.DARK_OAK_DOOR, BlockFace.NORTH, true, true));
        }
        BlockData bags = B.of(Material.SANDSTONE_WALL);
        for (int u = 1; u < w - 1; u++) {
            if (Math.abs(u - mid) <= 1) continue;
            f.set(u, y0 + 1, 1, bags);
            if (r.nextInt(3) > 0) f.set(u, y0 + 2, 1, bags);
        }
        int y = y0 + 1;
        int office = 6;
        b.fill(1, y, office, w - 2, top - 1, office, wall);
        b.fill(mid, y, office, mid, y + 1, office, B.AIR);
        for (int u : new int[]{3, w - 4}) {
            b.fill(u, y, 3, u + 1, y, 3, B.of(Material.SPRUCE_PLANKS));
            b.set(u, y, 4, B.stairs(Material.SPRUCE_STAIRS, BlockFace.NORTH, false));
        }
        Structures.chest(b, 1, y, 1, BlockFace.EAST, PlaceType.POLICE);
        b.set(w - 2, y, 1, B.of(Material.BOOKSHELF));
        int cells = office + 2;
        BlockData bars = B.pane(Material.IRON_BARS, true);
        b.fill(1, y, cells, w - 2, y + 2, cells, bars);
        b.fill(5, y, cells, 5, top - 1, bd - 2, wall);
        b.fill(9, y, cells, 9, top - 1, bd - 2, wall);
        b.fill(10, y, cells, 10, top - 1, bd - 2, wall);
        b.fill(3, y, cells, 3, y + 1, cells, B.AIR);
        b.fill(7, y, cells, 7, y + 1, cells, B.AIR);
        b.set(1, y, bd - 2, B.bed(Material.GRAY_BED, BlockFace.SOUTH, true));
        b.set(1, y, bd - 3, B.bed(Material.GRAY_BED, BlockFace.SOUTH, false));
        b.set(8, y, bd - 2, B.of(Material.CAULDRON));
        if (r.nextBoolean()) Structures.barrel(b, 6, y, bd - 2, PlaceType.POLICE);
        b.set(14 + r.nextInt(3), y, cells, B.AIR);
        Structures.chest(b, 12, y, bd - 2, BlockFace.NORTH, PlaceType.POLICE);
        Structures.chest(b, w - 2, y, bd - 2, BlockFace.NORTH, PlaceType.POLICE);
        Structures.barrel(b, w - 2, y, cells + 2, PlaceType.POLICE);
        b.set(15, y, bd - 2, B.of(Material.SMITHING_TABLE));
        Structures.cobwebs(b, r, w, bd, top - 1, 2 + r.nextInt(3));
        Structures.decay(b, r, w, bd, y0 + 2, top - 1, r.nextInt(3));
    }

    static void park(Frame f, Random r, int y0) {
        int w = f.width(), d = f.depth(), c = w / 2;
        BlockData path = B.of(Material.DIRT_PATH);
        BlockData gravel = B.of(Material.GRAVEL);
        f.fill(c - 1, y0, 0, c + 1, y0, d - 1, path);
        f.fill(0, y0, c - 1, w - 1, y0, c + 1, path);
        for (int k = -5; k <= 5; k++) {
            f.set(c + k, y0, c - 5, gravel);
            f.set(c + k, y0, c + 5, gravel);
            f.set(c - 5, y0, c + k, gravel);
            f.set(c + 5, y0, c + k, gravel);
        }
        f.fill(c - 2, y0, c - 2, c + 2, y0, c + 2, B.of(Material.STONE_BRICKS));
        f.fill(c - 2, y0 + 1, c - 2, c + 2, y0 + 1, c + 2, B.of(Material.STONE_BRICK_WALL));
        f.fill(c - 1, y0 + 1, c - 1, c + 1, y0 + 1, c + 1, r.nextInt(3) == 0 ? B.AIR : B.WATER);
        f.set(c, y0 + 1, c, B.of(Material.CHISELED_STONE_BRICKS));
        f.set(c, y0 + 2, c, B.of(r.nextBoolean() ? Material.MOSSY_STONE_BRICK_WALL : Material.STONE_BRICK_WALL));
        int trees = 7 + r.nextInt(5);
        for (int k = 0; k < trees; k++) {
            int u = 2 + r.nextInt(w - 4), v = 2 + r.nextInt(d - 4);
            if (Math.abs(u - c) <= 7 && Math.abs(v - c) <= 7) continue;
            if (Math.abs(u - c) <= 3 || Math.abs(v - c) <= 3) continue;
            boolean birch = r.nextInt(3) == 0;
            Structures.tree(f, r, u, y0 + 1, v, birch ? Material.BIRCH_LOG : Material.OAK_LOG, birch ? Material.BIRCH_LEAVES : Material.OAK_LEAVES);
        }
        for (int k : new int[]{-12, -8, 8, 12}) {
            f.set(c + 2, y0 + 1, c + k, B.stairs(Material.OAK_STAIRS, BlockFace.EAST, false));
            f.set(c + k, y0 + 1, c - 2, B.stairs(Material.OAK_STAIRS, BlockFace.NORTH, false));
        }
        for (int[] spot : new int[][]{{c - 2, c - 7}, {c + 2, c + 7}, {c - 7, c + 2}, {c + 7, c - 2}}) Structures.lamp(f, spot[0], y0 + 1, spot[1]);
        for (int k = 0; k < 14; k++) {
            int u = 1 + r.nextInt(w - 2), v = 1 + r.nextInt(d - 2);
            if (Math.abs(u - c) <= 5 || Math.abs(v - c) <= 5) continue;
            Material[] plants = {Material.SHORT_GRASS, Material.FERN, Material.POPPY, Material.DANDELION, Material.OXEYE_DAISY};
            f.set(u, y0 + 1, v, B.of(plants[r.nextInt(plants.length)]));
        }
        if (r.nextInt(100) < 45) {
            int tu = 4 + r.nextInt(6), tv = c + 7 + r.nextInt(5);
            Structures.tent(f, tu, y0 + 1, tv, 5, Material.ORANGE_WOOL);
            f.set(tu + 1, y0 + 1, tv + 1, B.bed(Material.BROWN_BED, BlockFace.SOUTH, true));
            f.set(tu + 1, y0 + 1, tv, B.bed(Material.BROWN_BED, BlockFace.SOUTH, false));
            Structures.chest(f, tu + 3, y0 + 1, tv + 3, BlockFace.WEST, PlaceType.HOUSE);
            f.set(tu + 6, y0 + 1, tv + 1, B.unlit(Material.CAMPFIRE));
        }
    }

    /** Square around the refuge: only the outer ring (14 to 18 blocks from the center) is decorated, no loot. */
    static void plaza(Frame f, Random r, int y0) {
        int c = f.width() / 2;
        int y = y0 + 1;
        for (int du : new int[]{-16, 16}) {
            for (int dv : new int[]{-16, 16}) {
                f.fill(c + du - 1, y, c + dv - 1, c + du + 1, y, c + dv + 1, B.of(Material.STONE_BRICKS));
                f.set(c + du, y, c + dv, B.GRASS);
                Structures.tree(f, r, c + du, y + 1, c + dv, Material.OAK_LOG, Material.OAK_LEAVES);
            }
        }
        for (int s : new int[]{-1, 1}) {
            Structures.lamp(f, c + 10 * s, y, c - 15);
            Structures.lamp(f, c + 10 * s, y, c + 15);
            Structures.lamp(f, c - 15, y, c + 10 * s);
            Structures.lamp(f, c + 15, y, c + 10 * s);
        }
        BlockData bags = B.of(Material.SANDSTONE_WALL);
        for (int k = -13; k <= 13; k++) {
            if (Math.abs(k) <= 3) continue;
            if (r.nextInt(4) > 0) f.set(c + k, y, c + 17, bags);
            if (r.nextInt(4) > 0) f.set(c + k, y, c - 17, bags);
        }
        Structures.tent(f, c - 18, y, c - 6, 5, Material.GREEN_WOOL);
        Structures.tent(f, c + 14, y, c - 6, 5, Material.GREEN_WOOL);
        Structures.tent(f, c + 14, y, c + 3, 5, Material.WHITE_WOOL);
        f.set(c + 16, y + 2, c + 3, B.of(Material.RED_WOOL));
        for (int dv : new int[]{2, 9}) {
            f.set(c - 14, y, c + dv, B.stairs(Material.SPRUCE_STAIRS, BlockFace.WEST, false));
            f.set(c + 14, y, c + dv, B.stairs(Material.SPRUCE_STAIRS, BlockFace.EAST, false));
        }
        truck(f, c + 4, c - 15, true, y, r.nextBoolean());
        f.fill(c - 16, y, c + 5, c - 15, y + 1, c + 6, B.of(Material.HAY_BLOCK));
        f.set(c + 16, y, c - 13, B.of(Material.SMOKER));
        f.set(c + 16, y, c - 12, B.of(Material.CRAFTING_TABLE));
    }

    /** Olive military truck, five long, with a canvas cover over the bed. */
    static void truck(Frame f, int u, int v, boolean alongU, int y, boolean wrecked) {
        BlockData body = B.of(wrecked ? Material.BLACKSTONE : Material.GREEN_CONCRETE);
        BlockData cover = B.of(Material.GREEN_WOOL);
        for (int a = 0; a < 6; a++) {
            for (int b = 0; b < 2; b++) {
                int uu = alongU ? u + a : u + b, vv = alongU ? v + b : v + a;
                f.set(uu, y, vv, a == 0 || a == 4 ? B.of(Material.COAL_BLOCK) : body);
                if (a <= 1) f.set(uu, y + 1, vv, a == 1 && !wrecked ? B.of(Material.GLASS) : body);
                else if (!wrecked) {
                    f.set(uu, y + 1, vv, B.log(Material.STRIPPED_SPRUCE_WOOD, Axis.Y));
                    f.set(uu, y + 2, vv, cover);
                }
            }
        }
    }
}
