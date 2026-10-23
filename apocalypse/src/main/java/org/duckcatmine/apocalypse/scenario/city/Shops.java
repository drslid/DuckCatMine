package org.duckcatmine.apocalypse.scenario.city;

import java.util.Random;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

/** Shops, supermarkets, pharmacies, garages, warehouses and gas stations. */
final class Shops {
    private Shops() {
    }

    private static BlockData facade(Random r) {
        Material[] materials = {Material.WHITE_TERRACOTTA, Material.BRICKS, Material.LIGHT_GRAY_CONCRETE, Material.BLUE_TERRACOTTA,
                Material.GREEN_TERRACOTTA, Material.SMOOTH_SANDSTONE, Material.STONE_BRICKS};
        return B.of(materials[r.nextInt(materials.length)]);
    }

    private static void storefront(Frame f, Random r, int w, int y, int doorU) {
        for (int u = 1; u < w - 1; u++) {
            if (u == doorU) continue;
            for (int yy = y; yy <= y + 2; yy++) Structures.window(f, r, u, yy, 0, true, Material.OAK_PLANKS);
        }
        f.fill(doorU, y, 0, doorU, y + 1, 0, B.AIR);
        if (r.nextBoolean()) {
            f.set(doorU, y, 0, B.door(Material.SPRUCE_DOOR, BlockFace.NORTH, false, r.nextBoolean()));
            f.set(doorU, y + 1, 0, B.door(Material.SPRUCE_DOOR, BlockFace.NORTH, true, false));
        }
    }

    static void shop(Frame f, Random r, int y0) {
        int w = f.width(), d = f.depth();
        BlockData wall = facade(r);
        int top = y0 + 5;
        f.fill(0, y0 + 1, 0, w - 1, top + 2, d - 1, B.AIR);
        f.fill(0, y0, 0, w - 1, y0, d - 1, B.of(r.nextBoolean() ? Material.SMOOTH_STONE : Material.POLISHED_DIORITE));
        Structures.walls(f, w, d, y0 + 1, top - 1, wall);
        f.fill(0, y0 + 4, 0, w - 1, y0 + 4, 0, B.of(Structures.wool(r)));
        int doorU = 2 + r.nextInt(Math.max(1, w - 4));
        storefront(f, r, w, y0 + 1, doorU);
        Structures.flatRoof(f, w, d, top, B.of(Material.SMOOTH_STONE), wall);
        int y = y0 + 1;
        f.fill(1, y, 3, 3, y, 3, B.of(Material.STRIPPED_OAK_WOOD));
        int back = d - 5;
        for (int row = 6; row < back - 1; row += 3) {
            for (int u = 2; u < w - 2; u++) {
                if (u == doorU) continue;
                f.set(u, y, row, B.of(Material.OAK_PLANKS));
                f.set(u, y + 1, row, B.slab(Material.OAK_SLAB, false));
                if (r.nextInt(5) == 0) Structures.barrel(f, u, y + 1, row, PlaceType.SHOP);
            }
        }
        f.fill(1, y, back, w - 2, y + 3, back, wall);
        f.fill(w / 2, y, back, w / 2, y + 1, back, B.AIR);
        Structures.chest(f, 1, y, d - 2, BlockFace.NORTH, PlaceType.SHOP);
        Structures.barrel(f, w - 2, y, d - 2, PlaceType.SHOP);
        if (r.nextBoolean()) Structures.barrel(f, w - 2, y, back + 1, PlaceType.SHOP);
        Structures.cobwebs(f, r, w, d, y0 + 4, 1 + r.nextInt(3));
        Structures.decay(f, r, w, d, y0 + 1, y0 + 3, r.nextInt(3));
    }

