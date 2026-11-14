package org.duckcatmine.apocalypse.survivors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.combat.FirearmService;
import org.duckcatmine.apocalypse.combat.Gun;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.items.ItemRegistry;
import org.duckcatmine.apocalypse.loot.LootTable;
import org.duckcatmine.apocalypse.mobs.Appearance;
import org.duckcatmine.apocalypse.persistence.Store;

/**
 * Survivors who join a player: engineer (crafts weapons from what you give him), scientist (antidotes and bandages),
 * guard (holds a place with any weapon) and explorer (brings back material). They can be bitten: cure them with an
 * antidote before the incubation ends, or they turn into an infected dressed as their former trade.
 */
public final class SurvivorService implements Listener {
    public enum Role {
        ENGINEER("Ingénieur", "Ingénieure", 0xE67E22, List.of("pistol", "shotgun", "smg", "ammo", "rocket", "pipe_bomb", "rocket_launcher")),
        SCIENTIST("Scientifique", "Scientifique", 0xF4F6F7, List.of("antidote", "bandage")),
        GUARD("Garde", "Garde", 0x1F3A5F, List.of()),
        EXPLORER("Explorateur", "Exploratrice", 0x8E7B56, List.of());
        final String male, female;
        final int color;
        final List<String> recipes;
        Role(String male, String female, int color, List<String> recipes) { this.male = male; this.female = female; this.color = color; this.recipes = recipes; }
        public String id() { return name().toLowerCase(Locale.ROOT); }
        public String label(boolean woman) { return woman ? female : male; }
        Appearance infectedLook() { return Appearance.valueOf(name()); }
        static Role of(String id) { for (Role r : values()) if (r.id().equals(id)) return r; return null; }
    }
    public record Info(String id, String name, String role, String owner, String state, int x, int y, int z) {}
    private static final String[] MEN = {"Hugo", "Karim", "Léo", "Mathis", "Yanis", "Bastien", "Malik", "Théo", "Samuel", "Nolan"};
    private static final String[] WOMEN = {"Léa", "Inès", "Chloé", "Sarah", "Manon", "Yasmine", "Clara", "Jade", "Nadia", "Emma"};
    public static final long INCUBATION_MS = 300_000, CRAFT_MS = 45_000, EXPLORE_MS = 300_000, WAITING_MS = 1_200_000;
    private static final int STORAGE = 18, ROWS = 36;
    private static final Gson GSON = new Gson();

    private final DuckCatApocalypse plugin;
    private final Random random = new Random();
    private final NamespacedKey role, owner, name, woman, state, until, job, bag, post, follow, expires;
    private final Map<String, Info> registry = new LinkedHashMap<>();
    private final Map<UUID, UUID> viewers = new HashMap<>();
    private final Map<UUID, Long> nextShot = new HashMap<>();
    private long nextCall, seconds;
    private BukkitTask walker;

    private static final class Menu implements InventoryHolder {
        final UUID survivor;
        Inventory inventory;
        Menu(UUID survivor) { this.survivor = survivor; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public SurvivorService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
        role = key("survivor_role"); owner = key("survivor_owner"); name = key("survivor_name"); woman = key("survivor_woman");
        state = key("survivor_state"); until = key("survivor_until"); job = key("survivor_job"); bag = key("survivor_bag");
        post = key("survivor_post"); follow = key("survivor_follow"); expires = key("survivor_expires");
    }

    private NamespacedKey key(String id) { return new NamespacedKey(plugin, id); }

    public void load() {
        try {
            String stored = plugin.database().call(c -> Store.meta(c, "survivors"));
            if (stored != null) for (Info info : GSON.<List<Info>>fromJson(stored, new TypeToken<List<Info>>() {}.getType())) registry.put(info.id(), info);
        } catch (Exception e) {
            plugin.getLogger().warning("Registre des survivants illisible : " + e.getMessage());
        }
    }

    private void persist() {
        String json = GSON.toJson(new ArrayList<>(registry.values()));
        plugin.database().submit(c -> { Store.putMeta(c, "survivors", json); return null; });
    }

    public List<Info> snapshot() { return List.copyOf(registry.values()); }

    public boolean isSurvivor(Entity entity) {
        return entity instanceof Mannequin && entity.getPersistentDataContainer().has(role);
    }

    private long owned(UUID player) {
        return registry.values().stream().filter(i -> player.toString().equals(i.owner())).count();
    }

    // ------------------------------------------------------------------ appearance

