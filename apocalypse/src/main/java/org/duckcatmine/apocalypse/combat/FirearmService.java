package org.duckcatmine.apocalypse.combat;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.items.ItemRegistry;

/** Server-side firearms: wall-blocked rays, ordinary damage protections, no ammo, finite wear. */
public final class FirearmService implements Listener {
    private final DuckCatApocalypse plugin;
    private final Map<UUID, Long> nextShot = new HashMap<>();
    private final Set<UUID> automatic = new HashSet<>();
    private BukkitTask firing;
    private int visualTicks;

    public FirearmService(DuckCatApocalypse plugin) { this.plugin = plugin; }

    private Gun held(Player player) {
        return Gun.of(plugin.items().idOf(player.getInventory().getItemInMainHand()), plugin.settings());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Player player = event.getPlayer();
        Gun gun = held(player);
        if (gun == null) return;
        // Interact-air can arrive cancelled by vanilla because PAPER has no default action.
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        boolean allowed = plugin.sessions().isSurvivor(player) && !player.isDead() && !plugin.scenario().inRefuge(player.getLocation());
        event.setUseItemInHand(allowed && gun.automatic() ? org.bukkit.event.Event.Result.ALLOW : org.bukkit.event.Event.Result.DENY);
        if (!allowed) { if (plugin.sessions().isSurvivor(player)) Text.warn(player, "Pas d'arme à feu dans le refuge."); return; }
        fire(player);
        if (gun.automatic() && held(player) != null) {
            automatic.add(player.getUniqueId());
            if (firing == null) firing = plugin.getServer().getScheduler().runTaskTimer(plugin, this::autoTick, 2L, 2L);
        }
    }

