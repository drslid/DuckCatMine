package org.duckcatmine.apocalypse.scenario.city;

import java.util.Random;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

/** Houses, apartment towers, ruins and front yards. */
final class Homes {
    private record Palette(BlockData wall, BlockData pillar, BlockData floor, Material roof, BlockData ridge, Material door,
                           Material boards, boolean flat) {
    }

    private Homes() {
    }

    private static Palette palette(Random r) {
        return switch (r.nextInt(7)) {
            case 0 -> new Palette(B.of(Material.OAK_PLANKS), B.log(Material.OAK_LOG, Axis.Y), B.of(Material.SPRUCE_PLANKS),
                    Material.DARK_OAK_STAIRS, B.of(Material.DARK_OAK_PLANKS), Material.OAK_DOOR, Material.OAK_PLANKS, false);
            case 1 -> new Palette(B.of(Material.BRICKS), B.of(Material.STONE_BRICKS), B.of(Material.OAK_PLANKS),
                    Material.STONE_BRICK_STAIRS, B.of(Material.STONE_BRICKS), Material.SPRUCE_DOOR, Material.SPRUCE_PLANKS, false);
            case 2 -> new Palette(B.of(Material.WHITE_TERRACOTTA), B.log(Material.STRIPPED_BIRCH_LOG, Axis.Y), B.of(Material.BIRCH_PLANKS),
                    Material.SPRUCE_STAIRS, B.of(Material.SPRUCE_PLANKS), Material.BIRCH_DOOR, Material.BIRCH_PLANKS, false);
            case 3 -> new Palette(B.of(Material.STONE_BRICKS), B.of(Material.POLISHED_ANDESITE), B.of(Material.DARK_OAK_PLANKS),
                    Material.DEEPSLATE_TILE_STAIRS, B.of(Material.DEEPSLATE_TILES), Material.DARK_OAK_DOOR, Material.DARK_OAK_PLANKS, false);
            case 4 -> new Palette(B.of(Material.MUD_BRICKS), B.log(Material.STRIPPED_SPRUCE_LOG, Axis.Y), B.of(Material.SPRUCE_PLANKS),
                    Material.MUD_BRICK_STAIRS, B.of(Material.PACKED_MUD), Material.SPRUCE_DOOR, Material.SPRUCE_PLANKS, false);
            case 5 -> new Palette(B.of(Material.LIGHT_GRAY_CONCRETE), B.of(Material.WHITE_CONCRETE), B.of(Material.POLISHED_ANDESITE),
                    Material.STONE_STAIRS, B.slab(Material.SMOOTH_STONE_SLAB, false), Material.DARK_OAK_DOOR, Material.DARK_OAK_PLANKS, true);
            default -> new Palette(B.of(Material.YELLOW_TERRACOTTA), B.of(Material.BRICKS), B.of(Material.ACACIA_PLANKS),
                    Material.BRICK_STAIRS, B.of(Material.BRICKS), Material.ACACIA_DOOR, Material.ACACIA_PLANKS, false);
        };
    }

