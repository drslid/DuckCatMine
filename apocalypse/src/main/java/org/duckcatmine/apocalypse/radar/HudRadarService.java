package org.duckcatmine.apocalypse.radar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;

/**
 * Top-right minimap drawn with the resource pack's radar font in an invisible boss bar: coloured surroundings, infected
 * as red dots, survivors in blue, loot containers as green squares. North is up. Clients without the pack keep the
 * hand-held sonar.
 */
public final class HudRadarService {
    public static final int SIZE = 23, CELL = 4, HALF = SIZE / 2;
    public static final int RED = 0xFF3B30, BLUE = 0x3FA9FF, GREEN = 0x37E05A, SELF = 0xFFFFFF, FACING = 0xFFD54A, UNKNOWN = 0x141414;
    public static final Key FONT = Key.key("duckcatmine", "radar");
    /** Right shift of the map from the screen centre, in GUI pixels. */
    public static final int OFFSET = 100;
    private record Cached(int color, long until) {}
    private final DuckCatApocalypse plugin;
    private final Map<Long, Cached> terrain = new HashMap<>();
    private final Map<Long, List<int[]>> containers = new HashMap<>();
    private final Map<Long, Long> containersUntil = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private long frames;

    public HudRadarService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    /** Characters of the radar font: cell glyph of a row, and negative/positive spaces as powers of two. */
    public static char row(int r) { return (char) (0xE200 + r); }

    public static String space(int pixels) {
        StringBuilder out = new StringBuilder();
        int left = Math.abs(pixels);
        for (int bit = 7; bit >= 0; bit--) while (left >= (1 << bit)) { out.append((char) ((pixels < 0 ? 0xE300 : 0xE310) + bit)); left -= 1 << bit; }
        return out.toString();
    }

    /** One component for the whole grid: rows are stacked by the font's per-row ascent, colours merge into runs. */
    public static Component compose(int[][] colors) {
        int width = SIZE * 3;
        TextComponent.Builder root = Component.text().font(FONT).shadowColor(ShadowColor.none());
        root.append(Component.text(space(2 * OFFSET + width)));
        for (int r = 0; r < SIZE; r++) {
            if (r > 0) root.append(Component.text(space(-width)));
            char glyph = row(r);
            int start = 0;
            for (int c = 1; c <= SIZE; c++) {
                if (c < SIZE && colors[r][c] == colors[r][start]) continue;
                root.append(Component.text(String.valueOf(glyph).repeat(c - start), TextColor.color(colors[r][start])));
                start = c;
            }
        }
        return root.build();
    }

    /** Every 5 ticks; redraws twice a second. */
    public void tick() {
        if (++frames % 2 != 0) return;
        World world = plugin.scenario().world();
        long now = System.currentTimeMillis();
        if (frames % 120 == 0) { terrain.values().removeIf(c -> c.until() < now); containersUntil.entrySet().removeIf(e -> { if (e.getValue() < now) { containers.remove(e.getKey()); return true; } return false; }); }
        for (var entry : new ArrayList<>(bars.entrySet())) {
            Player p = plugin.getServer().getPlayer(entry.getKey());
            if (p == null || world == null || !p.getWorld().equals(world) || !wanted(p)) {
                if (p != null) p.hideBossBar(entry.getValue());
                bars.remove(entry.getKey());
            }
        }
        if (world == null || !plugin.scenario().isEnabled()) return;
        var mobs = plugin.mobs().mapPositions(world, org.duckcatmine.apocalypse.mobs.MobService.MAP_LIMIT);
        for (Player player : world.getPlayers()) {
            if (!wanted(player)) continue;
            int[][] grid = grid(world, player, mobs, now);
            BossBar bar = bars.get(player.getUniqueId());
            Component title = compose(grid);
            if (bar == null) {
                bar = BossBar.bossBar(title, 0f, BossBar.Color.PINK, BossBar.Overlay.PROGRESS);
                bars.put(player.getUniqueId(), bar);
                player.showBossBar(bar);
            } else bar.name(title);
        }
    }

    private boolean wanted(Player player) {
        return plugin.sessions().isSurvivor(player) && !player.isDead() && plugin.horrorModels().ready(player.getUniqueId());
    }

