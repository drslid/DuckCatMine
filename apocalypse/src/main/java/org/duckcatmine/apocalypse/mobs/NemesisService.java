package org.duckcatmine.apocalypse.mobs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.persistence.Store;

/**
 * The Nemesis: one boss every N waves. It arrives about 200 blocks from a survivor, always walks towards the nearest
 * survivor, never jumps (it steps up one block), smashes walls in its way, roars to call the infected and kills with
 * one blow. A sprinting survivor outruns it. Also the Souriant's two-blow strike and its night look.
 */
public final class NemesisService implements Listener, AutoCloseable {
    public static final String NEMESIS = "nemesis", SMILER = "smiler";
    public static final double ARRIVAL = 190;
    private final DuckCatApocalypse plugin;
    private final Random random = new Random();
    private final Set<long[]> tickets = new HashSet<>();
    private final BossBar bar = BossBar.bossBar(Component.text("NÉMÉSIS", NamedTextColor.DARK_RED, TextDecoration.BOLD), 1f,
            BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
    private final Set<UUID> viewers = new HashSet<>();
    private UUID boss;
    private boolean summoning;
    private int waves;
    private long nextRoar, lastMovedAt, aloneSince;
    private Location lastPosition;

    public NemesisService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            String stored = plugin.database().call(c -> Store.meta(c, "waves"));
            waves = stored == null ? 0 : Integer.parseInt(stored);
        } catch (Exception e) {
            waves = 0;
        }
    }

    public int waves() {
        return waves;
    }

    /** Waves left before the next Nemesis (0 while one is hunting). */
    public int nextIn() {
        if (boss() != null) return 0;
        int every = plugin.settings().play().survival().bossEveryWaves();
        return every - Math.floorMod(waves, every);
    }

    public Mob boss() {
        return boss != null && plugin.getServer().getEntity(boss) instanceof Mob mob && mob.isValid() && !mob.isDead() ? mob : null;
    }

    /** A horde peak or a night horde: every N waves, the Nemesis comes. */
    public void wave(List<Player> active) {
        waves++;
        int snapshot = waves;
        plugin.database().submit(c -> {
            Store.putMeta(c, "waves", Integer.toString(snapshot));
            return null;
        });
        var rules = plugin.settings().play().survival();
        if (!rules.boss() || waves % rules.bossEveryWaves() != 0 || active.isEmpty()) return;
        summon(active.get(random.nextInt(active.size())));
    }

    public boolean summon(Player target) {
        if (summoning || boss() != null || !plugin.scenario().isScenarioWorld(target.getWorld())) return false;
        summoning = true;
        attempt(target.getWorld(), target.getLocation().clone(), 0);
        return true;
    }

    private void attempt(World world, Location center, int tries) {
        if (tries >= 10 || !plugin.scenario().isEnabled()) {
            summoning = false;
            plugin.getLogger().info("Némésis : aucun point d'arrivée trouvé à " + (int) ARRIVAL + " blocs.");
            return;
        }
        double angle = random.nextDouble() * Math.PI * 2;
        int x = (int) Math.floor(center.getX() + Math.cos(angle) * ARRIVAL), z = (int) Math.floor(center.getZ() + Math.sin(angle) * ARRIVAL);
        if (!world.getWorldBorder().isInside(new Location(world, x, center.getY(), z))) { attempt(world, center, tries + 1); return; }
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            Location spot = plugin.director().outdoorSpot(world, x, z);
            if (spot == null || near(world, spot, 120)) { attempt(world, center, tries + 1); return; }
            Zombie mob = plugin.mobs().spawn(NEMESIS, Appearance.NEMESIS, spot);
            summoning = false;
            if (mob == null) return;
            configure(mob);
            boss = mob.getUniqueId();
            lastMovedAt = plugin.clock().now();
            nextRoar = lastMovedAt + 8000;
            hold(mob);
            announce(spot);
        });
    }

    private void configure(Mob mob) {
        set(mob, Attribute.JUMP_STRENGTH, 0);
        set(mob, Attribute.STEP_HEIGHT, 1.1);
        set(mob, Attribute.FOLLOW_RANGE, 128);
        set(mob, Attribute.SAFE_FALL_DISTANCE, 12);
        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(false);
        mob.setSilent(false);
    }

    private static void set(Mob mob, Attribute attribute, double value) {
        var instance = mob.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private boolean near(World world, Location spot, double distance) {
        for (Player p : world.getPlayers()) if (p.getLocation().distanceSquared(spot) < distance * distance) return true;
        return false;
    }

    private void announce(Location at) {
        plugin.events().add("boss", "Le Némésis est arrivé (vague " + waves + ")");
        for (Player player : at.getWorld().getPlayers()) {
            if (!plugin.sessions().isSurvivor(player)) continue;
            player.showTitle(Title.title(Component.text("NÉMÉSIS", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                    Component.text("Il arrive. Il te trouvera. Cours.", NamedTextColor.RED),
                    Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofSeconds(4), java.time.Duration.ofSeconds(1))));
            Vector toward = at.toVector().subtract(player.getLocation().toVector()).setY(0);
            Location from = player.getLocation().add(toward.lengthSquared() > 1 ? toward.normalize().multiply(12) : new Vector());
            roarSound(player, from);
        }
    }

    private void roarSound(Player listener, Location from) {
        listener.playSound(from, Sound.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2f, 0.45f);
        listener.playSound(from, Sound.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 2f, 0.55f);
        listener.playSound(from, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.2f, 0.5f);
        listener.playSound(from, "duckcatmine:horror.nemesis_roar", SoundCategory.HOSTILE, 2f, 1f);
    }

    /** Every second. */
    public void tick() {
        Mob mob = boss();
        if (mob == null) {
            if (boss != null) clear();
            return;
        }
        long now = plugin.clock().now();
        hold(mob);
        Player target = nearest(mob);
        updateBar(mob);
        if (target == null) {
            if (aloneSince == 0) aloneSince = now;
            if (now - aloneSince > 300_000) { mob.remove(); clear(); }
            return;
        }
        aloneSince = 0;
        Location here = mob.getLocation();
        if (lastPosition == null || lastPosition.distanceSquared(here) > 0.25) { lastPosition = here; lastMovedAt = now; }
        double distance = Math.hypot(target.getX() - here.getX(), target.getZ() - here.getZ());
        if (distance < 24 && mob.hasLineOfSight(target)) {
            mob.setTarget(target);
        } else {
            Vector step = target.getLocation().toVector().subtract(here.toVector()).setY(0);
            if (step.lengthSquared() > 1) {
                step.normalize().multiply(Math.min(14, distance));
                Location waypoint = here.clone().add(step);
                int top = waypoint.getWorld().isChunkLoaded(waypoint.getBlockX() >> 4, waypoint.getBlockZ() >> 4)
                        ? waypoint.getWorld().getHighestBlockYAt(waypoint, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1 : here.getBlockY();
                waypoint.setY(Math.abs(top - here.getY()) < 6 ? top : here.getY());
                mob.getPathfinder().moveTo(waypoint, 1.0);
            }
        }
        if (now - lastMovedAt > 1500 && distance < 48) smash(mob, target.getLocation());
        if (now >= nextRoar && distance < 64) roar(mob, target, now);
    }

    private Player nearest(Mob mob) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : mob.getWorld().getPlayers()) {
            if (!plugin.sessions().isSurvivor(player) || player.isDead() || player.getGameMode() == GameMode.CREATIVE
                    || player.getGameMode() == GameMode.SPECTATOR || plugin.scenario().inRefuge(player.getLocation())) continue;
            double d = player.getLocation().distanceSquared(mob.getLocation());
            if (d < bestDistance) { bestDistance = d; best = player; }
        }
        return best;
    }

    /** Walls, doors and barricades in front of it, up to its full height, break at once. */
    private void smash(Mob mob, Location target) {
        Vector direction = target.toVector().subtract(mob.getLocation().toVector()).setY(0);
        if (direction.lengthSquared() < 0.04) return;
        direction.normalize();
        boolean broke = false;
        for (double ahead : new double[] {0.8, 1.4})
            for (int dy = 0; dy < 3; dy++) {
                Block block = mob.getLocation().add(direction.clone().multiply(ahead)).add(0, dy + 0.2, 0).getBlock();
                if (!block.equals(mob.getLocation().getBlock()) && plugin.siege().smash(block)) broke = true;
            }
        if (broke) {
            mob.swingMainHand();
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 2f, 0.5f);
            lastMovedAt = plugin.clock().now();
        }
    }

    private void roar(Mob mob, Player target, long now) {
        var archetype = plugin.settings().archetype(NEMESIS);
        double radius = archetype == null ? 96 : archetype.ability("roar-radius", 96);
        nextRoar = now + (long) ((archetype == null ? 25 : archetype.ability("roar-cooldown-seconds", 25)) * 1000);
        Location at = mob.getEyeLocation();
        for (Player player : mob.getWorld().getPlayers())
            if (player.getLocation().distanceSquared(at) < 160 * 160) roarSound(player, at);
        mob.getWorld().spawnParticle(Particle.SONIC_BOOM, at, 3, 0.5, 0.3, 0.5, 0);
        mob.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 20, 0.6, 0.4, 0.6, 0.02);
        plugin.noise().emitInfected(mob.getLocation(), radius);
        int reinforcements = archetype == null ? 6 : (int) archetype.ability("reinforcements", 6);
        if (reinforcements > 0) plugin.director().reinforce(target, reinforcements);
        for (Player player : mob.getWorld().getPlayers())
            if (plugin.sessions().isSurvivor(player) && player.getLocation().distanceSquared(at) < 24 * 24)
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, true));
    }

    private void updateBar(Mob mob) {
        var max = mob.getAttribute(Attribute.MAX_HEALTH);
        bar.progress((float) Math.max(0, Math.min(1, mob.getHealth() / (max == null ? 400 : max.getValue()))));
        Set<UUID> now = new HashSet<>();
        for (Player player : mob.getWorld().getPlayers())
            if (player.getLocation().distanceSquared(mob.getLocation()) < 128 * 128) { now.add(player.getUniqueId()); player.showBossBar(bar); }
        for (UUID id : viewers) if (!now.contains(id)) { Player p = plugin.getServer().getPlayer(id); if (p != null) p.hideBossBar(bar); }
        viewers.clear();
        viewers.addAll(now);
    }

    /** Keeps the boss's 3×3 chunks ticking so it really walks from 200 blocks away. */
    private void hold(Mob mob) {
        World world = mob.getWorld();
        int cx = mob.getLocation().getBlockX() >> 4, cz = mob.getLocation().getBlockZ() >> 4;
        Set<long[]> wanted = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) wanted.add(new long[] {cx + dx, cz + dz});
        for (long[] old : new ArrayList<>(tickets))
            if (wanted.stream().noneMatch(w -> w[0] == old[0] && w[1] == old[1])) { world.removePluginChunkTicket((int) old[0], (int) old[1], plugin); tickets.remove(old); }
        for (long[] chunk : wanted)
            if (tickets.stream().noneMatch(t -> t[0] == chunk[0] && t[1] == chunk[1])) { world.addPluginChunkTicket((int) chunk[0], (int) chunk[1], plugin); tickets.add(chunk); }
    }

    private void clear() {
        boss = null;
        World world = plugin.scenario().world();
        if (world != null) for (long[] t : tickets) world.removePluginChunkTicket((int) t[0], (int) t[1], plugin);
        tickets.clear();
        for (UUID id : viewers) { Player p = plugin.getServer().getPlayer(id); if (p != null) p.hideBossBar(bar); }
        viewers.clear();
        lastPosition = null;
    }

    /** One blow from the Nemesis kills; the Souriant needs two. Armour does not save you. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onStrike(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.sessions().isSurvivor(player)) return;
        String kind = plugin.mobs().archetypeOf(event.getDamager());
        if (!NEMESIS.equals(kind) && !SMILER.equals(kind)) return;
        var max = player.getAttribute(Attribute.MAX_HEALTH);
        double full = max == null ? 20 : max.getValue();
        double blow = NEMESIS.equals(kind) ? full * 10 : full / 2 + 0.5;
        event.setDamage(EntityDamageEvent.DamageModifier.BASE, blow);
        for (var modifier : List.of(EntityDamageEvent.DamageModifier.ARMOR, EntityDamageEvent.DamageModifier.MAGIC, EntityDamageEvent.DamageModifier.RESISTANCE,
                EntityDamageEvent.DamageModifier.BLOCKING, EntityDamageEvent.DamageModifier.ABSORPTION))
            if (event.isApplicable(modifier)) event.setDamage(modifier, 0);
        if (NEMESIS.equals(kind)) player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.5f);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (!event.getEntity().getUniqueId().equals(boss)) return;
        Location at = event.getEntity().getLocation();
        clear();
        plugin.events().add("boss", "Le Némésis est tombé");
        for (Player player : at.getWorld().getPlayers()) {
            if (!plugin.sessions().isSurvivor(player)) continue;
            player.showTitle(Title.title(Component.text("Le Némésis est tombé", NamedTextColor.GOLD),
                    Component.text("Pour cette fois.", NamedTextColor.GRAY)));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 0.8f);
        }
    }

    /** Every second: the Souriant laughs near survivors; at night only its grin shows when the pack is missing. */
    public void smilers() {
        World world = plugin.scenario().world();
        if (world == null) return;
        boolean night = !world.isDayTime();
        for (UUID id : plugin.mobs().trackedIds()) {
            if (!(plugin.getServer().getEntity(id) instanceof Mob mob) || !SMILER.equals(plugin.mobs().archetypeOf(mob))) continue;
            if (!plugin.horrorModels().active(mob)) {
                if (night) mob.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false, false));
                else mob.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
            if (random.nextInt(6) == 0)
                for (Player player : world.getPlayers())
                    if (player.getLocation().distanceSquared(mob.getLocation()) < 20 * 20) {
                        world.playSound(mob.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, SoundCategory.HOSTILE, 1f, 0.55f);
                        break;
                    }
        }
    }

    public boolean isBoss(Entity entity) {
        return entity != null && entity.getUniqueId().equals(boss);
    }

    @Override
    public void close() {
        Mob mob = boss();
        if (mob != null) mob.remove();
        clear();
    }
}
