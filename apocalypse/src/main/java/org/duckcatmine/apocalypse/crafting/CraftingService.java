package org.duckcatmine.apocalypse.crafting;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.items.ItemRegistry;

/**
 * Scenario crafting (spec §12): a recipe menu on the workbench, the mechanical workshop and the medical post, plus a
 * read-only catalogue. Ingredients are authenticated scenario items or plain vanilla items; they are consumed only once
 * the whole recipe is available, and the result goes to the inventory or falls at the player's feet when it is full.
 */
public final class CraftingService implements Listener {
    public enum Station {
        WORKBENCH("workbench", "Établi"), WORKSHOP("workshop", "Atelier mécanique"), MEDICAL("medical", "Poste médical"),
        CATALOG("", "Catalogue des recettes");

        private final String id;
        private final String label;

        Station(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String label() {
            return label;
        }

        static String labelOf(String id) {
            for (Station station : values()) if (station.id.equals(id)) return station.label;
            return id;
        }
    }

    private static final int VANILLA_SLOT = 45;
    private static final int REPAIR_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    private static final class Menu implements InventoryHolder {
        final Station station;
        final List<ApocalypseConfig.Recipe> recipes = new ArrayList<>();
        Inventory inventory;

        Menu(Station station) {
            this.station = station;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final DuckCatApocalypse plugin;

    public CraftingService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        if (!plugin.sessions().isSurvivor(player) || player.isSneaking() && event.getItem() != null) return;
        Block block = event.getClickedBlock();
        Station station = null;
        if (block.getType() == Material.CRAFTING_TABLE) station = Station.WORKBENCH;
        else if (plugin.placed().isStation(block, ItemRegistry.WORKSHOP)) station = Station.WORKSHOP;
        else if (plugin.placed().isStation(block, ItemRegistry.MEDICAL_STATION)) station = Station.MEDICAL;
        if (station == null) return;
        event.setCancelled(true);
        open(player, station, null);
    }

    /** Opens a station menu, or the catalogue (all recipes, optionally filtered by result). */
    public void open(Player player, Station station, String filter) {
        Menu menu = new Menu(station);
        for (ApocalypseConfig.Recipe recipe : plugin.settings().play().recipes().values()) {
            if (station != Station.CATALOG && !recipe.station().equals(station.id)) continue;
            if (filter != null && !recipe.id().contains(filter.toLowerCase(Locale.ROOT)) && !recipe.result().item().contains(filter.toLowerCase(Locale.ROOT)))
                continue;
            if (menu.recipes.size() < 45) menu.recipes.add(recipe);
        }
        menu.inventory = plugin.getServer().createInventory(menu, 54, Component.text(station.label()));
        render(player, menu);
        player.openInventory(menu.inventory);
    }

    private void render(Player player, Menu menu) {
        Inventory inventory = menu.inventory;
        inventory.clear();
        for (int slot = 0; slot < menu.recipes.size(); slot++) inventory.setItem(slot, icon(player, menu.recipes.get(slot), menu.station));
        if (menu.station == Station.WORKBENCH)
            inventory.setItem(VANILLA_SLOT, button(Material.CRAFTING_TABLE, "Établi classique", "Recettes Minecraft habituelles."));
        if (menu.station == Station.WORKSHOP) {
            List<String> lines = new ArrayList<>(List.of("Répare l'arme tenue en main de "
                    + plugin.settings().play().repair().restorePercent() + " % de son usure."));
            lines.add("Arme de mêlée : " + describe(plugin.settings().play().repair().melee()));
            lines.add("Pistolet : " + describe(plugin.settings().play().repair().pistol()));
            inventory.setItem(REPAIR_SLOT, button(Material.ANVIL, "Réparer l'arme en main", lines.toArray(String[]::new)));
        }
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "Fermer"));
    }

