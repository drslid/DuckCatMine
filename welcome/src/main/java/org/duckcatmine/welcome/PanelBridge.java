package org.duckcatmine.welcome;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/** A deliberately small, console-only protocol for live player management. */
final class PanelBridge implements CommandExecutor {
    private static final String PREFIX = "DCPLAYER:";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender)) {
            sender.sendMessage("Cette commande est reservee a la console.");
            return true;
        }
        if (args.length < 2 || !args[1].matches("[A-Za-z0-9_]{3,16}")) return error(sender, "syntax");
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) return error(sender, "offline");
        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "status" -> { if (args.length != 2) return error(sender, "syntax"); sender.sendMessage(PREFIX + status(player)); }
                case "inventory" -> {
                    if (args.length != 4) return error(sender, "syntax");
                    int page = Integer.parseInt(args[3]);
                    sender.sendMessage(PREFIX + inventory(player, args[2], page));
                }
                case "set", "clear", "amount", "swap" -> {
                    if (!mutate(sender, player, args)) return true;
                    player.updateInventory();
                    sender.sendMessage(PREFIX + "{\"ok\":true}");
                }
                case "heal" -> {
                    if (args.length != 2) return error(sender, "syntax");
                    player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                    player.setFireTicks(0);
                    sender.sendMessage(PREFIX + "{\"ok\":true}");
                }
                case "feed" -> {
                    if (args.length != 2) return error(sender, "syntax");
                    player.setFoodLevel(20);
                    player.setSaturation(20f);
                    sender.sendMessage(PREFIX + "{\"ok\":true}");
                }
                case "xp" -> {
                    if (args.length != 3) return error(sender, "syntax");
                    int levels = Integer.parseInt(args[2]);
                    if (levels < -1000 || levels > 1000) return error(sender, "range");
                    player.giveExpLevels(levels);
                    sender.sendMessage(PREFIX + "{\"ok\":true}");
                }
                default -> { return error(sender, "syntax"); }
            }
        } catch (NumberFormatException e) { return error(sender, "syntax"); }
        catch (IllegalArgumentException e) { return error(sender, "invalid"); }
        return true;
    }

    private boolean error(CommandSender sender, String code) {
        sender.sendMessage(PREFIX + "{\"error\":\"" + code + "\"}");
        return true;
    }

    private boolean mutationError(CommandSender sender, String code) {
        error(sender, code);
        return false;
    }

    private String status(Player player) {
        var loc = player.getLocation();
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        return "{\"name\":" + q(player.getName()) + ",\"health\":" + player.getHealth()
                + ",\"maxHealth\":" + maxHealth + ",\"food\":" + player.getFoodLevel()
                + ",\"saturation\":" + player.getSaturation() + ",\"level\":" + player.getLevel()
                + ",\"xpProgress\":" + player.getExp() + ",\"gamemode\":" + q(player.getGameMode().name().toLowerCase(Locale.ROOT))
                + ",\"world\":" + q(loc.getWorld().getName()) + ",\"x\":" + Math.round(loc.getX())
                + ",\"y\":" + Math.round(loc.getY()) + ",\"z\":" + Math.round(loc.getZ())
                + ",\"selected\":" + player.getInventory().getHeldItemSlot() + ",\"op\":" + player.isOp() + "}";
    }

    private String inventory(Player player, String section, int page) {
        int total = switch (section) {
            case "main" -> 36;
            case "ender" -> 27;
            case "equipment" -> 5;
            default -> throw new IllegalArgumentException("section");
        };
        if (page < 0 || page >= (total + 8) / 9) throw new IllegalArgumentException("page");
        StringBuilder result = new StringBuilder("{\"section\":").append(q(section))
                .append(",\"page\":").append(page).append(",\"items\":[");
        for (int index = page * 9; index < Math.min(total, (page + 1) * 9); index++) {
            if (index > page * 9) result.append(',');
            String slot = section.equals("equipment") ? new String[]{"head", "chest", "legs", "feet", "offhand"}[index] : Integer.toString(index);
            ItemStack item = get(player, section + ":" + slot);
            result.append("{\"slot\":").append(q(section + ":" + slot))
                    .append(",\"rev\":").append(q(rev(item)));
            if (!empty(item)) {
                result.append(",\"id\":").append(q(item.getType().getKey().toString()))
                        .append(",\"amount\":").append(item.getAmount());
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName()) result.append(",\"name\":").append(q(meta.getDisplayName()));
                if (meta != null && meta.hasEnchants()) result.append(",\"enchanted\":true");
                if (meta instanceof Damageable damageable && damageable.hasDamage()) result.append(",\"damage\":").append(damageable.getDamage());
            }
            result.append('}');
        }
        return result.append("]}").toString();
    }

    private boolean mutate(CommandSender sender, Player player, String[] args) {
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("swap")) {
            if (args.length != 6) return mutationError(sender, "syntax");
            ItemStack first = get(player, args[2]), second = get(player, args[4]);
            if (!rev(first).equals(args[3]) || !rev(second).equals(args[5])) return mutationError(sender, "changed");
            set(player, args[2], second == null ? null : second.clone());
            set(player, args[4], first == null ? null : first.clone());
            return true;
        }
        if (args.length < 4) return mutationError(sender, "syntax");
        ItemStack current = get(player, args[2]);
        if (!rev(current).equals(args[3])) return mutationError(sender, "changed");
        switch (action) {
            case "clear" -> { if (args.length != 4) return mutationError(sender, "syntax"); set(player, args[2], null); }
            case "amount" -> {
                if (args.length != 5 || empty(current)) return mutationError(sender, "syntax");
                int count = Integer.parseInt(args[4]);
                if (count < 1 || count > current.getMaxStackSize()) return mutationError(sender, "range");
                ItemStack updated = current.clone();
                updated.setAmount(count);
                set(player, args[2], updated);
            }
            case "set" -> {
                if (args.length != 6 || !args[4].matches("minecraft:[a-z0-9_]+")) return mutationError(sender, "syntax");
                Material material = Material.matchMaterial(args[4].substring("minecraft:".length()));
                if (material == null || !material.isItem() || material.isAir()) return mutationError(sender, "invalid");
                int count = Integer.parseInt(args[5]);
                if (count < 1 || count > material.getMaxStackSize()) return mutationError(sender, "range");
                set(player, args[2], new ItemStack(material, count));
            }
            default -> { return mutationError(sender, "syntax"); }
        }
        return true;
    }

    private ItemStack get(Player player, String key) {
        String[] parts = key.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("slot");
        PlayerInventory inventory = player.getInventory();
        return switch (parts[0]) {
            case "main" -> inventory.getItem(index(parts[1], 36));
            case "ender" -> player.getEnderChest().getItem(index(parts[1], 27));
            case "equipment" -> switch (parts[1]) {
                case "head" -> inventory.getHelmet();
                case "chest" -> inventory.getChestplate();
                case "legs" -> inventory.getLeggings();
                case "feet" -> inventory.getBoots();
                case "offhand" -> inventory.getItemInOffHand();
                default -> throw new IllegalArgumentException("slot");
            };
            default -> throw new IllegalArgumentException("section");
        };
    }

    private void set(Player player, String key, ItemStack item) {
        String[] parts = key.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("slot");
        PlayerInventory inventory = player.getInventory();
        switch (parts[0]) {
            case "main" -> inventory.setItem(index(parts[1], 36), item);
            case "ender" -> player.getEnderChest().setItem(index(parts[1], 27), item);
            case "equipment" -> {
                switch (parts[1]) {
                    case "head" -> inventory.setHelmet(item);
                    case "chest" -> inventory.setChestplate(item);
                    case "legs" -> inventory.setLeggings(item);
                    case "feet" -> inventory.setBoots(item);
                    case "offhand" -> inventory.setItemInOffHand(item);
                    default -> throw new IllegalArgumentException("slot");
                }
            }
            default -> throw new IllegalArgumentException("section");
        }
    }

    private int index(String value, int size) {
        if (!value.matches("[0-9]{1,2}")) throw new IllegalArgumentException("slot");
        int index = Integer.parseInt(value);
        if (index >= size) throw new IllegalArgumentException("slot");
        return index;
    }

    private boolean empty(ItemStack item) { return item == null || item.getType().isAir() || item.getAmount() < 1; }
    private String rev(ItemStack item) { return empty(item) ? "0" : Integer.toUnsignedString(item.hashCode(), 16); }

    private String q(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') escaped.append('\\').append(c);
            else if (c < 32) escaped.append(String.format("\\u%04x", (int) c));
            else escaped.append(c);
        }
        return escaped.append('"').toString();
    }
}
