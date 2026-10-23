package org.duckcatmine.apocalypse.mobs;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;

/** Registry of the scenario's infected. Vanilla zombie behaviours that would break the rules are switched off. */
public final class MobService {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Color[] CLOTHES = {Color.fromRGB(0x5B6770), Color.fromRGB(0x7A5C3E), Color.fromRGB(0x3E5A7A),
            Color.fromRGB(0x6E3B3B), Color.fromRGB(0x4F6B3A), Color.fromRGB(0x8A8A7A)};

    private final DuckCatApocalypse plugin;
    private final Map<UUID, String> tracked = new HashMap<>();
    private final Random random = new Random();

    public MobService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public String archetypeOf(Entity entity) {
        return entity == null ? null : entity.getPersistentDataContainer().get(plugin.keys().archetype, PersistentDataType.STRING);
    }

    public boolean isScenarioMob(Entity entity) {
        return archetypeOf(entity) != null;
    }

    public int count() {
        return tracked.size();
    }

    public Map<String, Integer> countByArchetype() {
        Map<String, Integer> counts = new HashMap<>();
        for (String archetype : tracked.values()) counts.merge(archetype, 1, Integer::sum);
        return counts;
    }

    public Zombie spawnWalker(Location location) {
        ApocalypseConfig.Archetype archetype = plugin.settings().archetype(ApocalypseConfig.WALKER);
        Zombie zombie = location.getWorld().spawn(location, Zombie.class, CreatureSpawnEvent.SpawnReason.CUSTOM, mob -> configure(mob, archetype));
        tracked.put(zombie.getUniqueId(), archetype.id());
        return zombie;
    }

    private void configure(Zombie zombie, ApocalypseConfig.Archetype archetype) {
        zombie.setAdult();
        zombie.setShouldBurnInDay(false);
        zombie.setCanBreakDoors(false);
        zombie.setCanPickupItems(false);
        zombie.setPersistent(false);
        zombie.setRemoveWhenFarAway(true);
        EntityEquipment equipment = zombie.getEquipment();
        equipment.clear();
        for (EquipmentSlot slot : SLOTS) equipment.setDropChance(slot, 0f);
        if (random.nextBoolean()) {
            ItemStack shirt = ItemStack.of(Material.LEATHER_CHESTPLATE);
            shirt.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(CLOTHES[random.nextInt(CLOTHES.length)]));
            equipment.setChestplate(shirt);
        }
        base(zombie, Attribute.MAX_HEALTH, archetype.health());
        zombie.setHealth(archetype.health());
        base(zombie, Attribute.ATTACK_DAMAGE, archetype.damage());
        base(zombie, Attribute.MOVEMENT_SPEED, archetype.speed());
        base(zombie, Attribute.FOLLOW_RANGE, archetype.followRange());
        base(zombie, Attribute.SPAWN_REINFORCEMENTS, 0);
        zombie.customName(Component.text("Errant", NamedTextColor.GRAY));
        zombie.setCustomNameVisible(false);
        zombie.getPersistentDataContainer().set(plugin.keys().archetype, PersistentDataType.STRING, archetype.id());
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
}
