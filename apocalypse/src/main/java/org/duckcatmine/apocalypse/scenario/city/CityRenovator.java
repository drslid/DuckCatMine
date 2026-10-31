package org.duckcatmine.apocalypse.scenario.city;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.scenario.GroundZeroGenerator;

/** Incremental, non-destructive update of existing city buildings. No terrain regeneration or world reset. */
public final class CityRenovator {
    public record Progress(String state, int buildings, int changed, int preserved, int skipped, String message) {}
    private record Change(BlueprintCanvas.Pos pos, BlockData before, BlockData after, PlaceType place) {}
    private final DuckCatApocalypse plugin;
    private BukkitTask task;
    private Progress progress = new Progress("idle", 0, 0, 0, 0, "Aucune rénovation en cours.");
    public CityRenovator(DuckCatApocalypse plugin) { this.plugin = plugin; }
    public Progress snapshot() { return progress; }
    public boolean running() { return task != null; }

    public void start(int x, int z, int radius) {
        World world = plugin.scenario().world();
        if (running()) throw new IllegalArgumentException("Une rénovation est déjà en cours.");
        if (world == null || !(world.getGenerator() instanceof GroundZeroGenerator)) throw new IllegalArgumentException("Ce monde n'utilise pas la ville Ground Zero.");
        if (!world.getPlayers().isEmpty()) throw new IllegalArgumentException("Fais sortir les joueurs de Ground Zero avant la rénovation.");
        if (radius < 16 || radius > 768) throw new IllegalArgumentException("Rayon attendu : 16 à 768 blocs.");
        CityLayout layout = CityLayout.of(world.getSeed());
        ArrayDeque<CityLayout.Placement> buildings = new ArrayDeque<>();
        for (int i = -CityLayout.CITY_CELLS; i <= CityLayout.CITY_CELLS; i++) for (int j = -CityLayout.CITY_CELLS; j <= CityLayout.CITY_CELLS; j++) {
            for (var p : layout.cell(i, j).placements()) if (eligible(p) && Math.abs(p.minX() - x) <= radius && Math.abs(p.minZ() - z) <= radius) buildings.add(p);
        }
        ArrayDeque<Change> changes = new ArrayDeque<>();
        boolean[] preparing = {false};
        List<org.bukkit.Chunk> tickets = new ArrayList<>();
        progress = new Progress("running", 0, 0, 0, 0, "Rénovation de " + buildings.size() + " bâtiments existants.");
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            // Pause automatically if anyone enters; no surprise construction around a player.
            if (!world.getPlayers().isEmpty()) { progress = new Progress("paused", progress.buildings(), progress.changed(), progress.preserved(), progress.skipped(), "En pause : un joueur est dans Ground Zero."); return; }
            if (preparing[0]) return;
            if (changes.isEmpty()) {
                for (var chunk : tickets) chunk.removePluginChunkTicket(plugin);
                tickets.clear();
                if (buildings.isEmpty()) {
                    task.cancel(); task = null;
                    progress = new Progress("done", progress.buildings(), progress.changed(), progress.preserved(), progress.skipped(), "Rénovation terminée. Ajouts et conteneurs conservés.");
                    plugin.events().add("maintenance", progress.message() + " " + progress.changed() + " blocs améliorés."); plugin.status().tick(); return;
                }
                var p = buildings.removeFirst();
                if (plugin.placed().claims().stream().anyMatch(c -> c.world().equals(world.getName()) && c.x() >= p.minX() - 12 && c.x() <= p.maxX() + 12 && c.z() >= p.minZ() - 12 && c.z() <= p.maxZ() + 12)) {
                    progress = new Progress("running", progress.buildings(), progress.changed(), progress.preserved(), progress.skipped() + 1, "Base revendiquée conservée."); return;
                }
                preparing[0] = true;
                List<java.util.concurrent.CompletableFuture<org.bukkit.Chunk>> loads = new ArrayList<>();
                for (int cx = p.minX() >> 4; cx <= p.maxX() >> 4; cx++) for (int cz = p.minZ() >> 4; cz <= p.maxZ() >> 4; cz++)
                    loads.add(world.getChunkAtAsync(cx, cz, false)); // Existing chunks only: unexplored terrain is untouched.
                java.util.concurrent.CompletableFuture.allOf(loads.toArray(java.util.concurrent.CompletableFuture[]::new))
                    .orTimeout(15, java.util.concurrent.TimeUnit.SECONDS).whenComplete((ignored, error) -> {
                        if (!plugin.isEnabled()) return;
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            preparing[0] = false;
                            if (error != null || loads.stream().anyMatch(f -> f.getNow(null) == null)) {
                                progress = new Progress("running", progress.buildings(), progress.changed(), progress.preserved(), progress.skipped() + 1, "Zone encore inexplorée ignorée."); return;
                            }
                            for (var future : loads) { var chunk = future.getNow(null); chunk.addPluginChunkTicket(plugin); tickets.add(chunk); }
                            // Chunk-load listeners have now indexed claims which were not resident before loading.
                            if (plugin.placed().claims().stream().anyMatch(c -> c.world().equals(world.getName()) && c.x() >= p.minX() - 12 && c.x() <= p.maxX() + 12 && c.z() >= p.minZ() - 12 && c.z() <= p.maxZ() + 12)) {
                                progress = new Progress("running", progress.buildings(), progress.changed(), progress.preserved(), progress.skipped() + 1, "Base revendiquée conservée."); return;
                            }
                            BlueprintCanvas old = new BlueprintCanvas(), next = new BlueprintCanvas();
                            Structures.render(p, old, layout, true); Structures.render(p, next, layout);
                            next.blocks.forEach((pos, after) -> {
                                BlockData before = old.blocks.get(pos);
                                if (before != null && !before.equals(after)) changes.add(new Change(pos, before, after, next.containers.get(pos)));
                            });
                            progress = new Progress("running", progress.buildings() + 1, progress.changed(), progress.preserved(), progress.skipped(), "Mise à jour des intérieurs et rayons.");
                        });
                    });
                return;
            }
            int changed = 0, preserved = 0;
            long deadline = System.nanoTime() + 2_000_000;
            for (int n = 0; n < 128 && !changes.isEmpty() && System.nanoTime() < deadline; n++) {
                Change c = changes.removeFirst(); var pos = c.pos();
                if (apply(world, c)) changed++; else preserved++;
            }
            progress = new Progress("running", progress.buildings(), progress.changed() + changed, progress.preserved() + preserved, progress.skipped(), progress.message());
        }, 1L, 1L);
        plugin.status().tick();
    }
    private boolean apply(World world, Change c) {
        var pos = c.pos();
        if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) return false;
        var block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        if (block.getState(false) instanceof TileState || plugin.placed().get(block) != null || !block.getBlockData().equals(c.before())) return false;
        block.setBlockData(c.after(), false);
        if (c.place() != null && block.getState() instanceof TileState tile) {
            tile.getPersistentDataContainer().set(plugin.keys().searchPlace, PersistentDataType.STRING, c.place().id()); tile.update(true, false);
        }
        return true;
    }

    public String testPreservation(Location origin) {
        World world = origin.getWorld();
        var layout = CityLayout.of(world.getSeed());
        var p = new CityLayout.Placement(CityLayout.Kind.HOUSE_LARGE, origin.getBlockX(), origin.getBlockZ(), 9, 10, org.bukkit.block.BlockFace.NORTH, 81, origin.getBlockY());
        BlueprintCanvas old = new BlueprintCanvas(), next = new BlueprintCanvas();
        Structures.render(p, old, layout, true); Structures.render(p, next, layout);
        var positions = new java.util.HashSet<>(old.blocks.keySet()); positions.addAll(next.blocks.keySet());
        for (var pos : positions) if (!world.getBlockAt(pos.x(),pos.y(),pos.z()).getType().isAir()) throw new IllegalStateException("Zone de test occupée");
        try {
            old.blocks.forEach((pos,data) -> world.getBlockAt(pos.x(),pos.y(),pos.z()).setBlockData(data,false));
            var sentinel = next.blocks.entrySet().stream().filter(e -> old.blocks.containsKey(e.getKey()) && old.blocks.get(e.getKey()).getMaterial().isAir() && !e.getValue().getMaterial().isAir()).findFirst().orElseThrow().getKey();
            var block = world.getBlockAt(sentinel.x(),sentinel.y(),sentinel.z()); block.setType(org.bukkit.Material.NETHERITE_BLOCK,false);
            var chest = old.containers.keySet().stream().filter(pos -> next.blocks.containsKey(pos) && !old.blocks.get(pos).equals(next.blocks.get(pos))).findFirst().orElseThrow();
            var container = (org.bukkit.block.Container) world.getBlockAt(chest.x(),chest.y(),chest.z()).getState(false);
            container.getInventory().setItem(0,org.bukkit.inventory.ItemStack.of(org.bukkit.Material.DIAMOND,3));
            int changed=0;
            for (var entry : next.blocks.entrySet()) {
                var before=old.blocks.get(entry.getKey());
                if (before != null && !before.equals(entry.getValue()) && apply(world,new Change(entry.getKey(),before,entry.getValue(),next.containers.get(entry.getKey())))) changed++;
            }
            if (changed < 10 || block.getType() != org.bukkit.Material.NETHERITE_BLOCK) throw new IllegalStateException("ajout joueur écrasé ou rénovation vide");
            var kept=world.getBlockAt(chest.x(),chest.y(),chest.z()).getState(false);
            if (!(kept instanceof org.bukkit.block.Container c) || c.getInventory().getItem(0) == null || c.getInventory().getItem(0).getAmount() != 3) throw new IllegalStateException("coffre altéré");
            return changed + " blocs rénovés, ajout en netherite et diamants du coffre conservés";
        } finally { for (var pos : positions) world.getBlockAt(pos.x(),pos.y(),pos.z()).setType(org.bukkit.Material.AIR,false); }
    }

    /** Paper integration test: all orientations, furnished rooms, reachable stair ends, shelf inventory markers. */
    public static String verifyBlueprints() {
        int checked = 0;
        CityLayout layout = CityLayout.of(4242);
        for (var kind : List.of(CityLayout.Kind.HOUSE_SMALL, CityLayout.Kind.HOUSE_LARGE, CityLayout.Kind.APARTMENT, CityLayout.Kind.SHOP, CityLayout.Kind.SUPERMARKET)) {
            for (var front : List.of(org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.WEST)) {
                int w = kind == CityLayout.Kind.SUPERMARKET ? 33 : kind == CityLayout.Kind.APARTMENT ? 15 : kind == CityLayout.Kind.SHOP ? 12 : 9;
                int d = kind == CityLayout.Kind.SUPERMARKET ? 29 : kind == CityLayout.Kind.APARTMENT ? 15 : 10;
                var p = new CityLayout.Placement(kind, 0, 0, w, d, front, 81, 70);
                BlueprintCanvas next = new BlueprintCanvas(); Structures.render(p, next, layout);
                if (next.containers.isEmpty()) throw new IllegalStateException(kind + " sans butin");
                for (var point : next.blocks.keySet()) if (point.x() < p.minX() || point.x() > p.maxX() || point.z() < p.minZ() || point.z() > p.maxZ())
                    throw new IllegalStateException(kind + " sort de sa parcelle");
                if (kind == CityLayout.Kind.APARTMENT || kind == CityLayout.Kind.HOUSE_LARGE) {
                    Frame frame = new Frame(next, p.minX(), p.minZ(), w,d,front);
                    int levels = kind == CityLayout.Kind.APARTMENT ? 2 + new java.util.Random(p.seed()).nextInt(3) : 1;
                    int u = kind == CityLayout.Kind.APARTMENT ? w / 2 : w - 2, v = kind == CityLayout.Kind.APARTMENT ? d - 6 : 2;
                    for (int level=0; level<levels; level++) for (int step=0; step<4; step++) {
                        int y = 71 + level*4 + step, x = frame.worldX(u,v+step), z = frame.worldZ(u,v+step);
                        var stair=next.blocks.get(new BlueprintCanvas.Pos(x,y,z));
                        if (stair == null || stair.getMaterial() != org.bukkit.Material.STONE_BRICK_STAIRS) throw new IllegalStateException("marche absente : " + kind);
                        for (int dy=1;dy<=2;dy++) {
                            var above=next.blocks.get(new BlueprintCanvas.Pos(x,y+dy,z));
                            if (above != null && !above.getMaterial().isAir()) throw new IllegalStateException("escalier obstrué : " + kind);
                        }
                    }
                }
                for (var entry : next.containers.entrySet()) {
                    var pos = entry.getKey();
                    ProbeCanvas probe = new ProbeCanvas(pos.x(), pos.y(), pos.z()); Structures.render(p, probe, layout);
                    if (probe.found() != entry.getValue()) throw new IllegalStateException("rayon non identifiable");
                }
                checked++;
            }
        }
        return checked + " plans, 4 orientations, meubles, escaliers et rayons cohérents";
    }

    private static boolean eligible(CityLayout.Placement p) { return switch (p.kind()) {
        case HOUSE_SMALL, HOUSE_LARGE, FARMHOUSE, APARTMENT, SHOP, SUPERMARKET -> true;
        default -> false;
    }; }
    private static boolean loaded(World w, CityLayout.Placement p) {
        for (int x = p.minX() >> 4; x <= p.maxX() >> 4; x++) for (int z = p.minZ() >> 4; z <= p.maxZ() >> 4; z++) if (!w.isChunkLoaded(x,z)) return false;
        return true;
    }
}