    @EventHandler public void onStop(PlayerStopUsingItemEvent event) { automatic.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { forget(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onConsume(PlayerItemConsumeEvent event) {
        if (Gun.of(plugin.items().idOf(event.getItem()), plugin.settings()) != null) event.setCancelled(true);
    }

    private void autoTick() {
        for (UUID id : Set.copyOf(automatic)) {
            Player player = plugin.getServer().getPlayer(id);
            Gun gun = player == null ? null : held(player);
            if (gun == null || !gun.automatic() || !player.hasActiveItem() || player.getActiveItemHand() != EquipmentSlot.HAND
                    || !plugin.items().is(player.getActiveItem(), ItemRegistry.SMG)
                    || !plugin.sessions().isSurvivor(player) || player.isDead() || plugin.scenario().inRefuge(player.getLocation())) { automatic.remove(id); continue; }
            fire(player);
            if (held(player) == null) automatic.remove(id);
        }
        if (automatic.isEmpty() && firing != null) { firing.cancel(); firing = null; }
    }

    public void fire(Player player) {
        Gun stats = held(player);
        if (stats == null || !plugin.sessions().isSurvivor(player) || player.isDead() || plugin.scenario().inRefuge(player.getLocation())) return;
        long now = plugin.clock().now();
        if (nextShot.getOrDefault(player.getUniqueId(), 0L) > now) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        int damage = java.util.Objects.requireNonNullElse(weapon.getData(DataComponentTypes.DAMAGE), 0);
        int max = java.util.Objects.requireNonNullElse(weapon.getData(DataComponentTypes.MAX_DAMAGE), stats.durability());
        if(ItemRegistry.ROCKET_LAUNCHER.equals(stats.id())){fireRocket(player,weapon,damage,now);return;}
        if (damage >= max) { Text.warn(player, "Arme hors d'usage : répare-la à l'atelier mécanique."); nextShot.put(player.getUniqueId(), now + 1500); return; }
        nextShot.put(player.getUniqueId(), now + stats.delayMs());
        Location eye = player.getEyeLocation();
        // Sum pellets by target: one damage event per shot, no invulnerability-frame exploit.
        Map<LivingEntity, Double> hits = new HashMap<>();
        var random = java.util.concurrent.ThreadLocalRandom.current();
        for (int pellet = 0; pellet < stats.pellets(); pellet++) {
            Vector direction = eye.getDirection();
            if (stats.spread() > 0) direction.add(new Vector(random.nextGaussian() * stats.spread(), random.nextGaussian() * stats.spread(), random.nextGaussian() * stats.spread())).normalize();
            RayTraceResult hit = trace(eye, direction, stats.range(), player);
            double length = hit == null ? stats.range() : hit.getHitPosition().distance(eye.toVector());
            for (double d = 1.5; d < length; d += 3)
                player.getWorld().spawnParticle(Particle.CRIT, eye.clone().add(direction.clone().multiply(d)), 1, 0, 0, 0, 0);
            if (hit != null && hit.getHitEntity() instanceof LivingEntity target) hits.merge(target, stats.damage(), Double::sum);
            else if (hit != null && hit.getHitBlock() != null)
                player.getWorld().spawnParticle(Particle.BLOCK, hit.getHitPosition().toLocation(player.getWorld()), 3, .05, .05, .05, hit.getHitBlock().getBlockData());
        }
        for (var hit : hits.entrySet()) {
            double before = hit.getKey().getHealth();
            hit.getKey().damage(hit.getValue(), DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(player).withDirectEntity(player).build());
            // A successful automatic shot does not suppress the next shot for vanilla's half-second hurt window.
            if (stats.automatic() && hit.getKey().getHealth() < before) hit.getKey().setNoDamageTicks(0);
        }
        player.getWorld().spawnParticle(Particle.SMOKE, eye.clone().add(eye.getDirection().multiply(.8)), 3, .05, .05, .05, .01);
        player.getWorld().playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, stats.pellets() > 1 ? 1.8f : 1.1f, stats.pellets() > 1 ? .5f : 1.4f);
        plugin.horrorModels().sound(eye,stats.id(),.9f);
        if (damage + 1 >= max) {
            player.clearActiveItem();
            player.getInventory().setItemInMainHand(null);
            player.playSound(eye, Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            Text.warn(player, stats.label() + " cassé. Répare la prochaine arme avant la fin de sa barre d'usure.");
        } else {
            weapon.setData(DataComponentTypes.DAMAGE, damage + 1);
            player.getInventory().setItemInMainHand(weapon);
        }
        plugin.noise().emit(player.getLocation(), plugin.settings().play().noise().gunshot(), player);
        plugin.protection().endByAction(player);
    }

    public static RayTraceResult trace(Location eye, Vector direction, double range, org.bukkit.entity.Entity excluded) {
        return eye.getWorld().rayTrace(eye, direction, range, FluidCollisionMode.NEVER, true, .15,
                entity -> entity instanceof LivingEntity living && entity != excluded && !living.isDead() && !(entity instanceof ArmorStand));
    }

    /** Existing ticker only updates the wear hint; automatic fire runs only while a trigger is held. */
    public void tick() {
        var world = plugin.scenario().world();
        if (world == null) return;
        boolean updateVisuals=++visualTicks%4==0;
        for (Player player : world.getPlayers()) {
            if(updateVisuals&&plugin.sessions().isSurvivor(player))refreshInventory(player.getInventory());
            Gun gun = held(player);
            if (gun == null || !plugin.sessions().isSurvivor(player)) continue;
            ItemStack item = player.getInventory().getItemInMainHand();
            int max = java.util.Objects.requireNonNullElse(item.getData(DataComponentTypes.MAX_DAMAGE), gun.durability());
            int wear = java.util.Objects.requireNonNullElse(item.getData(DataComponentTypes.DAMAGE), 0);
            int left=ItemRegistry.ROCKET_LAUNCHER.equals(gun.id())?RocketBudget.remaining(item.getPersistentDataContainer().get(plugin.keys().charges,PersistentDataType.INTEGER),wear):Math.max(0,max-wear);
            player.sendActionBar(Component.text(gun.label()+" · "+left+(ItemRegistry.ROCKET_LAUNCHER.equals(gun.id())?" / 10 roquettes":" tirs · sans munitions"),NamedTextColor.GOLD));
        }
    }

    private void fireRocket(Player player,ItemStack weapon,int wear,long now){
        int left=RocketBudget.remaining(weapon.getPersistentDataContainer().get(plugin.keys().charges,PersistentDataType.INTEGER),wear);
        if(left<=0){Text.warn(player,"Lance-roquettes épuisé : ses 10 tirs ont été utilisés.");nextShot.put(player.getUniqueId(),now+1500);return;}
        if(!plugin.rockets().launch(player)){Text.warn(player,"Trop de roquettes en vol ou zone non disponible. Réessaie dans un instant.");nextShot.put(player.getUniqueId(),now+300);return;}
        nextShot.put(player.getUniqueId(),now+2000);
        weapon.editPersistentDataContainer(data->data.set(plugin.keys().charges,PersistentDataType.INTEGER,left-1));
        weapon.setData(DataComponentTypes.MAX_DAMAGE,10);weapon.setData(DataComponentTypes.DAMAGE,10-left+1);
        if(left==1){player.getInventory().setItemInMainHand(null);player.playSound(player.getLocation(),Sound.ENTITY_ITEM_BREAK,1,1);Text.warn(player,"Les 10 roquettes sont épuisées.");}
        else player.getInventory().setItemInMainHand(weapon);
        plugin.noise().emit(player.getLocation(),plugin.settings().play().noise().explosion(),player);plugin.protection().endByAction(player);
    }
    private void refreshInventory(org.bukkit.inventory.Inventory inventory){
        for(int slot=0;slot<inventory.getSize();slot++){
            ItemStack item=inventory.getItem(slot);
            if(org.duckcatmine.apocalypse.items.GunModels.apply(item,plugin.items().idOf(item)))inventory.setItem(slot,item);
        }
    }
    @EventHandler public void onOpen(org.bukkit.event.inventory.InventoryOpenEvent event){
        if(event.getPlayer() instanceof Player p&&plugin.sessions().isSurvivor(p))refreshInventory(event.getInventory());
    }
    @EventHandler public void onItem(org.bukkit.event.entity.ItemSpawnEvent event){
        ItemStack item=event.getEntity().getItemStack();
        if(org.duckcatmine.apocalypse.items.GunModels.apply(item,plugin.items().idOf(item)))event.getEntity().setItemStack(item);
    }

    public void forget(UUID id) { automatic.remove(id); nextShot.remove(id); }

    // ------------------------------------------------------------------ decoy

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Snowball ball && plugin.items().is(ball.getItem(), ItemRegistry.DECOY))
            ball.getPersistentDataContainer().set(plugin.keys().decoy, PersistentDataType.BOOLEAN, true);
    }

    @EventHandler
    public void onLand(ProjectileHitEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(plugin.keys().decoy)) return;
        Location at = event.getEntity().getLocation();
        Player owner = event.getEntity().getShooter() instanceof Player player ? player : null;
        int pulses = (int) Math.round(plugin.settings().play().noise().decoySeconds());
        BukkitTask[] task = new BukkitTask[1];
        int[] count = {0};
        task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (count[0]++ >= pulses || !plugin.scenario().isEnabled()) {
                task[0].cancel();
                return;
            }
            at.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 2.5f, count[0] % 2 == 0 ? 1.2f : 0.9f);
            at.getWorld().spawnParticle(Particle.NOTE, at.clone().add(0, 0.6, 0), 3, 0.3, 0.3, 0.3, 1);
            plugin.noise().emit(at, plugin.settings().play().noise().decoy(), null);
        }, 0L, 20L);
        if (owner != null) Text.info(owner, "Le leurre sonne : les infectés vont vers lui.");
    }
}