    static void pharmacy(Frame f, Random r, int y0) {
        int w = f.width(), d = f.depth();
        BlockData wall = B.of(Material.WHITE_CONCRETE);
        int top = y0 + 6;
        f.fill(0, y0 + 1, 0, w - 1, top + 2, d - 1, B.AIR);
        f.fill(0, y0, 0, w - 1, y0, d - 1, B.of(Material.POLISHED_DIORITE));
        Structures.walls(f, w, d, y0 + 1, top - 1, wall);
        int mid = w / 2;
        storefront(f, r, w, y0 + 1, mid);
        BlockData cross = B.of(Material.LIME_CONCRETE);
        f.set(mid, y0 + 3, 0, cross);
        f.fill(mid - 1, y0 + 4, 0, mid + 1, y0 + 4, 0, cross);
        f.set(mid, y0 + 5, 0, cross);
        Structures.flatRoof(f, w, d, top, B.of(Material.SMOOTH_STONE), wall);
        int y = y0 + 1;
        f.fill(1, y, 6, w - 3, y, 6, B.of(Material.WHITE_CONCRETE));
        f.fill(1, y + 1, 6, w - 3, y + 1, 6, B.slab(Material.QUARTZ_SLAB, false));
        for (int u = 1; u < w - 1; u += 2) Structures.barrel(f, u, y, d - 2, PlaceType.PHARMACY);
        f.set(w - 2, y, 8, B.of(Material.BREWING_STAND));
        Structures.chest(f, 1, y, 8, BlockFace.EAST, PlaceType.PHARMACY);
        f.fill(2, y, d - 2, 2, y + 1, d - 2, B.of(Material.WHITE_STAINED_GLASS));
        Structures.cobwebs(f, r, w, d, y0 + 5, r.nextInt(3));
        Structures.decay(f, r, w, d, y0 + 1, y0 + 4, r.nextInt(2));
    }

    static void supermarket(Frame f, Random r, int y0) {
        int w = f.width(), d = f.depth();
        BlockData wall = B.of(r.nextBoolean() ? Material.LIGHT_GRAY_CONCRETE : Material.WHITE_CONCRETE);
        int top = y0 + 6;
        f.fill(0, y0 + 1, 0, w - 1, top + 2, d - 1, B.AIR);
        f.fill(0, y0, 0, w - 1, y0, d - 1, B.of(Material.SMOOTH_STONE));
        Structures.walls(f, w, d, y0 + 1, top - 1, wall);
        f.fill(0, y0 + 5, 0, w - 1, y0 + 5, 0, B.of(Material.RED_CONCRETE));
        for (int u = 2; u < w - 2; u++) {
            for (int yy = y0 + 1; yy <= y0 + 3; yy++) Structures.window(f, r, u, yy, 0, true, Material.SPRUCE_PLANKS);
        }
        f.fill(w / 2 - 1, y0 + 1, 0, w / 2 + 1, y0 + 2, 0, B.AIR);
        Structures.flatRoof(f, w, d, top, B.of(Material.SMOOTH_STONE), wall);
        for (int u = 4; u < w - 4; u += 6) f.set(u, top, d / 2, B.of(Material.GLASS));
        int y = y0 + 1;
        for (int u = 3; u < w - 3; u += 5) {
            f.fill(u, y, 3, u + 1, y, 3, B.of(Material.STRIPPED_BIRCH_WOOD));
            f.set(u + 1, y + 1, 3, B.of(Material.LIGHT_WEIGHTED_PRESSURE_PLATE));
        }
        int back = d - 7;
        for (int u = 3; u < w - 3; u += 4) {
            for (int v = 6; v < back; v++) {
                f.set(u, y, v, B.of(Material.SPRUCE_PLANKS));
                f.set(u, y + 1, v, B.slab(Material.SPRUCE_SLAB, false));
                f.set(u, y + 2, v, B.of(Material.SPRUCE_PLANKS));
                if (r.nextInt(9) == 0) Structures.barrel(f, u, y + 1, v, PlaceType.SUPERMARKET);
            }
        }
        f.fill(1, y, back, w - 2, y + 4, back, wall);
        f.fill(w / 2, y, back, w / 2 + 1, y + 2, back, B.AIR);
        for (int u = 2; u < w - 2; u += 4) Structures.chest(f, u, y, d - 2, BlockFace.NORTH, PlaceType.SUPERMARKET);
        for (int u = 4; u < w - 2; u += 4) f.set(u, y, d - 2, B.of(Material.STRIPPED_SPRUCE_WOOD));
        f.fill(w - 6, y, d - 1, w - 3, y + 2, d - 1, B.AIR);
        Structures.cobwebs(f, r, w, d, y0 + 5, 3 + r.nextInt(4));
        Structures.decay(f, r, w, d, y0 + 1, y0 + 4, 1 + r.nextInt(3));
    }

