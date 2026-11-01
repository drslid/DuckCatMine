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
    private final DuckCatApocalypse plugin;
    private BukkitTask task;
    private final List<org.bukkit.Chunk> tickets = new ArrayList<>();
    private List<RenovationAreas.Area> excluded = List.of();
    private String protectionProblem;
    private Progress progress = new Progress("idle", 0, 0, 0, 0, "Aucune rénovation en cours.");
    public CityRenovator(DuckCatApocalypse plugin) { this.plugin = plugin; reloadExclusions(); }
    public List<RenovationAreas.Area> exclusions() { return excluded; }
    private void reloadExclusions() {
        try { excluded=RenovationAreas.read(plugin.getDataFolder().toPath().resolve("renovation-exclusions.json"));protectionProblem=null; }
        catch(java.io.IOException e) { protectionProblem=e.getMessage(); }
    }
    private boolean protectedBuilding(World world,CityLayout.Placement p) {
        return excluded.stream().anyMatch(a->a.intersects(world.getName(),p.minX()-AccessKit.MARGIN,p.minZ()-AccessKit.MARGIN,p.maxX()+AccessKit.MARGIN,p.maxZ()+AccessKit.MARGIN));
    }
    public Progress snapshot() { return progress; }
    public boolean running() { return task != null; }
    /** Work may continue while every player stays inside an entirely excluded district. */
    public boolean blockedByPlayers() {
        World world=plugin.scenario().world();if(world==null)return false;
        return world.getPlayers().stream().anyMatch(player->{var at=player.getLocation();return excluded.stream().noneMatch(a->a.contains(world.getName(),at.getBlockX(),at.getBlockZ()));});
    }

    public void start(int x, int z, int radius) {
        World world = plugin.scenario().world();
        if (running()) throw new IllegalArgumentException("Une rénovation est déjà en cours.");
        reloadExclusions();
        if(protectionProblem!=null) throw new IllegalArgumentException(protectionProblem);
        if (world == null || !(world.getGenerator() instanceof GroundZeroGenerator)) throw new IllegalArgumentException("Ce monde n'utilise pas la ville Ground Zero.");
        if (blockedByPlayers()) throw new IllegalArgumentException("Les joueurs doivent rejoindre un quartier préservé ou quitter Ground Zero avant la rénovation.");
        if (radius < 16 || radius > 768) throw new IllegalArgumentException("Rayon attendu : 16 à 768 blocs.");
        CityLayout layout = CityLayout.of(world.getSeed());
        ArrayDeque<CityLayout.Placement> buildings = new ArrayDeque<>();
        for (int i = -CityLayout.CITY_CELLS; i <= CityLayout.CITY_CELLS; i++) for (int j = -CityLayout.CITY_CELLS; j <= CityLayout.CITY_CELLS; j++) {
            for (var p : layout.cell(i, j).placements()) if (eligible(p) && Math.abs(p.minX() - x) <= radius && Math.abs(p.minZ() - z) <= radius) buildings.add(p);
        }
        ArrayDeque<BuildingUpgrade.Batch> changes = new ArrayDeque<>();
        boolean[] preparing = {false};
        progress = new Progress("running", 0, 0, 0, 0, "Rénovation de " + buildings.size() + " bâtiments existants.");
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            // Pause automatically if anyone enters; no surprise construction around a player.
            if (blockedByPlayers()) { progress = new Progress("paused", progress.buildings(), progress.changed(), progress.preserved(), progress.skipped(), "En pause : un joueur est hors des quartiers préservés."); return; }
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
                if(protectedBuilding(world,p)) {
                    progress=new Progress("running",progress.buildings(),progress.changed(),progress.preserved(),progress.skipped()+1,"Quartier préservé : aucun travail.");return;
                }
                if (plugin.placed().claims().stream().anyMatch(c -> c.world().equals(world.getName()) && c.x() >= p.minX() - 12 && c.x() <= p.maxX() + 12 && c.z() >= p.minZ() - 12 && c.z() <= p.maxZ() + 12)) {
                    progress = new Progress("running", progress.buildings(), progress.changed(), progress.preserved(), progress.skipped() + 1, "Base revendiquée conservée."); return;
                }
                preparing[0] = true;
                List<java.util.concurrent.CompletableFuture<org.bukkit.Chunk>> loads = new ArrayList<>();
                BlueprintCanvas next = new BlueprintCanvas();Structures.render(p,next,layout);
                var needed=new java.util.HashSet<Long>();
                for(var at:next.blocks.keySet()) needed.add(((long)(at.x()>>4)<<32)|((at.z()>>4)&0xffffffffL));
                for(long chunk:needed) loads.add(world.getChunkAtAsync((int)(chunk>>32),(int)chunk,false));
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
                            try {
                                var plan=BuildingUpgrade.prepare(plugin,world,p,layout);
                                changes.addAll(plan.batches());
                                progress = new Progress("running", progress.buildings() + 1, progress.changed(), progress.preserved()+plan.preserved(), progress.skipped(), "Entrées, circulation et pièces — référence v"+plan.revision()+".");
                            } catch(IllegalStateException e) {
                                progress=new Progress("running",progress.buildings(),progress.changed(),progress.preserved(),progress.skipped()+1,"Bâtiment conservé : accès à vérifier.");
                                plugin.getLogger().info("Rénovation ignorée en "+p.minX()+","+p.minZ()+" : "+e.getMessage());
                            }
                        });
                    });
                return;
            }
            int changed = 0, preserved = 0;
            long deadline = System.nanoTime() + 2_000_000;
            for (int n = 0; n < 128 && !changes.isEmpty() && System.nanoTime() < deadline; n++) {
                var batch=changes.removeFirst();
                int done=BuildingUpgrade.apply(plugin,world,batch,excluded);
                changed+=done;preserved+=batch.edits().size()-done;
            }
            progress = new Progress("running", progress.buildings(), progress.changed() + changed, progress.preserved() + preserved, progress.skipped(), progress.message());
        }, 1L, 1L);
        plugin.status().tick();
    }
    public void close() {
        if(task!=null) task.cancel();task=null;
        for(var chunk:tickets) chunk.removePluginChunkTicket(plugin);
        tickets.clear();
    }

    public String testPreservation(Location origin) {
        World world = origin.getWorld();
        var layout = CityLayout.of(world.getSeed());
        var p = new CityLayout.Placement(CityLayout.Kind.HOUSE_LARGE, origin.getBlockX(), origin.getBlockZ(), 9, 10, org.bukkit.block.BlockFace.NORTH, 81, origin.getBlockY());
        BlueprintCanvas old = new BlueprintCanvas(), next = new BlueprintCanvas();
        Structures.renderRevision(p, old, layout, 2); Structures.render(p, next, layout);
        var positions = new java.util.HashSet<>(old.blocks.keySet()); positions.addAll(next.blocks.keySet());
        for (var pos : positions) if (!world.getBlockAt(pos.x(),pos.y(),pos.z()).getType().isAir()) throw new IllegalStateException("Zone de test occupée");
        try {
            old.blocks.forEach((pos,data) -> world.getBlockAt(pos.x(),pos.y(),pos.z()).setBlockData(data,false));
            Frame frame=new Frame(next,p.minX(),p.minZ(),p.width(),p.depth(),p.front());
            var sentinel = new BlueprintCanvas.Pos(frame.worldX(1,3),origin.getBlockY()+1,frame.worldZ(1,3));
            var block = world.getBlockAt(sentinel.x(),sentinel.y(),sentinel.z()); block.setType(org.bukkit.Material.NETHERITE_BLOCK,false);
            var chest = old.containers.keySet().stream().findFirst().orElseThrow();
            var container = (org.bukkit.block.Container) world.getBlockAt(chest.x(),chest.y(),chest.z()).getState(false);
            container.getInventory().setItem(0,org.bukkit.inventory.ItemStack.of(org.bukkit.Material.DIAMOND,3));
            int changed=0;var plan=BuildingUpgrade.prepare(plugin,world,p,layout);
            var guard=List.of(new RenovationAreas.Area(world.getName(),"Zone test protégée",p.minX()-12,p.minZ()-12,p.maxX()+12,p.maxZ()+12));
            for(var batch:plan.batches()) if(BuildingUpgrade.apply(plugin,world,batch,guard)!=0) throw new IllegalStateException("zone exclue modifiée");
            for(var batch:plan.batches()) changed+=BuildingUpgrade.apply(plugin,world,batch,List.of());
            if (changed < 2 || block.getType() != org.bukkit.Material.NETHERITE_BLOCK) throw new IllegalStateException("ajout joueur écrasé ou rénovation vide");
            var kept=world.getBlockAt(chest.x(),chest.y(),chest.z()).getState(false);
            if (!(kept instanceof org.bukkit.block.Container c) || c.getInventory().getItem(0) == null || c.getInventory().getItem(0).getAmount() != 3) throw new IllegalStateException("coffre altéré");
            return changed + " blocs rénovés, zone exclue respectée, ajout en netherite et diamants du coffre conservés";
        } finally { for (var pos : positions) {
            var block=world.getBlockAt(pos.x(),pos.y(),pos.z());
            if(block.getState(false) instanceof org.bukkit.block.Container box) org.duckcatmine.apocalypse.loot.SearchService.localInventory(box).clear();
            block.setType(org.bukkit.Material.AIR,false);
        } }
    }

    /** Private preview exported from the exact generator models, for before/after review. */
    public static void writePreview(java.nio.file.Path file) throws java.io.IOException {
        List<Object> models=new ArrayList<>();var layout=CityLayout.of(4242);
        for(var spec:List.of(new Object[]{CityLayout.Kind.HOUSE_SMALL,7,8},new Object[]{CityLayout.Kind.HOUSE_LARGE,9,10},new Object[]{CityLayout.Kind.APARTMENT,15,15},new Object[]{CityLayout.Kind.SHOP,12,15})) {
            var kind=(CityLayout.Kind)spec[0];int w=(int)spec[1],d=(int)spec[2];
            var p=new CityLayout.Placement(kind,0,0,w,d,org.bukkit.block.BlockFace.NORTH,81,70);
            java.util.Map<String,Object> versions=new java.util.LinkedHashMap<>();
            for(int revision:new int[]{2,3}) {
                var canvas=new BlueprintCanvas();Structures.renderRevision(p,canvas,layout,revision);
                List<Object> blocks=new ArrayList<>();
                for(var e:canvas.blocks.entrySet()) if(!e.getValue().getMaterial().isAir()) {
                    var pos=e.getKey();blocks.add(java.util.Map.of("x",pos.x(),"y",pos.y(),"z",pos.z(),"state",e.getValue().getAsString(),"solid",e.getValue().getMaterial().isSolid()));
                }
                versions.put(revision==2?"before":"after",blocks);
            }
            models.add(java.util.Map.of("kind",kind.name(),"width",w,"depth",d,"floorY",70,"versions",versions));
        }
        java.nio.file.Files.writeString(file,new com.google.gson.Gson().toJson(java.util.Map.of("models",models)));
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
                for (var point : next.blocks.keySet()) if (point.x() < p.minX()-AccessKit.MARGIN || point.x() > p.maxX()+AccessKit.MARGIN || point.z() < p.minZ()-AccessKit.MARGIN || point.z() > p.maxZ()+AccessKit.MARGIN)
                    throw new IllegalStateException(kind + " sort de sa parcelle");
                AccessAudit.verify(p,next);
                if (kind == CityLayout.Kind.APARTMENT || kind == CityLayout.Kind.HOUSE_LARGE) {
                    Frame frame = new Frame(next, p.minX(), p.minZ(), w,d,front);
                    int levels = kind == CityLayout.Kind.APARTMENT ? 2 + new java.util.Random(p.seed()).nextInt(3) : 1; // One flight fewer than occupied floors.
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
        for(var front:List.of(org.bukkit.block.BlockFace.NORTH,org.bukkit.block.BlockFace.EAST,org.bukkit.block.BlockFace.SOUTH,org.bukkit.block.BlockFace.WEST)) {
            for(int width:new int[]{7,8,9}) for(int depth:new int[]{8,9,10}) for(int seed:new int[]{7,81}) {
                var p=new CityLayout.Placement(CityLayout.Kind.HOUSE_SMALL,0,0,width,depth,front,seed,70);
                BlueprintCanvas canvas=new BlueprintCanvas();Structures.render(p,canvas,layout);AccessAudit.verify(p,canvas);checked++;
            }
        }
        return checked + " plans : accès depuis la rue, pièces, lits, coffres, escaliers et portes cohérents";
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
