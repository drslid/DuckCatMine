package org.duckcatmine.apocalypse.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.items.ItemRegistry;

/**
 * Thrown weapons. A pipe bomb is lit when thrown, lands, rolls and blows up when its fuse ends: damage fades with distance
 * and is reduced behind a wall, the thrower is not spared, survivors follow the PvP and team rules, no block is destroyed
 * and the whole district hears it. A Molotov cocktail breaks where it lands and keeps a small area burning for a few
 * seconds; nothing is set on fire in the world itself.
 */
public final class ThrowableService implements Listener {
    private record Bomb(UUID thrower, long explodeAt, Location last) {
    }

    private record Fire(Location center, UUID thrower, long endsAt) {
    }

    private final DuckCatApocalypse plugin;
    private final Map<UUID, Bomb> bombs = new HashMap<>();
    private final List<Fire> fires = new ArrayList<>();
    private org.bukkit.scheduler.BukkitTask task;
    private long ticks;

    public ThrowableService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Lit bombs and fires simply end with the server: nothing they would have done is replayed. */
    public void stop() {
        if (task != null) task.cancel();
        for (UUID id : bombs.keySet()) {
            Entity carrier = Bukkit.getEntity(id);
            if (carrier != null) carrier.remove();
        }
        bombs.clear();
        fires.clear();
    }

