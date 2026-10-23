package org.duckcatmine.apocalypse.death;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.core.Durations;
import org.duckcatmine.apocalypse.journal.OpState;
import org.duckcatmine.apocalypse.journal.OpType;
import org.duckcatmine.apocalypse.journal.PlayerOp;
import org.duckcatmine.apocalypse.persistence.PersistenceException;
import org.duckcatmine.apocalypse.persistence.Store;
import org.duckcatmine.apocalypse.session.SessionService;

/**
 * Death bags are logical containers stored in the database. Their in-world entities are disposable views,
 * recreated whenever the chunk is loaded; a withdrawal is journaled before the item reaches the player.
 */
public final class BagService implements Listener {
    private static final int MENU_SIZE = 54;

    static final class Bag {
        final String id;
        final UUID owner;
        final String ownerName;
        final String world;
        final double x, y, z;
        final long expiresAt;
        final BagModel model;
        final ItemStack[] items;
        UUID hitbox, icon, label;
        long shownSecond = -1;

        Bag(Store.BagRow row, BagModel model, ItemStack[] items) {
            this.id = row.id();
            this.owner = row.owner();
            this.ownerName = row.ownerName();
            this.world = row.world();
            this.x = row.x();
            this.y = row.y();
            this.z = row.z();
            this.expiresAt = row.expiresAt();
            this.model = model;
            this.items = items;
        }
    }

    private static final class BagMenu implements InventoryHolder {
        private final String bagId;
        private final int[] slots = new int[MENU_SIZE];
        private Inventory inventory;

        BagMenu(String bagId) {
            this.bagId = bagId;
            Arrays.fill(slots, -1);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final DuckCatApocalypse plugin;
    private final Map<String, Bag> bags = new LinkedHashMap<>();

    public BagService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public void load() {
        for (Store.BagRow row : plugin.database().call(Store::activeBags)) {
            List<Store.BagItemRow> rows = plugin.database().call(c -> Store.bagItems(c, row.id()));
            int size = rows.stream().mapToInt(Store.BagItemRow::slot).max().orElse(-1) + 1;
            SlotState[] states = new SlotState[size];
            Arrays.fill(states, SlotState.PURGED);
            ItemStack[] items = new ItemStack[size];
            for (Store.BagItemRow item : rows) {
                states[item.slot()] = item.state();
                try {
                    items[item.slot()] = ItemStack.deserializeBytes(item.item());
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.SEVERE, "Objet illisible dans le sac " + row.id() + " (emplacement " + item.slot() + ")", e);
                }
            }
            bags.put(row.id(), new Bag(row, new BagModel(states), items));
        }
    }

    void register(Store.BagRow row, List<ItemStack> items) {
        SlotState[] states = new SlotState[items.size()];
        Arrays.fill(states, SlotState.PRESENT);
        bags.put(row.id(), new Bag(row, new BagModel(states), items.toArray(ItemStack[]::new)));
    }

    public int count() {
        return bags.size();
    }

    public void tick() {
        long now = plugin.clock().now();
        World world = plugin.scenario().world();
        for (Bag bag : List.copyOf(bags.values())) {
            if (now >= bag.expiresAt) {
                if (plugin.clock().running()) expire(bag);
                continue;
            }
            if (world == null || !plugin.scenario().isEnabled() || !world.getName().equals(bag.world)) {
                hide(bag);
                continue;
            }
            syncVisual(bag, world, now);
        }
    }

    private void syncVisual(Bag bag, World world, long now) {
        if (!world.isChunkLoaded((int) Math.floor(bag.x) >> 4, (int) Math.floor(bag.z) >> 4)) {
            bag.hitbox = bag.icon = bag.label = null;
            return;
        }
        if (!alive(bag.hitbox) || !alive(bag.icon) || !alive(bag.label)) {
            hide(bag);
            Location at = new Location(world, bag.x, bag.y, bag.z);
            bag.hitbox = world.spawn(at, Interaction.class, hitbox -> {
                hitbox.setInteractionWidth(0.9f);
                hitbox.setInteractionHeight(0.9f);
                hitbox.setResponsive(true);
                tag(hitbox, bag);
            }).getUniqueId();
            bag.icon = world.spawn(at.clone().add(0, 0.45, 0), ItemDisplay.class, icon -> {
                icon.setItemStack(ItemStack.of(Material.BUNDLE));
                icon.setBillboard(Display.Billboard.VERTICAL);
                icon.setGlowing(true);
                icon.setGlowColorOverride(Color.fromRGB(0xB02020));
                tag(icon, bag);
            }).getUniqueId();
            bag.label = world.spawn(at.clone().add(0, 1.3, 0), TextDisplay.class, label -> {
                label.setBillboard(Display.Billboard.CENTER);
                tag(label, bag);
            }).getUniqueId();
            bag.shownSecond = -1;
        }
        long second = Math.ceilDiv(bag.expiresAt - now, 1000L);
        if (second != bag.shownSecond && Bukkit.getEntity(bag.label) instanceof TextDisplay label) {
            bag.shownSecond = second;
            label.text(Component.text("Sac de " + bag.ownerName, NamedTextColor.RED)
                    .append(Component.newline())
                    .append(Component.text(Durations.clock(bag.expiresAt - now), NamedTextColor.WHITE)));
        }
    }

