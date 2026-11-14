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
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;

/**
 * GPS-style radar in the top-right corner, drawn pixel by pixel with the resource pack's radar font inside an invisible
 * boss bar. The map turns with the player (forward is up) inside a round frame; infected are red, survivors blue, loot
 * containers green, supply crates white. Arrows on the rim point to every other survivor and to crates out of range.
 * Below it: how many infected are onto you, and the nearest crate's countdown and distance. Without the pack, a plain
 * boss bar shows the detection gauge.
 */
public final class HudRadarService {
    public static final int DIAMETER = 57, RADIUS = DIAMETER / 2, RANGE = 128, GAUGE_ROWS = 3, ROWS = DIAMETER + 1 + GAUGE_ROWS;
    public static final double BLOCKS_PER_PIXEL = (double) RANGE / RADIUS;
    public static final int CELL = 4;
    public static final int RED = 0xFF3B30, BOSS = 0xFF00A0, BLUE = 0x4FB4FF, GREEN = 0x37E05A, WHITE = 0xFFFFFF, RIM = 0x1B2126,
            NORTH = 0xE53935, UNKNOWN = 0x101315, GAUGE_BACK = 0x2A2F33;
    public static final Key FONT = Key.key("duckcatmine", "radar");
    /** Map's left edge, right of the screen centre, in GUI pixels: near the right edge at common GUI scales. */
    public static final int OFFSET = 150;
    private static final String DIGITS = "0123456789:m▣";
    private record Cached(int color, long until) {}
    private final DuckCatApocalypse plugin;
    private final Map<Long, Cached> terrain = new HashMap<>();
    private final Map<Long, List<int[]>> containers = new HashMap<>();
    private final Map<Long, Long> containersUntil = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>(), gauges = new HashMap<>();
    private int sampled;
    private long frames;

    public HudRadarService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ text layout (font characters)

    public static char row(int r) { return (char) (0xE200 + r); }

    public static String space(int pixels) {
        StringBuilder out = new StringBuilder();
        int left = Math.abs(pixels);
        for (int bit = 7; bit >= 0; bit--) while (left >= (1 << bit)) { out.append((char) ((pixels < 0 ? 0xE300 : 0xE310) + bit)); left -= 1 << bit; }
        return out.toString();
    }

    /** Width in GUI pixels of {@link #digits}: glyphs advance 4, the narrow ':' advances 3. */
    public static int textWidth(String text) {
        int width = 0;
        for (char c : text.toCharArray()) width += c == ':' ? 3 : 4;
        return width;
    }

