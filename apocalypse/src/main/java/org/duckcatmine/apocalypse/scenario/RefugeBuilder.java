package org.duckcatmine.apocalypse.scenario;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

/** Deterministic public refuge: walled square, south gate, rules on signs facing the spawn point. */
final class RefugeBuilder {
    private RefugeBuilder() {
    }

    static void build(World world, RefugeArea area, int floorY) {
        int cx = area.centerX(), cz = area.centerZ(), r = area.radius();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                set(world, x, floorY, z, Material.STONE_BRICKS);
                for (int y = floorY + 1; y <= floorY + 6; y++) set(world, x, y, z, Material.AIR);
                boolean edge = x == cx - r || x == cx + r || z == cz - r || z == cz + r;
                boolean gate = z == cz + r && Math.abs(x - cx) <= 1;
                if (!edge || gate) continue;
                for (int y = floorY + 1; y <= floorY + 3; y++) {
                    boolean window = y == floorY + 2 && Math.floorMod(x + z, 4) == 0 && x != cx - r && x != cx + r;
                    set(world, x, y, z, window ? Material.IRON_BARS : Material.STONE_BRICKS);
                }
                set(world, x, floorY + 4, z, Material.STONE_BRICK_WALL);
            }
        }
        for (int side : new int[]{-2, 2}) set(world, cx + side, floorY + 5, cz + r, Material.LANTERN);
        for (int dx : new int[]{-(r - 2), r - 2}) {
            for (int dz : new int[]{-(r - 2), r - 2}) set(world, cx + dx, floorY + 1, cz + dz, Material.LANTERN);
        }
        set(world, cx - 3, floorY + 1, cz - r + 2, Material.CRAFTING_TABLE);
        sign(world, cx - 2, floorY + 1, cz + 3, List.of("REFUGE", "Zone sûre", "Morsure = 60 s", "pour l'antidote"));
        sign(world, cx, floorY + 1, cz + 3, List.of("MORT", "Ton sac reste", "5 minutes là", "où tu tombes"));
        sign(world, cx + 2, floorY + 1, cz + 3, List.of("SORTIR", "Reviens ici puis", "/apocalypse", "leave"));
    }

    private static void set(World world, int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }

    private static void sign(World world, int x, int y, int z, List<String> lines) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.SPRUCE_SIGN, false);
        if (block.getBlockData() instanceof Rotatable rotatable) {
            rotatable.setRotation(BlockFace.NORTH);
            block.setBlockData(rotatable, false);
        }
        if (!(block.getState() instanceof Sign sign)) return;
        SignSide front = sign.getSide(Side.FRONT);
        for (int i = 0; i < lines.size(); i++) {
            front.line(i, Component.text(lines.get(i), i == 0 ? NamedTextColor.DARK_RED : NamedTextColor.BLACK));
        }
        sign.setWaxed(true);
        sign.update(true, false);
    }
}