    private void tag(Entity entity, Bag bag) {
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(plugin.keys().bag, PersistentDataType.STRING, bag.id);
    }

    private static boolean alive(UUID id) {
        if (id == null) return false;
        Entity entity = Bukkit.getEntity(id);
        return entity != null && entity.isValid();
    }

    private static void remove(UUID id) {
        if (id == null) return;
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) entity.remove();
    }

    private void hide(Bag bag) {
        remove(bag.hitbox);
        remove(bag.icon);
        remove(bag.label);
        bag.hitbox = bag.icon = bag.label = null;
    }

    public void hideAll() {
        for (Bag bag : bags.values()) hide(bag);
    }

    private void expire(Bag bag) {
        List<Integer> purged = bag.model.expire();
        hide(bag);
        closeViews(bag);
        bags.remove(bag.id);
        long now = plugin.clock().now();
        plugin.database().submit(c -> {
            Store.expireBag(c, bag.id);
            Store.audit(c, now, "system", "bag_expired", bag.id + " purged=" + purged.size());
            return null;
        });
        Player owner = plugin.getServer().getPlayer(bag.owner);
        if (owner != null && !purged.isEmpty())
            Text.warn(owner, "Ton sac a disparu : " + purged.size() + " objet(s) sont perdus définitivement.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        String bagId = event.getRightClicked().getPersistentDataContainer().get(plugin.keys().bag, PersistentDataType.STRING);
        if (bagId == null) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Bag bag = bags.get(bagId);
        if (bag != null) open(event.getPlayer(), bag);
    }

    /** Fallback when the in-world view is missing: the nearest own bag within normal reach. */
    public void openNearest(Player player) {
        Bag nearest = null;
        double best = Double.MAX_VALUE;
        for (Bag bag : bags.values()) {
            if (!bag.owner.equals(player.getUniqueId()) || !player.getWorld().getName().equals(bag.world)) continue;
            double distance = distanceSquared(player, bag);
            if (distance < best) {
                best = distance;
                nearest = bag;
            }
        }
        if (nearest == null || !withinReach(player, nearest)) {
            Text.error(player, "Aucun de tes sacs n'est à portée.");
            return;
        }
        open(player, nearest);
    }

    private void open(Player player, Bag bag) {
        if (!plugin.teams().canOpenBag(player, bag.owner)) {
            Text.error(player, "Ce sac appartient à " + bag.ownerName + ".");
            return;
        }
        if (!plugin.sessions().isSurvivor(player) || player.isDead()) return;
        if (!withinReach(player, bag)) {
            Text.error(player, "Approche-toi du sac.");
            return;
        }
        long now = plugin.clock().now();
        if (now >= bag.expiresAt) {
            expire(bag);
            return;
        }
        BagMenu menu = new BagMenu(bag.id);
        menu.inventory = Bukkit.createInventory(menu, MENU_SIZE,
                Component.text((bag.owner.equals(player.getUniqueId()) ? "Ton sac" : "Sac de " + bag.ownerName) + " — "
                        + Durations.clock(bag.expiresAt - now), NamedTextColor.DARK_RED));
        render(menu, bag);
        player.openInventory(menu.inventory);
    }

    private static void render(BagMenu menu, Bag bag) {
        menu.inventory.clear();
        Arrays.fill(menu.slots, -1);
        int position = 0;
        for (int slot = 0; slot < bag.model.size() && position < MENU_SIZE; slot++) {
            if (bag.model.state(slot) != SlotState.PRESENT || bag.items[slot] == null) continue;
            menu.inventory.setItem(position, bag.items[slot].clone());
            menu.slots[position++] = slot;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BagMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= MENU_SIZE || menu.slots[raw] < 0) return;
        Bag bag = bags.get(menu.bagId);
        if (bag == null) {
            player.closeInventory();
            return;
        }
        take(player, bag, menu.slots[raw]);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BagMenu) event.setCancelled(true);
    }

