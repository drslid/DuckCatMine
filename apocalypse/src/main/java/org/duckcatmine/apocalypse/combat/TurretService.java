package org.duckcatmine.apocalypse.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.crafting.PlacedBlocks;
import org.duckcatmine.apocalypse.items.ItemRegistry;

/** Craftable stationary crossbow. Only resident chunks are indexed; one display per base, bounded targeting batches. */
public final class TurretService implements Listener {
    public static final double RANGE = 8, DAMAGE = 4;
    public static final int PER_CHUNK = 4;
    private static final long COOLDOWN = 1250;
    private record Position(World world, int x, int y, int z) {
        static Position of(Block block) { return new Position(block.getWorld(), block.getX(), block.getY(), block.getZ()); }
        Location muzzle() { return new Location(world, x + .5, y + 1.22, z + .5); }
    }
    private static final class Turret {
        final Position pos;
        PlacedBlocks.Placed placed;
        ItemDisplay model;
        long nextShot;
        int fired, hits;
        String outcome="none";
        String lastTravel="none";
        Turret(Position pos, PlacedBlocks.Placed placed) { this.pos = pos; this.placed = placed; }
    }
    private record Shot(Location origin, long expires, Turret turret) {}
    private final DuckCatApocalypse plugin;
    private final Map<Position, Turret> turrets = new LinkedHashMap<>();
    private final Map<UUID, Shot> shots = new HashMap<>();
    private final NamespacedKey bolt;
    private int cursor;

