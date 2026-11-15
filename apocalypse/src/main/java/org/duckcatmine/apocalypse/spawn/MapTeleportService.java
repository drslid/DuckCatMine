package org.duckcatmine.apocalypse.spawn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.persistence.Store;

/** Async chunk preparation, main-thread safety checks, and bounded receipts for the panel's existing status feed. */
public final class MapTeleportService {
    public record Receipt(String id, String player, String state, String message, long at) {}
    private final DuckCatApocalypse plugin;
    private final Map<String, Receipt> receipts = new LinkedHashMap<>();
    private final Map<UUID, String> pending = new java.util.HashMap<>();
    public MapTeleportService(DuckCatApocalypse plugin) { this.plugin = plugin; }
    public List<Receipt> snapshot() {
        long cutoff = System.currentTimeMillis() - 120_000;
        receipts.values().removeIf(r -> r.at() < cutoff);
        return List.copyOf(receipts.values());
    }
    private void receipt(String id, Player player, String state, String message) {
        receipts.put(id, new Receipt(id, player.getName(), state, message, System.currentTimeMillis()));
        while (receipts.size() > 32) receipts.remove(receipts.keySet().iterator().next());
        plugin.status().tick();
    }
    public String request(Player player, int x, Integer y, int z, String actor) {
        if (!ready(player) || pending.containsKey(player.getUniqueId())) throw new IllegalArgumentException("Survivant indisponible ou déplacement déjà en préparation.");
        World world = plugin.scenario().world();
        if (Math.abs((long) x) > 30_000_000 || Math.abs((long) z) > 30_000_000 || (y != null && (y < world.getMinHeight() || y >= world.getMaxHeight() - 2)))
            throw new IllegalArgumentException("Coordonnées hors du monde.");
        if (!world.getWorldBorder().isInside(new Location(world, x + .5, 80, z + .5))) throw new IllegalArgumentException("Point hors de la frontière de Ground Zero.");
        String id = UUID.randomUUID().toString(), run = plugin.sessions().runOf(player);
        pending.put(player.getUniqueId(), id);
        receipt(id, player, "pending", "Préparation de l'emplacement…");
        world.getChunkAtAsync(x >> 4, z >> 4).whenComplete((chunk, error) -> {
            if (!plugin.isEnabled()) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!id.equals(pending.get(player.getUniqueId()))) return;
                pending.remove(player.getUniqueId());
                if (error != null || chunk == null || !ready(player) || !java.util.Objects.equals(run, plugin.sessions().runOf(player))) {
                    receipt(id, player, "error", "Déplacement annulé : survivant ou terrain indisponible."); return;
                }
                int targetY = y == null ? world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1 : y + 1;
                Location spot = safeColumn(world, x, targetY, z);
                if (spot == null) { receipt(id, player, "error", "Point dangereux ou obstrué : choisis un sol plat et dégagé."); return; }
                spot.setYaw(player.getYaw());
                plugin.vehicles().dismountForTeleport(player);
                plugin.sessions().cancelLeave(player, "téléportation sur la carte");
                if (!plugin.sessions().teleport(player, spot)) { receipt(id, player, "error", "Téléportation refusée."); return; }
                String detail = player.getName() + " -> " + x + " " + spot.getBlockY() + " " + z;
                plugin.database().submit(c -> { Store.audit(c, plugin.clock().now(), actor, "admin_teleport_point", detail); return null; });
                plugin.events().add("admin", detail);
                Text.good(player, "Téléportation sur le point choisi dans le panneau.");
                receipt(id, player, "done", "Téléportation réussie : " + x + " · " + spot.getBlockY() + " · " + z);
            });
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (id.equals(pending.get(player.getUniqueId()))) { pending.remove(player.getUniqueId()); receipt(id, player, "error", "Terrain trop long à charger : réessaie."); }
        }, 300L);
        return id;
    }
    private boolean ready(Player p) { return p != null && p.isOnline() && !p.isDead() && plugin.scenario().isEnabled()
            && plugin.sessions().isSurvivor(p) && !plugin.sessions().isTransitioning(p) && !plugin.intro().playing(p); }

    public static Location safeColumn(World world, int x, int y, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        for (int dy : new int[]{0, 1, -1, 2, -2}) {
            int feetY = y + dy;
            if (feetY <= world.getMinHeight() || feetY + 1 >= world.getMaxHeight()) continue;
            var feet = world.getBlockAt(x, feetY, z); var head = world.getBlockAt(x, feetY + 1, z); var floor = world.getBlockAt(x, feetY - 1, z);
            if (!feet.isPassable() || !head.isPassable() || feet.isLiquid() || head.isLiquid() || !floor.isSolid()) continue;
            if (danger(feet.getType()) || danger(head.getType()) || danger(floor.getType())) continue;
            Location at = new Location(world, x + .5, feetY, z + .5);
            if (!world.getWorldBorder().isInside(at)) continue;
            if (world.getNearbyEntities(at.clone().add(0, .9, 0), .4, .8, .4).stream().anyMatch(e -> e instanceof Player || e instanceof org.bukkit.entity.Interaction)) continue;
            return at;
        }
        return null;
    }
    private static boolean danger(Material m) { return switch (m) {
        case MAGMA_BLOCK, CACTUS, CAMPFIRE, SOUL_CAMPFIRE, FIRE, SOUL_FIRE, POWDER_SNOW, SWEET_BERRY_BUSH,
                POINTED_DRIPSTONE, NETHER_PORTAL, END_PORTAL, BARRIER, LAVA, WATER -> true;
        default -> false;
    }; }
}
