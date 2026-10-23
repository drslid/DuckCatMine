package org.duckcatmine.apocalypse.mobs;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.scenario.GroundZeroGenerator;
import org.duckcatmine.apocalypse.scenario.city.CityLayout;
import org.duckcatmine.apocalypse.scenario.city.Sector;

/**
 * Keeps a bounded population around each active survivor: local cap and archetype mix by sector, a little more at
 * night, always on the ground (never on roofs), out of reach, never in the refuge or in unloaded chunks. Indoor spawns
 * only happen in dark abandoned buildings. Hordes and tension cycles come with the threat director.
 */
public final class SpawnDirector {
    private static final int LOCAL_RADIUS = 40;
    private static final int ATTEMPTS = 8;

    private final DuckCatApocalypse plugin;
    private final Random random = new Random();

    public SpawnDirector(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    private CityLayout layout(World world) {
        return world.getGenerator() instanceof GroundZeroGenerator ? CityLayout.of(world.getSeed()) : null;
    }

    public Sector sectorAt(Location location) {
        CityLayout layout = layout(location.getWorld());
        if (plugin.scenario().inRefuge(location)) return Sector.REFUGE;
        return layout == null ? Sector.RESIDENTIAL : layout.sectorAt(location.getBlockX(), location.getBlockZ());
    }

    public void tick() {
        ApocalypseConfig settings = plugin.settings();
        ApocalypseConfig.Spawning spawning = settings.spawning();
        World world = plugin.scenario().world();
        if (!plugin.scenario().isEnabled() || world == null || !spawning.enabled()) return;
        despawnFar(world, spawning.despawnDistance());
        int total = plugin.mobs().count();
        boolean night = !world.isDayTime();
        for (Player player : world.getPlayers()) {
            if (total >= spawning.globalCap()) return;
            if (player.isDead() || !plugin.sessions().isSurvivor(player) || plugin.scenario().inRefuge(player.getLocation())) continue;
            Sector sector = sectorAt(player.getLocation());
            ApocalypseConfig.SectorRule rule = settings.sector(sector.id());
            if (rule == null || rule.mix().isEmpty()) continue;
            int cap = plugin.threat().localCap(rule.localCap() + (night ? spawning.nightBonus() : 0));
            if (plugin.mobs().countNear(player.getLocation(), LOCAL_RADIUS) >= cap) continue;
            Location spot = findSpot(world, player, spawning.minDistance(), spawning.maxDistance(), random.nextDouble() < spawning.indoorChance());
            if (spot == null) continue;
            String archetype = pickArchetype(rule.mix());
            if (archetype == null) continue;
            plugin.mobs().spawn(archetype, pickAppearance(settings.archetype(archetype), sector), spot);
            total++;
        }
    }

    /** Small capped reinforcement (howler call): walkers only, inside the global budget, never next to the survivor. */
    public void reinforce(Player player, int count) {
        ApocalypseConfig.Spawning spawning = plugin.settings().spawning();
        World world = player.getWorld();
        if (!plugin.scenario().isEnabled() || !plugin.scenario().isScenarioWorld(world) || !spawning.enabled()) return;
        Sector sector = sectorAt(player.getLocation());
        for (int k = 0; k < count && plugin.mobs().count() < spawning.globalCap(); k++) {
            Location spot = findSpot(world, player, spawning.minDistance(), spawning.maxDistance(), false);
            if (spot != null) plugin.mobs().spawn(ApocalypseConfig.WALKER, pickAppearance(plugin.settings().archetype(ApocalypseConfig.WALKER), sector), spot);
        }
    }

    /**
     * A horde: a group gathered out of sight, further than usual, that walks to where the survivor is now. Inside the
     * global budget; the mix follows the sector where the group appears. Returns how many infected came.
     */
    public int horde(Player player, int size) {
        ApocalypseConfig settings = plugin.settings();
        ApocalypseConfig.Spawning spawning = settings.spawning();
        World world = player.getWorld();
        if (!plugin.scenario().isEnabled() || !plugin.scenario().isScenarioWorld(world) || !spawning.enabled()) return 0;
        Location center = findSpot(world, player, spawning.maxDistance(), spawning.maxDistance() + 16, false);
        if (center == null) return 0;
        Sector sector = sectorAt(center);
        ApocalypseConfig.SectorRule rule = settings.sector(sector.id());
        Map<String, Double> mix = rule == null || rule.mix().isEmpty() ? Map.of(ApocalypseConfig.WALKER, 1.0) : rule.mix();
        Location goal = player.getLocation();
        CityLayout layout = layout(world);
        int spawned = 0;
        for (int k = 0; k < size && plugin.mobs().count() < spawning.globalCap(); k++) {
            Location spot = k == 0 ? center : nearby(world, layout, center);
            if (spot == null) continue;
            String archetype = pickArchetype(mix);
            if (archetype == null) archetype = ApocalypseConfig.WALKER;
            Mob mob = plugin.mobs().spawn(archetype, pickAppearance(settings.archetype(archetype), sector), spot);
            if (mob == null) continue;
            plugin.mobs().send(mob, goal.clone().add(random.nextGaussian() * 2, 0, random.nextGaussian() * 2), 90_000);
            spawned++;
        }
        return spawned;
    }

    private Location nearby(World world, CityLayout layout, Location center) {
        for (int attempt = 0; attempt < 4; attempt++) {
            int x = center.getBlockX() + random.nextInt(7) - 3, z = center.getBlockZ() + random.nextInt(7) - 3;
            if (!world.isChunkLoaded(x >> 4, z >> 4) || plugin.scenario().refugeArea().near(x, z, 8)) continue;
            int top = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            int ground = layout == null ? top : layout.floorAt(x, z, top);
            if (top > ground + 1) continue;
            Location spot = new Location(world, x + 0.5, ground + 1, z + 0.5);
            Block feet = spot.getBlock();
            if (feet.isPassable() && !feet.isLiquid() && feet.getRelative(BlockFace.UP).isPassable() && feet.getRelative(BlockFace.DOWN).isSolid())
                return spot;
        }
        return null;
    }

    private String pickArchetype(Map<String, Double> mix) {
        Map<String, Integer> alive = plugin.mobs().countByArchetype();
        double boost = plugin.threat().specialBoost();
        double total = 0;
        for (Map.Entry<String, Double> entry : mix.entrySet()) if (allowed(entry.getKey(), alive)) total += weight(entry, boost);
        if (total <= 0) return null;
        double roll = random.nextDouble() * total;
        for (Map.Entry<String, Double> entry : mix.entrySet()) {
            if (!allowed(entry.getKey(), alive)) continue;
            roll -= weight(entry, boost);
            if (roll < 0) return entry.getKey();
        }
        return null;
    }

    /** Special infected become more common as the threat rises. */
    private static double weight(Map.Entry<String, Double> entry, double boost) {
        return ApocalypseConfig.WALKER.equals(entry.getKey()) ? entry.getValue() : entry.getValue() * boost;
    }

    private boolean allowed(String archetypeId, Map<String, Integer> alive) {
        ApocalypseConfig.Archetype archetype = plugin.settings().archetype(archetypeId);
        return archetype != null && archetype.minThreat() <= plugin.threat().level()
                && (archetype.maxAlive() == 0 || alive.getOrDefault(archetypeId, 0) < archetype.maxAlive());
    }

    private Appearance pickAppearance(ApocalypseConfig.Archetype archetype, Sector sector) {
        List<String> ids = archetype.appearances();
        double total = 0;
        for (String id : ids) total += Wardrobe.affinity(Appearance.byId(id), sector);
        double roll = random.nextDouble() * total;
        for (String id : ids) {
            Appearance appearance = Appearance.byId(id);
            roll -= Wardrobe.affinity(appearance, sector);
            if (roll < 0) return appearance;
        }
        return Appearance.byId(ids.getFirst());
    }

    private Location findSpot(World world, Player player, double minDistance, double maxDistance, boolean indoorAllowed) {
        CityLayout layout = layout(world);
        Location origin = player.getLocation();
        double facing = Math.toRadians(origin.getYaw() + 90);
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = attempt < ATTEMPTS / 2 ? facing + Math.PI + (random.nextDouble() - 0.5) * Math.PI / 1.5
                    : random.nextDouble() * Math.PI * 2;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            int x = (int) Math.floor(origin.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(origin.getZ() + Math.sin(angle) * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4) || plugin.scenario().refugeArea().near(x, z, 8)) continue;
            int top = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            int ground = layout == null ? top : layout.floorAt(x, z, top);
            boolean covered = top > ground + 1;
            if (covered && !indoorAllowed) continue;
            Location spot = new Location(world, x + 0.5, ground + 1, z + 0.5);
            if (!world.getWorldBorder().isInside(spot)) continue;
            Block feet = spot.getBlock();
            Block below = feet.getRelative(BlockFace.DOWN);
            if (!feet.isPassable() || feet.isLiquid() || !feet.getRelative(BlockFace.UP).isPassable() || !below.isSolid()) continue;
            if (covered && feet.getLightFromBlocks() > 7) continue;
            if (nearAnySurvivor(world, spot, minDistance)) continue;
            return spot;
        }
        return null;
    }

    private boolean nearAnySurvivor(World world, Location spot, double distance) {
        double squared = distance * distance;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(spot) < squared) return true;
        }
        return false;
    }

    private void despawnFar(World world, int distance) {
        double squared = (double) distance * distance;
        for (UUID id : plugin.mobs().trackedIds()) {
            Entity entity = plugin.getServer().getEntity(id);
            if (!(entity instanceof Zombie mob) || !mob.getWorld().equals(world) || mob.getTarget() != null) continue;
            boolean near = false;
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(mob.getLocation()) <= squared) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                mob.remove();
                plugin.mobs().forget(id);
            }
        }
    }
}
