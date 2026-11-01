package org.duckcatmine.apocalypse.scenario.city;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;

/** Block data factory for generated structures; authored for a front facing north (see {@link Frame}). */
final class B {
    static final BlockData AIR = Material.AIR.createBlockData();
    static final BlockData STONE = Material.STONE.createBlockData();
    static final BlockData DEEPSLATE = Material.DEEPSLATE.createBlockData();
    static final BlockData BEDROCK = Material.BEDROCK.createBlockData();
    static final BlockData DIRT = Material.DIRT.createBlockData();
    static final BlockData GRASS = Material.GRASS_BLOCK.createBlockData();
    static final BlockData WATER = Material.WATER.createBlockData();
    static final BlockData SAND = Material.SAND.createBlockData();
    static final BlockData GRAVEL = Material.GRAVEL.createBlockData();
    static final BlockData COBWEB = Material.COBWEB.createBlockData();

    private B() {
    }

    static BlockData of(Material material) {
        return material.createBlockData();
    }

    static BlockData stairs(Material material, BlockFace facing, boolean top) {
        Stairs stairs = (Stairs) material.createBlockData();
        stairs.setFacing(facing);
        stairs.setHalf(top ? Bisected.Half.TOP : Bisected.Half.BOTTOM);
        return stairs;
    }

    static BlockData slab(Material material, boolean top) {
        Slab slab = (Slab) material.createBlockData();
        slab.setType(top ? Slab.Type.TOP : Slab.Type.BOTTOM);
        return slab;
    }

    static BlockData facing(Material material, BlockFace face) {
        BlockData data = material.createBlockData();
        if (data instanceof Directional directional && directional.getFaces().contains(face)) directional.setFacing(face);
        return data;
    }

    static BlockData door(Material material, BlockFace facing, boolean upper, boolean open) {
        Door door = (Door) material.createBlockData();
        door.setFacing(facing);
        door.setHalf(upper ? Bisected.Half.TOP : Bisected.Half.BOTTOM);
        door.setOpen(open);
        return door;
    }

    static BlockData open(Material material, BlockFace facing, boolean open) {
        BlockData data = facing(material, facing);
        if (data instanceof Openable openable) openable.setOpen(open);
        return data;
    }

    /** Glass pane or bars running along the local x axis ({@code true}) or z axis. */
    static BlockData pane(Material material, boolean alongX) {
        MultipleFacing pane = (MultipleFacing) material.createBlockData();
        pane.setFace(alongX ? BlockFace.EAST : BlockFace.NORTH, true);
        pane.setFace(alongX ? BlockFace.WEST : BlockFace.SOUTH, true);
        return pane;
    }

    static BlockData bed(Material material, BlockFace facing, boolean head) {
        Bed bed = (Bed) material.createBlockData();
        bed.setFacing(facing);
        bed.setPart(head ? Bed.Part.HEAD : Bed.Part.FOOT);
        return bed;
    }

    static BlockData lantern(boolean hanging) {
        Lantern lantern = (Lantern) Material.LANTERN.createBlockData();
        lantern.setHanging(hanging);
        return lantern;
    }

    static BlockData leaves(Material material) {
        Leaves leaves = (Leaves) material.createBlockData();
        leaves.setPersistent(true);
        return leaves;
    }

    static BlockData log(Material material, Axis axis) {
        Orientable log = (Orientable) material.createBlockData();
        log.setAxis(axis);
        return log;
    }

    static BlockData unlit(Material material) {
        BlockData data = material.createBlockData();
        if (data instanceof Lightable lightable) lightable.setLit(false);
        return data;
    }

    static BlockData lit(Material material) {
        BlockData data = material.createBlockData();
        if (data instanceof Lightable lightable) lightable.setLit(true);
        return data;
    }

    /** Fully grown crop. */
    static BlockData crop(Material material) {
        BlockData data = material.createBlockData();
        if (data instanceof Ageable ageable) ageable.setAge(ageable.getMaximumAge());
        return data;
    }

    static BlockData farmland() {
        Farmland farmland = (Farmland) Material.FARMLAND.createBlockData();
        farmland.setMoisture(farmland.getMaximumMoisture());
        return farmland;
    }
}
