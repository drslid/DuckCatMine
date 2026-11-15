package org.duckcatmine.apocalypse.fauna;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.scenario.city.Sector;

/**
 * Saddled horses left behind in the woods outside the city: ride them to go faster. A bite from an infected is fatal to
 * a horse after a short agony; it never turns.
 */
public final class HorseService implements Listener {
    public static final int WORLD_CAP = 16, NEAR_CAP = 2;
    private static final Horse.Color[] COLORS = Horse.Color.values();
    private static final Horse.Style[] STYLES = Horse.Style.values();
    private final DuckCatApocalypse plugin;
    private final NamespacedKey wild;
    private final Random random = new Random();
    private final Map<UUID, Long> dying = new HashMap<>();
    private long seconds;

    public HorseService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
        wild = new NamespacedKey(plugin, "wild_horse");
    }

    public boolean healthy(Entity entity) {
        return entity instanceof Horse horse && horse.isValid() && !dying.containsKey(horse.getUniqueId());
    }

    /** Every second. */
    public void tick() {
        seconds++;
        World world = plugin.scenario().world();
        if (world == null || !plugin.scenario().isEnabled()) return;
        long now = plugin.clock().now();
        for (var entry : Map.copyOf(dying).entrySet()) {
            if (!(plugin.getServer().getEntity(entry.getKey()) instanceof Horse horse) || !horse.isValid()) { dying.remove(entry.getKey()); continue; }
            if (now < entry.getValue()) {
                horse.getWorld().spawnParticle(Particle.DUST, horse.getLocation().add(0, 1.4, 0), 4, .4, .3, .4,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(96, 120, 40), 1.2f));
                if (seconds % 3 == 0) horse.getWorld().playSound(horse.getLocation(), Sound.ENTITY_HORSE_HURT, 0.8f, 0.6f);
                continue;
            }
            dying.remove(entry.getKey());
            horse.eject();
            horse.getWorld().playSound(horse.getLocation(), Sound.ENTITY_HORSE_DEATH, 1f, 0.7f);
            horse.setHealth(0);
        }
        if (!plugin.settings().play().survival().horses() || seconds % 30 != 0) return;
        int total = (int) world.getEntitiesByClass(Horse.class).stream().filter(h -> h.getPersistentDataContainer().has(wild)).count();
        for (Player player : world.getPlayers()) {
            if (total >= WORLD_CAP) return;
            if (!plugin.sessions().isSurvivor(player) || player.isDead()) continue;
            Sector sector = plugin.director().sectorAt(player.getLocation());
            if (sector != Sector.COUNTRYSIDE && sector != Sector.OUTSKIRTS) continue;
            if (player.getLocation().getNearbyEntitiesByType(Horse.class, 96).size() >= NEAR_CAP) continue;
            total += herd(world, player.getLocation());
        }
    }

    private int herd(World world, Location around) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2, distance = 48 + random.nextDouble() * 40;
            int x = (int) Math.floor(around.getX() + Math.cos(angle) * distance), z = (int) Math.floor(around.getZ() + Math.sin(angle) * distance);
            if (plugin.director().sectorAt(new Location(world, x, around.getY(), z)) != Sector.COUNTRYSIDE) continue;
            Location spot = plugin.director().outdoorSpot(world, x, z);
            if (spot == null || !wooded(world, x, z)) continue;
            int count = 1 + random.nextInt(3);
            for (int n = 0; n < count; n++) spawn(spot.clone().add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2));
            return count;
        }
        return 0;
    }

    private static boolean wooded(World world, int x, int z) {
        for (int dx = -8; dx <= 8; dx += 4)
            for (int dz = -8; dz <= 8; dz += 4) {
                if (!world.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) continue;
                Material top = world.getHighestBlockAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING).getType();
                if (Tag.LEAVES.isTagged(top) || Tag.LOGS.isTagged(top)) return true;
            }
        return false;
    }

    public Horse spawn(Location at) {
        Location spot = plugin.director().outdoorSpot(at.getWorld(), at.getBlockX(), at.getBlockZ());
        if (spot == null) spot = at;
        return spot.getWorld().spawn(spot, Horse.class, horse -> {
            horse.setColor(COLORS[random.nextInt(COLORS.length)]);
            horse.setStyle(STYLES[random.nextInt(STYLES.length)]);
            horse.setTamed(true);
            horse.setAdult();
            horse.getInventory().setSaddle(ItemStack.of(Material.SADDLE));
            horse.setPersistent(true);
            horse.setRemoveWhenFarAway(false);
            horse.getPersistentDataContainer().set(wild, PersistentDataType.BOOLEAN, true);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBite(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Horse horse) || !plugin.scenario().isScenarioWorld(horse.getWorld())) return;
        if (!plugin.mobs().isScenarioMob(event.getDamager()) || dying.containsKey(horse.getUniqueId())) return;
        if (random.nextDouble() >= plugin.settings().play().fauna().infectionChance()) return;
        dying.put(horse.getUniqueId(), plugin.clock().now() + 15_000 + random.nextInt(15_000));
        for (Entity rider : horse.getPassengers())
            if (rider instanceof Player player) player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Ton cheval a été mordu : il ne survivra pas longtemps.", net.kyori.adventure.text.format.NamedTextColor.RED));
    }
}