    static void garage(Frame f, Random r, int y0) {
        int w = f.width(), d = f.depth();
        Material[] materials = {Material.ANDESITE, Material.STONE_BRICKS, Material.POLISHED_ANDESITE, Material.GRAY_CONCRETE};
        BlockData wall = B.of(materials[r.nextInt(materials.length)]);
        int top = y0 + 5;
        f.fill(0, y0 + 1, 0, w - 1, top + 2, d - 1, B.AIR);
        f.fill(0, y0, 0, w - 1, y0, d - 1, B.of(Material.STONE));
        Structures.walls(f, w, d, y0 + 1, top - 1, wall);
        f.fill(2, y0 + 1, 0, w - 4, y0 + 3, 0, B.AIR);
        Structures.flatRoof(f, w, d, top, B.of(Material.SMOOTH_STONE), null);
        Structures.window(f, r, w - 2, y0 + 2, 0, true, Material.OAK_PLANKS);
        Structures.windows(f, r, w, d, y0 + 2, 0, w, Material.OAK_PLANKS);
        int y = y0 + 1;
        if (r.nextInt(100) < 75) Streets.car(f, 3, 2, false, r.nextLong(), y);
        f.set(w - 2, y, 2, B.of(Material.CRAFTING_TABLE));
        f.set(w - 2, y, 3, B.facing(Material.ANVIL, BlockFace.NORTH));
        f.set(w - 2, y, 4, B.of(Material.GRINDSTONE));
        f.set(w - 2, y, 5, B.of(Material.SMITHING_TABLE));
        Structures.barrel(f, w - 2, y, 7, PlaceType.GARAGE);
        Structures.barrel(f, w - 2, y, 8, PlaceType.GARAGE);
        Structures.chest(f, w - 3, y, d - 2, BlockFace.NORTH, PlaceType.GARAGE);
        f.set(1, y, d - 2, B.of(Material.BLAST_FURNACE));
        for (int k = 0; k < 3; k++) f.set(2 + r.nextInt(Math.max(1, w - 5)), y, 6 + r.nextInt(Math.max(1, d - 8)), B.of(Material.BLACK_CARPET));
        Structures.cobwebs(f, r, w, d, y0 + 4, 1 + r.nextInt(2));
    }

    static void warehouse(Frame f, Random r, int y0) {
        int w = f.width(), d = f.depth();
        BlockData lower = B.of(Material.STONE_BRICKS);
        BlockData upper = B.of(r.nextBoolean() ? Material.LIGHT_GRAY_TERRACOTTA : Material.CYAN_TERRACOTTA);
        int top = y0 + 8;
        f.fill(0, y0 + 1, 0, w - 1, top + 2, d - 1, B.AIR);
        f.fill(0, y0, 0, w - 1, y0, d - 1, B.of(Material.SMOOTH_STONE));
        Structures.walls(f, w, d, y0 + 1, y0 + 2, lower);
        Structures.walls(f, w, d, y0 + 3, top - 1, upper);
        Structures.corners(f, w, d, y0 + 1, top - 1, B.of(Material.POLISHED_ANDESITE));
        for (int u = 3; u < w - 3; u += 3) f.set(u, y0 + 6, 0, B.pane(Material.IRON_BARS, true));
        f.fill(3, y0 + 1, 0, 6, y0 + 4, 0, B.AIR);
        f.fill(w - 7, y0 + 1, 0, w - 4, y0 + 4, 0, B.AIR);
        Structures.flatRoof(f, w, d, top, B.slab(Material.SMOOTH_STONE_SLAB, false), null);
        for (int u = 5; u < w - 4; u += 7) f.fill(u, top, 4, u, top, d - 5, B.of(Material.GLASS));
        int y = y0 + 1;
        for (int v = 5; v < d - 3; v += 4) {
            for (int u = 2; u < w - 2; u++) {
                if (u >= 3 && u <= 6 || u >= w - 7 && u <= w - 4) {
                    if (v < 7) continue;
                }
                f.set(u, y, v, B.of(Material.SPRUCE_FENCE));
                f.set(u, y + 1, v, B.slab(Material.SPRUCE_SLAB, false));
                f.set(u, y + 2, v, B.of(Material.SPRUCE_FENCE));
                f.set(u, y + 3, v, B.slab(Material.SPRUCE_SLAB, false));
                int roll = r.nextInt(14);
                if (roll == 0) Structures.barrel(f, u, y + 2, v, PlaceType.WAREHOUSE);
                else if (roll < 5) f.set(u, y + 2, v, B.log(Material.STRIPPED_SPRUCE_WOOD, Axis.Y));
            }
        }
        Structures.chest(f, w - 2, y, d - 2, BlockFace.NORTH, PlaceType.WAREHOUSE);
        Structures.chest(f, 1, y, d - 2, BlockFace.NORTH, PlaceType.WAREHOUSE);
        f.fill(w / 2 - 1, y, 2, w / 2, y, 3, B.of(Material.YELLOW_CONCRETE));
        f.set(w / 2 - 1, y + 1, 2, B.pane(Material.IRON_BARS, true));
        Structures.cobwebs(f, r, w, d, top - 1, 4 + r.nextInt(5));
        Structures.decay(f, r, w, d, y0 + 3, top - 1, 1 + r.nextInt(4));
    }