    public TurretService(DuckCatApocalypse plugin) { this.plugin = plugin; bolt = new NamespacedKey(plugin, "turret_bolt"); }
    public void track(Block block, PlacedBlocks.Placed placed) {
        Position pos = Position.of(block);
        Turret turret = turrets.computeIfAbsent(pos, p -> new Turret(p, placed));
        turret.placed = placed;
    }
    public void untrack(Block block) { remove(Position.of(block)); }
    private void remove(Position pos) {
        Turret turret = turrets.remove(pos);
        if (turret != null && turret.model != null) turret.model.remove();
    }
    public boolean canManage(Player player, UUID owner) {
        return player.hasPermission("duckcat.apocalypse.admin") || player.getUniqueId().equals(owner)
                || plugin.teams().sameTeam(player.getUniqueId(), owner);
    }
    public boolean isTurret(Block block) {
        var placed = plugin.placed().get(block);
        return placed != null && ItemRegistry.AUTO_TURRET.equals(placed.kind());
    }
    public String placementProblem(Block block) {
        if (!block.getRelative(BlockFace.DOWN).isSolid() || !block.getRelative(BlockFace.UP).getType().isAir())
            return "Pose la mini-arbalète sur un sol solide, avec un bloc libre au-dessus.";
        if (plugin.placed().count(block.getChunk(), p -> ItemRegistry.AUTO_TURRET.equals(p.kind())) >= PER_CHUNK)
            return "Déjà 4 mini-arbalètes dans cette zone de 16 × 16 blocs.";
        return null;
    }
    /** Called every five ticks. At most sixteen bases acquire a target per call, with fair rotation. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (var entry : Map.copyOf(shots).entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof Arrow arrow) || !arrow.isValid()) { shots.remove(entry.getKey()); continue; }
            Shot shot = entry.getValue();
            if (now >= shot.expires() || !arrow.getWorld().equals(shot.origin().getWorld()) || arrow.isInBlock()
                    || arrow.getLocation().distanceSquared(shot.origin()) > RANGE * RANGE) {
                shot.turret().lastTravel="age="+arrow.getTicksLived()+" ticking="+arrow.isTicking()+" pos="+arrow.getLocation()+" block="+arrow.isInBlock()+" expired="+(now>=shot.expires());
                arrow.remove(); shots.remove(entry.getKey());
            }
        }
        if (!plugin.scenario().isEnabled() || turrets.isEmpty()) return;
        List<Turret> snapshot = List.copyOf(turrets.values());
        for (int n = 0; n < Math.min(16, snapshot.size()); n++) {
            Turret turret = snapshot.get(Math.floorMod(cursor++, snapshot.size()));
            Position pos = turret.pos;
            if (!pos.world().isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) { remove(pos); continue; }
            Block block = pos.world().getBlockAt(pos.x(), pos.y(), pos.z());
            if (block.getType() != Material.DISPENSER || !isTurret(block)) { plugin.placed().remove(block); continue; }
            if (turret.model == null || !turret.model.isValid()) turret.model = model(pos.muzzle());
            if (turret.placed.data() != 0 || now < turret.nextShot || plugin.scenario().inRefuge(pos.muzzle())) continue;
            LivingEntity target = target(pos.muzzle());
            if (target == null) continue;
            turret.nextShot = now + COOLDOWN;
            shoot(turret, target, now);
        }
    }
    private LivingEntity target(Location origin) {
        LivingEntity best = null;
        double nearest = RANGE * RANGE;
        for (Entity entity : origin.getWorld().getNearbyEntities(origin, RANGE, RANGE, RANGE)) {
            if (!(entity instanceof LivingEntity living) || living.isDead() || !plugin.mobs().isScenarioMob(living)
                    || plugin.scenario().inRefuge(living.getLocation())) continue;
            Location aim = aim(living);
            double distance = aim.distanceSquared(origin);
            if (distance > nearest || distance < .01 || !loadedRay(origin,aim)) continue;
            if (origin.getWorld().rayTraceBlocks(origin, aim.toVector().subtract(origin.toVector()).normalize(),
                    Math.sqrt(distance), org.bukkit.FluidCollisionMode.NEVER, true) != null) continue;
            nearest = distance; best = living;
        }
        return best;
    }
    private static boolean loadedRay(Location from, Location to) {
        var delta=to.toVector().subtract(from.toVector());
        int steps=(int)Math.ceil(delta.length()*2);
        for(int n=0;n<=steps;n++) {
            var point=from.clone().add(delta.clone().multiply(n/(double)Math.max(1,steps)));
            if(!from.getWorld().isChunkLoaded(point.getBlockX()>>4,point.getBlockZ()>>4)) return false;
        }
        return true;
    }
    public boolean hasVisual(Block block) {
        Turret turret=turrets.get(Position.of(block));
        return turret!=null && turret.model!=null && turret.model.isValid();
    }
    /** Diagnostic on demand; no logging or extra scans in the combat loop. */
    public String diagnostics(Block base) {
        Turret t=turrets.get(Position.of(base));
        if(t==null) return "unindexed";
        Location origin=t.pos.muzzle();
        List<String> seen=new ArrayList<>();
        for(Entity e:origin.getWorld().getNearbyEntities(origin,8,8,8)) if(e instanceof LivingEntity living) {
            Location end=aim(living); double distance=end.distance(origin);
            var hit=distance<.01?null:origin.getWorld().rayTraceBlocks(origin,end.toVector().subtract(origin.toVector()).normalize(),distance,org.bukkit.FluidCollisionMode.NEVER,true);
            seen.add(e.getType()+" "+e.getUniqueId()+" enemy="+plugin.mobs().isScenarioMob(e)+" d="+distance+" valid="+e.isValid()+" ticking="+e.isTicking()+" hp="+living.getHealth()+" loaded="+loadedRay(origin,end)+" wall="+(hit==null?"none":hit.getHitBlock()));
        }
        List<String> bolts=new ArrayList<>();
        for(var e:shots.entrySet()) if(e.getValue().turret()==t) {
            Entity arrow=Bukkit.getEntity(e.getKey());
            bolts.add(arrow==null?"missing":arrow.getLocation()+" v="+arrow.getVelocity()+" age="+arrow.getTicksLived()+" ticking="+arrow.isTicking());
        }
        return "fired="+t.fired+" hits="+t.hits+" outcome="+t.outcome+" last="+t.lastTravel+" near="+seen+" bolts="+bolts;
    }
    private static Location aim(LivingEntity entity) { return entity.getLocation().add(0, Math.min(1, entity.getHeight() * .55), 0); }
    private void shoot(Turret turret, LivingEntity target, long now) {
        Location origin = turret.pos.muzzle();
        var direction = aim(target).toVector().subtract(origin.toVector()).normalize();
        Location facing = origin.clone().setDirection(direction);
        turret.model.setRotation(facing.getYaw(), facing.getPitch());
        Arrow arrow = origin.getWorld().spawnArrow(origin.clone().add(direction.clone().multiply(.35)), direction, 1.6f, 0);
        arrow.setGravity(false);
        arrow.setCritical(false);
        arrow.setDamage(2);
        arrow.setKnockbackStrength(0);
        arrow.setPersistent(false);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        Player owner = Bukkit.getPlayer(turret.placed.owner());
        if (owner != null && plugin.sessions().isSurvivor(owner) && owner.getWorld().equals(origin.getWorld())) arrow.setShooter(owner);
        // setShooter may reset pickup behaviour: explicitly keep automatic bolts unrecoverable.
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.getPersistentDataContainer().set(bolt, PersistentDataType.BOOLEAN, true);
        shots.put(arrow.getUniqueId(), new Shot(origin, now + 1500, turret));
        turret.fired++;
        origin.getWorld().playSound(origin, Sound.ITEM_CROSSBOW_SHOOT, .7f, 1.15f);
        plugin.noise().emit(origin, 8, owner);
    }
    private static ItemDisplay model(Location at) {
        ItemStack item = ItemStack.of(Material.CROSSBOW);
        CrossbowMeta meta = (CrossbowMeta) item.getItemMeta(); meta.addChargedProjectile(ItemStack.of(Material.ARROW)); item.setItemMeta(meta);
        return at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(item);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateX((float) Math.PI / 2), new Vector3f(1.15f), new Quaternionf()));
            display.setPersistent(false); display.setInvulnerable(true); display.setGravity(false);
            display.setTeleportDuration(5); display.setInterpolationDuration(5); display.setViewRange(1f);
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow) || !arrow.getPersistentDataContainer().has(bolt)) return;
        Shot shot = shots.get(arrow.getUniqueId());
        if (shot != null) { shot.turret().hits++; shot.turret().outcome=event.getEntity().getType()+" cancelled="+event.isCancelled(); }
        if (shot == null || !(event.getEntity() instanceof LivingEntity target) || !plugin.mobs().isScenarioMob(target)
                || !target.getWorld().equals(shot.origin().getWorld()) || aim(target).distanceSquared(shot.origin()) > RANGE * RANGE
                || plugin.scenario().inRefuge(target.getLocation()) || !plugin.scenario().isEnabled()) {
            event.setCancelled(true); arrow.remove(); return;
        }
        if (!event.isCancelled()) event.setDamage(DAMAGE);
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || !isTurret(event.getClickedBlock())) return;
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        if (!event.getPlayer().getInventory().getItemInMainHand().isEmpty()) return;
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        var placed = plugin.placed().get(event.getClickedBlock());
        if (!canManage(event.getPlayer(), placed.owner())) { Text.info(event.getPlayer(), "Mini-arbalète automatique · portée 8 blocs · propriété d'une autre équipe."); return; }
        int paused = placed.data() == 0 ? 1 : 0;
        plugin.placed().put(event.getClickedBlock(), new PlacedBlocks.Placed(placed.kind(), placed.owner(), paused));
        Text.good(event.getPlayer(), paused == 0 ? "Mini-arbalète activée : surveillance des infectés à 8 blocs." : "Mini-arbalète en pause. Clic droit main vide pour reprendre.");
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (!isTurret(event.getBlock())) return;
        ItemStack item = plugin.items().create(ItemRegistry.AUTO_TURRET,1);
        plugin.placed().remove(event.getBlock());
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(.5,.5,.5),item);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) { if (isTurret(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder(false) instanceof BlockInventoryHolder holder && isTurret(holder.getBlock())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        for (var inventory : List.of(event.getSource(), event.getDestination()))
            if (inventory.getHolder(false) instanceof BlockInventoryHolder holder && isTurret(holder.getBlock())) { event.setCancelled(true); return; }
    }
    public void close() {
        for (var turret : List.copyOf(turrets.values())) if (turret.model != null) turret.model.remove();
        for (UUID id : List.copyOf(shots.keySet())) { Entity arrow = Bukkit.getEntity(id); if (arrow != null) arrow.remove(); }
        turrets.clear(); shots.clear();
    }
    public int count() { return turrets.size(); }
}
