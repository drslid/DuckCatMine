package org.duckcatmine.apocalypse.scenario.city;

import java.util.Random;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

/** Furnished homes: living/sleeping spaces, kitchens, bathrooms, and usable stairs. */
final class V2Homes {
    private V2Homes() {}
    static void house(Frame f, Random r, int y0, int floors, PlaceType place) {
        LegacyHomes.house(f, r, y0, floors, place);
        int w = f.width(), d = f.depth(), y = y0 + 1;
        f.fill(1, y, 1, w - 2, y + 2, d - 2, B.AIR);
        kitchen(f, y, 1, 1, place);
        if (floors == 1) {
            int partition = Math.max(4, d / 2);
            f.fill(1, y, partition, w - 2, y + 2, partition, B.of(Material.WHITE_TERRACOTTA));
            opening(f, w / 2, y, partition);
            Structures.bed(f, w - 2, y, d - 2, d - 3, Material.LIGHT_BLUE_BED);
            Structures.chest(f, 1, y, d - 2, BlockFace.NORTH, place);
            f.set(1, y + 1, d - 2, B.of(Material.BOOKSHELF));
            sofa(f, 1, y, partition - 1);
        } else {
            sofa(f, 1, y, d / 2);
            Structures.table(f, new Random(7), w / 2, y, d - 3);
            f.fill(1, y0 + 5, 1, w - 2, y0 + 7, d - 2, B.AIR);
            Structures.bed(f, 1, y0 + 5, d - 2, d - 3, Material.GREEN_BED);
            Structures.chest(f, 2, y0 + 5, d - 2, BlockFace.NORTH, place);
            f.fill(1, y0 + 5, d / 2, w - 3, y0 + 7, d / 2, B.of(Material.WHITE_TERRACOTTA));
            opening(f, w / 2, y0 + 5, d / 2);
            bathroom(f, 1, y0 + 5, 1);
            stairs(f, w - 2, y0, 2);
            Structures.barrel(f, 1, y, d - 2, place);
        }
        f.set(w - 2, y + 2, d - 2, B.lantern(true));
        f.set(w / 2, y + 2, 0, B.of(Material.SEA_LANTERN));
    }
    static void apartment(Frame f, Random r, int y0) {
        int floors = LegacyHomes.apartment(f, r, y0), w = f.width(), d = f.depth(), mid = w / 2;
        for (int level = 0; level < floors; level++) {
            int fy = y0 + level * 4, y = fy + 1;
            f.fill(1, y, 1, w - 2, y + 2, d - 2, B.AIR);
            f.fill(mid - 2, y, 1, mid - 2, y + 2, d - 2, B.of(Material.WHITE_TERRACOTTA));
            f.fill(mid + 2, y, 1, mid + 2, y + 2, d - 2, B.of(Material.WHITE_TERRACOTTA));
            for (int doorX : new int[]{mid - 2, mid + 2}) opening(f, doorX, y, 4, BlockFace.EAST);
            for (int x : new int[]{1, mid + 3}) {
                kitchen(f, y, x, 1, PlaceType.HOUSE);
                sofa(f, x, y, 5);
                f.fill(x, y, 8, x + 3, y + 2, 8, B.of(Material.WHITE_TERRACOTTA));
                opening(f, x + 2, y, 8);
                Structures.bed(f, x, y, d - 2, d - 3, level % 2 == 0 ? Material.CYAN_BED : Material.ORANGE_BED);
                Structures.chest(f, x + 1, y, d - 2, BlockFace.NORTH, PlaceType.HOUSE);
                bathroom(f, x + 2, y, d - 3);
            }
            f.fill(mid - 1, fy, 1, mid + 1, fy, d - 2, B.of(Material.POLISHED_DIORITE));
            if (level < floors - 1) stairs(f, mid, fy, d - 6);
            f.set(mid, y + 2, 3, B.of(Material.SEA_LANTERN));
        }
        Structures.ladder(f, mid, y0 + 1, y0 + floors * 4, d - 2, BlockFace.NORTH);
        for (int level = 1; level < floors; level++) {
            f.fill(mid, y0 + level * 4, d - 6, mid, y0 + level * 4, d - 4, B.AIR);
            f.set(mid, y0 + level * 4, d - 3, B.stairs(Material.STONE_BRICK_STAIRS, BlockFace.SOUTH, false));
        }
        f.fill(mid - 1, y0 + 1, 0, mid + 1, y0 + 2, 0, B.AIR);
    }
    private static void kitchen(Frame f, int y, int x, int v, PlaceType place) {
        Structures.barrel(f, x, y, v, place);
        f.set(x + 1, y, v, B.facing(Material.FURNACE, BlockFace.SOUTH));
        f.set(x + 2, y, v, B.of(Material.CRAFTING_TABLE));
        f.set(x, y + 2, v, B.slab(Material.OAK_SLAB, false));
        f.set(x + 1, y + 2, v, B.of(Material.IRON_TRAPDOOR));
        f.set(x + 2, y + 2, v, B.slab(Material.OAK_SLAB, false));
    }
    private static void bathroom(Frame f, int x, int y, int v) {
        f.set(x, y, v, B.of(Material.CAULDRON));
        f.set(x + 1, y, v, B.stairs(Material.QUARTZ_STAIRS, BlockFace.SOUTH, false));
        f.set(x, y + 1, v, B.of(Material.TRIPWIRE_HOOK));
    }
    private static void sofa(Frame f, int x, int y, int v) {
        f.set(x, y, v, B.stairs(Material.SPRUCE_STAIRS, BlockFace.EAST, false));
        f.set(x + 1, y, v, B.stairs(Material.SPRUCE_STAIRS, BlockFace.EAST, false));
        f.set(x + 1, y, v + 1, B.of(Material.RED_CARPET));
    }
    private static void opening(Frame f, int x, int y, int v) { opening(f,x,y,v,BlockFace.NORTH); }
    private static void opening(Frame f, int x, int y, int v, BlockFace facing) {
        f.fill(x, y, v, x, y + 1, v, B.AIR);
        f.set(x, y, v, B.door(Material.OAK_DOOR, facing, false, false));
        f.set(x, y + 1, v, B.door(Material.OAK_DOOR, facing, true, false));
    }
    private static void stairs(Frame f, int x, int fy, int v) {
        f.fill(x, fy + 1, v, x, fy + 6, v + 3, B.AIR);
        for (int step = 0; step < 4; step++) f.set(x, fy + 1 + step, v + step, B.stairs(Material.STONE_BRICK_STAIRS, BlockFace.SOUTH, false));
    }
    static void ruin(Frame f, Random r, int y) { LegacyHomes.ruin(f, r, y); }
    static void yard(Frame f, Random r, int y) { LegacyHomes.yard(f, r, y); }
}
