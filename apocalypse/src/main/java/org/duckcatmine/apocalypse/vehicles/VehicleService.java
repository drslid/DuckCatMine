package org.duckcatmine.apocalypse.vehicles;

import io.papermc.paper.entity.TeleportFlag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.items.ItemRegistry;
import org.duckcatmine.apocalypse.scenario.GroundZeroGenerator;
import org.duckcatmine.apocalypse.scenario.city.CityLayout;
import org.duckcatmine.apocalypse.scenario.city.Streets;

/**
 * Abandoned cars that survivors can enter and drive, without client mod: the body is made of block displays, a
 * hitbox lets players climb in, seats are invisible displays they ride. The server reads the driver's movement keys,
 * moves the car one tick at a time against the real blocks (steps up kerbs, stops at walls, falls), runs over
 * infected, burns fuel and makes noise. A parked empty car fills its footprint with barrier blocks so it stays solid.
 * Chunks generated before cars were entities get their block cars swapped (and street trees added) once, when loaded.
 */
public final class VehicleService implements Listener {
    public static final int REVISION = 2;
    private static final double SEAT_HEIGHT = 0.40;
    private static final double[][] SEATS = {{0.42, -0.05}, {-0.42, -0.05}, {0.42, -0.90}, {-0.42, -0.90}};
    private static final Set<Material> SIDEWALK = Set.of(Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS,
            Material.MOSSY_STONE_BRICKS, Material.SMOOTH_STONE);

    private final DuckCatApocalypse plugin;
    private final Map<UUID, Vehicle> vehicles = new HashMap<>();
    private final Set<UUID> active = new LinkedHashSet<>();
    private final Map<UUID, UUID> seatToCar = new HashMap<>();
    private final Map<UUID, UUID> riderToCar = new HashMap<>();
    private final LinkedHashSet<Long> pending = new LinkedHashSet<>();
    private final Set<Long> fresh = new HashSet<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private BukkitTask task;
    private long ticks;
    private int converted;

