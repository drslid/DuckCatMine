package org.duckcatmine.apocalypse.combat;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

/**
 * The makeshift pistol (spec §13) and the sound decoy. A shot is a ray traced from the eyes that stops at the first
 * solid block or creature; damage goes through the normal damage events (PvP rules, teams, protections apply). One
 * cartridge and one hit per shot, a 6-round magazine reloaded in 2.5 s (interrupted if the pistol leaves the hand),
 * wear per shot, and a loud noise that draws the infected to where the shot was fired.
 */
public final class FirearmService implements Listener {
    private record Reload(String gunId, long doneAt) {
    }

    private final DuckCatApocalypse plugin;
    private final Map<UUID, Long> nextShot = new HashMap<>();
    private final Map<UUID, Reload> reloads = new HashMap<>();

    public FirearmService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    private boolean holdsPistol(Player player) {
        return plugin.items().is(player.getInventory().getItemInMainHand(), ItemRegistry.PISTOL);
    }

    private static String gunId(ItemStack pistol, DuckCatApocalypse plugin) {
        return pistol.getPersistentDataContainer().get(plugin.keys().gunId, PersistentDataType.STRING);
    }

    private int magazine(ItemStack pistol) {
        Integer value = pistol.getPersistentDataContainer().get(plugin.keys().magazine, PersistentDataType.INTEGER);
        return value == null ? 0 : value;
    }

    private static boolean broken(ItemStack pistol) {
        Integer damage = pistol.getData(DataComponentTypes.DAMAGE);
        Integer max = pistol.getData(DataComponentTypes.MAX_DAMAGE);
        return damage != null && max != null && damage >= max;
    }