    static void gasStation(Frame f, Random r, int y0, CityLayout layout) {
        int w = f.width(), d = f.depth();
        if (y0 != Terrain.CITY_Y) Structures.flatten(f, layout, y0, 5);
        f.fill(0, y0, 0, w - 1, y0, d - 1, B.of(Material.GRAY_CONCRETE));
        f.fill(0, y0 + 1, 0, w - 1, y0 + 8, d - 1, B.AIR);
        int cu = 4, cv = 4, cw = 14, cd = 10;
        BlockData pillar = B.of(Material.POLISHED_ANDESITE);
        for (int[] corner : new int[][]{{cu, cv}, {cu + cw - 1, cv}, {cu, cv + cd - 1}, {cu + cw - 1, cv + cd - 1}})
            f.fill(corner[0], y0 + 1, corner[1], corner[0], y0 + 4, corner[1], pillar);
        f.fill(cu, y0 + 5, cv, cu + cw - 1, y0 + 5, cv + cd - 1, B.slab(Material.SMOOTH_STONE_SLAB, false));
        f.fill(cu, y0 + 5, cv, cu + cw - 1, y0 + 5, cv, B.of(Material.RED_CONCRETE));
        for (int u = cu + 3; u < cu + cw - 2; u += 4) {
            f.set(u, y0 + 1, cv + cd / 2, B.of(Material.IRON_BLOCK));
            f.set(u, y0 + 2, cv + cd / 2, B.of(Material.ANDESITE_WALL));
            if (r.nextBoolean()) f.set(u, y0 + 1, cv + cd / 2 + 1, B.of(Material.BLACK_CARPET));
        }
        if (r.nextBoolean()) Streets.car(f, cu + 2, cv + 1, true, r.nextLong(), y0 + 1);
        int su = w - 13, sv = d - 12;
        shopFrame(f.sub(su, sv, 11, 10), r, y0);
        Structures.lamp(f, 1, y0 + 1, 1);
        Structures.lamp(f, w - 2, y0 + 1, 1);
    }

    /** Small convenience store of a gas station, authored in its own frame. */
    static void shopFrame(Frame f, Random r, int y0) {
        int w = f.width(), d = f.depth();
        BlockData wall = B.of(Material.WHITE_CONCRETE);
        f.fill(0, y0 + 1, 0, w - 1, y0 + 6, d - 1, B.AIR);
        f.fill(0, y0, 0, w - 1, y0, d - 1, B.of(Material.SMOOTH_STONE));
        Structures.walls(f, w, d, y0 + 1, y0 + 4, wall);
        f.fill(0, y0 + 4, 0, w - 1, y0 + 4, 0, B.of(Material.RED_CONCRETE));
        storefront(f, r, w, y0 + 1, w / 2);
        Structures.flatRoof(f, w, d, y0 + 5, B.of(Material.SMOOTH_STONE), null);
        int y = y0 + 1;
        Structures.barrel(f, 1, y, d - 2, PlaceType.GAS_STATION);
        Structures.barrel(f, w - 2, y, d - 2, PlaceType.GAS_STATION);
        Structures.chest(f, w - 2, y, 3, BlockFace.WEST, PlaceType.GAS_STATION);
        f.fill(1, y, 3, 3, y, 3, B.of(Material.STRIPPED_OAK_WOOD));
        for (int u = 2; u < w - 3; u += 2) {
            f.set(u, y, 6, B.of(Material.OAK_PLANKS));
            f.set(u, y + 1, 6, B.slab(Material.OAK_SLAB, false));
        }
        Structures.cobwebs(f, r, w, d, y0 + 3, r.nextInt(3));
    }
}