    private Mannequin create(Location at, Role kind, boolean female, boolean infected) {
        String first = female ? WOMEN[random.nextInt(WOMEN.length)] : MEN[random.nextInt(MEN.length)];
        return at.getWorld().spawn(at, Mannequin.class, m -> {
            var data = m.getPersistentDataContainer();
            data.set(role, PersistentDataType.STRING, kind.id());
            data.set(name, PersistentDataType.STRING, first);
            data.set(woman, PersistentDataType.BOOLEAN, female);
            data.set(state, PersistentDataType.STRING, infected ? "infected" : "idle");
            if (infected) data.set(until, PersistentDataType.LONG, plugin.clock().now() + INCUBATION_MS);
            data.set(expires, PersistentDataType.LONG, plugin.clock().now() + WAITING_MS);
            m.setPersistent(true);
            m.setRemoveWhenFarAway(false);
            var health = m.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) health.setBaseValue(30);
            m.setHealth(30);
            dress(m);
        });
    }

    private void dress(Mannequin m) {
        Role kind = roleOf(m);
        if (kind == null) return;
        boolean sick = "infected".equals(m.getPersistentDataContainer().get(state, PersistentDataType.STRING));
        boolean female = Boolean.TRUE.equals(m.getPersistentDataContainer().get(woman, PersistentDataType.BOOLEAN));
        m.setProfile(ResolvableProfile.resolvableProfile().skinPatch(p -> p
                .body(Key.key("duckcatmine", "entity/survivor/" + kind.id() + (sick ? "_infected" : "")))
                .model(female ? org.bukkit.profile.PlayerTextures.SkinModel.SLIM : org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC)).build());
        var eq = m.getEquipment();
        eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, sick ? 0x6B8E23 : kind.color));
        switch (kind) {
            case ENGINEER -> eq.setHelmet(leather(Material.LEATHER_HELMET, 0xF1C40F));
            case GUARD -> eq.setHelmet(ItemStack.of(Material.CHAINMAIL_HELMET));
            case EXPLORER -> eq.setHelmet(leather(Material.LEATHER_HELMET, 0x7B5B3A));
            default -> eq.setHelmet(null);
        }
        m.customName(Component.text(nameOf(m) + " · " + kind.label(female) + (sick ? " (mordu" + (female ? "e" : "") + ")" : ""),
                sick ? NamedTextColor.RED : NamedTextColor.GREEN));
        m.setCustomNameVisible(true);
    }

    private static ItemStack leather(Material material, int rgb) {
        ItemStack stack = ItemStack.of(material);
        stack.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(Color.fromRGB(rgb)));
        return stack;
    }

    private Role roleOf(Entity e) { return Role.of(e.getPersistentDataContainer().get(role, PersistentDataType.STRING)); }
    private String nameOf(Entity e) { return e.getPersistentDataContainer().getOrDefault(name, PersistentDataType.STRING, "Survivant"); }
    private UUID ownerOf(Entity e) {
        String id = e.getPersistentDataContainer().get(owner, PersistentDataType.STRING);
        return id == null ? null : UUID.fromString(id);
    }
    private boolean female(Entity e) { return Boolean.TRUE.equals(e.getPersistentDataContainer().get(woman, PersistentDataType.BOOLEAN)); }
    private String label(Entity e) { Role r = roleOf(e); return nameOf(e) + (r == null ? "" : " (" + r.label(female(e)).toLowerCase(Locale.ROOT) + ")"); }

    // ------------------------------------------------------------------ calls for help

    /** Every second. */
    public void tick() {
        seconds++;
        World world = plugin.scenario().world();
        if (world == null || !plugin.scenario().isEnabled()) return;
        long now = plugin.clock().now();
        var rules = plugin.settings().play().survival();
        for (Mannequin m : world.getEntitiesByClass(Mannequin.class)) {
            if (!isSurvivor(m) || !m.isValid()) continue;
            behave(m, now);
        }
        if (seconds % 30 == 0) persist();
        if (!rules.survivors()) return;
        List<Player> active = new ArrayList<>();
        for (Player p : world.getPlayers())
            if (plugin.sessions().isSurvivor(p) && !p.isDead() && !plugin.scenario().inRefuge(p.getLocation())) active.add(p);
        if (nextCall == 0) nextCall = now + (long) (rules.survivorMinutes() * 60_000 * (0.5 + random.nextDouble()));
        if (active.isEmpty()) { nextCall += 1000; return; }
        if (now < nextCall) return;
        nextCall = now + (long) (rules.survivorMinutes() * 60_000 * (0.7 + random.nextDouble() * 0.6));
        long waiting = registry.values().stream().filter(i -> i.owner() == null).count();
        if (waiting < 2) call(active.get(random.nextInt(active.size())), 0);
    }

    /** A survivor waits 40 to 90 blocks away and calls for help on the radio. Admin command too. */
    public void call(Player near, int tries) {
        if (tries >= 8) return;
        World world = near.getWorld();
        double angle = random.nextDouble() * Math.PI * 2, distance = 40 + random.nextDouble() * 50;
        int x = (int) Math.floor(near.getX() + Math.cos(angle) * distance), z = (int) Math.floor(near.getZ() + Math.sin(angle) * distance);
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            Location spot = plugin.director().outdoorSpot(world, x, z);
            if (spot == null || !near.isOnline()) { call(near, tries + 1); return; }
            Role kind = Role.values()[random.nextInt(Role.values().length)];
            boolean sick = random.nextDouble() < plugin.settings().play().survival().survivorInfectedChance();
            Mannequin m = create(spot, kind, random.nextBoolean(), sick);
            remember(m);
            persist();
            plugin.events().add("survivor", label(m) + " appelle à l'aide en X " + spot.getBlockX() + ", Z " + spot.getBlockZ());
            for (Player player : world.getPlayers()) {
                if (!plugin.sessions().isSurvivor(player)) continue;
                double dx = spot.getX() - player.getX(), dz = spot.getZ() - player.getZ();
                player.sendMessage(Component.text("📻 TALKIE ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("« Ici " + nameOf(m) + ", " + kind.label(female(m)).toLowerCase(Locale.ROOT)
                                + (sick ? ". J'ai été mordu" + (female(m) ? "e" : "") + "… aidez-moi" : ". Je suis seul" + (female(m) ? "e" : "") + ", venez me chercher")
                                + " ! » ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                        .append(Component.text("→ " + Math.round(Math.hypot(dx, dz)) + " m " + org.duckcatmine.apocalypse.survival.SupplyDropService.direction(dx, dz),
                                NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f, 1.2f);
            }
        });
    }

    private void remember(Mannequin m) {
        UUID o = ownerOf(m);
        Location at = m.getLocation();
        registry.put(m.getUniqueId().toString(), new Info(m.getUniqueId().toString(), nameOf(m), roleOf(m).id(), o == null ? null : o.toString(),
                m.getPersistentDataContainer().getOrDefault(state, PersistentDataType.STRING, "idle"), at.getBlockX(), at.getBlockY(), at.getBlockZ()));
    }

    // ------------------------------------------------------------------ behaviour

    private void behave(Mannequin m, long now) {
        PersistentDataContainer data = m.getPersistentDataContainer();
        String current = data.getOrDefault(state, PersistentDataType.STRING, "idle");
        UUID boss = ownerOf(m);
        if (boss == null && now > data.getOrDefault(expires, PersistentDataType.LONG, Long.MAX_VALUE) && !nearPlayer(m, 48)) {
            registry.remove(m.getUniqueId().toString());
            m.remove();
            return;
        }
        if ("infected".equals(current)) {
            m.getWorld().spawnParticle(Particle.DUST, m.getLocation().add(0, 1.8, 0), 3, .25, .3, .25, new Particle.DustOptions(Color.fromRGB(110, 140, 40), 1f));
            if (now >= data.getOrDefault(until, PersistentDataType.LONG, now)) { turn(m); return; }
        }
        if ("away".equals(current)) {
            if (now >= data.getOrDefault(until, PersistentDataType.LONG, now)) back(m);
            return;
        }
        String work = data.get(job, PersistentDataType.STRING);
        if (work != null && now >= data.getOrDefault(until, PersistentDataType.LONG, now)) finish(m, work);
        if (roleOf(m) == Role.GUARD && !"infected".equals(current)) guard(m, now);
        // Infected nearby go for the survivor when no player holds their attention.
        if (seconds % 2 == 0) for (Entity e : m.getNearbyEntities(10, 4, 10))
            if (e instanceof Mob mob && plugin.mobs().isScenarioMob(mob) && mob.getTarget() == null && mob.hasLineOfSight(m)) mob.setTarget(m);
        remember(m);
        if (boss != null && Boolean.TRUE.equals(data.get(follow, PersistentDataType.BOOLEAN)) && walker == null)
            walker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::walk, 1L, 1L);
    }

    private boolean nearPlayer(Entity e, double distance) {
        for (Player p : e.getWorld().getPlayers()) if (p.getLocation().distanceSquared(e.getLocation()) < distance * distance) return true;
        return false;
    }

    /** Followers walk after their leader each tick (ground following, one-block steps, catch-up beyond 24 blocks). */
    private void walk() {
        World world = plugin.scenario().world();
        boolean any = false;
        if (world != null) for (Mannequin m : world.getEntitiesByClass(Mannequin.class)) {
            if (!isSurvivor(m) || !Boolean.TRUE.equals(m.getPersistentDataContainer().get(follow, PersistentDataType.BOOLEAN))) continue;
            UUID boss = ownerOf(m);
            Player leader = boss == null ? null : plugin.getServer().getPlayer(boss);
            if (leader == null || !leader.getWorld().equals(m.getWorld()) || "away".equals(m.getPersistentDataContainer().get(state, PersistentDataType.STRING))) continue;
            any = true;
            Location here = m.getLocation(), target = leader.getLocation();
            double dx = target.getX() - here.getX(), dz = target.getZ() - here.getZ(), d = Math.hypot(dx, dz);
            if (d > 24) { m.teleport(behind(leader)); continue; }
            if (d < 2.5) continue;
            double speed = leader.isSprinting() || d > 8 ? 0.28 : 0.2;
            double nx = here.getX() + dx / d * speed, nz = here.getZ() + dz / d * speed;
            Block feet = world.getBlockAt((int) Math.floor(nx), here.getBlockY(), (int) Math.floor(nz));
            double ny = here.getY();
            if (!feet.isPassable()) {
                if (feet.getRelative(BlockFace.UP).isPassable() && feet.getRelative(BlockFace.UP, 2).isPassable()) ny = feet.getY() + 1;
                else continue;
            } else if (feet.getRelative(BlockFace.DOWN).isPassable()) {
                Block below = feet.getRelative(BlockFace.DOWN);
                for (int n = 0; n < 4 && below.isPassable(); n++) below = below.getRelative(BlockFace.DOWN);
                ny = below.getY() + 1;
            }
            Location next = new Location(world, nx, ny, nz, (float) Math.toDegrees(Math.atan2(-dx, dz)), 0);
            m.teleport(next);
        }
        if (!any && walker != null) { walker.cancel(); walker = null; }
    }

    private Location behind(Player leader) {
        Location at = leader.getLocation();
        Vector back = at.getDirection().setY(0);
        if (back.lengthSquared() < 0.01) back = new Vector(0, 0, 1);
        Location spot = at.clone().subtract(back.normalize().multiply(2));
        int y = spot.getWorld().getHighestBlockYAt(spot, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return Math.abs(y + 1 - at.getY()) < 3 ? spot.clone().add(0, y + 1 - spot.getY(), 0) : at.clone();
    }

    private void guard(Mannequin m, long now) {
        if (nextShot.getOrDefault(m.getUniqueId(), 0L) > now) return;
        ItemStack[] items = storage(m);
        ItemStack weapon = items[0];
        Gun gun = weapon == null ? null : Gun.of(plugin.items().idOf(weapon), plugin.settings());
        double range = gun != null ? Math.min(32, gun.range()) : 3.5;
        Mob prey = null;
        double best = range * range;
        for (Entity e : m.getNearbyEntities(range, 6, range)) {
            if (!(e instanceof Mob mob) || !plugin.mobs().isScenarioMob(mob) || mob.isDead() || !m.hasLineOfSight(mob)) continue;
            double d = e.getLocation().distanceSquared(m.getLocation());
            if (d < best) { best = d; prey = mob; }
        }
        if (prey == null) return;
        Location eye = m.getEyeLocation();
        Vector aim = prey.getEyeLocation().subtract(0, 0.4, 0).toVector().subtract(eye.toVector());
        m.setRotation((float) Math.toDegrees(Math.atan2(-aim.getX(), aim.getZ())), 0);
        if (gun != null && consume(m, items, gun.ammo())) {
            RayTraceResult hit = FirearmService.trace(eye, aim.clone().normalize(), range, m);
            if (hit != null && hit.getHitEntity() instanceof LivingEntity target && plugin.mobs().isScenarioMob(target))
                target.damage(gun.damage() * Math.max(1, gun.pellets() / 2.0), m);
            for (double s = 1.5; s < Math.sqrt(best); s += 3) m.getWorld().spawnParticle(Particle.CRIT, eye.clone().add(aim.clone().normalize().multiply(s)), 1, 0, 0, 0, 0);
            m.getWorld().playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.1f, 1.3f);
            plugin.noise().emit(m.getLocation(), plugin.settings().play().noise().gunshot() / 2, null);
            nextShot.put(m.getUniqueId(), now + Math.max(400, gun.delayMs()));
        } else if (Math.sqrt(best) <= 3.5) {
            double damage = weapon == null ? 3 : plugin.settings().play().weapons().getOrDefault(String.valueOf(plugin.items().idOf(weapon)),
                    new ApocalypseConfig.Weapon("", 5, 1, 1, 3, 0)).damage();
            m.swingMainHand();
            prey.damage(damage, m);
            nextShot.put(m.getUniqueId(), now + 900);
        }
        m.getEquipment().setItemInMainHand(weapon == null ? null : weapon.clone());
    }

    private boolean consume(Mannequin m, ItemStack[] items, String ammo) {
        for (int n = 1; n < items.length; n++) {
            if (!plugin.items().is(items[n], ammo)) continue;
            items[n].setAmount(items[n].getAmount() - 1);
            if (items[n].getAmount() <= 0) items[n] = null;
            setStorage(m, items);
            return true;
        }
        return false;
    }

    private void finish(Mannequin m, String work) {
        PersistentDataContainer data = m.getPersistentDataContainer();
        data.remove(job);
        ItemStack[] items = storage(m);
        List<ItemStack> results = new ArrayList<>();
        if (work.startsWith("craft:")) {
            var recipe = plugin.settings().play().recipes().get(work.substring(6));
            ItemStack made = recipe == null ? null : plugin.items().resolve(recipe.result().item(), recipe.result().amount());
            if (made != null) results.add(made);
        }
        for (ItemStack result : results) add(m, items, result);
        setStorage(m, items);
        tell(m, label(m) + " a terminé : " + (results.isEmpty() ? "rien à remettre" : results.getFirst().getAmount() + " × "
                + plugin.items().label(plugin.items().idOf(results.getFirst()))) + ". Récupère-le dans son sac.");
        m.getWorld().playSound(m.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.3f);
    }

    private void back(Mannequin m) {
        PersistentDataContainer data = m.getPersistentDataContainer();
        double roll = random.nextDouble();
        if (roll < 0.05) {
            tell(m, label(m) + " n'est pas revenu" + (female(m) ? "e" : "") + " d'exploration.");
            registry.remove(m.getUniqueId().toString());
            m.remove();
            return;
        }
        m.setInvisible(false);
        m.setInvulnerable(false);
        m.setCustomNameVisible(true);
        ItemStack[] items = storage(m);
        String[] places = {"house", "shop", "warehouse", "garage", "police", "supermarket"};
        var place = plugin.settings().place(places[random.nextInt(places.length)]);
        int found = 0;
        if (place != null) for (int n = 0; n < 4 + random.nextInt(4); n++) {
            LootTable.Entry entry = place.loot().pick(random.nextDouble());
            ItemStack stack = plugin.items().loot(entry.item(), entry.amount());
            if (stack != null) { add(m, items, stack); found++; }
        }
        setStorage(m, items);
        boolean bitten = roll < 0.15;
        data.set(state, PersistentDataType.STRING, bitten ? "infected" : "idle");
        if (bitten) data.set(until, PersistentDataType.LONG, plugin.clock().now() + INCUBATION_MS);
        dress(m);
        tell(m, label(m) + " est revenu" + (female(m) ? "e" : "") + " avec " + found + " lot(s) de matériel" + (bitten ? "… mais a été mordu" + (female(m) ? "e" : "") + " ! Soigne-l" + (female(m) ? "a" : "e") + " vite." : "."));
    }

    private void turn(Mannequin m) {
        Role kind = roleOf(m);
        Location at = m.getLocation();
        for (ItemStack item : storage(m)) if (item != null) at.getWorld().dropItemNaturally(at, item);
        tell(m, label(m) + " a succombé à l'infection et s'est transformé" + (female(m) ? "e" : "") + ".");
        plugin.events().add("survivor", label(m) + " s'est transformé en infecté");
        registry.remove(m.getUniqueId().toString());
        m.remove();
        at.getWorld().playSound(at, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 0.7f);
        if (kind != null) plugin.mobs().spawn(ApocalypseConfig.WALKER, kind.infectedLook(), at);
    }

    private void tell(Mannequin m, String message) {
        UUID boss = ownerOf(m);
        Player player = boss == null ? null : plugin.getServer().getPlayer(boss);
        if (player != null) Text.info(player, message);
    }

    // ------------------------------------------------------------------ storage

    private ItemStack[] storage(Mannequin m) {
        byte[] bytes = m.getPersistentDataContainer().get(bag, PersistentDataType.BYTE_ARRAY);
        ItemStack[] items = new ItemStack[STORAGE];
        if (bytes != null) {
            ItemStack[] stored = ItemStack.deserializeItemsFromBytes(bytes);
            System.arraycopy(stored, 0, items, 0, Math.min(STORAGE, stored.length));
        }
        for (int n = 0; n < items.length; n++) if (items[n] != null && items[n].isEmpty()) items[n] = null;
        return items;
    }

    private void setStorage(Mannequin m, ItemStack[] items) {
        ItemStack[] copy = Arrays.stream(items).map(i -> i == null ? ItemStack.empty() : i).toArray(ItemStack[]::new);
        m.getPersistentDataContainer().set(bag, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(copy));
    }

    private void add(Mannequin m, ItemStack[] items, ItemStack stack) {
        for (int n = roleOf(m) == Role.GUARD ? 1 : 0; n < items.length; n++) if (items[n] == null) { items[n] = stack; return; }
        m.getWorld().dropItemNaturally(m.getLocation(), stack);
    }

    private boolean has(ItemStack[] items, Map<String, Integer> ingredients) {
        for (var need : ingredients.entrySet()) if (count(items, need.getKey()) < need.getValue()) return false;
        return true;
    }

    private int count(ItemStack[] items, String ingredient) {
        int total = 0;
        for (ItemStack item : items) if (item != null && matches(item, ingredient)) total += item.getAmount();
        return total;
    }

    private boolean matches(ItemStack item, String ingredient) {
        String id = plugin.items().idOf(item);
        if (ingredient.startsWith(ItemRegistry.NAMESPACE + ":")) return ingredient.substring(ItemRegistry.NAMESPACE.length() + 1).equals(id);
        if (id != null) return false;
        return switch (ingredient) {
            case "#planks" -> Tag.PLANKS.isTagged(item.getType());
            case "#logs" -> Tag.LOGS.isTagged(item.getType());
            case "#wool" -> Tag.WOOL.isTagged(item.getType());
            default -> item.getType() == Material.matchMaterial(ingredient);
        };
    }

    private void take(ItemStack[] items, Map<String, Integer> ingredients) {
        for (var need : ingredients.entrySet()) {
            int left = need.getValue();
            for (int n = 0; n < items.length && left > 0; n++) {
                if (items[n] == null || !matches(items[n], need.getKey())) continue;
                int used = Math.min(left, items[n].getAmount());
                items[n].setAmount(items[n].getAmount() - used);
                if (items[n].getAmount() <= 0) items[n] = null;
                left -= used;
            }
        }
    }

    // ------------------------------------------------------------------ interaction

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Mannequin m) || !isSurvivor(m)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!plugin.sessions().isSurvivor(player)) return;
        if ("away".equals(m.getPersistentDataContainer().get(state, PersistentDataType.STRING))) return;
        UUID boss = ownerOf(m);
        if (boss == null) {
            if (owned(player.getUniqueId()) >= plugin.settings().play().survival().survivorsPerPlayer()) {
                Text.warn(player, "Tu ne peux pas protéger plus de " + plugin.settings().play().survival().survivorsPerPlayer() + " survivants.");
                return;
            }
            m.getPersistentDataContainer().set(owner, PersistentDataType.STRING, player.getUniqueId().toString());
            m.getPersistentDataContainer().set(follow, PersistentDataType.BOOLEAN, true);
            remember(m);
            persist();
            Text.good(player, label(m) + " te rejoint et te suit. Clic droit pour lui confier une mission.");
            plugin.events().add("survivor", label(m) + " a rejoint " + player.getName());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
            return;
        }
        if (!boss.equals(player.getUniqueId()) && !plugin.teams().sameTeam(boss, player.getUniqueId())) {
            Text.warn(player, label(m) + " fait confiance à un autre survivant.");
            return;
        }
        open(player, m);
    }

    private void open(Player player, Mannequin m) {
        UUID busy = viewers.get(m.getUniqueId());
        if (busy != null && !busy.equals(player.getUniqueId()) && plugin.getServer().getPlayer(busy) != null) {
            Text.warn(player, label(m) + " parle déjà avec quelqu'un.");
            return;
        }
        Menu menu = new Menu(m.getUniqueId());
        Inventory inventory = plugin.getServer().createInventory(menu, ROWS, Component.text(label(m)));
        menu.inventory = inventory;
        render(inventory, m, player);
        viewers.put(m.getUniqueId(), player.getUniqueId());
        player.openInventory(inventory);
    }

    private void render(Inventory inventory, Mannequin m, Player player) {
        Role kind = roleOf(m);
        PersistentDataContainer data = m.getPersistentDataContainer();
        boolean sick = "infected".equals(data.get(state, PersistentDataType.STRING));
        long now = plugin.clock().now();
        String work = data.get(job, PersistentDataType.STRING);
        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int n = 0; n < STORAGE; n++) inventory.setItem(n, filler);
        List<String> status = new ArrayList<>();
        status.add("Santé : " + Math.round(m.getHealth()) + " / 30");
        if (sick) status.add("Mordu : " + (Math.max(0, data.getOrDefault(until, PersistentDataType.LONG, now) - now) / 1000) + " s avant de se transformer");
        if (work != null) status.add("Travail en cours : " + (Math.max(0, data.getOrDefault(until, PersistentDataType.LONG, now) - now) / 1000) + " s");
        status.add(kind == Role.GUARD ? "1re case du sac : son arme (balles dans le sac)." : "Sac : dépose ce qu'il lui faut.");
        inventory.setItem(0, button(Material.NAME_TAG, label(m), status));
        boolean following = Boolean.TRUE.equals(data.get(follow, PersistentDataType.BOOLEAN));
        inventory.setItem(1, button(following ? Material.LEAD : Material.OAK_SIGN, following ? "Rester ici" : "Me suivre",
                List.of(following ? "Il attend à cet endroit." : "Il te suit à pied.")));
        if (sick) inventory.setItem(2, button(Material.EXPERIENCE_BOTTLE, "Soigner avec un antidote", List.of("Un antidote de ton inventaire.", "Obligatoire avant toute mission.")));
        int slot = 3;
        if (kind == Role.GUARD) inventory.setItem(slot++, button(Material.SHIELD, "Garder cet endroit", List.of("Il se poste ici et tire sur les infectés.", "Arme : 1re case de son sac.")));
        if (kind == Role.EXPLORER) inventory.setItem(slot++, button(Material.COMPASS, "Partir en exploration (5 min)", List.of("Il ramène du matériel.", "Risque : morsure ou disparition.")));
        for (String id : kind == null ? List.<String>of() : kind.recipes) {
            var recipe = plugin.settings().play().recipes().get(id);
            if (recipe == null || slot > 7) continue;
            ItemStack icon = plugin.items().resolve(recipe.result().item(), recipe.result().amount());
            if (icon == null) continue;
            List<Component> lore = new ArrayList<>();
            lore.add(line("Fabrique en " + CRAFT_MS / 1000 + " s avec, dans son sac :", NamedTextColor.GRAY));
            recipe.ingredients().forEach((k, v) -> lore.add(line(" · " + v + " × " + ingredientLabel(k), NamedTextColor.GRAY)));
            icon.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
            inventory.setItem(slot++, tag(icon, "craft:" + id));
        }
        inventory.setItem(8, button(Material.BARRIER, "Renvoyer", List.of("Il part avec ses affaires (déposées au sol).")));
        for (int n = 9; n < STORAGE; n++) inventory.setItem(n, button(Material.BLACK_STAINED_GLASS_PANE, n == 9 ? "↓ Sac du survivant ↓" : " ", List.of()));
        ItemStack[] items = storage(m);
        for (int n = 0; n < STORAGE; n++) inventory.setItem(STORAGE + n, items[n]);
    }

    private String ingredientLabel(String key) {
        if (key.startsWith(ItemRegistry.NAMESPACE + ":")) { String l = plugin.items().label(key.substring(ItemRegistry.NAMESPACE.length() + 1)); return l == null ? key : l; }
        return switch (key) { case "#planks" -> "planches"; case "#logs" -> "bûches"; case "#wool" -> "laine"; default -> key.replace("minecraft:", ""); };
    }

    private ItemStack button(Material material, String title, List<String> lore) {
        ItemStack stack = ItemStack.of(material);
        stack.setData(DataComponentTypes.ITEM_NAME, Component.text(title, NamedTextColor.WHITE));
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(lore.stream().map(l -> line(l, NamedTextColor.GRAY)).toList()));
        return stack;
    }

    private ItemStack tag(ItemStack stack, String action) {
        stack.editPersistentDataContainer(d -> d.set(job, PersistentDataType.STRING, action));
        return stack;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu) || !(event.getWhoClicked() instanceof Player player)) return;
        if (!(plugin.getServer().getEntity(menu.survivor) instanceof Mannequin m) || !m.isValid()) { event.setCancelled(true); player.closeInventory(); return; }
        int raw = event.getRawSlot();
        if (raw >= STORAGE) {
            if (raw < ROWS || !event.isShiftClick()) return;
            return; // shift-click from the player's inventory fills free bag slots only: buttons have no free slot
        }
        event.setCancelled(true);
        PersistentDataContainer data = m.getPersistentDataContainer();
        boolean sick = "infected".equals(data.get(state, PersistentDataType.STRING));
        save(menu.inventory, m);
        switch (raw) {
            case 1 -> {
                boolean following = !Boolean.TRUE.equals(data.get(follow, PersistentDataType.BOOLEAN));
                data.set(follow, PersistentDataType.BOOLEAN, following);
                Text.info(player, label(m) + (following ? " te suit." : " t'attend ici."));
            }
            case 2 -> { if (sick) cure(player, m); }
            case 8 -> {
                for (ItemStack item : storage(m)) if (item != null) m.getWorld().dropItemNaturally(m.getLocation(), item);
                registry.remove(m.getUniqueId().toString());
                persist();
                Text.info(player, label(m) + " reprend la route seul" + (female(m) ? "e" : "") + ".");
                m.remove();
                player.closeInventory();
                return;
            }
            default -> {
                ItemStack clicked = event.getCurrentItem();
                String action = clicked == null ? null : clicked.getPersistentDataContainer().get(job, PersistentDataType.STRING);
                Role kind = roleOf(m);
                if (sick && (action != null || clicked != null && clicked.getType() != Material.GRAY_STAINED_GLASS_PANE && raw >= 3 && raw <= 7)) {
                    Text.warn(player, label(m) + " est mordu" + (female(m) ? "e" : "") + " : soigne-l" + (female(m) ? "a" : "e") + " d'abord avec un antidote.");
                    break;
                }
                if (data.has(job)) { Text.warn(player, label(m) + " est déjà occupé" + (female(m) ? "e" : "") + "."); break; }
                if (action != null && action.startsWith("craft:")) craft(player, m, action.substring(6));
                else if (clicked != null && clicked.getType() == Material.SHIELD && kind == Role.GUARD) {
                    data.set(follow, PersistentDataType.BOOLEAN, false);
                    Location at = player.getLocation();
                    data.set(post, PersistentDataType.STRING, at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ());
                    m.teleport(at);
                    Text.good(player, label(m) + " garde cet endroit.");
                } else if (clicked != null && clicked.getType() == Material.COMPASS && kind == Role.EXPLORER) {
                    data.set(state, PersistentDataType.STRING, "away");
                    data.set(until, PersistentDataType.LONG, plugin.clock().now() + EXPLORE_MS);
                    data.set(follow, PersistentDataType.BOOLEAN, false);
                    m.setInvisible(true);
                    m.setInvulnerable(true);
                    m.setCustomNameVisible(false);
                    Text.good(player, label(m) + " part explorer les environs. Retour dans 5 minutes.");
                    player.closeInventory();
                    return;
                }
            }
        }
        render(menu.inventory, m, player);
    }

    private void craft(Player player, Mannequin m, String id) {
        var recipe = plugin.settings().play().recipes().get(id);
        if (recipe == null) return;
        ItemStack[] items = storage(m);
        if (!has(items, recipe.ingredients())) {
            Text.warn(player, "Il manque des matériaux dans son sac pour : " + plugin.items().label(id.equals("ammo") ? "ammo" : id) + ".");
            return;
        }
        take(items, recipe.ingredients());
        setStorage(m, items);
        m.getPersistentDataContainer().set(job, PersistentDataType.STRING, "craft:" + id);
        m.getPersistentDataContainer().set(until, PersistentDataType.LONG, plugin.clock().now() + CRAFT_MS);
        m.getWorld().playSound(m.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1f);
        Text.good(player, label(m) + " se met au travail (" + CRAFT_MS / 1000 + " s).");
    }

    private void cure(Player player, Mannequin m) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (!plugin.items().is(item, ItemRegistry.ANTIDOTE)) continue;
            item.setAmount(item.getAmount() - 1);
            m.getPersistentDataContainer().set(state, PersistentDataType.STRING, "idle");
            m.getPersistentDataContainer().remove(until);
            dress(m);
            m.getWorld().playSound(m.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.8f, 1.2f);
            Text.good(player, label(m) + " est sauvé" + (female(m) ? "e" : "") + ".");
            remember(m);
            return;
        }
        Text.warn(player, "Il te faut un antidote NEXUS dans ton inventaire.");
    }

    private void save(Inventory inventory, Mannequin m) {
        ItemStack[] items = new ItemStack[STORAGE];
        for (int n = 0; n < STORAGE; n++) {
            ItemStack item = inventory.getItem(STORAGE + n);
            items[n] = item == null || item.isEmpty() ? null : item.clone();
        }
        setStorage(m, items);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu)) return;
        for (int slot : event.getRawSlots()) if (slot < STORAGE) { event.setCancelled(true); return; }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu)) return;
        viewers.remove(menu.survivor);
        if (plugin.getServer().getEntity(menu.survivor) instanceof Mannequin m && m.isValid()) save(menu.inventory, m);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent event) {
        if (!isSurvivor(event.getEntity())) return;
        Entity damager = event.getDamager() instanceof org.bukkit.entity.Projectile p && p.getShooter() instanceof Entity shooter ? shooter : event.getDamager();
        if (damager instanceof Player || isSurvivor(damager)) { event.setCancelled(true); return; }
        Mannequin m = (Mannequin) event.getEntity();
        String kind = plugin.mobs().archetypeOf(damager);
        var archetype = kind == null ? null : plugin.settings().archetype(kind);
        if (archetype == null || "infected".equals(m.getPersistentDataContainer().get(state, PersistentDataType.STRING))) return;
        if (random.nextDouble() < archetype.biteChance() * plugin.settings().infection().biteMultiplier()) {
            m.getPersistentDataContainer().set(state, PersistentDataType.STRING, "infected");
            m.getPersistentDataContainer().set(until, PersistentDataType.LONG, plugin.clock().now() + INCUBATION_MS);
            m.getPersistentDataContainer().remove(job);
            dress(m);
            tell(m, label(m) + " a été mordu" + (female(m) ? "e" : "") + " ! Donne-lui un antidote dans les 5 minutes.");
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!isSurvivor(event.getEntity())) return;
        Mannequin m = (Mannequin) event.getEntity();
        for (ItemStack item : storage(m)) if (item != null) event.getDrops().add(item);
        tell(m, label(m) + " est mort" + (female(m) ? "e" : "") + ".");
        plugin.events().add("survivor", label(m) + " est mort");
        registry.remove(m.getUniqueId().toString());
        persist();
    }

    public void stop() {
        if (walker != null) walker.cancel();
        walker = null;
        persist();
    }
}