    private void take(Player player, Bag bag, int slot) {
        long now = plugin.clock().now();
        if (now >= bag.expiresAt) {
            expire(bag);
            return;
        }
        SessionService sessions = plugin.sessions();
        if (!plugin.teams().canOpenBag(player, bag.owner) || !sessions.isSurvivor(player) || !withinReach(player, bag)) {
            player.closeInventory();
            return;
        }
        ItemStack item = bag.items[slot];
        if (bag.model.state(slot) != SlotState.PRESENT || item == null) {
            refreshViews(bag);
            return;
        }
        if (!fits(player.getInventory(), item)) {
            Text.warn(player, "Inventaire plein : libère de la place pour reprendre cet objet.");
            return;
        }
        if (!plugin.database().healthy()) {
            Text.error(player, "Sauvegarde indisponible : récupération suspendue pour éviter toute perte.");
            return;
        }
        UUID id = player.getUniqueId();
        long applied = sessions.appliedSeq(player);
        String name = player.getName();
        long seq;
        try {
            seq = plugin.database().call(c -> {
                Store.ensurePlayer(c, id, name, applied);
                long next = Store.nextSeq(c, id);
                Store.insertOp(c, id, next, OpType.BAG_TAKE, bag.id + ":" + slot, now);
                if (!Store.moveBagItem(c, bag.id, slot, SlotState.PRESENT, SlotState.MOVING, next))
                    throw new SQLException("Objet déjà repris : sac " + bag.id + " emplacement " + slot);
                return next;
            });
        } catch (PersistenceException e) {
            plugin.getLogger().log(Level.SEVERE, "Récupération refusée pour " + name, e);
            Text.error(player, "Récupération impossible pour le moment.");
            return;
        }
        bag.model.beginTake(slot);
        for (ItemStack leftover : player.getInventory().addItem(item.clone()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        sessions.markApplied(player, SessionService.Profile.APOCALYPSE, sessions.runOf(player), seq);
        bag.model.confirmTake(slot);
        plugin.database().submit(c -> {
            Store.moveBagItem(c, bag.id, slot, SlotState.MOVING, SlotState.TAKEN, seq);
            Store.setOpState(c, id, seq, OpState.DONE);
            Store.settleBag(c, bag.id);
            return null;
        });
        if (bag.model.settled()) {
            hide(bag);
            closeViews(bag);
            bags.remove(bag.id);
            Text.good(player, "Sac vidé.");
        } else {
            refreshViews(bag);
        }
    }

    /** Resolves a withdrawal interrupted by an incident, once its player is seen again. */
    public void resolveTake(PlayerOp op, boolean confirmed) {
        String[] parts = op.payload().split(":");
        String bagId = parts[0];
        int slot = Integer.parseInt(parts[1]);
        boolean expired = plugin.database().call(c -> {
            Store.BagRow row = Store.bag(c, bagId);
            boolean gone = row == null || "EXPIRED".equals(row.state());
            if (confirmed) Store.moveBagItem(c, bagId, slot, SlotState.MOVING, SlotState.TAKEN, op.seq());
            else Store.moveBagItem(c, bagId, slot, SlotState.MOVING, gone ? SlotState.PURGED : SlotState.PRESENT, null);
            Store.setOpState(c, op.player(), op.seq(), confirmed ? OpState.DONE : OpState.ROLLED_BACK);
            Store.settleBag(c, bagId);
            return gone;
        });
        Bag bag = bags.get(bagId);
        if (bag == null || slot >= bag.model.size()) return;
        if (confirmed) bag.model.confirmTake(slot);
        else bag.model.cancelTake(slot, expired);
        if (bag.model.settled()) {
            hide(bag);
            bags.remove(bagId);
        }
    }

    static boolean fits(PlayerInventory inventory, ItemStack item) {
        int remaining = item.getAmount();
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.isEmpty()) return true;
            if (stack.isSimilar(item)) remaining -= Math.max(0, stack.getMaxStackSize() - stack.getAmount());
            if (remaining <= 0) return true;
        }
        return false;
    }

    private boolean withinReach(Player player, Bag bag) {
        double radius = plugin.settings().death().bagAccessRadius();
        return player.getWorld().getName().equals(bag.world) && distanceSquared(player, bag) <= radius * radius;
    }

    private static double distanceSquared(Player player, Bag bag) {
        Location location = player.getLocation();
        double dx = location.getX() - bag.x, dy = location.getY() - bag.y, dz = location.getZ() - bag.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void closeViews(Bag bag) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BagMenu menu && menu.bagId.equals(bag.id))
                player.closeInventory();
        }
    }

    private void refreshViews(Bag bag) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BagMenu menu && menu.bagId.equals(bag.id))
                render(menu, bag);
        }
    }

    public List<String> describe(UUID owner) {
        long now = plugin.clock().now();
        List<String> lines = new ArrayList<>();
        for (Bag bag : bags.values()) {
            if (!bag.owner.equals(owner)) continue;
            lines.add(Text.position(bag.x, bag.y, bag.z) + " — " + Durations.clock(bag.expiresAt - now));
        }
        return lines;
    }

    public void remind(Player player) {
        for (String line : describe(player.getUniqueId())) Text.warn(player, "Ton sac t'attend : " + line + ".");
    }
}