    private ApocalypseConfig.Explosives rules() {
        return plugin.settings().play().explosives();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball ball)) return;
        String kind = plugin.items().idOf(ball.getItem());
        if (!ItemRegistry.PIPE_BOMB.equals(kind) && !ItemRegistry.MOLOTOV.equals(kind)) return;
        if (!(ball.getShooter() instanceof Player player) || !plugin.sessions().isSurvivor(player)
                || plugin.scenario().inRefuge(player.getLocation())) {
            event.setCancelled(true);
            if (ball.getShooter() instanceof Player player) Text.warn(player, "Pas d'explosif ni de feu dans le refuge.");
            return;
        }
        ball.getPersistentDataContainer().set(plugin.keys().thrown, PersistentDataType.STRING, kind);
        if (ItemRegistry.PIPE_BOMB.equals(kind)) {
            bombs.put(ball.getUniqueId(), new Bomb(player.getUniqueId(), plugin.clock().now() + (long) (rules().fuseSeconds() * 1000),
                    ball.getLocation()));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1f, 1.2f);
        }
        plugin.protection().endByAction(player);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        String kind = projectile.getPersistentDataContainer().get(plugin.keys().thrown, PersistentDataType.STRING);
        if (kind == null) return;
        Player thrower = projectile.getShooter() instanceof Player player ? player : null;
        Location at = event.getHitEntity() != null ? event.getHitEntity().getLocation() : projectile.getLocation();
        if (ItemRegistry.MOLOTOV.equals(kind)) {
            ignite(at, thrower);
            return;
        }
        Bomb bomb = bombs.remove(projectile.getUniqueId());
        long explodeAt = bomb == null ? plugin.clock().now() + (long) (rules().fuseSeconds() * 1000) : bomb.explodeAt();
        Vector drift = projectile.getVelocity().multiply(0.15);
        Item resting = at.getWorld().dropItem(at, plugin.items().create(ItemRegistry.PIPE_BOMB, 1), item -> {
            item.setCanPlayerPickup(false);
            item.setCanMobPickup(false);
            item.setUnlimitedLifetime(true);
            item.setPersistent(false);
            item.setVelocity(drift);
            item.getPersistentDataContainer().set(plugin.keys().thrown, PersistentDataType.STRING, kind);
        });
        bombs.put(resting.getUniqueId(), new Bomb(thrower == null ? null : thrower.getUniqueId(), explodeAt, at));
    }

    /** Every tick: fuses burn down, fires keep burning. */
    private void tick() {
        ticks++;
        long now = plugin.clock().now();
        Iterator<Map.Entry<UUID, Bomb>> entries = bombs.entrySet().iterator();
        List<Runnable> blasts = new ArrayList<>();
        while (entries.hasNext()) {
            Map.Entry<UUID, Bomb> entry = entries.next();
            Entity carrier = Bukkit.getEntity(entry.getKey());
            Bomb bomb = entry.getValue();
            Location at = carrier != null && carrier.isValid() ? carrier.getLocation() : bomb.last();
            if (carrier != null) entry.setValue(new Bomb(bomb.thrower(), bomb.explodeAt(), at));
            if (now < bomb.explodeAt()) {
                if (ticks % 2 == 0 && at.getWorld() != null) at.getWorld().spawnParticle(Particle.SMOKE, at.clone().add(0, 0.3, 0), 1, 0.02, 0.02, 0.02, 0.01);
                continue;
            }
            entries.remove();
            if (carrier != null) carrier.remove();
            Player thrower = bomb.thrower() == null ? null : Bukkit.getPlayer(bomb.thrower());
            blasts.add(() -> explode(at.clone().add(0, 0.3, 0), rules().bombRadius(), rules().bombDamage(), thrower));
        }
        blasts.forEach(Runnable::run);
        if (ticks % 5 != 0) return;
        fires.removeIf(fire -> fire.endsAt() <= now);
        for (Fire fire : List.copyOf(fires)) burn(fire, ticks % 10 == 0);
    }

    /** Blast at a point: damage fades with distance, cars nearby are hit too. */
    public void explode(Location center, double radius, double damage, Player thrower) {
        World world = center.getWorld();
        if (world == null || !world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) return;
        if (plugin.scenario().inRefuge(center)) {
            world.spawnParticle(Particle.SMOKE, center, 20, 0.3, 0.3, 0.3, 0.02);
            world.playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
            return;
        }
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 40, radius / 4, radius / 6, radius / 4, 0.03);
        world.spawnParticle(Particle.FLAME, center, 25, radius / 5, radius / 8, radius / 5, 0.05);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.85f);
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living.isDead() || entity instanceof ArmorStand) continue;
            Location body = living.getLocation().add(0, living.getHeight() / 2, 0);
            double distance = body.distance(center);
            if (distance > radius) continue;
            double factor = Math.pow(1 - distance / radius, 1.3);
            Vector toward = body.toVector().subtract(center.toVector());
            RayTraceResult wall = toward.lengthSquared() < 1e-4 ? null
                    : world.rayTraceBlocks(center, toward.clone().normalize(), distance, FluidCollisionMode.NEVER, true);
            double dealt = damage * factor * (wall == null ? 1 : 0.4);
            if (dealt < 0.5) continue;
            if (living instanceof Player victim && thrower != null && !plugin.teams().canHarm(thrower, victim)) continue;
            DamageSource source = thrower == null
                    ? DamageSource.builder(DamageType.EXPLOSION).withDamageLocation(center).build()
                    : DamageSource.builder(DamageType.PLAYER_EXPLOSION).withCausingEntity(thrower).withDirectEntity(thrower)
                    .withDamageLocation(center).build();
            living.damage(dealt, source);
            Vector push = toward.lengthSquared() < 1e-4 ? new Vector(0, 1, 0) : toward.normalize();
            living.setVelocity(push.multiply(0.4 + factor * 1.2).setY(0.25 + factor * 0.5));
        }
        plugin.vehicles().blast(center, radius, damage, thrower);
        plugin.noise().emit(center, plugin.settings().play().noise().explosion(), thrower);
        plugin.events().add("explosion", (thrower == null ? "Une explosion" : "Explosion déclenchée par " + thrower.getName())
                + " (" + center.getBlockX() + ", " + center.getBlockZ() + ")");
    }

    private void ignite(Location at, Player thrower) {
        World world = at.getWorld();
        if (world == null) return;
        world.playSound(at, Sound.BLOCK_GLASS_BREAK, 1.2f, 1f);
        if (plugin.scenario().inRefuge(at) || at.getBlock().isLiquid()) {
            world.spawnParticle(Particle.SMOKE, at, 15, 0.3, 0.2, 0.3, 0.02);
            return;
        }
        world.playSound(at, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.8f);
        fires.add(new Fire(at.clone(), thrower == null ? null : thrower.getUniqueId(), plugin.clock().now() + (long) (rules().fireSeconds() * 1000)));
        plugin.noise().emit(at, plugin.settings().play().noise().glass(), thrower);
    }

    private void burn(Fire fire, boolean hurt) {
        World world = fire.center().getWorld();
        if (world == null || !world.isChunkLoaded(fire.center().getBlockX() >> 4, fire.center().getBlockZ() >> 4)) return;
        double radius = rules().fireRadius();
        world.spawnParticle(Particle.FLAME, fire.center(), 18, radius / 2, 0.2, radius / 2, 0.01);
        world.spawnParticle(Particle.SMOKE, fire.center().clone().add(0, 0.5, 0), 6, radius / 2, 0.3, radius / 2, 0.01);
        if (!hurt) return;
        world.playSound(fire.center(), Sound.BLOCK_FIRE_AMBIENT, 1.2f, 1f);
        Player thrower = fire.thrower() == null ? null : Bukkit.getPlayer(fire.thrower());
        for (Entity entity : world.getNearbyEntities(fire.center(), radius, 1.5, radius)) {
            if (!(entity instanceof LivingEntity living) || living.isDead() || entity instanceof ArmorStand) continue;
            if (living instanceof Player victim && thrower != null && !plugin.teams().canHarm(thrower, victim)) continue;
            living.setFireTicks(Math.max(living.getFireTicks(), 80));
            double dealt = rules().fireDamagePerSecond() / 2;
            if (dealt <= 0) continue;
            DamageSource source = thrower == null ? DamageSource.builder(DamageType.IN_FIRE).build()
                    : DamageSource.builder(DamageType.IN_FIRE).withCausingEntity(thrower).withDirectEntity(thrower).build();
            living.damage(dealt, source);
        }
    }
}