    private int[][] grid(World world, Player player, List<org.duckcatmine.apocalypse.mobs.MobService.MapPosition> mobs, long now) {
        int gx = Math.floorDiv(player.getLocation().getBlockX(), CELL), gz = Math.floorDiv(player.getLocation().getBlockZ(), CELL);
        int[][] grid = new int[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) grid[r][c] = terrain(world, gx + c - HALF, gz + r - HALF, now);
        for (int cx = (gx - HALF) * CELL >> 4; cx <= (gx + HALF) * CELL >> 4; cx++)
            for (int cz = (gz - HALF) * CELL >> 4; cz <= (gz + HALF) * CELL >> 4; cz++)
                for (int[] at : containers(world, cx, cz, now)) mark(grid, gx, gz, at[0], at[1], GREEN);
        for (var mob : mobs) mark(grid, gx, gz, mob.x(), mob.z(), RED);
        for (Player other : world.getPlayers())
            if (other != player && !other.isDead() && plugin.sessions().isSurvivor(other))
                mark(grid, gx, gz, other.getLocation().getBlockX(), other.getLocation().getBlockZ(), BLUE);
        grid[HALF][HALF] = SELF;
        double yaw = Math.toRadians(player.getLocation().getYaw());
        int fc = HALF + (int) Math.round(-Math.sin(yaw)), fr = HALF + (int) Math.round(Math.cos(yaw));
        if (fc != HALF || fr != HALF) grid[fr][fc] = FACING;
        return grid;
    }

    private static void mark(int[][] grid, int gx, int gz, int x, int z, int color) {
        int c = Math.floorDiv(x, CELL) - gx + HALF, r = Math.floorDiv(z, CELL) - gz + HALF;
        if (c >= 0 && c < SIZE && r >= 0 && r < SIZE) grid[r][c] = color;
    }

    private int terrain(World world, int gx, int gz, long now) {
        long key = ((long) gx << 32) ^ (gz & 0xFFFFFFFFL);
        Cached cached = terrain.get(key);
        if (cached != null && cached.until() > now) return cached.color();
        int x = gx * CELL + CELL / 2, z = gz * CELL + CELL / 2;
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return UNKNOWN;
        Block top = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
        int color = color(top, world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR));
        terrain.put(key, new Cached(color, now + 30_000));
        return color;
    }

    /** Muted terrain palette: water, trees, grass, sand, roads, roofs. */
    static int color(Block top, int floor) {
        Material type = top.getType();
        if (type == Material.WATER || top.isLiquid()) return 0x2B5DA8;
        if (Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type)) return 0x2D5A27;
        if (type == Material.GRASS_BLOCK || type == Material.SHORT_GRASS || type == Material.FARMLAND || type == Material.MOSS_BLOCK) return 0x4C7A36;
        if (type == Material.DIRT || type == Material.COARSE_DIRT || type == Material.PODZOL || type == Material.DIRT_PATH) return 0x6E5A3A;
        if (type == Material.SAND || type == Material.SANDSTONE || type == Material.GRAVEL) return 0xB8A877;
        String name = type.name();
        if (name.contains("CONCRETE") && !name.contains("POWDER") || name.contains("ANDESITE") || type == Material.STONE || type == Material.BLACKSTONE)
            return top.getY() - floor > 3 ? 0x7A6250 : 0x58595B;
        if (name.contains("SLAB") || name.contains("STAIRS") || name.contains("PLANKS") || name.contains("BRICK") || name.contains("TERRACOTTA")) return 0x7A6250;
        return 0x5E625A;
    }

    private List<int[]> containers(World world, int cx, int cz, long now) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        Long until = containersUntil.get(key);
        if (until != null && until > now) return containers.getOrDefault(key, List.of());
        List<int[]> found = new ArrayList<>();
        if (world.isChunkLoaded(cx, cz)) {
            Chunk chunk = world.getChunkAt(cx, cz);
            for (var state : chunk.getTileEntities(block -> block.getType() == Material.CHEST || block.getType() == Material.BARREL, false))
                if (state instanceof TileState tile && !tile.getPersistentDataContainer().has(plugin.keys().playerPlaced)
                        && !tile.getPersistentDataContainer().has(plugin.keys().searchRolled, PersistentDataType.LONG))
                    found.add(new int[] {state.getX(), state.getZ()});
        }
        containers.put(key, found);
        containersUntil.put(key, now + 20_000);
        return found;
    }

    public void stop() {
        for (var entry : bars.entrySet()) {
            Player p = plugin.getServer().getPlayer(entry.getKey());
            if (p != null) p.hideBossBar(entry.getValue());
        }
        bars.clear();
    }
}
