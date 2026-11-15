package org.duckcatmine.apocalypse.fauna;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;

/**
 * Stray dogs roam the streets now and then; meat or a bone wins one over. A tamed dog follows its survivor (right click:
 * sit / follow, vanilla), attacks the infected nearby, and the infected never attack it.
 */
public final class DogService implements Listener {
    private static final Set<Material> TREATS = Set.of(Material.BONE, Material.BEEF, Material.COOKED_BEEF, Material.PORKCHOP,
            Material.COOKED_PORKCHOP, Material.CHICKEN, Material.COOKED_CHICKEN, Material.MUTTON, Material.COOKED_MUTTON,
            Material.RABBIT, Material.COOKED_RABBIT, Material.ROTTEN_FLESH);
    private static final int MAX_STRAYS = 6;
    private final DuckCatApocalypse plugin;
    private final NamespacedKey key;

    public DogService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "companion");
    }

    public boolean isDog(Entity entity) {
        return entity instanceof Wolf && entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Once a second: tamed dogs go for the closest infected; now and then a stray appears near a survivor. */
    public void tick() {
        World world = plugin.scenario().world();
        if (world == null || !plugin.scenario().isEnabled()) return;
        int strays = 0;
        for (Wolf wolf : world.getEntitiesByClass(Wolf.class)) {
            if (!isDog(wolf)) continue;
            if (!wolf.isTamed()) {
                strays++;
                if (world.getPlayers().stream().noneMatch(p -> p.getLocation().distanceSquared(wolf.getLocation()) < 160 * 160)) wolf.remove();
                continue;
            }
            if (wolf.isSitting() || (wolf.getTarget() != null && !wolf.getTarget().isDead())) continue;
            LivingEntity prey = null;
            double best = 12 * 12;
            for (Entity near : wolf.getNearbyEntities(12, 5, 12)) {
                if (!(near instanceof LivingEntity living) || living.isDead() || !plugin.mobs().isScenarioMob(near)) continue;
                double d = near.getLocation().distanceSquared(wolf.getLocation());
                if (d < best && wolf.hasLineOfSight(near)) { best = d; prey = living; }
            }
            if (prey != null) wolf.setTarget(prey);
        }
        if (strays >= MAX_STRAYS) return;
        for (Player player : world.getPlayers()) {
            if (!plugin.sessions().isSurvivor(player) || plugin.scenario().inRefuge(player.getLocation())
                    || ThreadLocalRandom.current().nextInt(240) != 0) continue;
            if (!player.getNearbyEntities(48, 16, 48).stream().filter(this::isDog).allMatch(e -> ((Wolf) e).isTamed())) continue;
            Location spot = spot(player.getLocation());
            if (spot == null) continue;
            world.spawn(spot, Wolf.class, wolf -> {
                wolf.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
                wolf.setRemoveWhenFarAway(false);
            });
            return;
        }
    }

    private Location spot(Location near) {
        World world = near.getWorld();
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2), distance = 16 + ThreadLocalRandom.current().nextDouble(12);
            int x = (int) Math.floor(near.getX() + Math.cos(angle) * distance), z = (int) Math.floor(near.getZ() + Math.sin(angle) * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block ground = world.getBlockAt(x, y, z);
            if (!ground.getType().isSolid() || ground.isLiquid() || Math.abs(y + 1 - near.getY()) > 6) continue;
            Location at = new Location(world, x + 0.5, y + 1, z + 0.5);
            if (!plugin.scenario().inRefuge(at)) return at;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Wolf wolf) || !isDog(wolf) || wolf.isTamed()) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!TREATS.contains(hand.getType())) {
            Text.info(player, "Un chien errant. Donne-lui de la viande ou un os pour l'apprivoiser.");
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        if (ThreadLocalRandom.current().nextInt(3) != 0) {
            wolf.getWorld().spawnParticle(Particle.SMOKE, wolf.getLocation().add(0, 0.8, 0), 6, 0.2, 0.2, 0.2, 0.01);
            Text.info(player, "Il mange, mais reste méfiant. Encore un peu.");
            return;
        }
        wolf.setOwner(player);
        wolf.setTamed(true);
        wolf.setSitting(false);
        wolf.getWorld().spawnParticle(Particle.HEART, wolf.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0);
        wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 1f, 1.1f);
        Text.good(player, "Ce chien te suit désormais et attaque les infectés. Clic droit : il reste là / il te suit.");
    }

    /** The infected ignore dogs. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (isDog(event.getTarget()) && plugin.mobs().isScenarioMob(event.getEntity())) event.setCancelled(true);
    }

    /** ...and cannot hurt (or contaminate) them. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent event) {
        if (!isDog(event.getEntity())) return;
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) damager = shooter;
        if (plugin.mobs().isScenarioMob(damager) || damager instanceof Mob && !(damager instanceof Wolf)) event.setCancelled(true);
    }
}