    /** Small 3×5 digits below the map (plus ':' 'm' and a crate icon); unknown characters become a space. */
    public static String digits(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            int index = DIGITS.indexOf(c);
            out.append(index < 0 ? space(4) : String.valueOf((char) (0xE400 + index)));
        }
        return out.toString();
    }

    /**
     * One component: each pixel is a 1 px glyph followed by a 1 px step back, so pixels touch; rows are stacked by the
     * font's per-row ascent and each row returns to the left edge. The whole title nets to a constant width.
     */
    public static Component compose(Integer[][] pixels, String info) {
        TextComponent.Builder root = Component.text().font(FONT).shadowColor(ShadowColor.none());
        root.append(Component.text(space(2 * OFFSET)));
        for (int r = 0; r < pixels.length; r++) {
            Integer[] line = pixels[r];
            int cursor = 0, c = 0;
            String glyph = row(r) + space(-1);
            while (c < line.length) {
                int start = c;
                Integer color = line[c];
                while (c < line.length && java.util.Objects.equals(line[c], color)) c++;
                if (color == null) continue;
                if (start > cursor) root.append(Component.text(space(start - cursor)));
                root.append(Component.text(glyph.repeat(c - start), TextColor.color(color)));
                cursor = c;
            }
            if (cursor > 0) root.append(Component.text(space(-cursor)));
        }
        if (info != null && !info.isEmpty()) {
            String text = digits(info);
            int width = textWidth(info), start = Math.max(0, (DIAMETER - width) / 2);
            root.append(Component.text(space(start)));
            root.append(Component.text(text, NamedTextColor.WHITE));
            root.append(Component.text(space(-(start + width))));
        }
        return root.build();
    }

    // ------------------------------------------------------------------ geometry

    /** Screen offset (pixels, x right, y down) of a world offset for a player looking at {@code yaw}: forward is up. */
    public static double[] screen(double dx, double dz, float yaw) {
        double rad = Math.toRadians(yaw), fx = -Math.sin(rad), fz = Math.cos(rad), rx = -Math.cos(rad), rz = -Math.sin(rad);
        return new double[] {(dx * rx + dz * rz) / BLOCKS_PER_PIXEL, -(dx * fx + dz * fz) / BLOCKS_PER_PIXEL};
    }

    private static boolean inside(int x, int y) {
        double dx = x - RADIUS, dy = y - RADIUS;
        return dx * dx + dy * dy <= RADIUS * RADIUS + RADIUS;
    }

    private static void put(Integer[][] px, int x, int y, int color) {
        if (y >= 0 && y < DIAMETER && x >= 0 && x < DIAMETER && inside(x, y)) px[y][x] = color;
    }

    private static void blob(Integer[][] px, double sx, double sy, int color, int size) {
        int cx = (int) Math.round(RADIUS + sx), cy = (int) Math.round(RADIUS + sy);
        for (int dx = 0; dx < size; dx++) for (int dy = 0; dy < size; dy++) put(px, cx + dx - size / 2, cy + dy - size / 2, color);
    }

    /** A small chevron on the rim pointing towards a target. */
    static void arrow(Integer[][] px, double sx, double sy, int color) {
        double length = Math.hypot(sx, sy);
        if (length < 0.001) return;
        double ux = sx / length, uy = sy / length, nx = -uy, ny = ux;
        for (int t = 0; t <= 3; t++) {
            double along = RADIUS - 1.5 - t;
            for (double k = -t * 0.6; k <= t * 0.6 + 1e-6; k += 0.5)
                put(px, (int) Math.round(RADIUS + ux * along + nx * k), (int) Math.round(RADIUS + uy * along + ny * k), color);
        }
    }

    // ------------------------------------------------------------------ rendering

    /** Every 5 ticks: four frames a second keep the turning map smooth. */
    public void tick() {
        frames++;
        World world = plugin.scenario().world();
        long now = System.currentTimeMillis();
        sampled = 0;
        if (frames % 240 == 0) {
            terrain.values().removeIf(c -> c.until() < now);
            containersUntil.entrySet().removeIf(e -> { if (e.getValue() < now) { containers.remove(e.getKey()); return true; } return false; });
        }
        cleanup(bars, world, true);
        cleanup(gauges, world, false);
        if (world == null || !plugin.scenario().isEnabled()) return;
        var mobs = plugin.mobs().mapPositions(world, org.duckcatmine.apocalypse.mobs.MobService.MAP_LIMIT);
        var drops = plugin.supplyDrops().snapshot();
        for (Player player : world.getPlayers()) {
            if (!plugin.sessions().isSurvivor(player) || player.isDead()) continue;
            double gauge = Math.min(1, plugin.mobs().awareness(player) / 6.0);
            if (!plugin.horrorModels().ready(player.getUniqueId())) {
                if (frames % 4 == 0) fallback(player, gauge);
                continue;
            }
            Integer[][] pixels = draw(world, player, mobs, drops, gauge, now);
            String info = info(player, drops);
            BossBar bar = bars.get(player.getUniqueId());
            Component title = compose(pixels, info);
            if (bar == null) {
                bar = BossBar.bossBar(title, 0f, BossBar.Color.PINK, BossBar.Overlay.PROGRESS);
                bars.put(player.getUniqueId(), bar);
                player.showBossBar(bar);
            } else bar.name(title);
        }
    }

    private void cleanup(Map<UUID, BossBar> map, World world, boolean needsPack) {
        for (var entry : new ArrayList<>(map.entrySet())) {
            Player p = plugin.getServer().getPlayer(entry.getKey());
            boolean keep = p != null && world != null && p.getWorld().equals(world) && plugin.sessions().isSurvivor(p) && !p.isDead()
                    && plugin.horrorModels().ready(p.getUniqueId()) == needsPack;
            if (keep) continue;
            if (p != null) p.hideBossBar(entry.getValue());
            map.remove(entry.getKey());
        }
    }

    private void fallback(Player player, double gauge) {
        BossBar bar = gauges.get(player.getUniqueId());
        Component title = Component.text(gauge <= 0 ? "Personne ne t'a repéré" : gauge >= 1 ? "Repéré par une foule d'infectés : cache-toi !" : "Des infectés t'ont repéré",
                gauge <= 0 ? NamedTextColor.GREEN : gauge < 0.5 ? NamedTextColor.YELLOW : NamedTextColor.RED);
        if (bar == null) {
            bar = BossBar.bossBar(title, (float) gauge, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_6);
            gauges.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        }
        bar.name(title);
        bar.progress((float) gauge);
        bar.color(gauge < 0.5 ? BossBar.Color.YELLOW : BossBar.Color.RED);
    }

    private String info(Player player, List<Map<String, Object>> drops) {
        Map<String, Object> nearest = null;
        double best = Double.MAX_VALUE;
        for (var drop : drops) {
            double d = Math.hypot(((Number) drop.get("x")).doubleValue() + 0.5 - player.getX(), ((Number) drop.get("z")).doubleValue() + 0.5 - player.getZ());
            if (d < best) { best = d; nearest = drop; }
        }
        if (nearest == null) return null;
        long left = ((Number) nearest.get("secondsLeft")).longValue();
        return "▣ " + left / 60 + ":" + String.format("%02d", left % 60) + " " + Math.round(best) + "m";
    }

    private Integer[][] draw(World world, Player player, List<org.duckcatmine.apocalypse.mobs.MobService.MapPosition> mobs,
                             List<Map<String, Object>> drops, double gauge, long now) {
        Integer[][] px = new Integer[ROWS][DIAMETER];
        Location at = player.getLocation();
        float yaw = at.getYaw();
        double rad = Math.toRadians(yaw), fx = -Math.sin(rad), fz = Math.cos(rad), rx = -Math.cos(rad), rz = -Math.sin(rad);
        for (int y = 0; y < DIAMETER; y++)
            for (int x = 0; x < DIAMETER; x++) {
                if (!inside(x, y)) continue;
                double u = (x - RADIUS) * BLOCKS_PER_PIXEL, v = (y - RADIUS) * BLOCKS_PER_PIXEL;
                double wx = at.getX() + u * rx - v * fx, wz = at.getZ() + u * rz - v * fz;
                px[y][x] = terrain(world, Math.floorDiv((int) Math.floor(wx), CELL), Math.floorDiv((int) Math.floor(wz), CELL), now);
            }
        int gx = Math.floorDiv(at.getBlockX(), 16), gz = Math.floorDiv(at.getBlockZ(), 16), reach = RANGE / 16 + 1;
        for (int cx = gx - reach; cx <= gx + reach; cx++)
            for (int cz = gz - reach; cz <= gz + reach; cz++)
                for (int[] c : containers(world, cx, cz, now)) {
                    double[] s = screen(c[0] + 0.5 - at.getX(), c[1] + 0.5 - at.getZ(), yaw);
                    if (s[0] * s[0] + s[1] * s[1] < (RADIUS - 1) * (RADIUS - 1)) blob(px, s[0], s[1], GREEN, 1);
                }
        for (var mob : mobs) {
            double[] s = screen(mob.x() + 0.5 - at.getX(), mob.z() + 0.5 - at.getZ(), yaw);
            if (s[0] * s[0] + s[1] * s[1] >= (RADIUS - 1) * (RADIUS - 1)) continue;
            boolean boss = "nemesis".equals(mob.type()) || "abomination".equals(mob.type());
            blob(px, s[0], s[1], boss ? BOSS : RED, boss ? 3 : 1);
        }
        for (var drop : drops) {
            double[] s = screen(((Number) drop.get("x")).doubleValue() + 0.5 - at.getX(), ((Number) drop.get("z")).doubleValue() + 0.5 - at.getZ(), yaw);
            if (s[0] * s[0] + s[1] * s[1] < (RADIUS - 3) * (RADIUS - 3)) blob(px, s[0], s[1], WHITE, 3);
            else arrow(px, s[0], s[1], WHITE);
        }
        for (Player other : world.getPlayers()) {
            if (other == player || other.isDead() || !plugin.sessions().isSurvivor(other)) continue;
            double[] s = screen(other.getX() - at.getX(), other.getZ() - at.getZ(), yaw);
            if (s[0] * s[0] + s[1] * s[1] < (RADIUS - 4) * (RADIUS - 4)) blob(px, s[0], s[1], BLUE, 2);
            arrow(px, s[0], s[1], BLUE);
        }
        // Rim, north mark and the player's own arrow (always pointing up).
        for (int y = 0; y < DIAMETER; y++)
            for (int x = 0; x < DIAMETER; x++)
                if (inside(x, y) && Math.hypot(x - RADIUS, y - RADIUS) > RADIUS - 1) px[y][x] = RIM;
        double[] north = screen(0, -1, yaw);
        double length = Math.hypot(north[0], north[1]);
        int nx = (int) Math.round(RADIUS + north[0] / length * (RADIUS - 5)), ny = (int) Math.round(RADIUS + north[1] / length * (RADIUS - 5));
        String[] letter = {"#..#", "##.#", "#.##", "#..#", "#..#"};
        for (int y = -3; y <= 3; y++) for (int x = -3; x <= 2; x++) put(px, nx + x, ny + y, RIM);
        for (int y = 0; y < letter.length; y++)
            for (int x = 0; x < 4; x++) if (letter[y].charAt(x) == '#') put(px, nx - 2 + x, ny - 2 + y, NORTH);
        int[][] self = {{0, -2}, {-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {0, 0}, {1, 0}, {-2, 1}, {-1, 1}, {1, 1}, {2, 1}};
        for (int[] p : self) put(px, RADIUS + p[0], RADIUS + p[1], WHITE);
        // Detection gauge under the map: empty when nobody noticed you, full when many infected are onto you.
        int width = 41, left = (DIAMETER - width) / 2, filled = (int) Math.round(gauge * (width - 2));
        int color = gauge < 0.34 ? 0x3DDC84 : gauge < 0.67 ? 0xFFC233 : 0xFF3B30;
        for (int r = 0; r < GAUGE_ROWS; r++)
            for (int x = 0; x < width; x++) {
                boolean frame = r == 0 || r == GAUGE_ROWS - 1 || x == 0 || x == width - 1;
                px[DIAMETER + 1 + r][left + x] = frame ? GAUGE_BACK : x - 1 < filled ? color : 0x15191C;
            }
        return px;
    }

    private int terrain(World world, int gx, int gz, long now) {
        long key = ((long) gx << 32) ^ (gz & 0xFFFFFFFFL);
        Cached cached = terrain.get(key);
        if (cached != null && cached.until() > now) return cached.color();
        int x = gx * CELL + CELL / 2, z = gz * CELL + CELL / 2;
        if (!world.isChunkLoaded(x >> 4, z >> 4) || sampled > 900) return cached != null ? cached.color() : UNKNOWN;
        sampled++;
        Block top = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
        int color = shade(color(top, world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR)), top.getY());
        terrain.put(key, new Cached(color, now + 60_000));
        return color;
    }

    /** Higher ground is a little lighter, lower ground a little darker, so relief and buildings read at a glance. */
    static int shade(int rgb, int y) {
        double f = Math.max(0.75, Math.min(1.25, 1 + (y - 70) * 0.012));
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * f), g = (int) Math.min(255, ((rgb >> 8) & 255) * f), b = (int) Math.min(255, (rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    /** Muted terrain palette: water, trees, grass, fields, sand, roads, sidewalks, roofs. */
    static int color(Block top, int floor) {
        Material type = top.getType();
        if (type == Material.WATER || top.isLiquid()) return 0x2B5DA8;
        if (Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type)) return 0x2D5A27;
        if (type == Material.GRASS_BLOCK || type == Material.SHORT_GRASS || type == Material.TALL_GRASS || type == Material.MOSS_BLOCK) return 0x4C7A36;
        if (type == Material.FARMLAND || type == Material.WHEAT || type == Material.HAY_BLOCK) return 0x8E7A3C;
        if (type == Material.DIRT || type == Material.COARSE_DIRT || type == Material.PODZOL || type == Material.DIRT_PATH) return 0x6E5A3A;
        if (type == Material.SAND || type == Material.SANDSTONE || type == Material.GRAVEL) return 0xB8A877;
        String name = type.name();
        boolean raised = top.getY() - floor > 3;
        if (name.contains("CONCRETE") && !name.contains("POWDER") || name.contains("ANDESITE") || type == Material.STONE || type == Material.BLACKSTONE)
            return raised ? 0x8A6E5A : name.contains("BLACK") || name.contains("GRAY") ? 0x3C3F42 : 0x6E7175;
        if (name.contains("SLAB") || name.contains("STAIRS") || name.contains("PLANKS") || name.contains("BRICK") || name.contains("TERRACOTTA")
                || name.contains("TILES") || name.contains("WOOL"))
            return raised ? 0x9A6F55 : 0x7A6250;
        if (name.contains("GLASS")) return 0x5E8FA8;
        return raised ? 0x7D7468 : 0x5E625A;
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
        for (var map : List.of(bars, gauges))
            for (var entry : map.entrySet()) {
                Player p = plugin.getServer().getPlayer(entry.getKey());
                if (p != null) p.hideBossBar(entry.getValue());
            }
        bars.clear();
        gauges.clear();
    }
}