    private int ammo(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) if (plugin.items().is(stack, ItemRegistry.AMMO)) total += stack.getAmount();
        return total;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Player player = event.getPlayer();
        if (!holdsPistol(player)) return;
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        fire(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!holdsPistol(event.getPlayer())) return;
        event.setCancelled(true);
        startReload(event.getPlayer());
    }

    private boolean allowed(Player player) {
        if (!plugin.sessions().isSurvivor(player)) return false;
        if (plugin.scenario().inRefuge(player.getLocation())) {
            Text.warn(player, "Pas d'arme à feu dans le refuge.");
            return false;
        }
        return true;
    }

    public void fire(Player player) {
        if (!allowed(player) || reloads.containsKey(player.getUniqueId())) return;
        ItemStack pistol = player.getInventory().getItemInMainHand();
        ApocalypseConfig.Pistol stats = plugin.settings().play().pistol();
        long now = plugin.clock().now();
        Long ready = nextShot.get(player.getUniqueId());
        if (ready != null && ready > now) return;
        if (broken(pistol)) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 0.6f);
            Text.warn(player, "Pistolet hors d'usage : répare-le à l'atelier mécanique.");
            return;
        }
        if (magazine(pistol) <= 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1.4f);
            if (ammo(player) > 0) startReload(player);
            else player.sendActionBar(Component.text("Chargeur vide — aucune munition", NamedTextColor.RED));
            return;
        }
        nextShot.put(player.getUniqueId(), now + (long) (stats.fireDelaySeconds() * 1000));
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        RayTraceResult hit = player.getWorld().rayTrace(eye, direction, stats.range(), FluidCollisionMode.NEVER, true, 0.15,
                entity -> entity instanceof LivingEntity living && entity != player && !living.isDead() && !(entity instanceof ArmorStand));
        double length = hit == null ? stats.range() : hit.getHitPosition().distance(eye.toVector());
        for (double d = 1.5; d < length; d += 1.5)
            player.getWorld().spawnParticle(Particle.CRIT, eye.clone().add(direction.clone().multiply(d)), 1, 0, 0, 0, 0);
        player.getWorld().spawnParticle(Particle.SMOKE, eye.clone().add(direction.clone().multiply(0.8)), 4, 0.05, 0.05, 0.05, 0.01);
        player.getWorld().playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.9f);
        player.getWorld().playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.5f, 0.6f);
        if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
            target.damage(stats.damage(), DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(player).withDirectEntity(player).build());
            target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getEyeLocation(), 4, 0.2, 0.2, 0.2, 0);
        } else if (hit != null && hit.getHitBlock() != null) {
            player.getWorld().spawnParticle(Particle.BLOCK, hit.getHitPosition().toLocation(player.getWorld()), 8, 0.05, 0.05, 0.05,
                    hit.getHitBlock().getBlockData());
        }
        int left = magazine(pistol) - 1;
        pistol.editPersistentDataContainer(data -> data.set(plugin.keys().magazine, PersistentDataType.INTEGER, left));
        Integer damage = pistol.getData(DataComponentTypes.DAMAGE);
        pistol.setData(DataComponentTypes.DAMAGE, (damage == null ? 0 : damage) + 1);
        player.getInventory().setItemInMainHand(pistol);
        plugin.noise().emit(player.getLocation(), plugin.settings().play().noise().gunshot(), player);
        plugin.protection().endByAction(player);
        show(player);
    }

    public void startReload(Player player) {
        if (!allowed(player) || reloads.containsKey(player.getUniqueId())) return;
        ItemStack pistol = player.getInventory().getItemInMainHand();
        ApocalypseConfig.Pistol stats = plugin.settings().play().pistol();
        if (magazine(pistol) >= stats.magazine()) return;
        if (ammo(player) <= 0) {
            player.sendActionBar(Component.text("Aucune munition de pistolet", NamedTextColor.RED));
            return;
        }
        reloads.put(player.getUniqueId(), new Reload(gunId(pistol, plugin), plugin.clock().now() + (long) (stats.reloadSeconds() * 1000)));
        player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 1f, 1f);
    }

    /** Every 5 ticks: finishes or interrupts reloads and shows the magazine to whoever holds a pistol. */
    public void tick() {
        long now = plugin.clock().now();
        for (Map.Entry<UUID, Reload> entry : Map.copyOf(reloads).entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            Reload reload = entry.getValue();
            if (player == null || player.isDead() || !holdsPistol(player)
                    || !reload.gunId().equals(gunId(player.getInventory().getItemInMainHand(), plugin))) {
                reloads.remove(entry.getKey());
                if (player != null) player.sendActionBar(Component.text("Rechargement interrompu", NamedTextColor.GRAY));
                continue;
            }
            if (now < reload.doneAt()) {
                player.sendActionBar(Component.text("Rechargement…", NamedTextColor.YELLOW));
                continue;
            }
            reloads.remove(entry.getKey());
            finishReload(player);
        }
        var world = plugin.scenario().world();
        if (world == null) return;
        for (Player player : world.getPlayers()) if (!reloads.containsKey(player.getUniqueId()) && holdsPistol(player)) show(player);
    }

    private void finishReload(Player player) {
        ItemStack pistol = player.getInventory().getItemInMainHand();
        int capacity = plugin.settings().play().pistol().magazine();
        int needed = capacity - magazine(pistol);
        int loaded = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && loaded < needed; slot++) {
            if (!plugin.items().is(contents[slot], ItemRegistry.AMMO)) continue;
            int taken = Math.min(needed - loaded, contents[slot].getAmount());
            contents[slot].setAmount(contents[slot].getAmount() - taken);
            if (contents[slot].getAmount() <= 0) contents[slot] = null;
            loaded += taken;
        }
        player.getInventory().setStorageContents(contents);
        int filled = magazine(pistol) + loaded;
        pistol.editPersistentDataContainer(data -> data.set(plugin.keys().magazine, PersistentDataType.INTEGER, filled));
        player.getInventory().setItemInMainHand(pistol);
        player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1f, 1.2f);
        show(player);
    }

    private void show(Player player) {
        ItemStack pistol = player.getInventory().getItemInMainHand();
        String state = broken(pistol) ? " — HORS D'USAGE" : "";
        player.sendActionBar(Component.text("Pistolet " + magazine(pistol) + "/" + plugin.settings().play().pistol().magazine()
                + " — munitions : " + ammo(player) + state, broken(pistol) ? NamedTextColor.RED : NamedTextColor.GOLD));
    }

    public void forget(UUID id) {
        reloads.remove(id);
        nextShot.remove(id);
    }

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