    public VehicleService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public void start() {
        World world = plugin.scenario().world();
        if (world != null) {
            register(world.getEntities());
            for (Chunk chunk : world.getLoadedChunks()) consider(chunk, false);
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Server stop: everyone gets out beside the car, cars are parked (solid) and saved in their chunks. */
    public void stop() {
        if (task != null) task.cancel();
        for (Vehicle vehicle : List.copyOf(vehicles.values())) {
            for (int seat = 0; seat < Vehicle.SEATS; seat++) {
                Player rider = vehicle.riders[seat] == null ? null : Bukkit.getPlayer(vehicle.riders[seat]);
                Entity chair = vehicle.seats[seat] == null ? null : Bukkit.getEntity(vehicle.seats[seat]);
                clearSeat(vehicle, seat);
                if (rider != null) {
                    rider.leaveVehicle();
                    Location out = exitSpot(vehicle, seat, rider);
                    if (out != null) rider.teleport(out);
                }
                if (chair != null) chair.remove();
            }
            vehicle.speed = 0;
            if (vehicle.engineOn) lights(vehicle, false);
            vehicle.engineOn = false;
            park(vehicle);
        }
        active.clear();
    }

    public int loaded() {
        return vehicles.size();
    }

    public int driven() {
        int count = 0;
        for (UUID id : active) {
            Vehicle vehicle = vehicles.get(id);
            if (vehicle != null && vehicle.riders[0] != null) count++;
        }
        return count;
    }

    public int converted() {
        return converted;
    }

    public boolean isRiding(Player player) {
        return riderToCar.containsKey(player.getUniqueId());
    }

    // ------------------------------------------------------------------ registry

    private boolean scenarioWorld(World world) {
        return plugin.scenario().isScenarioWorld(world);
    }

    private String tag(Entity entity, NamespacedKeyKind kind) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        return data.get(kind == NamespacedKeyKind.ID ? plugin.keys().vehicle : plugin.keys().vehiclePart, PersistentDataType.STRING);
    }

    private enum NamespacedKeyKind { ID, PART }

    private void register(Collection<? extends Entity> entities) {
        Map<UUID, List<Entity>> groups = new HashMap<>();
        for (Entity entity : entities) {
            String id = tag(entity, NamespacedKeyKind.ID);
            if (id == null) continue;
            try {
                groups.computeIfAbsent(UUID.fromString(id), key -> new ArrayList<>()).add(entity);
            } catch (IllegalArgumentException ignored) {
                // Foreign or broken tag: not one of our cars.
            }
        }
        groups.forEach(this::build);
    }

    private void build(UUID id, List<Entity> entities) {
        Vehicle known = vehicles.get(id);
        if (known != null) {
            for (Entity entity : entities) {
                if (entity instanceof Interaction) known.interaction = entity.getUniqueId();
                else if (!known.parts.contains(entity.getUniqueId())) known.parts.add(entity.getUniqueId());
            }
            return;
        }
        Entity body = null;
        for (Entity entity : entities) if (VehicleModel.BODY.equals(tag(entity, NamespacedKeyKind.PART))) body = entity;
        if (body == null) return;
        Location at = body.getLocation();
        Vehicle vehicle = Vehicle.decode(id, body.getWorld(), body.getPersistentDataContainer().get(plugin.keys().vehicleState,
                PersistentDataType.STRING), at.getX(), at.getY(), at.getZ(), at.getYaw());
        vehicle.body = body.getUniqueId();
        for (Entity entity : entities) {
            if (entity instanceof Interaction) vehicle.interaction = entity.getUniqueId();
            else vehicle.parts.add(entity.getUniqueId());
        }
        vehicles.put(id, vehicle);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (scenarioWorld(event.getWorld())) register(event.getEntities());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (!VehicleModel.BODY.equals(tag(entity, NamespacedKeyKind.PART))) continue;
            String id = tag(entity, NamespacedKeyKind.ID);
            if (id == null) continue;
            UUID carId;
            try {
                carId = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Vehicle vehicle = vehicles.get(carId);
            if (vehicle != null && !vehicle.occupied()) {
                vehicles.remove(carId);
                active.remove(carId);
            }
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        vehicles.values().removeIf(vehicle -> vehicle.world.equals(event.getWorld()));
        active.removeIf(id -> !vehicles.containsKey(id));
        pending.clear();
        fresh.clear();
    }

    // ------------------------------------------------------------------ old chunks: block cars to entities, street trees

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private void consider(Chunk chunk, boolean isNew) {
        if (!(chunk.getWorld().getGenerator() instanceof GroundZeroGenerator)) return;
        Integer revision = chunk.getPersistentDataContainer().get(plugin.keys().chunkRevision, PersistentDataType.INTEGER);
        if (revision != null && revision >= REVISION) return;
        long key = key(chunk.getX(), chunk.getZ());
        if (isNew) fresh.add(key);
        pending.add(key);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (scenarioWorld(event.getWorld())) consider(event.getChunk(), event.isNewChunk());
    }

    private void upgradePending() {
        World world = plugin.scenario().world();
        if (world == null) {
            pending.clear();
            return;
        }
        int budget = 2;
        Iterator<Long> keys = pending.iterator();
        while (budget > 0 && keys.hasNext()) {
            long key = keys.next();
            keys.remove();
            boolean isNew = fresh.remove(key);
            int cx = (int) (key >> 32), cz = (int) key;
            if (!world.isChunkLoaded(cx, cz)) continue;
            if (upgrade(world, world.getChunkAt(cx, cz), isNew)) budget--;
        }
    }

    /** Loads and upgrades every chunk of a square at once (admin command); returns chunks upgraded and cars present after. */
    public int[] upgradeArea(World world, int centerX, int centerZ, int radius) {
        int chunks = 0;
        for (int cx = (centerX - radius) >> 4; cx <= (centerX + radius) >> 4; cx++) {
            for (int cz = (centerZ - radius) >> 4; cz <= (centerZ + radius) >> 4; cz++) {
                if (upgrade(world, world.getChunkAt(cx, cz), false)) chunks++;
            }
        }
        return new int[]{chunks, vehicles.size()};
    }

    private boolean upgrade(World world, Chunk chunk, boolean isNew) {
        if (!(world.getGenerator() instanceof GroundZeroGenerator)) return false;
        Integer revision = chunk.getPersistentDataContainer().get(plugin.keys().chunkRevision, PersistentDataType.INTEGER);
        if (revision != null && revision >= REVISION) return false;
        int cx = chunk.getX(), cz = chunk.getZ();
        try {
            CityLayout layout = CityLayout.of(world.getSeed());
            for (CityLayout.VehicleSpot spot : layout.vehicles(cx, cz)) convert(world, spot);
            if (!isNew) plantTrees(world, layout, cx, cz);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Mise à niveau du tronçon " + cx + "," + cz + " incomplète", e);
        }
        chunk.getPersistentDataContainer().set(plugin.keys().chunkRevision, PersistentDataType.INTEGER, REVISION);
        return true;
    }

    private static int[][] footprint(CityLayout.VehicleSpot spot) {
        int[][] cells = new int[8][];
        int k = 0;
        for (int a = 0; a < 4; a++) {
            for (int b = 0; b < 2; b++) {
                cells[k++] = spot.alongX() ? new int[]{spot.minX() + a, spot.minZ() + b} : new int[]{spot.minX() + b, spot.minZ() + a};
            }
        }
        return cells;
    }

    /** Swaps an old block car for an entity car, or adds the car of a freshly generated chunk. Never removes player blocks. */
    private void convert(World world, CityLayout.VehicleSpot spot) {
        int y = spot.y();
        int[][] cells = footprint(spot);
        int legacy = 0;
        for (int[] cell : cells) if (Streets.legacyCarBlock(world.getBlockAt(cell[0], y, cell[1]).getType())) legacy++;
        if (legacy >= 5) {
            for (int[] cell : cells) {
                for (int dy = 0; dy <= 2; dy++) {
                    Block block = world.getBlockAt(cell[0], y + dy, cell[1]);
                    if (Streets.legacyCarBlock(block.getType())) block.setType(Material.AIR, false);
                }
            }
        }
        int blocked = 0;
        for (int[] cell : cells) {
            for (int dy = 0; dy <= 1; dy++) if (!world.getBlockAt(cell[0], y + dy, cell[1]).isPassable()) blocked++;
        }
        if (blocked > 2) return;
        Location center = new Location(world, spot.centerX(), y, spot.centerZ());
        for (Entity near : world.getNearbyEntities(center, 1.5, 2, 1.5)) if (tag(near, NamespacedKeyKind.ID) != null) return;
        long seed = spot.seed();
        boolean burnt = Streets.carBurnt(seed);
        boolean flip = ((seed >>> 5) & 1) == 0;
        float yaw = spot.alongX() ? (flip ? 90f : -90f) : (flip ? 0f : 180f);
        double fuel = burnt || Math.floorMod(seed >>> 40, 100L) < 35 ? 0 : 2 + Math.floorMod(seed >>> 48, 13L);
        double health = burnt ? 0 : 45 + Math.floorMod(seed >>> 52, 56L);
        Vehicle vehicle = spawn(world, spot.centerX(), y, spot.centerZ(), yaw, burnt ? VehicleModel.WRECK : VehicleModel.SEDAN,
                Streets.carPaint(seed), fuel, health);
        park(vehicle);
        converted++;
    }

    /** Street trees for chunks generated before they existed: whole trees inside the chunk, on intact sidewalk, in free air. */
    private void plantTrees(World world, CityLayout layout, int cx, int cz) {
        int x0 = cx << 4, z0 = cz << 4;
        for (List<Streets.TreeBlock> tree : Streets.streetTrees(layout, x0, z0)) {
            boolean fits = true;
            for (Streets.TreeBlock block : tree) {
                if (block.x() < x0 || block.x() > x0 + 15 || block.z() < z0 || block.z() > z0 + 15) {
                    fits = false;
                    break;
                }
            }
            if (!fits) continue;
            Streets.TreeBlock base = tree.getFirst();
            if (!SIDEWALK.contains(world.getBlockAt(base.x(), base.y(), base.z()).getType())) continue;
            boolean free = true;
            for (int k = 1; k < tree.size() && free; k++) {
                Streets.TreeBlock block = tree.get(k);
                free = world.getBlockAt(block.x(), block.y(), block.z()).getType().isAir();
            }
            if (!free) continue;
            for (Streets.TreeBlock block : tree) world.getBlockAt(block.x(), block.y(), block.z()).setBlockData(block.data(), false);
        }
    }

    // ------------------------------------------------------------------ spawning, parking, saving

    private void mark(Entity entity, UUID id, String role) {
        entity.getPersistentDataContainer().set(plugin.keys().vehicle, PersistentDataType.STRING, id.toString());
        entity.getPersistentDataContainer().set(plugin.keys().vehiclePart, PersistentDataType.STRING, role);
    }

    Vehicle spawn(World world, double x, double y, double z, float yaw, VehicleModel model, Material paint, double fuel, double health) {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = new Vehicle(id, world, model, paint, x, y, z, yaw, fuel, health);
        spawnParts(vehicle);
        Location at = new Location(world, x, y, z, yaw, 0);
        Interaction door = world.spawn(at, Interaction.class, hit -> {
            hit.setInteractionWidth(2.3f);
            hit.setInteractionHeight(1.75f);
            hit.setResponsive(true);
            mark(hit, id, "door");
        });
        vehicle.interaction = door.getUniqueId();
        vehicles.put(id, vehicle);
        save(vehicle);
        return vehicle;
    }

    private void spawnParts(Vehicle vehicle) {
        Location at = new Location(vehicle.world, vehicle.x, vehicle.y, vehicle.z, vehicle.yaw, 0);
        for (VehicleModel.Part part : vehicle.model.parts(vehicle.paint)) {
            BlockDisplay display = vehicle.world.spawn(at, BlockDisplay.class, d -> {
                d.setBlock(part.data());
                d.setTransformation(part.transformation());
                d.setTeleportDuration(2);
                d.setViewRange(0.9f);
                if (part.glow()) d.setBrightness(new Display.Brightness(15, 15));
                if (VehicleModel.BODY.equals(part.role())) {
                    d.setShadowRadius(1.5f);
                    d.setShadowStrength(0.55f);
                }
                mark(d, vehicle.id, part.role());
            });
            vehicle.parts.add(display.getUniqueId());
            if (VehicleModel.BODY.equals(part.role())) vehicle.body = display.getUniqueId();
        }
    }

    private void save(Vehicle vehicle) {
        Entity body = vehicle.body == null ? null : Bukkit.getEntity(vehicle.body);
        if (body != null) body.getPersistentDataContainer().set(plugin.keys().vehicleState, PersistentDataType.STRING, vehicle.encode());
    }

    /** An empty car standing still becomes solid: barrier blocks in its free footprint, removed when someone gets in. */
    private void park(Vehicle vehicle) {
        unpark(vehicle);
        double rad = Math.toRadians(vehicle.yaw);
        double fx = -Math.sin(rad), fz = Math.cos(rad), lx = Math.cos(rad), lz = Math.sin(rad);
        Set<Long> cells = new HashSet<>();
        for (double a = -0.75; a <= 0.76; a += 0.5) {
            for (double b = -1.85; b <= 1.86; b += 0.5) {
                int bx = (int) Math.floor(vehicle.x + fx * b + lx * a), bz = (int) Math.floor(vehicle.z + fz * b + lz * a);
                cells.add(key(bx, bz));
            }
        }
        int low = (int) Math.floor(vehicle.y + 0.01), high = (int) Math.floor(vehicle.y + 1.6);
        for (long cell : cells) {
            int bx = (int) (cell >> 32), bz = (int) cell;
            if (!vehicle.world.isChunkLoaded(bx >> 4, bz >> 4)) continue;
            for (int by = low; by <= high; by++) {
                Block block = vehicle.world.getBlockAt(bx, by, bz);
                if (!block.getType().isAir()) continue;
                block.setType(Material.BARRIER, false);
                vehicle.barriers.add(new int[]{bx, by, bz});
            }
        }
        active.remove(vehicle.id);
        save(vehicle);
    }

    private void unpark(Vehicle vehicle) {
        for (int[] cell : vehicle.barriers) {
            if (!vehicle.world.isChunkLoaded(cell[0] >> 4, cell[2] >> 4)) continue;
            Block block = vehicle.world.getBlockAt(cell[0], cell[1], cell[2]);
            if (block.getType() == Material.BARRIER) block.setType(Material.AIR, false);
        }
        vehicle.barriers.clear();
    }

    /** Moves every display, the hitbox and the seats (with their riders) to the car's position. */
    private void place(Vehicle vehicle) {
        Location at = new Location(vehicle.world, vehicle.x, vehicle.y, vehicle.z, vehicle.yaw, 0);
        for (UUID id : vehicle.parts) {
            Entity part = Bukkit.getEntity(id);
            if (part != null) part.teleport(at);
        }
        Entity door = vehicle.interaction == null ? null : Bukkit.getEntity(vehicle.interaction);
        if (door != null) door.teleport(at);
        for (int seat = 0; seat < Vehicle.SEATS; seat++) {
            Entity chair = vehicle.seats[seat] == null ? null : Bukkit.getEntity(vehicle.seats[seat]);
            if (chair != null) chair.teleport(seatLocation(vehicle, seat), TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }
    }

    private Location local(Vehicle vehicle, double lx, double ly, double lz) {
        double rad = Math.toRadians(vehicle.yaw);
        return new Location(vehicle.world, vehicle.x - Math.sin(rad) * lz + Math.cos(rad) * lx, vehicle.y + ly,
                vehicle.z + Math.cos(rad) * lz + Math.sin(rad) * lx, vehicle.yaw, 0);
    }

    private Location seatLocation(Vehicle vehicle, int seat) {
        return local(vehicle, SEATS[seat][0], SEAT_HEIGHT, SEATS[seat][1]);
    }

    // ------------------------------------------------------------------ getting in and out

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction door)) return;
        String id = tag(door, NamespacedKeyKind.ID);
        if (id == null) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUniqueId(), now);
        if (last != null && now - last < 350) return;
        Vehicle vehicle = vehicles.get(UUID.fromString(id));
        if (vehicle == null) {
            register(door.getWorld().getNearbyEntities(door.getLocation(), 1, 2, 1));
            vehicle = vehicles.get(UUID.fromString(id));
        }
        if (vehicle == null || !plugin.sessions().isSurvivor(player) || plugin.intro().playing(player) || player.isInsideVehicle()) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (plugin.items().is(hand, ItemRegistry.JERRYCAN)) {
            if (player.isSneaking()) siphon(player, vehicle, hand);
            else refuel(player, vehicle, hand);
            return;
        }
        if (plugin.items().is(hand, "scrap")) {
            repair(player, vehicle, hand);
            return;
        }
        if (vehicle.model == VehicleModel.WRECK) {
            salvage(player, vehicle);
            return;
        }
        enter(player, vehicle);
    }

    private void enter(Player player, Vehicle vehicle) {
        if (Math.abs(vehicle.speed) > 0.2) {
            Text.warn(player, "Elle roule trop vite pour monter.");
            return;
        }
        int seat = -1;
        for (int i = 0; i < Vehicle.SEATS && seat < 0; i++) if (vehicle.riders[i] == null) seat = i;
        if (seat < 0) {
            Text.warn(player, "Plus de place dans cette voiture.");
            return;
        }
        if (!vehicle.occupied()) unpark(vehicle);
        ItemDisplay chair = vehicle.world.spawn(seatLocation(vehicle, seat), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setTeleportDuration(2);
        });
        if (!chair.addPassenger(player)) {
            chair.remove();
            if (!vehicle.occupied()) park(vehicle);
            return;
        }
        vehicle.seats[seat] = chair.getUniqueId();
        vehicle.riders[seat] = player.getUniqueId();
        seatToCar.put(chair.getUniqueId(), vehicle.id);
        riderToCar.put(player.getUniqueId(), vehicle.id);
        active.add(vehicle.id);
        vehicle.world.playSound(chair.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.9f, 1.4f);
        if (seat == 0) {
            Text.info(player, "Au volant. Avancer, reculer et tourner : touches de déplacement. Saut : frein à main. Sprint : klaxon."
                    + " Maj : descendre. Le moteur s'entend de loin.");
            if (!plugin.settings().play().vehicles().enabled()) Text.warn(player, "Les voitures sont immobilisées sur ce serveur.");
            else if (vehicle.health <= 0) Text.warn(player, "Moteur hors d'usage : clic droit avec de la ferraille pour le réparer.");
            else if (vehicle.fuel <= 0.05) Text.warn(player, "Réservoir vide : siphonne une autre voiture avec un jerrican.");
            else Text.info(player, String.format(java.util.Locale.ROOT, "Essence : %.0f L — état : %.0f %%.", vehicle.fuel, vehicle.health));
        } else {
            Text.info(player, "Passager. Maj pour descendre.");
        }
    }

    private void clearSeat(Vehicle vehicle, int seat) {
        if (vehicle.seats[seat] != null) seatToCar.remove(vehicle.seats[seat]);
        if (vehicle.riders[seat] != null) riderToCar.remove(vehicle.riders[seat]);
        vehicle.seats[seat] = null;
        vehicle.riders[seat] = null;
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        UUID carId = seatToCar.get(event.getDismounted().getUniqueId());
        if (carId == null || !(event.getEntity() instanceof Player player)) return;
        Vehicle vehicle = vehicles.get(carId);
        if (vehicle == null) return;
        int seat = vehicle.seatOf(player.getUniqueId());
        if (seat < 0) return;
        Entity chair = event.getDismounted();
        double speed = Math.abs(vehicle.speed);
        clearSeat(vehicle, seat);
        if (seat == 0 && vehicle.engineOn) stopEngine(vehicle);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            chair.remove();
            if (!player.isOnline() || player.isDead() || player.isInsideVehicle() || !player.getWorld().equals(vehicle.world)
                    || plugin.sessions().isTransitioning(player) || player.getLocation().distanceSquared(local(vehicle, 0, 0, 0)) > 36) return;
            Location out = exitSpot(vehicle, seat, player);
            if (out != null) plugin.sessions().teleport(player, out);
            if (speed > 0.3) player.damage((speed - 0.25) * 16, DamageSource.builder(DamageType.FALL).build());
            vehicle.world.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.9f, 1.4f);
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID carId = riderToCar.get(player.getUniqueId());
        if (carId == null) return;
        Vehicle vehicle = vehicles.get(carId);
        int seat = vehicle == null ? -1 : vehicle.seatOf(player.getUniqueId());
        Entity chair = seat < 0 ? null : Bukkit.getEntity(vehicle.seats[seat]);
        if (vehicle != null && seat >= 0) clearSeat(vehicle, seat);
        else riderToCar.remove(player.getUniqueId());
        player.leaveVehicle();
        if (chair != null) chair.remove();
        if (vehicle != null && seat >= 0) {
            if (seat == 0 && vehicle.engineOn) stopEngine(vehicle);
            Location out = exitSpot(vehicle, seat, player);
            if (out != null) player.teleport(out);
        }
    }

    /** Beside the door, else the other side, the front or the back; the ground must hold and the head be free. */
    private Location exitSpot(Vehicle vehicle, int seat, Player player) {
        double side = SEATS[seat][0] > 0 ? 1 : -1, lz = SEATS[seat][1];
        double[][] tries = {{side * 1.6, lz}, {-side * 1.6, lz}, {0, 3.0}, {0, -3.0}};
        for (double[] attempt : tries) {
            Location spot = local(vehicle, attempt[0], 0, attempt[1]);
            int bx = spot.getBlockX(), bz = spot.getBlockZ();
            for (int dy : new int[]{0, 1, -1}) {
                int by = (int) Math.floor(vehicle.y + 0.01) + dy;
                Block feet = vehicle.world.getBlockAt(bx, by, bz);
                if (!feet.isPassable() || feet.isLiquid() || !feet.getRelative(0, 1, 0).isPassable()
                        || !feet.getRelative(0, -1, 0).getType().isSolid()) continue;
                return new Location(vehicle.world, bx + 0.5, by, bz + 0.5, player.getLocation().getYaw(), player.getLocation().getPitch());
            }
        }
        return null;
    }

    /** Infected cannot reach someone inside a moving car. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onRiderHurt(EntityDamageByEntityEvent event) {
        UUID carId = riderToCar.get(event.getEntity().getUniqueId());
        if (carId == null || event.getDamager() instanceof Player) return;
        Vehicle vehicle = vehicles.get(carId);
        if (vehicle != null && Math.abs(vehicle.speed) > 0.15) event.setCancelled(true);
    }

    // ------------------------------------------------------------------ fuel, repairs, wrecks

    private void siphon(Player player, Vehicle vehicle, ItemStack can) {
        double capacity = plugin.settings().play().vehicles().jerrycanLitres();
        double room = capacity - plugin.items().fuel(can);
        if (room < 0.5) {
            Text.warn(player, "Ton jerrican est plein.");
            return;
        }
        if (vehicle.fuel < 0.5) {
            Text.warn(player, "Réservoir vide : rien à siphonner.");
            return;
        }
        double taken = Math.min(room, vehicle.fuel);
        vehicle.fuel -= taken;
        plugin.items().setFuel(can, plugin.items().fuel(can) + taken);
        player.getInventory().setItemInMainHand(can);
        vehicle.world.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL, 1f, 0.8f);
        Text.good(player, String.format(java.util.Locale.ROOT, "Siphonné : %.0f L. Il reste %.0f L dans cette voiture.", taken, vehicle.fuel));
        save(vehicle);
    }

    private void refuel(Player player, Vehicle vehicle, ItemStack can) {
        if (vehicle.model == VehicleModel.WRECK) {
            Text.warn(player, "Une épave calcinée ne roulera plus.");
            return;
        }
        double tank = plugin.settings().play().vehicles().tankLitres();
        double poured = Math.min(plugin.items().fuel(can), tank - vehicle.fuel);
        if (plugin.items().fuel(can) < 0.5) {
            Text.warn(player, "Jerrican vide : accroupis-toi et clic droit sur une autre voiture pour siphonner.");
            return;
        }
        if (poured < 0.5) {
            Text.info(player, "Le réservoir est plein.");
            return;
        }
        vehicle.fuel += poured;
        plugin.items().setFuel(can, plugin.items().fuel(can) - poured);
        player.getInventory().setItemInMainHand(can);
        vehicle.world.playSound(player.getLocation(), Sound.ITEM_BUCKET_EMPTY_LAVA, 1f, 1.2f);
        Text.good(player, String.format(java.util.Locale.ROOT, "Plein fait : +%.0f L (réservoir : %.0f L).", poured, vehicle.fuel));
        save(vehicle);
    }

    private void repair(Player player, Vehicle vehicle, ItemStack scrap) {
        if (vehicle.model == VehicleModel.WRECK) {
            salvage(player, vehicle);
            return;
        }
        if (vehicle.health >= 99.5) {
            Text.info(player, "Cette voiture est en bon état.");
            return;
        }
        scrap.setAmount(scrap.getAmount() - 1);
        player.getInventory().setItemInMainHand(scrap.getAmount() <= 0 ? null : scrap);
        vehicle.health = Math.min(100, vehicle.health + 15);
        vehicle.world.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.3f);
        plugin.noise().emit(player.getLocation(), 10, player);
        Text.good(player, String.format(java.util.Locale.ROOT, "Réparée : état %.0f %%.", vehicle.health));
        save(vehicle);
    }

    /** A burnt-out car gives a little scrap, once. */
    private void salvage(Player player, Vehicle vehicle) {
        if (vehicle.salvaged) {
            Text.info(player, "Épave déjà dépouillée.");
            return;
        }
        vehicle.salvaged = true;
        int amount = 1 + (int) Math.floorMod(vehicle.id.getLeastSignificantBits(), 3L);
        ItemStack scrap = plugin.items().create("scrap", amount);
        for (ItemStack left : player.getInventory().addItem(scrap).values()) vehicle.world.dropItemNaturally(player.getLocation(), left);
        if (Math.floorMod(vehicle.id.getMostSignificantBits(), 4L) == 0) {
            for (ItemStack left : player.getInventory().addItem(plugin.items().create("spring", 1)).values())
                vehicle.world.dropItemNaturally(player.getLocation(), left);
        }
        vehicle.world.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1f, 0.7f);
        plugin.noise().emit(player.getLocation(), 12, player);
        Text.good(player, "Tu récupères " + amount + " ferraille sur l'épave.");
        save(vehicle);
    }

    // ------------------------------------------------------------------ driving

    private void tick() {
        ticks++;
        upgradePending();
        if (active.isEmpty()) return;
        for (UUID id : List.copyOf(active)) {
            Vehicle vehicle = vehicles.get(id);
            if (vehicle == null) {
                active.remove(id);
                continue;
            }
            try {
                step(vehicle);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Voiture " + id + " immobilisée après une erreur", e);
                vehicle.speed = 0;
            }
        }
    }

    private Player rider(Vehicle vehicle, int seat) {
        return vehicle.riders[seat] == null ? null : Bukkit.getPlayer(vehicle.riders[seat]);
    }

    private record Controls(boolean forward, boolean backward, boolean left, boolean right, boolean handbrake, boolean horn) {
    }

    private void step(Vehicle vehicle) {
        Player driver = rider(vehicle, 0);
        Input input = driver == null ? null : driver.getCurrentInput();
        Controls controls = input == null ? new Controls(false, false, false, false, true, false)
                : new Controls(input.isForward(), input.isBackward(), input.isLeft(), input.isRight(), input.isJump(), input.isSprint());
        drive(vehicle, controls, driver, false);
    }

    private void drive(Vehicle vehicle, Controls controls, Player driver, boolean simulated) {
        vehicle.ticks++;
        ApocalypseConfig.Vehicles rules = plugin.settings().play().vehicles();
        boolean forward = controls.forward(), backward = controls.backward(), left = controls.left(), right = controls.right();
        boolean handbrake = controls.handbrake(), horn = controls.horn();
        boolean driven = driver != null || simulated;
        boolean canRun = (simulated || rules.enabled() && plugin.scenario().isEnabled()) && vehicle.model == VehicleModel.SEDAN
                && vehicle.fuel > 0 && vehicle.health > 0;
        if (driven && canRun && !vehicle.engineOn && (forward || backward)) startEngine(vehicle);
        if (vehicle.engineOn && (!driven || !canRun)) {
            if (driver != null && vehicle.fuel <= 0) Text.warn(driver, "Panne sèche : le réservoir est vide.");
            stopEngine(vehicle);
        }
        CityLayout layout = vehicle.world.getGenerator() instanceof GroundZeroGenerator ? CityLayout.of(vehicle.world.getSeed()) : null;
        boolean street = layout == null || layout.isStreet((int) Math.floor(vehicle.x), (int) Math.floor(vehicle.z));
        double max = street ? rules.maxSpeedRoad() : rules.maxSpeedOffroad();
        double speed = VehiclePhysics.speed(vehicle.speed, forward, backward, handbrake, vehicle.engineOn, max);
        float yaw = vehicle.yaw + VehiclePhysics.steer(speed, left, right);
        double rad = Math.toRadians(yaw), fx = -Math.sin(rad), fz = Math.cos(rad);
        double nx = vehicle.x + fx * speed, nz = vehicle.z + fz * speed;
        double ground = vehicle.y;
        boolean moving = speed != 0 || yaw != vehicle.yaw;
        if (moving) {
            double probe = probe(vehicle, nx, nz, yaw, speed);
            if (Double.isNaN(probe)) {
                crash(vehicle, speed, driver);
                speed = vehicle.speed;
                nx = vehicle.x;
                nz = vehicle.z;
                yaw = vehicle.yaw;
            } else {
                ground = probe;
            }
        }
        double y = vehicle.y;
        if (ground > y + 0.01) {
            if (ground - y > 0.4) speed *= 0.75;
            y = ground;
            vehicle.fall = 0;
        } else if (ground < y - 0.01 || vehicle.fall > 0) {
            vehicle.fall = Math.min(1.2, vehicle.fall + 0.08);
            y = Math.max(ground, y - vehicle.fall);
            if (y <= ground + 0.001) land(vehicle);
        }
        double moved = Math.hypot(nx - vehicle.x, nz - vehicle.z);
        boolean changed = moved > 0 || yaw != vehicle.yaw || y != vehicle.y;
        vehicle.x = nx;
        vehicle.z = nz;
        vehicle.y = y;
        vehicle.yaw = yaw;
        vehicle.speed = speed;
        if (changed) place(vehicle);
        if (vehicle.engineOn) vehicle.fuel = Math.max(0, vehicle.fuel - moved * rules.fuelPerBlock());
        if (Math.abs(vehicle.speed) > 0.02) hit(vehicle, driver, fx, fz);
        effects(vehicle, driver, horn, rules);
        if (driver != null && vehicle.ticks % 5 == 0) gauge(driver, vehicle);
        if (vehicle.ticks % 100 == 0) {
            save(vehicle);
            vehicle.hits.values().removeIf(at -> System.currentTimeMillis() - at > 2000);
        }
        if (!vehicle.occupied() && vehicle.speed == 0 && vehicle.fall == 0 && !simulated) park(vehicle);
    }

    /**
     * Self-test on the real server, high in the sky: a car on a short test road drives into a wall, then everything is
     * removed. Checks the body, the barrier blocks of a parked car, acceleration, the collision and the damage.
     */
    public String selfTest(Location base) {
        World world = base.getWorld();
        List<Block> built = new ArrayList<>();
        Vehicle vehicle = null;
        try {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -3; dz <= 16; dz++) {
                    Block floor = base.clone().add(dx, -1, dz).getBlock();
                    if (!floor.getType().isAir()) throw new IllegalStateException("emplacement de test occupé");
                    floor.setType(Material.SMOOTH_STONE, false);
                    built.add(floor);
                }
                for (int dy = 0; dy <= 1; dy++) {
                    Block wall = base.clone().add(dx, dy, 16).getBlock();
                    wall.setType(Material.STONE_BRICKS, false);
                    built.add(wall);
                }
            }
            vehicle = spawn(world, base.getBlockX() + 0.5, base.getBlockY(), base.getBlockZ() + 0.5, 0f, VehicleModel.SEDAN,
                    Material.RED_CONCRETE, 10, 100);
            int parts = vehicle.parts.size();
            if (parts != VehicleModel.SEDAN.parts(Material.RED_CONCRETE).size() || vehicle.interaction == null)
                throw new IllegalStateException(parts + " pièces, portière " + vehicle.interaction);
            park(vehicle);
            int barriers = vehicle.barriers.size();
            if (barriers < 12) throw new IllegalStateException("voiture garée peu solide : " + barriers + " blocs barrière");
            unpark(vehicle);
            double startZ = vehicle.z, top = 0;
            int ticks = 0;
            while (ticks < 200 && (ticks < 5 || vehicle.speed > 0)) {
                drive(vehicle, new Controls(true, false, false, false, false, false), null, true);
                top = Math.max(top, vehicle.speed);
                ticks++;
            }
            double travelled = vehicle.z - startZ;
            if (travelled < 8) throw new IllegalStateException("n'a roulé que " + Math.round(travelled) + " blocs");
            if (vehicle.z > base.getZ() + 16 - 2.0) throw new IllegalStateException("a traversé le mur (z " + vehicle.z + ")");
            if (vehicle.health >= 100) throw new IllegalStateException("choc sans dégât");
            if (vehicle.fuel >= 10) throw new IllegalStateException("aucune essence consommée");
            return String.format(java.util.Locale.ROOT, "%d pièces, %d blocs barrière garée, %.0f blocs parcourus jusqu'à %d km/h, arrêtée par le mur (état %.0f %%, %.2f L consommés)",
                    parts, barriers, travelled, VehiclePhysics.kmh(top), vehicle.health, 10 - vehicle.fuel);
        } finally {
            if (vehicle != null) remove(vehicle);
            for (Block block : built) block.setType(Material.AIR, false);
        }
    }

    private void remove(Vehicle vehicle) {
        unpark(vehicle);
        for (UUID id : vehicle.parts) {
            Entity part = Bukkit.getEntity(id);
            if (part != null) part.remove();
        }
        Entity door = vehicle.interaction == null ? null : Bukkit.getEntity(vehicle.interaction);
        if (door != null) door.remove();
        vehicles.remove(vehicle.id);
        active.remove(vehicle.id);
    }

    /** Ground under the car at the next position, or NaN when a wall, water, the refuge or the edge of the world is in the way. */
    private double probe(Vehicle vehicle, double nx, double nz, float yaw, double speed) {
        double rad = Math.toRadians(yaw), fx = -Math.sin(rad), fz = Math.cos(rad), lx = Math.cos(rad), lz = Math.sin(rad);
        double best = Double.NEGATIVE_INFINITY;
        for (double a : new double[]{-0.75, 0.75}) {
            for (double b : new double[]{-1.35, 1.35}) {
                double support = support(vehicle, nx + fx * b + lx * a, nz + fz * b + lz * a);
                if (Double.isNaN(support)) return Double.NaN;
                best = Math.max(best, support);
            }
        }
        double edge = speed >= 0 ? 2.1 : -2.1;
        for (double a : new double[]{-0.9, 0, 0.9}) {
            if (Double.isNaN(support(vehicle, nx + fx * edge + lx * a, nz + fz * edge + lz * a))) return Double.NaN;
        }
        for (double a : new double[]{-0.95, 0.95}) {
            for (double b : new double[]{-1.2, 1.2}) {
                if (Double.isNaN(support(vehicle, nx + fx * b + lx * a, nz + fz * b + lz * a))) return Double.NaN;
            }
        }
        return best;
    }

    private double support(Vehicle vehicle, double px, double pz) {
        World world = vehicle.world;
        int bx = (int) Math.floor(px), bz = (int) Math.floor(pz);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4) || !world.getWorldBorder().isInside(new Location(world, px, vehicle.y, pz)))
            return Double.NaN;
        if (plugin.scenario().isScenarioWorld(world) && plugin.scenario().refugeArea().near(bx, bz, 1)) return Double.NaN;
        double limit = vehicle.y + 1.05;
        int top = (int) Math.floor(vehicle.y + 1.65), bottom = (int) Math.floor(vehicle.y) - 3;
        for (int by = top; by >= bottom; by--) {
            Block block = world.getBlockAt(bx, by, bz);
            if (block.isLiquid()) return Double.NaN;
            if (block.isPassable()) continue;
            double surface = block.getBoundingBox().getMaxY();
            if (surface > limit + 1e-6) return Double.NaN;
            return surface;
        }
        return vehicle.y - 3.5;
    }

    private void crash(Vehicle vehicle, double speed, Player driver) {
        double impact = Math.abs(speed);
        vehicle.speed = impact > 0.08 ? -speed * 0.2 : 0;
        if (impact < 0.18) return;
        Location at = local(vehicle, 0, 0.8, Math.signum(speed) * 2.1);
        vehicle.world.playSound(at, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.4f, 0.6f);
        if (impact > 0.4) vehicle.world.playSound(at, Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
        vehicle.world.spawnParticle(Particle.CRIT, at, 12, 0.4, 0.3, 0.4, 0.1);
        vehicle.health = Math.max(0, vehicle.health - (impact - 0.15) * 70);
        plugin.noise().emit(at, 20, driver);
        if (impact > 0.45) {
            for (int seat = 0; seat < Vehicle.SEATS; seat++) {
                Player rider = rider(vehicle, seat);
                if (rider != null) rider.damage((impact - 0.4) * 18, DamageSource.builder(DamageType.FLY_INTO_WALL).build());
            }
        }
        if (vehicle.health <= 0) engineDestroyed(vehicle);
    }

    private void land(Vehicle vehicle) {
        double fall = vehicle.fall;
        vehicle.fall = 0;
        if (fall < 0.7) return;
        vehicle.world.playSound(local(vehicle, 0, 0, 0), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.2f, 0.5f);
        vehicle.health = Math.max(0, vehicle.health - fall * 25);
        for (int seat = 0; seat < Vehicle.SEATS; seat++) {
            Player rider = rider(vehicle, seat);
            if (rider != null) rider.damage(fall * 8, DamageSource.builder(DamageType.FALL).build());
        }
        if (vehicle.health <= 0) engineDestroyed(vehicle);
    }

    /** Runs into what stands in front: infected and animals are hurt and thrown aside; protected survivors stop the car. */
    private void hit(Vehicle vehicle, Player driver, double fx, double fz) {
        double sign = Math.signum(vehicle.speed);
        Location front = new Location(vehicle.world, vehicle.x + fx * 1.5 * sign, vehicle.y + 0.8, vehicle.z + fz * 1.5 * sign);
        long now = System.currentTimeMillis();
        for (Entity entity : vehicle.world.getNearbyEntities(front, 1.4, 0.9, 1.4)) {
            if (!(entity instanceof LivingEntity living) || living.isDead() || entity instanceof ArmorStand
                    || riderToCar.containsKey(entity.getUniqueId())) continue;
            if (living instanceof Player target && (target.getGameMode() == GameMode.SPECTATOR || target.getGameMode() == GameMode.CREATIVE)) continue;
            double impact = Math.abs(vehicle.speed);
            if (living instanceof Player target && (driver == null || !plugin.teams().canHarm(driver, target))) {
                vehicle.speed = 0;
                continue;
            }
            if (impact < 0.15) {
                living.setVelocity(new Vector(fx * sign, 0.1, fz * sign).multiply(0.3));
                vehicle.speed *= 0.3;
                continue;
            }
            Long last = vehicle.hits.get(entity.getUniqueId());
            if (last != null && now - last < 600) continue;
            vehicle.hits.put(entity.getUniqueId(), now);
            double damage = plugin.settings().play().vehicles().impactDamage() * impact;
            DamageSource source = driver == null ? DamageSource.builder(DamageType.GENERIC).build()
                    : DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(driver).withDirectEntity(driver).build();
            living.damage(damage, source);
            living.setVelocity(new Vector(fx * sign, 0, fz * sign).multiply(0.6 + impact * 1.5).setY(0.35 + impact * 0.3));
            AttributeInstance health = living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            vehicle.health = Math.max(0, vehicle.health - 1 - (health == null ? 20 : health.getValue()) / 20);
            vehicle.speed *= 0.8;
            vehicle.world.playSound(living.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.7f);
            if (driver != null) plugin.protection().endByAction(driver);
            if (vehicle.health <= 0) engineDestroyed(vehicle);
        }
    }

    private void effects(Vehicle vehicle, Player driver, boolean horn, ApocalypseConfig.Vehicles rules) {
        Location hood = local(vehicle, 0, 1.0, 1.4);
        if (vehicle.engineOn && vehicle.ticks % 20 == 0) {
            float pace = (float) Math.abs(vehicle.speed);
            vehicle.world.playSound(hood, Sound.ENTITY_MINECART_RIDING, 0.6f + pace * 0.5f, 0.5f + pace * 0.9f);
            plugin.noise().emit(hood, rules.engineNoise() + Math.abs(vehicle.speed) * 30, driver);
        }
        if (horn && driver != null && vehicle.ticks - vehicle.lastHorn > 20 && vehicle.model == VehicleModel.SEDAN && vehicle.health > 0) {
            vehicle.lastHorn = vehicle.ticks;
            vehicle.world.playSound(hood, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 3f, 1.25f);
            vehicle.world.playSound(hood, Sound.BLOCK_NOTE_BLOCK_BASS, 3f, 0.9f);
            plugin.noise().emit(hood, rules.hornNoise(), driver);
        }
        if (vehicle.model == VehicleModel.SEDAN && vehicle.health < 30 && vehicle.ticks % 10 == 0)
            vehicle.world.spawnParticle(vehicle.health <= 0 ? Particle.CAMPFIRE_COSY_SMOKE : Particle.LARGE_SMOKE, hood, 2, 0.2, 0.1, 0.2, 0.01);
    }

    private void gauge(Player driver, Vehicle vehicle) {
        NamedTextColor color = vehicle.health < 30 || vehicle.fuel < 3 ? NamedTextColor.RED : NamedTextColor.GOLD;
        String engine = vehicle.engineOn ? "" : vehicle.health <= 0 ? " — moteur hors d'usage" : vehicle.fuel <= 0 ? " — réservoir vide" : " — moteur coupé";
        driver.sendActionBar(Component.text(VehiclePhysics.kmh(vehicle.speed) + " km/h · essence " + Math.round(vehicle.fuel)
                + " L · état " + Math.round(vehicle.health) + " %" + engine, color));
    }

    private void startEngine(Vehicle vehicle) {
        vehicle.engineOn = true;
        Location hood = local(vehicle, 0, 0.8, 1.4);
        vehicle.world.playSound(hood, Sound.BLOCK_PISTON_EXTEND, 1f, 0.5f);
        vehicle.world.playSound(hood, Sound.ENTITY_MINECART_RIDING, 1f, 0.6f);
        plugin.noise().emit(hood, plugin.settings().play().vehicles().engineNoise(), rider(vehicle, 0));
        lights(vehicle, true);
    }

    private void stopEngine(Vehicle vehicle) {
        vehicle.engineOn = false;
        lights(vehicle, false);
        save(vehicle);
    }

    private void lights(Vehicle vehicle, boolean on) {
        for (UUID id : vehicle.parts) {
            if (!(Bukkit.getEntity(id) instanceof BlockDisplay part)) continue;
            String role = part.getPersistentDataContainer().get(plugin.keys().vehiclePart, PersistentDataType.STRING);
            if (!VehicleModel.HEADLIGHT.equals(role) && !VehicleModel.TAILLIGHT.equals(role)) continue;
            part.setBlock(VehicleModel.lightData(role, on));
            part.setBrightness(on ? new Display.Brightness(15, 15) : null);
        }
    }

    private void engineDestroyed(Vehicle vehicle) {
        if (vehicle.engineOn) stopEngine(vehicle);
        vehicle.world.playSound(local(vehicle, 0, 1, 1.4), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.6f);
        Player driver = rider(vehicle, 0);
        if (driver != null) Text.warn(driver, "Le moteur a lâché : répare-le avec de la ferraille (clic droit, hors de la voiture).");
        save(vehicle);
    }

    // ------------------------------------------------------------------ explosions

    /** A blast damages nearby cars; a car with fuel left that gets destroyed burns out a few seconds later. */
    public void blast(Location center, double radius, double power, Player source) {
        for (Vehicle vehicle : List.copyOf(vehicles.values())) {
            if (!vehicle.world.equals(center.getWorld()) || vehicle.model != VehicleModel.SEDAN) continue;
            double distance = local(vehicle, 0, 0.8, 0).distance(center);
            double reach = radius + 2;
            if (distance > reach) continue;
            boolean alive = vehicle.health > 0;
            vehicle.health = Math.max(0, vehicle.health - power * 1.5 * (1 - distance / reach));
            if (alive && vehicle.health <= 0) {
                engineDestroyed(vehicle);
                if (vehicle.fuel >= 3) burnOut(vehicle, source);
            }
            save(vehicle);
        }
    }

    private void burnOut(Vehicle vehicle, Player source) {
        int[] count = {0};
        BukkitTask[] fire = new BukkitTask[1];
        fire[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!vehicles.containsKey(vehicle.id) || vehicle.model != VehicleModel.SEDAN) {
                fire[0].cancel();
                return;
            }
            Location hood = local(vehicle, 0, 1.0, 0.6);
            vehicle.world.spawnParticle(Particle.FLAME, hood, 12, 0.6, 0.3, 1.0, 0.02);
            vehicle.world.spawnParticle(Particle.LARGE_SMOKE, hood, 6, 0.5, 0.3, 0.8, 0.02);
            vehicle.world.playSound(hood, Sound.BLOCK_FIRE_AMBIENT, 1.5f, 0.8f);
            if (++count[0] < 8) return;
            fire[0].cancel();
            for (int seat = 0; seat < Vehicle.SEATS; seat++) {
                Player rider = rider(vehicle, seat);
                if (rider != null) rider.leaveVehicle();
            }
            plugin.throwables().explode(hood, 4, 18, source);
            toWreck(vehicle);
        }, 0L, 10L);
    }

    private void toWreck(Vehicle vehicle) {
        for (UUID id : vehicle.parts) {
            Entity part = Bukkit.getEntity(id);
            if (part != null) part.remove();
        }
        vehicle.parts.clear();
        vehicle.body = null;
        vehicle.model = VehicleModel.WRECK;
        vehicle.fuel = 0;
        vehicle.health = 0;
        vehicle.speed = 0;
        spawnParts(vehicle);
        if (!vehicle.occupied()) park(vehicle);
        save(vehicle);
    }
}
