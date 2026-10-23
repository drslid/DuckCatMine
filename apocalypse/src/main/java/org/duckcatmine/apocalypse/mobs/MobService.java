package org.duckcatmine.apocalypse.mobs;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;

/**
 * Registry and behaviours of the scenario's infected. Vanilla zombie behaviours that would break the rules are switched
 * off; special archetypes get telegraphed abilities with a recovery window (spec §8).
 */
public final class MobService {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private enum Phase { IDLE, WIND_UP, ACTIVE, RECOVERY }

    private static final class Tracked {
        final String archetype;
        Phase phase = Phase.IDLE;
        long phaseUntil;
        long cooldownUntil;
        boolean interrupted;
        Location lastSeen;
        long lastSeenAt;

        Tracked(String archetype) {
            this.archetype = archetype;
        }
    }

    private final DuckCatApocalypse plugin;
    private final Map<UUID, Tracked> tracked = new HashMap<>();
    private final Random random = new Random();
    private boolean slamming;

    public MobService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public String archetypeOf(Entity entity) {
        return entity == null ? null : entity.getPersistentDataContainer().get(plugin.keys().archetype, PersistentDataType.STRING);
    }

    public String appearanceOf(Entity entity) {
        return entity == null ? null : entity.getPersistentDataContainer().get(plugin.keys().appearance, PersistentDataType.STRING);
    }

    public boolean isScenarioMob(Entity entity) {
        return archetypeOf(entity) != null;
    }

    /** True while an abomination slam is being applied: area damage, never a bite. */
    public boolean slamming() {
        return slamming;
    }

    public int count() {
        return tracked.size();
    }

    public Map<String, Integer> countByArchetype() {
        Map<String, Integer> counts = new HashMap<>();
        for (Tracked mob : tracked.values()) counts.merge(mob.archetype, 1, Integer::sum);
        return counts;
    }

    public Zombie spawn(String archetypeId, Appearance appearance, Location location) {
        ApocalypseConfig.Archetype archetype = plugin.settings().archetype(archetypeId);
        if (archetype == null) return null;
        Zombie zombie = location.getWorld().spawn(location, Wardrobe.entityClass(appearance), CreatureSpawnEvent.SpawnReason.CUSTOM,
                mob -> configure(mob, archetype, appearance));
        tracked.put(zombie.getUniqueId(), new Tracked(archetype.id()));
        if ("abomination".equals(archetype.id()))
            location.getWorld().playSound(location, Sound.ENTITY_RAVAGER_ROAR, 3f, 0.5f);
        return zombie;
    }

    public Zombie spawnWalker(Location location) {
        return spawn(ApocalypseConfig.WALKER, Appearance.CITIZEN, location);
    }

    private void configure(Zombie zombie, ApocalypseConfig.Archetype archetype, Appearance appearance) {
        zombie.setAdult();
        zombie.setShouldBurnInDay(false);
        zombie.setCanBreakDoors(false);
        zombie.setCanPickupItems(false);
        zombie.setPersistent(false);
        zombie.setRemoveWhenFarAway(true);
        EntityEquipment equipment = zombie.getEquipment();
        equipment.clear();
        for (EquipmentSlot slot : SLOTS) equipment.setDropChance(slot, 0f);
        double size = Wardrobe.dress(zombie, appearance, random);
        base(zombie, Attribute.MAX_HEALTH, archetype.health());
        zombie.setHealth(archetype.health());
        base(zombie, Attribute.ATTACK_DAMAGE, archetype.damage());
        double variation = archetype.speedVariation() * (random.nextDouble() * 2 - 1);
        base(zombie, Attribute.MOVEMENT_SPEED, archetype.speed() + variation);
        base(zombie, Attribute.FOLLOW_RANGE, archetype.followRange());
        base(zombie, Attribute.SPAWN_REINFORCEMENTS, 0);
        base(zombie, Attribute.ARMOR, archetype.armor());
        base(zombie, Attribute.KNOCKBACK_RESISTANCE, archetype.knockbackResistance());
        base(zombie, Attribute.SCALE, Math.max(0.5, Math.min(2.0, archetype.scale() * size)));
        NamedTextColor color = switch (archetype.id()) {
            case "abomination" -> NamedTextColor.DARK_RED;
            case "howler", "breaker" -> NamedTextColor.RED;
            case "runner", "pursuer" -> NamedTextColor.GOLD;
            default -> NamedTextColor.GRAY;
        };
        zombie.customName(Component.text(archetype.name(), color));
        zombie.setCustomNameVisible(false);
        zombie.getPersistentDataContainer().set(plugin.keys().archetype, PersistentDataType.STRING, archetype.id());
        zombie.getPersistentDataContainer().set(plugin.keys().appearance, PersistentDataType.STRING, appearance.id());
    }