    static void house(Frame f, Random r, int y0, int floors, PlaceType place) {
        int w = f.width(), d = f.depth();
        Palette p = palette(r);
        int top = y0 + floors * 4;
        f.fill(0, y0 + 1, 0, w - 1, top + d / 2 + 2, d - 1, B.AIR);
        f.fill(0, y0 - 2, 0, w - 1, y0 - 1, d - 1, B.of(Material.COBBLESTONE));
        f.fill(0, y0, 0, w - 1, y0, d - 1, B.of(Material.COBBLESTONE));
        f.fill(1, y0, 1, w - 2, y0, d - 2, p.floor());
        for (int level = 0; level < floors; level++) {
            int fy = y0 + level * 4;
            Structures.walls(f, w, d, fy + 1, fy + 3, p.wall());
            Structures.corners(f, w, d, fy + 1, fy + 3, p.pillar());
            if (level > 0) {
                f.fill(0, fy, 0, w - 1, fy, d - 1, p.pillar());
                f.fill(1, fy, 1, w - 2, fy, d - 2, p.floor());
            }
            Structures.windows(f, r, w, d, fy + 2, level == 0 ? w / 2 - 1 : 99, level == 0 ? w / 2 + 1 : -1, p.boards());
        }
        if (p.flat()) Structures.flatRoof(f, w, d, top, p.ridge(), p.wall());
        else {
            f.fill(1, top, 1, w - 2, top, d - 2, p.floor());
            Structures.gable(f, w, d, top, p.roof(), p.ridge(), p.wall());
        }
        Structures.door(f, r, w / 2, y0 + 1, p.door());
        Structures.decay(f, r, w, d, y0 + 1, top - 1, r.nextInt(3));
        Structures.cobwebs(f, r, w, d, y0 + 3, 1 + r.nextInt(3));
        int y = y0 + 1;
        Structures.barrel(f, 1, y, d - 2, place);
        f.set(2, y, d - 2, B.facing(Material.FURNACE, BlockFace.NORTH));
        f.set(3, y, d - 2, B.of(Material.CRAFTING_TABLE));
        if (w > 7 && r.nextBoolean()) f.set(4, y, d - 2, B.of(Material.CAULDRON));
        if (d >= 9) Structures.table(f, r, w / 2, y, d / 2 + 1);
        Structures.chest(f, w - 2, y, 1, BlockFace.SOUTH, place);
        if (r.nextBoolean()) f.set(w - 2, y, d / 2, B.of(Material.BOOKSHELF));
        if (r.nextInt(3) == 0) f.set(w / 2, y0 + 3, d / 2, B.lantern(true));
        if (floors == 1) {
            Structures.bed(f, 1, y, 1, 2, Structures.bedColor(r));
            if (r.nextBoolean()) f.set(1, y, 3, B.of(Structures.carpet(r)));
        } else {
            Structures.ladder(f, 1, y, y0 + 4, 1, BlockFace.EAST);
            f.set(1, y0 + 5, 1, B.AIR);
            int y2 = y0 + 5;
            Structures.bed(f, w - 2, y2, d - 2, d - 3, Structures.bedColor(r));
            if (w > 8) Structures.bed(f, w - 4, y2, d - 2, d - 3, Structures.bedColor(r));
            Structures.chest(f, 3, y2, d - 2, BlockFace.NORTH, place);
            if (r.nextBoolean()) Structures.barrel(f, w - 2, y2, 2, place);
            f.set(w / 2, y2, d / 2, B.of(Structures.carpet(r)));
            Structures.cobwebs(f, r, w, d, y0 + 7, r.nextInt(3));
        }
    }

    static void apartment(Frame f, Random r, int y0) {
        int w = f.width(), d = f.depth();
        int floors = 3 + r.nextInt(3);
        Material[] facades = {Material.BRICKS, Material.WHITE_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.CYAN_TERRACOTTA,
                Material.LIGHT_GRAY_TERRACOTTA, Material.SMOOTH_SANDSTONE};
        BlockData wall = B.of(facades[r.nextInt(facades.length)]);
        BlockData pillar = B.of(r.nextBoolean() ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE);
        BlockData floor = B.of(Material.POLISHED_ANDESITE);
        int top = y0 + floors * 4;
        f.fill(0, y0 + 1, 0, w - 1, top + 2, d - 1, B.AIR);
        f.fill(0, y0, 0, w - 1, y0, d - 1, floor);
        int mid = w / 2;
        for (int level = 0; level < floors; level++) {
            int fy = y0 + level * 4, y = fy + 1;
            if (level > 0) f.fill(0, fy, 0, w - 1, fy, d - 1, floor);
            Structures.walls(f, w, d, y, fy + 3, wall);
            Structures.corners(f, w, d, y, fy + 3, pillar);
            Structures.windows(f, r, w, d, fy + 2, level == 0 ? mid - 1 : 99, level == 0 ? mid + 1 : -1, Material.SPRUCE_PLANKS);
            f.fill(mid - 2, y, 1, mid - 2, fy + 3, d - 2, wall);
            f.fill(mid + 2, y, 1, mid + 2, fy + 3, d - 2, wall);
            f.fill(mid - 2, y, 4, mid - 2, y + 1, 4, B.AIR);
            f.fill(mid + 2, y, d - 5, mid + 2, y + 1, d - 5, B.AIR);
            f.set(1, y, d - 2, B.bed(Structures.bedColor(r), BlockFace.SOUTH, true));
            f.set(1, y, d - 3, B.bed(Structures.bedColor(r), BlockFace.SOUTH, false));
            f.set(w - 2, y, 1, B.of(Material.CRAFTING_TABLE));
            Structures.barrel(f, 1, y, 1, PlaceType.HOUSE);
            if (r.nextBoolean()) Structures.chest(f, w - 2, y, d - 2, BlockFace.NORTH, PlaceType.HOUSE);
            if (r.nextBoolean()) Structures.table(f, r, 3, y, d / 2);
            if (r.nextBoolean()) f.set(w - 3, y, d / 2, B.of(Material.BOOKSHELF));
            Structures.cobwebs(f, r, w, d, fy + 3, r.nextInt(3));
            if (level > 0) f.set(mid, fy, d - 2, B.AIR);
        }
        Structures.ladder(f, mid, y0 + 1, top, d - 2, BlockFace.NORTH);
        f.set(mid, top, d - 2, B.AIR);
        Structures.flatRoof(f, w, d, top, floor, B.of(Material.STONE_BRICK_WALL));
        f.set(mid, top, d - 2, B.AIR);
        Structures.ladder(f, mid, top, top, d - 2, BlockFace.NORTH);
        f.fill(mid - 1, y0 + 1, 0, mid + 1, y0 + 2, 0, B.AIR);
        if (r.nextBoolean()) f.set(mid, y0 + 1, 0, B.door(Material.DARK_OAK_DOOR, BlockFace.NORTH, false, true));
        if (r.nextBoolean()) f.set(mid, y0 + 2, 0, B.door(Material.DARK_OAK_DOOR, BlockFace.NORTH, true, true));
        if (r.nextInt(3) == 0) f.set(2, top + 1, 2, B.of(Material.CAULDRON));
        Structures.decay(f, r, w, d, y0 + 1, top - 1, 1 + r.nextInt(4));
    }

