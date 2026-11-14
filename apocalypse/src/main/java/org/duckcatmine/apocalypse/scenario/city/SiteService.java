package org.duckcatmine.apocalypse.scenario.city;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.persistence.Store;
import org.duckcatmine.apocalypse.scenario.GroundZeroGenerator;

/**
 * Additions to an already generated Ground Zero, built once when survivors come near: a walled prison with an
 * armoury, hidden bunkers in the countryside and cellars under a third of the houses. Nothing is regenerated;
 * containers are ordinary searchable loot (armoury, house, garage tables).
 */
public final class SiteService implements Listener {
    public record Site(String id, String kind, String label, int x, int z) {}
    private static final int PRISON_W = 40, PRISON_D = 32, BUNKER = 11;
    private final DuckCatApocalypse plugin;
    private final NamespacedKey cellars;
    private final Set<String> built = new HashSet<>(), loading = new HashSet<>(), forced = new HashSet<>();
    private final List<Site> sites = new ArrayList<>();
    private long seconds;

    public SiteService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
        cellars = new NamespacedKey(plugin, "cellar_rev");
    }

    private static CityLayout layout(World world) {
        return world.getGenerator() instanceof GroundZeroGenerator ? CityLayout.of(world.getSeed()) : null;
    }

    /** Deterministic sites: one prison north-west of the city, five bunkers around the countryside. */
    public List<Site> sites(World world) {
        if (!sites.isEmpty() || layout(world) == null) return sites;
        int edge = CityLayout.CITY_EDGE;
        sites.add(new Site("prison", "prison", "Prison de Ground Zero", -(edge + 190), -(edge + 170)));
        java.util.Random random = new java.util.Random(world.getSeed() ^ 0x5EEDB0B5L);
        double half = world.getWorldBorder().getSize() / 2 - 80;
        for (int n = 0; n < 5; n++) {
            double angle = Math.toRadians(30 + n * 72 + random.nextInt(20));
            double distance = Math.min(half, edge + 260 + random.nextInt(280));
            int x = (int) (Math.cos(angle) * distance), z = (int) (Math.sin(angle) * distance);
            if (Math.abs(x - CityLayout.CELL / 2) < 24) x += 40;
            if (Math.abs(z - CityLayout.CELL / 2) < 24) z += 40;
            sites.add(new Site("bunker-" + n, "bunker", "Bunker", x, z));
        }
        return sites;
    }

    public void load() {
        World world = plugin.scenario().world();
        if (world == null) return;
        for (Site site : sites(world)) {
            try {
                if (plugin.database().call(c -> Store.meta(c, "site:" + site.id())) != null) built.add(site.id());
            } catch (Exception ignored) {
                // Retried on the next approach; building twice is prevented by the in-memory set afterwards.
            }
        }
    }

    /** Known places shown on the web map (bunkers stay secret until found). */
    public List<Site> visible(World world) {
        List<Site> list = new ArrayList<>();
        for (Site site : sites(world)) if (site.kind().equals("prison")) list.add(site);
        return list;
    }

    /** Administrator: build a site now (its chunks load asynchronously first). Returns false for an unknown or built site. */
    public boolean force(World world, String id) {
        for (Site site : sites(world)) if (site.id().equals(id) && !built.contains(id)) { forced.add(id); return true; }
        return false;
    }

    public boolean built(String id) { return built.contains(id); }

    /** Every second: build a site once a survivor is within 160 blocks and all its chunks are loaded. */
    public void tick() {
        seconds++;
        World world = plugin.scenario().world();
        if (world == null || !plugin.scenario().isEnabled() || seconds % 3 != 0) return;
        for (Site site : sites(world)) {
            if (built.contains(site.id())) continue;
            boolean near = forced.contains(site.id());
            for (Player p : world.getPlayers())
                if (Math.hypot(p.getX() - site.x(), p.getZ() - site.z()) < 160) { near = true; break; }
            if (!near) continue;
            int half = site.kind().equals("prison") ? Math.max(PRISON_W, PRISON_D) / 2 + 4 : BUNKER;
            boolean ready = true;
            for (int cx = (site.x() - half) >> 4; cx <= (site.x() + half) >> 4; cx++)
                for (int cz = (site.z() - half) >> 4; cz <= (site.z() + half) >> 4; cz++)
                    if (!world.isChunkLoaded(cx, cz)) {
                        ready = false;
                        if (loading.add(site.id() + cx + ":" + cz)) world.getChunkAtAsync(cx, cz);
                    }
            if (!ready) continue;
            built.add(site.id());
            if (site.kind().equals("prison")) prison(world, site); else bunker(world, site);
            plugin.database().submit(c -> { Store.putMeta(c, "site:" + site.id(), "built"); return null; });
            plugin.events().add("site", site.label() + " apparu en X " + site.x() + ", Z " + site.z());
        }
    }

    // ------------------------------------------------------------------ prison

    private void prison(World world, Site site) {
        CityLayout layout = layout(world);
        int x0 = site.x() - PRISON_W / 2, z0 = site.z() - PRISON_D / 2, y = layout.groundY(site.x(), site.z());
        for (int x = x0 - 2; x < x0 + PRISON_W + 2; x++)
            for (int z = z0 - 2; z < z0 + PRISON_D + 2; z++) {
                for (int dy = 1; dy <= 14; dy++) world.getBlockAt(x, y + dy, z).setType(Material.AIR, false);
                for (int dy = 0; dy >= -6; dy--) {
                    Block b = world.getBlockAt(x, y + dy, z);
                    if (dy == 0) b.setType(x < x0 || x >= x0 + PRISON_W || z < z0 || z >= z0 + PRISON_D ? Material.GRASS_BLOCK : Material.GRAVEL, false);
                    else if (!b.getType().isSolid()) b.setType(Material.STONE, false);
                }
            }
        // Outer wall with barbed top and corner towers.
        for (int x = x0; x < x0 + PRISON_W; x++)
            for (int z = z0; z < z0 + PRISON_D; z++) {
                boolean edge = x == x0 || x == x0 + PRISON_W - 1 || z == z0 || z == z0 + PRISON_D - 1;
                if (!edge) continue;
                boolean gate = z == z0 + PRISON_D - 1 && Math.abs(x - site.x()) <= 1;
                for (int dy = 1; dy <= 5; dy++)
                    world.getBlockAt(x, y + dy, z).setType(gate ? (dy <= 3 ? Material.IRON_BARS : Material.STONE_BRICKS) : dy == 5 ? Material.IRON_BARS : Material.STONE_BRICKS, false);
            }
        for (int[] c : new int[][] {{x0, z0}, {x0 + PRISON_W - 4, z0}, {x0, z0 + PRISON_D - 4}, {x0 + PRISON_W - 4, z0 + PRISON_D - 4}}) {
            for (int dx = 0; dx < 4; dx++) for (int dz = 0; dz < 4; dz++) for (int dy = 1; dy <= 8; dy++) {
                boolean shell = dx == 0 || dx == 3 || dz == 0 || dz == 3;
                world.getBlockAt(c[0] + dx, y + dy, c[1] + dz).setType(dy == 8 ? Material.SPRUCE_SLAB : shell ? (dy == 7 ? Material.IRON_BARS : Material.CHISELED_STONE_BRICKS) : Material.AIR, false);
            }
            world.getBlockAt(c[0] + 1, y + 6, c[1] + 1).setType(Material.LANTERN, false);
        }
        // Cell block: corridor, six barred cells, the guards' office and the armoury.
        int bx0 = site.x() - 13, bz0 = site.z() - 8, bw = 26, bd = 12;
        box(world, bx0, y + 1, bz0, bx0 + bw - 1, y + 5, bz0 + bd - 1, Material.STONE_BRICKS, Material.SMOOTH_STONE_SLAB);
        for (int x = bx0 + 1; x < bx0 + bw - 1; x++) for (int z = bz0 + 1; z < bz0 + bd - 1; z++) world.getBlockAt(x, y, z).setType(Material.POLISHED_ANDESITE, false);
        for (int dy = 1; dy <= 2; dy++) world.getBlockAt(site.x(), y + dy, bz0 + bd - 1).setType(Material.AIR, false); // entrance
        int corridor = bz0 + bd - 4;
        for (int x = bx0 + 1; x <= bx0 + 18; x += 3) {
            for (int dy = 1; dy <= 4; dy++) {
                for (int z = bz0 + 1; z < corridor; z++) world.getBlockAt(x, y + dy, z).setType(Material.STONE_BRICKS, false);
                world.getBlockAt(x + 1, y + dy, corridor - 1).setType(Material.IRON_BARS, false);
                world.getBlockAt(x + 2, y + dy, corridor - 1).setType(Material.IRON_BARS, false);
            }
            world.getBlockAt(x + 1, y + 1, bz0 + 1).setType(Material.WHITE_CARPET, false);
            if (((x - bx0) / 3) % 2 == 0) container(world.getBlockAt(x + 2, y + 1, bz0 + 1), Material.BARREL, PlaceType.HOUSE);
        }
        for (int dy = 1; dy <= 4; dy++) for (int z = bz0 + 1; z < bz0 + bd - 1; z++) world.getBlockAt(bx0 + 20, y + dy, z).setType(Material.STONE_BRICKS, false);
        world.getBlockAt(bx0 + 20, y + 1, corridor + 1).setType(Material.AIR, false);
        world.getBlockAt(bx0 + 20, y + 2, corridor + 1).setType(Material.AIR, false);
        for (int n = 0; n < 4; n++) container(world.getBlockAt(bx0 + 22 + (n % 2) * 2, y + 1, bz0 + 2 + (n / 2) * 3), Material.CHEST, PlaceType.ARMORY);
        container(world.getBlockAt(bx0 + 24, y + 1, bz0 + 9), Material.CHEST, PlaceType.POLICE);
        for (int x = bx0 + 3; x < bx0 + bw - 2; x += 5) world.getBlockAt(x, y + 4, corridor + 1).setType(Material.LANTERN, false);
    }

    private static void box(World world, int x0, int y0, int z0, int x1, int y1, int z1, Material wall, Material roof) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) {
            boolean shell = x == x0 || x == x1 || z == z0 || z == z1;
            world.getBlockAt(x, y, z).setType(y == y1 ? roof : shell ? (y == y0 + 2 && (x + z) % 4 == 0 ? Material.IRON_BARS : wall) : Material.AIR, false);
        }
    }

    private void container(Block block, Material type, PlaceType place) {
        block.setType(type, false);
        if (block.getState() instanceof TileState tile) {
            tile.getPersistentDataContainer().set(plugin.keys().searchPlace, PersistentDataType.STRING, place.id());
            tile.update(true, false);
        }
    }

    // ------------------------------------------------------------------ bunker

    private void bunker(World world, Site site) {
        CityLayout layout = layout(world);
        int y = layout.groundY(site.x(), site.z());
        int top = y - 3, floor = y - 8, x0 = site.x() - 5, z0 = site.z() - 3;
        for (int x = x0 - 1; x <= x0 + 10; x++) for (int z = z0 - 1; z <= z0 + 7; z++) for (int yy = floor - 1; yy <= top; yy++) {
            boolean shell = x == x0 - 1 || x == x0 + 10 || z == z0 - 1 || z == z0 + 7 || yy == floor - 1 || yy == top;
            world.getBlockAt(x, yy, z).setType(shell ? Material.GRAY_CONCRETE : Material.AIR, false);
        }
        // Hatch and ladder shaft against the west wall.
        int hx = x0, hz = site.z();
        for (int yy = floor; yy <= y; yy++) {
            world.getBlockAt(hx - 1, yy, hz).setType(Material.GRAY_CONCRETE, false);
            Block shaft = world.getBlockAt(hx, yy, hz);
            if (yy == y) {
                shaft.setType(Material.IRON_TRAPDOOR, false);
                if (shaft.getBlockData() instanceof TrapDoor door) { door.setHalf(Bisected.Half.TOP); door.setFacing(BlockFace.EAST); shaft.setBlockData(door, false); }
            } else {
                shaft.setType(Material.LADDER, false);
                if (shaft.getBlockData() instanceof Ladder ladder) { ladder.setFacing(BlockFace.EAST); shaft.setBlockData(ladder, false); }
                for (BlockFace side : new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH})
                    if (yy > top) world.getBlockAt(hx, yy, hz).getRelative(side).setType(Material.GRAY_CONCRETE, false);
            }
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (dx != 0 || dz != 0) world.getBlockAt(hx + dx, y, hz + dz).setType(Material.POLISHED_ANDESITE, false);
        world.getBlockAt(hx + 2, y + 1, hz).setType(Material.IRON_BARS, false);
        for (int n = 0; n < 3; n++) container(world.getBlockAt(x0 + 9, floor, z0 + 1 + n * 2), Material.CHEST, PlaceType.ARMORY);
        container(world.getBlockAt(x0 + 5, floor, z0 + 6), Material.BARREL, PlaceType.MILITARY);
        bed(world.getBlockAt(x0 + 2, floor, z0), BlockFace.EAST);
        bed(world.getBlockAt(x0 + 2, floor, z0 + 6), BlockFace.EAST);
        world.getBlockAt(x0 + 5, top - 1, site.z()).setBlockData(Material.LANTERN.createBlockData("[hanging=true]"), false);
    }

    private static void bed(Block foot, BlockFace toward) {
        Block head = foot.getRelative(toward);
        var footData = (org.bukkit.block.data.type.Bed) Material.RED_BED.createBlockData();
        footData.setFacing(toward);
        footData.setPart(org.bukkit.block.data.type.Bed.Part.FOOT);
        var headData = (org.bukkit.block.data.type.Bed) footData.clone();
        headData.setPart(org.bukkit.block.data.type.Bed.Part.HEAD);
        foot.setBlockData(footData, false);
        head.setBlockData(headData, false);
    }

    // ------------------------------------------------------------------ cellars

    /** A third of the houses get a cellar under their floor, entered through a trapdoor. Done once per chunk. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunk(ChunkLoadEvent event) {
        if (!plugin.scenario().isScenarioWorld(event.getWorld())) return;
        Chunk chunk = event.getChunk();
        if (chunk.getPersistentDataContainer().has(cellars)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> cellars(event.getWorld(), chunk));
    }

    private void cellars(World world, Chunk chunk) {
        CityLayout layout = layout(world);
        if (layout == null || !chunk.isLoaded() || chunk.getPersistentDataContainer().has(cellars)) return;
        Map<String, CityLayout.Placement> houses = new LinkedHashMap<>();
        int x0 = chunk.getX() << 4, z0 = chunk.getZ() << 4;
        for (int x = x0; x < x0 + 16; x += 4) for (int z = z0; z < z0 + 16; z += 4)
            for (CityLayout.Placement p : layout.placementsAt(x, z))
                if (p.kind() == CityLayout.Kind.HOUSE_SMALL || p.kind() == CityLayout.Kind.HOUSE_LARGE || p.kind() == CityLayout.Kind.FARMHOUSE)
                    houses.put(p.minX() + ":" + p.minZ(), p);
        boolean complete = true;
        for (CityLayout.Placement house : houses.values()) {
            int bx = house.minX() + 2, bz = house.minZ() + 2;
            if ((bx >> 4) != chunk.getX() || (bz >> 4) != chunk.getZ()) continue; // owned by the chunk holding its corner
            if (Math.floorMod(house.seed() >>> 11, 3L) != 0 || house.maxX() - house.minX() < 8 || house.maxZ() - house.minZ() < 8) continue;
            if (!world.isChunkLoaded((bx + 6) >> 4, (bz + 6) >> 4) || !world.isChunkLoaded((bx - 1) >> 4, (bz - 1) >> 4)) { complete = false; continue; }
            cellar(world, bx, bz, house.floorY());
        }
        if (complete) chunk.getPersistentDataContainer().set(cellars, PersistentDataType.INTEGER, 1);
    }

    private void cellar(World world, int bx, int bz, int floorY) {
        int[][] corners = {{bx, bz, 1}, {bx + 4, bz, -1}, {bx, bz + 4, 1}, {bx + 4, bz + 4, -1}};
        int[] hatch = null;
        for (int[] c : corners)
            if (world.getBlockAt(c[0], floorY, c[1]).isPassable() && world.getBlockAt(c[0], floorY + 1, c[1]).isPassable()
                    && world.getBlockAt(c[0], floorY - 1, c[1]).getType().isSolid()) { hatch = c; break; }
        if (hatch == null) return;
        int bottom = floorY - 5;
        for (int x = bx - 1; x <= bx + 5; x++) for (int z = bz - 1; z <= bz + 5; z++) for (int y = bottom; y <= floorY - 2; y++) {
            boolean shell = x == bx - 1 || x == bx + 5 || z == bz - 1 || z == bz + 5 || y == bottom;
            world.getBlockAt(x, y, z).setType(shell ? (y == bottom ? Material.COBBLESTONE : Material.STONE_BRICKS) : Material.AIR, false);
        }
        Block door = world.getBlockAt(hatch[0], floorY - 1, hatch[1]);
        door.setType(Material.SPRUCE_TRAPDOOR, false);
        BlockFace facing = hatch[2] > 0 ? BlockFace.EAST : BlockFace.WEST;
        if (door.getBlockData() instanceof TrapDoor trap) { trap.setHalf(Bisected.Half.TOP); trap.setFacing(facing); door.setBlockData(trap, false); }
        for (int y = bottom + 1; y <= floorY - 2; y++) {
            Block ladder = world.getBlockAt(hatch[0], y, hatch[1]);
            ladder.setType(Material.LADDER, false);
            if (ladder.getBlockData() instanceof Ladder data) { data.setFacing(facing); ladder.setBlockData(data, false); }
        }
        int far = hatch[0] == bx ? bx + 4 : bx;
        container(world.getBlockAt(far, bottom + 1, bz + 4 - (hatch[1] == bz ? 0 : 4)), Material.CHEST, PlaceType.HOUSE);
        container(world.getBlockAt(far, bottom + 1, bz + (hatch[1] == bz ? 0 : 4)), Material.BARREL, PlaceType.GARAGE);
        world.getBlockAt(bx + 2, bottom + 1, bz + 2).setType(Material.LANTERN, false);
    }
}