    private static void base(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    public int countNear(Location center, double radius) {
        int count = 0;
        double squared = radius * radius;
        for (UUID id : tracked.keySet()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.getWorld().equals(center.getWorld()) && entity.getLocation().distanceSquared(center) <= squared) count++;
        }
        return count;
    }

    public Iterable<UUID> trackedIds() {
        return Map.copyOf(tracked).keySet();
    }

    public void forget(UUID id) {
        tracked.remove(id);
    }

    /** Drops stale entries and removes infected that wandered into the refuge (no reward). */
    public void sweep() {
        Iterator<UUID> ids = tracked.keySet().iterator();
        while (ids.hasNext()) {
            Entity entity = Bukkit.getEntity(ids.next());
            if (entity == null || !entity.isValid()) {
                ids.remove();
            } else if (plugin.scenario().inRefuge(entity.getLocation())) {
                entity.remove();
                ids.remove();
            }
        }
    }

    public void removeAll() {
        for (UUID id : tracked.keySet()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
        tracked.clear();
    }

    // ------------------------------------------------------------------ behaviours (every 5 ticks)

    public void tick() {
        if (!plugin.clock().running()) return;
        long now = plugin.clock().now();
        for (Map.Entry<UUID, Tracked> entry : Map.copyOf(tracked).entrySet()) {
            if (!(Bukkit.getEntity(entry.getKey()) instanceof Mob mob) || !mob.isValid()) continue;
            Tracked state = entry.getValue();
            ApocalypseConfig.Archetype archetype = plugin.settings().archetype(state.archetype);
            if (archetype == null) continue;
            switch (state.archetype) {
                case "pursuer" -> pursuer(mob, state, archetype, now);
                case "runner" -> runner(mob, state, archetype, now);
                case "howler" -> howler(mob, state, archetype, now);
                case "abomination" -> abomination(mob, state, archetype, now);
                default -> {
                }
            }
        }
    }

    /** A hit interrupts a howler's scream while it is being prepared. */
    public void damaged(Entity entity) {
        Tracked state = tracked.get(entity.getUniqueId());
        if (state != null && "howler".equals(state.archetype) && state.phase == Phase.WIND_UP) state.interrupted = true;
    }

    private Player target(Mob mob) {
        return mob.getTarget() instanceof Player player && player.isValid() && !player.isDead()
                && player.getWorld().equals(mob.getWorld()) ? player : null;
    }

    /** Keeps searching the last place where it saw its prey, for a bounded time. */
    private void pursuer(Mob mob, Tracked state, ApocalypseConfig.Archetype archetype, long now) {
        Player target = target(mob);
        if (target != null) {
            if (mob.hasLineOfSight(target)) {
                state.lastSeen = target.getLocation();
                state.lastSeenAt = now;
            }
            return;
        }
        if (state.lastSeen == null || !state.lastSeen.getWorld().equals(mob.getWorld())) return;
        if (now - state.lastSeenAt > archetype.ability("memory-seconds", 12) * 1000 || mob.getLocation().distanceSquared(state.lastSeen) < 4) {
            state.lastSeen = null;
            return;
        }
        mob.getPathfinder().moveTo(state.lastSeen, 1.15);
    }

    /** Announced burst of speed, then an exhausted recovery: the window to break line of sight or strike. */
    private void runner(Mob mob, Tracked state, ApocalypseConfig.Archetype archetype, long now) {
        switch (state.phase) {
            case IDLE -> {
                Player target = target(mob);
                if (target == null || now < state.cooldownUntil) return;
                double range = archetype.ability("sprint-range", 14);
                double distance = mob.getLocation().distanceSquared(target.getLocation());
                if (distance > range * range || distance < 9 || !mob.hasLineOfSight(target)) return;
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_FOX_SCREECH, 1.4f, 0.55f);
                state.phase = Phase.WIND_UP;
                state.phaseUntil = now + 500;
            }
            case WIND_UP -> {
                if (now < state.phaseUntil) return;
                speed(mob, plugin.keys().burst, archetype.ability("sprint-speed", 0.34));
                state.phase = Phase.ACTIVE;
                state.phaseUntil = now + (long) (archetype.ability("sprint-seconds", 2.5) * 1000);
            }
            case ACTIVE -> {
                if (now < state.phaseUntil) return;
                clearSpeed(mob, plugin.keys().burst);
                speed(mob, plugin.keys().slowed, archetype.ability("recovery-speed", 0.12));
                state.phase = Phase.RECOVERY;
                state.phaseUntil = now + (long) (archetype.ability("recovery-seconds", 3) * 1000);
            }
            case RECOVERY -> {
                if ((now / 250) % 3 == 0) {
                    mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_WOLF_PANT, 0.7f, 0.6f);
                    mob.getWorld().spawnParticle(Particle.CLOUD, mob.getEyeLocation(), 2, 0.1, 0.1, 0.1, 0.01);
                }
                if (now < state.phaseUntil) return;
                clearSpeed(mob, plugin.keys().slowed);
                state.phase = Phase.IDLE;
                state.cooldownUntil = now + (long) (archetype.ability("cooldown-seconds", 8) * 1000);
            }
        }
    }

    /** Prepared scream, interrupted by a hit; draws nearby infected to it and asks for a small capped reinforcement. */
    private void howler(Mob mob, Tracked state, ApocalypseConfig.Archetype archetype, long now) {
        double radius = archetype.ability("radius", 32);
        switch (state.phase) {
            case IDLE -> {
                Player target = target(mob);
                if (target == null || now < state.cooldownUntil || !mob.hasLineOfSight(target)) return;
                if (mob.getLocation().distanceSquared(target.getLocation()) > radius * radius * 0.5) return;
                state.phase = Phase.WIND_UP;
                state.interrupted = false;
                state.phaseUntil = now + (long) (archetype.ability("charge-seconds", 2) * 1000);
                speed(mob, plugin.keys().slowed, 0);
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 1.6f);
            }
            case WIND_UP -> {
                mob.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, mob.getEyeLocation().add(0, 0.4, 0), 1, 0.2, 0.1, 0.2, 0);
                if (state.interrupted) {
                    clearSpeed(mob, plugin.keys().slowed);
                    state.phase = Phase.IDLE;
                    state.cooldownUntil = now + 10_000;
                    mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_HURT, 1f, 0.7f);
                    return;
                }
                if (now < state.phaseUntil) return;
                clearSpeed(mob, plugin.keys().slowed);
                scream(mob, radius, (int) archetype.ability("reinforcements", 2));
                state.phase = Phase.IDLE;
                state.cooldownUntil = now + (long) (archetype.ability("cooldown-seconds", 40) * 1000);
            }
            default -> state.phase = Phase.IDLE;
        }
    }

    private void scream(Mob howler, double radius, int reinforcements) {
        Location origin = howler.getLocation();
        howler.getWorld().playSound(origin, Sound.ENTITY_GHAST_SCREAM, 4f, 0.5f);
        howler.getWorld().spawnParticle(Particle.SONIC_BOOM, howler.getEyeLocation(), 1);
        double squared = radius * radius;
        for (Map.Entry<UUID, Tracked> entry : tracked.entrySet()) {
            if ("howler".equals(entry.getValue().archetype)) continue;
            if (!(Bukkit.getEntity(entry.getKey()) instanceof Mob other) || !other.getWorld().equals(howler.getWorld())) continue;
            if (other.getTarget() != null || other.getLocation().distanceSquared(origin) > squared) continue;
            other.getPathfinder().moveTo(origin, 1.1);
        }
        if (reinforcements > 0 && howler.getTarget() instanceof Player player) plugin.director().reinforce(player, reinforcements);
    }

    /** Telegraphed ground slam: visible wind-up, area damage that is never a bite, long recovery. */
    private void abomination(Mob mob, Tracked state, ApocalypseConfig.Archetype archetype, long now) {
        switch (state.phase) {
            case IDLE -> {
                Player target = target(mob);
                if (target == null || now < state.cooldownUntil) return;
                if (mob.getLocation().distanceSquared(target.getLocation()) > 3.5 * 3.5) return;
                state.phase = Phase.WIND_UP;
                state.phaseUntil = now + (long) (archetype.ability("slam-wind-up-seconds", 1.5) * 1000);
                speed(mob, plugin.keys().slowed, 0);
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.6f);
            }
            case WIND_UP -> {
                mob.getWorld().spawnParticle(Particle.LARGE_SMOKE, mob.getLocation().add(0, 0.2, 0), 6, 1.2, 0.1, 1.2, 0.01);
                if (now < state.phaseUntil) return;
                slam(mob, archetype);
                speed(mob, plugin.keys().slowed, archetype.speed() * 0.3);
                state.phase = Phase.RECOVERY;
                state.phaseUntil = now + (long) (archetype.ability("slam-recovery-seconds", 3) * 1000);
            }
            case RECOVERY -> {
                if (now < state.phaseUntil) return;
                clearSpeed(mob, plugin.keys().slowed);
                state.phase = Phase.IDLE;
                state.cooldownUntil = now + (long) (archetype.ability("slam-cooldown-seconds", 10) * 1000);
            }
            default -> state.phase = Phase.IDLE;
        }
    }

    private void slam(Mob mob, ApocalypseConfig.Archetype archetype) {
        Location center = mob.getLocation();
        double radius = archetype.ability("slam-radius", 4);
        double damage = archetype.ability("slam-damage", 10);
        mob.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
        mob.getWorld().spawnParticle(Particle.EXPLOSION, center, 3, 1.5, 0.2, 1.5, 0);
        DamageSource source = DamageSource.builder(DamageType.MOB_ATTACK_NO_AGGRO).withCausingEntity(mob).withDirectEntity(mob).build();
        for (Player player : mob.getWorld().getPlayers()) {
            if (player.isDead() || !plugin.sessions().isSurvivor(player) || plugin.protection().isProtected(player)) continue;
            Location at = player.getLocation();
            if (at.distanceSquared(center) > radius * radius) continue;
            slamming = true;
            try {
                player.damage(damage, source);
            } finally {
                slamming = false;
            }
            Vector push = at.toVector().subtract(center.toVector()).setY(0);
            if (push.lengthSquared() > 1e-4) push.normalize();
            player.setVelocity(push.multiply(0.9).setY(0.45));
        }
    }

    /** Sets the total movement speed through a removable modifier (the base value keeps the per-mob variation). */
    private static void speed(Mob mob, NamespacedKey key, double target) {
        AttributeInstance instance = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (instance == null) return;
        instance.removeModifier(key);
        instance.addTransientModifier(new AttributeModifier(key, target - instance.getBaseValue(), AttributeModifier.Operation.ADD_NUMBER));
    }

    private static void clearSpeed(Mob mob, NamespacedKey key) {
        AttributeInstance instance = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (instance != null) instance.removeModifier(key);
    }
}