    private ItemStack icon(Player player, ApocalypseConfig.Recipe recipe, Station station) {
        ItemStack icon = plugin.items().resolve(recipe.result().item(), recipe.result().amount());
        if (icon == null) icon = ItemStack.of(Material.BARRIER);
        List<Component> lore = new ArrayList<>();
        if (!recipe.usage().isEmpty()) lore.add(line(recipe.usage(), NamedTextColor.GRAY));
        lore.add(line("Ingrédients :", NamedTextColor.WHITE));
        boolean ready = true;
        for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
            int have = count(player, ingredient.getKey());
            boolean enough = have >= ingredient.getValue();
            ready &= enough;
            lore.add(line((enough ? "✔ " : "✘ ") + ingredient.getValue() + " × " + name(ingredient.getKey()) + " (" + have + ")",
                    enough ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        lore.add(line("Poste : " + Station.labelOf(recipe.station()) + (recipe.noise().equals("none") ? "" : " — bruyant"), NamedTextColor.DARK_GRAY));
        if (station == Station.CATALOG) lore.add(line("Fabrique-le sur ce poste.", NamedTextColor.DARK_GRAY));
        else lore.add(line(ready ? "Clic : fabriquer — Maj+clic : jusqu'à 8" : "Il manque des ingrédients.", ready ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY));
        icon.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        return icon;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack button(Material material, String name, String... lines) {
        ItemStack stack = ItemStack.of(material);
        stack.setData(DataComponentTypes.ITEM_NAME, Component.text(name, NamedTextColor.YELLOW));
        List<Component> lore = new ArrayList<>();
        for (String text : lines) lore.add(line(text, NamedTextColor.GRAY));
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        return stack;
    }

    private String describe(Map<String, Integer> cost) {
        List<String> parts = new ArrayList<>();
        cost.forEach((item, amount) -> parts.add(amount + " × " + name(item)));
        return String.join(", ", parts);
    }

    /** Display name of an ingredient identifier. */
    private String name(String ingredient) {
        if (ingredient.equals("#planks")) return "planches";
        if (ingredient.equals("#logs")) return "bûches";
        if (ingredient.equals("#wool")) return "laine";
        ItemStack stack = plugin.items().resolve(ingredient, 1);
        if (stack == null) return ingredient;
        Component name = stack.getData(DataComponentTypes.ITEM_NAME);
        return name != null ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name)
                : stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != menu.inventory) return;
        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        if (menu.station == Station.CATALOG) return;
        if (!plugin.sessions().isSurvivor(player)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        if (slot == VANILLA_SLOT && menu.station == Station.WORKBENCH) {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.openWorkbench(null, true));
            return;
        }
        if (slot == REPAIR_SLOT && menu.station == Station.WORKSHOP) {
            repair(player);
            render(player, menu);
            return;
        }
        if (slot < 0 || slot >= menu.recipes.size()) return;
        craft(player, menu.recipes.get(slot), event.isShiftClick() ? 8 : 1);
        render(player, menu);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
    }

    /** Crafts up to {@code times} times; each craft consumes every ingredient first, then gives the result once. */
    public int craft(Player player, ApocalypseConfig.Recipe recipe, int times) {
        int made = 0;
        for (int k = 0; k < times; k++) {
            for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
                if (count(player, ingredient.getKey()) < ingredient.getValue()) {
                    if (made == 0) Text.warn(player, "Il manque : " + ingredient.getValue() + " × " + name(ingredient.getKey()) + ".");
                    return made;
                }
            }
            ItemStack result = plugin.items().resolve(recipe.result().item(), recipe.result().amount());
            if (result == null) return made;
            for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) consume(player, ingredient.getKey(), ingredient.getValue());
            player.getInventory().addItem(result).values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            made++;
        }
        if (made > 0) {
            player.playSound(player.getLocation(), recipe.station().equals("workshop") ? Sound.BLOCK_ANVIL_USE : Sound.UI_STONECUTTER_TAKE_RESULT, 0.8f, 1.1f);
            double loudness = switch (recipe.noise()) {
                case "low" -> 0.5;
                case "medium" -> 1.0;
                case "high" -> 2.0;
                default -> 0;
            };
            plugin.noise().emit(player.getLocation(), plugin.settings().play().noise().crafting() * loudness, player);
            Text.good(player, "Fabriqué : " + made * recipe.result().amount() + " × " + name(recipe.result().item()) + ".");
        }
        return made;
    }

    /** Repairs the weapon held in the main hand at the workshop, for its configured components. */
    public boolean repair(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        String id = plugin.items().idOf(held);
        boolean pistol = ItemRegistry.PISTOL.equals(id);
        if (id == null || !(pistol || ItemRegistry.isMelee(id)) || !held.hasData(DataComponentTypes.MAX_DAMAGE)) {
            Text.warn(player, "Tiens en main une arme de mêlée ou le pistolet à réparer.");
            return false;
        }
        Integer damage = held.getData(DataComponentTypes.DAMAGE);
        if (damage == null || damage == 0) {
            Text.info(player, "Cette arme est en parfait état.");
            return false;
        }
        Map<String, Integer> cost = pistol ? plugin.settings().play().repair().pistol() : plugin.settings().play().repair().melee();
        for (Map.Entry<String, Integer> part : cost.entrySet()) {
            if (count(player, part.getKey()) < part.getValue()) {
                Text.warn(player, "Réparation : il faut " + describe(cost) + ".");
                return false;
            }
        }
        cost.forEach((item, amount) -> consume(player, item, amount));
        int max = held.getData(DataComponentTypes.MAX_DAMAGE);
        int repaired = Math.max(0, damage - max * plugin.settings().play().repair().restorePercent() / 100);
        held.setData(DataComponentTypes.DAMAGE, repaired);
        player.getInventory().setItemInMainHand(held);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.3f);
        plugin.noise().emit(player.getLocation(), plugin.settings().play().noise().crafting() * 0.5, player);
        Text.good(player, "Arme réparée : usure " + (max - repaired) + "/" + max + ".");
        return true;
    }

    public int count(Player player, String ingredient) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) if (matches(stack, ingredient)) total += stack.getAmount();
        return total;
    }

    private void consume(Player player, String ingredient, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && amount > 0; slot++) {
            ItemStack stack = contents[slot];
            if (!matches(stack, ingredient)) continue;
            int taken = Math.min(amount, stack.getAmount());
            stack.setAmount(stack.getAmount() - taken);
            amount -= taken;
            contents[slot] = stack.getAmount() <= 0 ? null : stack;
        }
        player.getInventory().setStorageContents(contents);
    }

    private boolean matches(ItemStack stack, String ingredient) {
        if (stack == null || stack.isEmpty()) return false;
        String id = plugin.items().idOf(stack);
        if (ingredient.startsWith(ItemRegistry.NAMESPACE + ":")) return ingredient.substring(ItemRegistry.NAMESPACE.length() + 1).equals(id);
        if (id != null) return false;
        return switch (ingredient) {
            case "#planks" -> Tag.PLANKS.isTagged(stack.getType());
            case "#logs" -> Tag.LOGS.isTagged(stack.getType());
            case "#wool" -> Tag.WOOL.isTagged(stack.getType());
            default -> stack.getType() == Material.matchMaterial(ingredient);
        };
    }
}
