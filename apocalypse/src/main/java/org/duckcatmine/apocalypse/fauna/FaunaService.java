package org.duckcatmine.apocalypse.fauna;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;

/**
 * Contamination of animals (spec §15): an infected that hurts a healthy animal may contaminate it; after a visible
 * incubation the animal turns hostile (infected wolf or livestock) and follows the infected rules. Infected animals do not
 * breed, cannot be tamed, count in the global caps and survive restarts (state in the entity's data).
 */
public final class FaunaService implements Listener {
    public static final String WOLF = "infected_wolf";
    public static final String LIVESTOCK = "infected_livestock";
    private static final long WINDOW_MS = 600_000;
    private static final double SPREAD_RADIUS = 10;

    private final DuckCatApocalypse plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> incubating = new HashMap<>();
    private final Map<UUID, Long> nextAttack = new HashMap<>();
    private final Deque<Long> recent = new ArrayDeque<>();

    public FaunaService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    private ApocalypseConfig.Fauna rules() {
        return plugin.settings().play().fauna();
    }

    public int incubatingCount() {
        return incubating.size();
    }

    public static String species(Entity entity) {
        return switch (entity.getType()) {
            case WOLF -> "wolf";
            case COW -> "cow";
            case SHEEP -> "sheep";
            case PIG -> "pig";
            case GOAT -> "goat";
            case CHICKEN -> "chicken";
            default -> null;
        };
    }

    public boolean isInfectedAnimal(Entity entity) {
        String archetype = plugin.mobs().archetypeOf(entity);
        return WOLF.equals(archetype) || LIVESTOCK.equals(archetype);
    }

    private boolean healthy(Entity entity) {
        return entity instanceof Animals && entity.isValid() && species(entity) != null && rules().species().contains(species(entity))
                && !plugin.mobs().isScenarioMob(entity) && !entity.getPersistentDataContainer().has(plugin.keys().incubation);
    }