    static void ruin(Frame f, Random r, int y0) {
        int w = f.width(), d = f.depth();
        Material[] materials = {Material.STONE_BRICKS, Material.BRICKS, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
                Material.CRACKED_STONE_BRICKS};
        BlockData wall = B.of(materials[r.nextInt(materials.length)]);
        f.fill(1, y0, 1, w - 2, y0, d - 2, B.of(Material.COBBLESTONE));
        for (int u = 1; u < w - 1; u++) {
            for (int v = 1; v < d - 1; v++) {
                boolean edge = u == 1 || u == w - 2 || v == 1 || v == d - 2;
                if (!edge) continue;
                int height = r.nextInt(4);
                if (height > 0) f.fill(u, y0 + 1, v, u, y0 + height, v, wall);
            }
        }
        Material[] rubble = {Material.GRAVEL, Material.COBBLESTONE, Material.ANDESITE, Material.BLACKSTONE, Material.COAL_BLOCK,
                Material.MOSSY_COBBLESTONE};
        for (int k = 0; k < 6 + r.nextInt(6); k++) {
            int u = 2 + r.nextInt(Math.max(1, w - 4)), v = 2 + r.nextInt(Math.max(1, d - 4));
            f.set(u, y0 + 1, v, B.of(rubble[r.nextInt(rubble.length)]));
            if (r.nextInt(3) == 0) f.set(u, y0 + 2, v, B.of(Material.COBBLESTONE_SLAB));
        }
        if (r.nextInt(3) == 0) f.set(w / 2, y0 + 1, d / 2, B.unlit(Material.CAMPFIRE));
        for (int k = 0; k < 3; k++) f.set(2 + r.nextInt(Math.max(1, w - 4)), y0 + 2, 2 + r.nextInt(Math.max(1, d - 4)), B.COBWEB);
        if (r.nextInt(100) < 65) Structures.chest(f, 2, y0 + 1, 2, BlockFace.SOUTH, PlaceType.HOUSE);
    }

    /** Front yard of a parcel: lawn, fences on three sides, a tree or a parked car. */
    static void yard(Frame f, Random r, int y0) {
        int w = f.width(), d = f.depth();
        if (r.nextInt(100) < 60) {
            BlockData fence = B.of(r.nextBoolean() ? Material.OAK_FENCE : Material.SPRUCE_FENCE);
            for (int u = 0; u < w; u++) if (r.nextInt(100) < 85) f.set(u, y0 + 1, d - 1, fence);
            for (int v = 1; v < d; v++) {
                if (r.nextInt(100) < 85) f.set(0, y0 + 1, v, fence);
                if (r.nextInt(100) < 85) f.set(w - 1, y0 + 1, v, fence);
            }
        }
        for (int k = 0; k < 4; k++) {
            Material[] plants = {Material.SHORT_GRASS, Material.POPPY, Material.DANDELION, Material.AZURE_BLUET, Material.TALL_GRASS};
            Material plant = plants[r.nextInt(plants.length)];
            if (plant != Material.TALL_GRASS) f.set(1 + r.nextInt(w - 2), y0 + 1, 1 + r.nextInt(2), B.of(plant));
        }
        if (r.nextInt(100) < 30) Structures.tree(f, r, 2 + r.nextInt(3), y0 + 1, d - 3, Material.OAK_LOG, Material.OAK_LEAVES);
        if (r.nextInt(100) < 18) Streets.car(f, w - 4, 0, false, r.nextLong(), y0 + 1);
    }
}
