package org.duckcatmine.apocalypse.radar;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.items.ItemRegistry;

/** Vanilla off-hand map HUD. Contextual canvas: no player ever sees another player's radar. */
@SuppressWarnings("deprecation")
public final class RadarService implements Listener {
    private final DuckCatApocalypse plugin;
    private final Path file;
    private int mapId = -1;
    private MapView view;
    private final Renderer renderer = new Renderer();
    private final Map<UUID,Integer> ranges = new HashMap<>();
    private final Map<UUID,Long> lastZoom = new HashMap<>();

    public RadarService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
        file = plugin.getDataFolder().toPath().resolve("radar-map-id.txt");
        try { if (Files.isRegularFile(file)) mapId = Integer.parseInt(Files.readString(file).trim()); }
        catch (Exception e) { plugin.getLogger().warning("Identifiant du radar illisible : création d'une nouvelle carte."); }
    }

    private void bind(MapView map) {
        for (var old : map.getRenderers()) map.removeRenderer(old);
        map.setTrackingPosition(false);
        map.setUnlimitedTracking(false);
        map.setLocked(true);
        map.addRenderer(renderer);
        view = map;
    }

    @EventHandler public void onMap(MapInitializeEvent event) { if (event.getMap().getId() == mapId) bind(event.getMap()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { renderer.states.remove(event.getPlayer().getUniqueId()); ranges.remove(event.getPlayer().getUniqueId()); lastZoom.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void onZoom(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || !event.getPlayer().isSneaking()
                || !plugin.sessions().isSurvivor(event.getPlayer()) || !plugin.items().is(event.getItem(),ItemRegistry.RADAR)) return;
        event.setCancelled(true);
        var id=event.getPlayer().getUniqueId();long now=System.currentTimeMillis();
        if(now-lastZoom.getOrDefault(id,0L)<300)return;lastZoom.put(id,now);
        int range=ranges.getOrDefault(id,RadarMath.RANGE)==100?50:100;
        ranges.put(id,range);renderer.states.remove(id);
        event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text("Sonar · "+range+" blocs"));
    }

    /** Maps can have been loaded with the world before listeners were registered. Rebind them on startup too. */
    public void restore() {
        if (mapId >= 0) {
            MapView existing = Bukkit.getMap(mapId);
            if (existing != null) bind(existing);
        }
    }

    public void configure(ItemStack item) {
        if (view == null && plugin.scenario().world() != null) {
            MapView old = mapId < 0 ? null : Bukkit.getMap(mapId);
            bind(old == null ? Bukkit.createMap(plugin.scenario().world()) : old);
            mapId = view.getId();
            try { Files.writeString(file, String.valueOf(mapId)); }
            catch (Exception e) { plugin.getLogger().severe("Identifiant du radar non sauvegardé : " + e.getMessage()); }
        }
        if (view != null && item.getItemMeta() instanceof MapMeta meta) { meta.setMapView(view); item.setItemMeta(meta); }
    }

    public void give(Player player) {
        if (!plugin.sessions().isSurvivor(player)) return;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (plugin.items().is(item, ItemRegistry.RADAR)) { configure(item); player.getInventory().setItem(slot,item); return; }
        }
        ItemStack radar = plugin.items().create(ItemRegistry.RADAR, 1);
        if (player.getInventory().getItemInOffHand().isEmpty()) player.getInventory().setItemInOffHand(radar);
        else player.getInventory().addItem(radar).values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }

    private static byte color(int rgb) { return MapPalette.matchColor(new Color(rgb)); }
    private static final byte BG = color(0x091c26), GRID = color(0x254b59), RING = color(0x3c827e), BLUE = color(0x40a9ff), RED = color(0xff4a46), WHITE = color(0xe7f4f6);
    private static final byte[] BACKGROUND = background();
    private static byte[] background() {
        byte[] data = new byte[128 * 128];
        java.util.Arrays.fill(data, BG);
        for (int x = 0; x < 128; x++) for (int y = 0; y < 128; y++) {
            double radius = Math.hypot(x - 64, y - 64);
            if (radius <= 48 && (x == 64 || y == 64 || Math.abs(radius - 24) < .5 || Math.abs(radius - 12) < .5)) data[y * 128 + x] = GRID;
            if (Math.abs(radius - 49) < .8) data[y * 128 + x] = RING;
        }
        return data;
    }
    private static void dot(byte[] data, int x, int y, byte color, int size) {
        for (int dx = -size; dx <= size; dx++) for (int dy = -size; dy <= size; dy++)
            if (x + dx >= 0 && x + dx < 128 && y + dy >= 0 && y + dy < 128) data[(y + dy) * 128 + x + dx] = color;
    }
    private static final class State { long at; byte[] last; }
    private final class Renderer extends MapRenderer {
        final Map<UUID, State> states = new HashMap<>();
        Renderer() { super(true); }
        @Override public void render(MapView map, MapCanvas canvas, Player viewer) {
            if (!plugin.sessions().isSurvivor(viewer)) return;
            State state = states.computeIfAbsent(viewer.getUniqueId(), ignored -> new State());
            long now = System.currentTimeMillis();
            if (now - state.at < 500) return;
            state.at = now;
            byte[] pixels = BACKGROUND.clone();
            Location at = viewer.getLocation();
            int range=ranges.getOrDefault(viewer.getUniqueId(),RadarMath.RANGE);
            for (var other : plugin.mobs().mapPositions(viewer.getWorld(),org.duckcatmine.apocalypse.mobs.MobService.MAP_LIMIT)) {
                double dx=other.x()-at.getX(),dz=other.z()-at.getZ();
                if (dx*dx+dz*dz > range*range) continue;
                var pos = RadarMath.project(dx,dz,at.getYaw(),range);
                dot(pixels, pos.x(), pos.y(), RED, 1);
            }
            for (Player other : viewer.getWorld().getPlayers()) {
                if (other == viewer || other.isDead() || !plugin.sessions().isSurvivor(other)) continue;
                Location p = other.getLocation();
                var pos = RadarMath.project(p.getX() - at.getX(), p.getZ() - at.getZ(), at.getYaw(),range);
                boolean infected = plugin.infections().remaining(other.getUniqueId()) != null;
                dot(pixels, pos.x(), pos.y(), infected && !pos.far() ? RED : BLUE, pos.far() ? 1 : 2);
            }
            // Viewer triangle points forward; nearby dots can never obscure the centre.
            dot(pixels, 64, 64, WHITE, 1); dot(pixels, 64, 61, WHITE, 0);
            for (int i = 0; i < pixels.length; i++) if (state.last == null || state.last[i] != pixels[i]) canvas.setPixel(i % 128, i / 128, pixels[i]);
            state.last = pixels;
        }
    }
}