    // ------------------------------------------------------------------ contamination

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Animals animal) || !plugin.scenario().isScenarioWorld(animal.getWorld())) return;
        if (!rules().enabled() || !plugin.mobs().isScenarioMob(event.getDamager()) || !healthy(animal)) return;
        if (plugin.scenario().inRefuge(animal.getLocation()) || random.nextDouble() >= rules().infectionChance()) return;
        if (!budget()) return;
        long now = plugin.clock().now();
        int seconds = rules().incubationMinSeconds() + random.nextInt(rules().incubationMaxSeconds() - rules().incubationMinSeconds() + 1);
        long deadline = now + seconds * 1000L;
        animal.getPersistentDataContainer().set(plugin.keys().incubation, PersistentDataType.LONG, deadline);
        animal.setPersistent(true);
        incubating.put(animal.getUniqueId(), deadline);
        recent.addLast(now);
    }

    /** At most max-infected alive and max-per-ten-minutes new contaminations. */
    private boolean budget() {
        long now = plugin.clock().now();
        while (!recent.isEmpty() && now - recent.peekFirst() > WINDOW_MS) recent.removeFirst();
        if (recent.size() >= rules().maxPerTenMinutes()) return false;
        Map<String, Integer> alive = plugin.mobs().countByArchetype();
        int infected = alive.getOrDefault(WOLF, 0) + alive.getOrDefault(LIVESTOCK, 0) + incubating.size();
        return infected < rules().maxInfected() && plugin.mobs().count() < plugin.settings().spawning().globalCap();
    }

    /** Every second: incubation signs and transformations, infected animals' behaviour. */
    public void tick() {
        if (!plugin.clock().running()) return;
        long now = plugin.clock().now();
        for (Map.Entry<UUID, Long> entry : Map.copyOf(incubating).entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof Animals animal) || !animal.isValid()) {
                incubating.remove(entry.getKey());
                continue;
            }
            if (now >= entry.getValue()) {
                incubating.remove(entry.getKey());
                transform(animal);
            } else {
                signs(animal, entry.getValue() - now);
            }
        }
        for (UUID id : plugin.mobs().trackedIds()) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof Mob mob) || !mob.isValid() || !isInfectedAnimal(mob)) continue;
            behave(mob, now);
        }
        nextAttack.values().removeIf(at -> at < now - 10_000);
    }

    private void signs(Animals animal, long remaining) {
        Location eye = animal.getEyeLocation();
        animal.getWorld().spawnParticle(Particle.DUST, eye, 3, 0.2, 0.2, 0.2, new Particle.DustOptions(Color.fromRGB(70, 110, 40), 1.0f));
        if (random.nextInt(4) == 0) animal.getWorld().playSound(eye, sickSound(animal), 0.7f, 0.5f);
        if (!animal.hasPotionEffect(PotionEffectType.SLOWNESS))
            animal.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) Math.min(remaining / 50, 1200), 1, false, false));
        if (random.nextInt(6) == 0) animal.getWorld().spawnParticle(Particle.SNEEZE, eye, 2, 0.1, 0.1, 0.1, 0.01);
    }

    private static Sound sickSound(Entity animal) {
        return switch (animal.getType()) {
            case WOLF -> Sound.ENTITY_WOLF_WHINE;
            case COW -> Sound.ENTITY_COW_HURT;
            case SHEEP -> Sound.ENTITY_SHEEP_HURT;
            case PIG -> Sound.ENTITY_PIG_HURT;
            default -> Sound.ENTITY_GENERIC_HURT;
        };
    }

    /** The animal becomes an infected one: hostile, sick-looking, under the scenario's rules. */
    public void transform(Animals animal) {
        String archetypeId = animal instanceof Wolf ? WOLF : LIVESTOCK;
        ApocalypseConfig.Archetype archetype = plugin.settings().archetype(archetypeId);
        PersistentDataContainer data = animal.getPersistentDataContainer();
        data.remove(plugin.keys().incubation);
        if (archetype == null) return;
        animal.removePotionEffect(PotionEffectType.SLOWNESS);
        animal.setBreed(false);
        animal.setPersistent(true);
        animal.setRemoveWhenFarAway(false);
        base(animal, Attribute.MAX_HEALTH, archetype.health());
        animal.setHealth(archetype.health());
        base(animal, Attribute.MOVEMENT_SPEED, archetype.speed());
        base(animal, Attribute.ATTACK_DAMAGE, archetype.damage());
        base(animal, Attribute.FOLLOW_RANGE, archetype.followRange());
        base(animal, Attribute.ARMOR, archetype.armor());
        base(animal, Attribute.KNOCKBACK_RESISTANCE, archetype.knockbackResistance());
        animal.customName(Component.text(archetype.name(), NamedTextColor.DARK_GREEN));
        animal.setCustomNameVisible(false);
        plugin.mobs().adopt(animal, archetypeId);
        configureAi(animal);
        Location at = animal.getLocation();
        animal.getWorld().playSound(at, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 0.6f);
        animal.getWorld().spawnParticle(Particle.DUST, at.add(0, 0.6, 0), 20, 0.4, 0.4, 0.4,
                new Particle.DustOptions(Color.fromRGB(50, 80, 30), 1.4f));
    }

    /** Vanilla goals are not saved: livestock loses its grazing and panicking on every load. */
    private void configureAi(Mob animal) {
        if (animal instanceof Wolf wolf) {
            wolf.setTamed(false);
            wolf.setOwner(null);
            wolf.setSitting(false);
            wolf.setAngry(true);
        } else {
            Bukkit.getMobGoals().removeAllGoals(animal);
        }
    }

    private static void base(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    // ------------------------------------------------------------------ behaviour

    private void behave(Mob mob, long now) {
        ApocalypseConfig.Archetype archetype = plugin.settings().archetype(plugin.mobs().archetypeOf(mob));
        if (archetype == null) return;
        if (random.nextInt(3) == 0)
            mob.getWorld().spawnParticle(Particle.DUST, mob.getEyeLocation(), 2, 0.2, 0.2, 0.2, new Particle.DustOptions(Color.fromRGB(60, 90, 35), 1.0f));
        LivingEntity target = mob.getTarget() != null && valid(mob, mob.getTarget(), archetype) ? mob.getTarget() : pick(mob, archetype);
        if (target == null) {
            if (mob.getTarget() != null) mob.setTarget(null);
            return;
        }
        if (mob.getTarget() != target) mob.setTarget(target);
        if (mob instanceof Wolf wolf) {
            if (!wolf.isAngry()) wolf.setAngry(true);
            return;
        }
        double distance = mob.getLocation().distanceSquared(target.getLocation());
        if (distance > 2.2 * 2.2) {
            mob.getPathfinder().moveTo(target, 1.0);
            return;
        }
        if (nextAttack.getOrDefault(mob.getUniqueId(), 0L) > now) return;
        nextAttack.put(mob.getUniqueId(), now + 1200);
        mob.lookAt(target);
        mob.getWorld().playSound(mob.getLocation(), sickSound(mob), 1f, 0.5f);
        DamageSource source = DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(mob).withDirectEntity(mob).build();
        target.damage(archetype.damage(), source);
    }

    private boolean valid(Mob mob, LivingEntity target, ApocalypseConfig.Archetype archetype) {
        if (!target.isValid() || target.isDead() || !target.getWorld().equals(mob.getWorld())) return false;
        double range = archetype.followRange();
        if (target.getLocation().distanceSquared(mob.getLocation()) > range * range) return false;
        if (target instanceof Player player) return survivorTarget(player);
        return healthy(target);
    }

    /** The nearest visible survivor within range, else a healthy animal close by (bounded spread). */
    private LivingEntity pick(Mob mob, ApocalypseConfig.Archetype archetype) {
        World world = mob.getWorld();
        Location at = mob.getLocation();
        double range = archetype.followRange();
        LivingEntity best = null;
        double bestDistance = range * range;
        for (Player player : world.getPlayers()) {
            double distance = player.getLocation().distanceSquared(at);
            if (distance < bestDistance && survivorTarget(player) && mob.hasLineOfSight(player) && !plugin.noise().concealed(player, mob)) {
                best = player;
                bestDistance = distance;
            }
        }
        if (best != null || random.nextInt(8) != 0) return best;
        bestDistance = SPREAD_RADIUS * SPREAD_RADIUS;
        for (Entity entity : mob.getNearbyEntities(SPREAD_RADIUS, 4, SPREAD_RADIUS)) {
            if (!(entity instanceof Animals animal) || !healthy(animal)) continue;
            double distance = animal.getLocation().distanceSquared(at);
            if (distance < bestDistance) {
                best = animal;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean survivorTarget(Player player) {
        return !player.isDead() && plugin.sessions().isSurvivor(player) && !plugin.scenario().inRefuge(player.getLocation())
                && !plugin.protection().isProtected(player);
    }

    // ------------------------------------------------------------------ persistence and rules

    /** Infected animals and incubations resume after a restart or when their chunk loads again. */
    @EventHandler
    public void onLoad(EntitiesLoadEvent event) {
        if (!plugin.scenario().isScenarioWorld(event.getWorld())) return;
        for (Entity entity : event.getEntities()) resume(entity);
    }

    public void resumeLoaded() {
        World world = plugin.scenario().world();
        if (world == null) return;
        for (LivingEntity entity : world.getLivingEntities()) resume(entity);
    }

    private void resume(Entity entity) {
        if (!(entity instanceof Animals animal)) return;
        Long deadline = animal.getPersistentDataContainer().get(plugin.keys().incubation, PersistentDataType.LONG);
        if (deadline != null) {
            incubating.put(animal.getUniqueId(), deadline);
            return;
        }
        String archetype = plugin.mobs().archetypeOf(animal);
        if ((WOLF.equals(archetype) || LIVESTOCK.equals(archetype)) && !plugin.mobs().tracks(animal)) {
            plugin.mobs().adopt(animal, archetype);
            configureAi(animal);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (sick(event.getMother()) || sick(event.getFather())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLove(EntityEnterLoveModeEvent event) {
        if (sick(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (sick(event.getEntity())) event.setCancelled(true);
    }

    private boolean sick(Entity entity) {
        return entity != null && (plugin.mobs().isScenarioMob(entity) || entity.getPersistentDataContainer().has(plugin.keys().incubation));
    }

    /** Zombies sometimes go for livestock when no survivor is around: the way contamination spreads. */
    public LivingEntity prey(Mob infected) {
        if (!rules().enabled() || random.nextInt(10) != 0) return null;
        for (Entity entity : infected.getNearbyEntities(SPREAD_RADIUS, 3, SPREAD_RADIUS)) {
            if (entity instanceof Animals animal && healthy(animal) && infected.hasLineOfSight(animal)) return animal;
        }
        return null;
    }
}
