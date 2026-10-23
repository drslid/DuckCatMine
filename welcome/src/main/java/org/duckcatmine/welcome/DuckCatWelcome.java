package org.duckcatmine.welcome;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Rotatable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class DuckCatWelcome extends JavaPlugin implements Listener {
    private static final String PREFIX = ChatColor.GOLD + "✦ DuckCatMine " + ChatColor.DARK_GRAY + "» " + ChatColor.RESET;
    private YamlConfiguration data;
    private File dataFile;
    private final Set<UUID> helpers = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        for (String id : data.getStringList("helpers")) {
            try { helpers.add(UUID.fromString(id)); } catch (IllegalArgumentException ignored) { }
        }
        if (!data.contains("warps.construire")) {
            Location start = configuredLocation("starting-warp");
            if (start == null) start = Bukkit.getWorlds().getFirst().getSpawnLocation().clone().add(0, 0, 40);
            if (start != null) {
                // The saved destination is placed on the actual surface, including grass and flowers.
                int y = start.getWorld().getHighestBlockYAt(start.getBlockX(), start.getBlockZ()) + 1;
                start.setY(y);
                data.set("warps.construire", start);
                saveData();
            }
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("dcpanel").setExecutor(new PanelBridge());

        getLogger().info("Guide et commandes d'accueil charges.");
    }

    private void saveData() {
        try { data.save(dataFile); } catch (IOException e) { getLogger().severe("Impossible d'enregistrer data.yml: " + e.getMessage()); }
    }

    private Location configuredLocation(String path) {
        ConfigurationSection section = getConfig().getConfigurationSection(path);
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) return null;
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    private Location spawn() {
        Location configured = configuredLocation("spawn");
        return configured != null ? configured : Bukkit.getWorlds().getFirst().getSpawnLocation();
    }

    private void teleport(Player player, Location location, String destination) {
        if (location == null || location.getWorld() == null) { player.sendMessage(PREFIX + ChatColor.RED + "Destination indisponible."); return; }
        player.teleportAsync(location).thenAccept(success -> Bukkit.getScheduler().runTask(this, () -> {
            if (player.isOnline()) player.sendMessage(PREFIX + (success ? ChatColor.GREEN + "Arrivée : " + destination : ChatColor.RED + "Téléportation impossible."));
        }));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) return;
            player.sendTitle(ChatColor.GOLD + "DuckCatMine", ChatColor.YELLOW + "Ton aventure commence ici", 10, 65, 20);
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Bienvenue " + player.getName() + " ! " + ChatColor.WHITE + "Ouvre " + ChatColor.AQUA + "/guide" + ChatColor.WHITE + " pour découvrir le serveur.");
            if (PanelBridge.scenario(player) != null) return;
            player.sendMessage(ChatColor.GRAY + "Maison : /sethome · /home   |   Départ : /spawn · /construire   |   Aide : /aides");
            if (getConfig().getBoolean("open-menu-on-join", true)) openGuide(player);
        }, 40L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Commande réservée aux joueurs."); return true; }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "guide" -> openGuide(player);
            case "nouveautes" -> showNews(player);
            case "spawn" -> teleport(player, spawn(), "Spawn");
            case "sethome" -> {
                data.set("homes." + player.getUniqueId(), player.getLocation());
                saveData();
                player.sendMessage(PREFIX + ChatColor.GREEN + "Maison enregistrée. Retrouve-la avec /home.");
            }
            case "home" -> {
                Location home = data.getLocation("homes." + player.getUniqueId());
                if (home == null) player.sendMessage(PREFIX + ChatColor.YELLOW + "Tu n'as pas encore de maison. Utilise /sethome à l'endroit voulu.");
                else teleport(player, home, "Maison");
            }
            case "delhome" -> {
                data.set("homes." + player.getUniqueId(), null);
                saveData();
                player.sendMessage(PREFIX + ChatColor.YELLOW + "Maison supprimée.");
            }
            case "construire" -> goWarp(player, "construire");
            case "warp" -> {
                if (args.length == 0) {
                    ConfigurationSection warps = data.getConfigurationSection("warps");
                    String names = warps == null ? "aucun" : String.join(", ", warps.getKeys(false));
                    player.sendMessage(PREFIX + ChatColor.YELLOW + "Destinations : " + ChatColor.AQUA + names + ChatColor.GRAY + " — /warp <nom>");
                } else goWarp(player, args[0]);
            }
            case "setwarp" -> {
                if (!player.hasPermission("duckcat.warp.manage")) { noPermission(player); return true; }
                if (args.length != 1 || !args[0].matches("[A-Za-z0-9_-]{2,24}")) { player.sendMessage(PREFIX + "Usage : /setwarp <nom>"); return true; }
                String name = args[0].toLowerCase(Locale.ROOT);
                data.set("warps." + name, player.getLocation());
                saveData();
                player.sendMessage(PREFIX + ChatColor.GREEN + "Warp " + name + " enregistré.");
            }
            case "delwarp" -> {
                if (!player.hasPermission("duckcat.warp.manage")) { noPermission(player); return true; }
                if (args.length != 1) { player.sendMessage(PREFIX + "Usage : /delwarp <nom>"); return true; }
                String name = args[0].toLowerCase(Locale.ROOT);
                data.set("warps." + name, null);
                saveData();
                player.sendMessage(PREFIX + ChatColor.YELLOW + "Warp " + name + " supprimé.");
            }
            case "aider" -> {
                if (!helpers.add(player.getUniqueId())) {
                    helpers.remove(player.getUniqueId());
                    player.sendMessage(PREFIX + ChatColor.YELLOW + "Tu ne figures plus parmi les volontaires.");
                } else player.sendMessage(PREFIX + ChatColor.GREEN + "Merci ! Tu apparais maintenant dans /aides lorsque tu es en ligne.");
                data.set("helpers", helpers.stream().map(UUID::toString).sorted().toList());
                saveData();
            }
            case "aides" -> showHelpers(player);
            default -> { return false; }
        }
        return true;
    }

    private void noPermission(Player player) { player.sendMessage(PREFIX + ChatColor.RED + "Cette commande est réservée aux administrateurs."); }

    private void goWarp(Player player, String requested) {
        String name = requested.toLowerCase(Locale.ROOT);
        Location warp = data.getLocation("warps." + name);
        if (warp == null) player.sendMessage(PREFIX + ChatColor.RED + "Warp inconnu. Essaie /warp pour voir la liste.");
        else teleport(player, warp, name);
    }

    private void showNews(Player player) {
        player.sendMessage(ChatColor.GOLD + "✦ " + ChatColor.YELLOW + "NOUVEAUTÉS DU SERVEUR" + ChatColor.GOLD + " ✦");
        for (String line : getConfig().getStringList("news")) player.sendMessage(ChatColor.GRAY + "• " + ChatColor.WHITE + line);
        player.sendMessage(ChatColor.GRAY + "Retrouve tout dans " + ChatColor.AQUA + "/guide" + ChatColor.GRAY + ".");
    }

    private void showHelpers(Player player) {
        String names = Bukkit.getOnlinePlayers().stream().filter(p -> helpers.contains(p.getUniqueId()))
                .map(Player::getName).sorted().collect(Collectors.joining(", "));
        player.sendMessage(PREFIX + (names.isBlank()
                ? ChatColor.YELLOW + "Aucun volontaire en ligne pour le moment. /aider permet de se proposer."
                : ChatColor.GREEN + "Joueurs volontaires en ligne : " + ChatColor.WHITE + names + ChatColor.GRAY + ". Écris-leur dans le chat."));
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        List<String> lines = new ArrayList<>();
        for (String line : lore) lines.add(ChatColor.GRAY + line);
        meta.setLore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    private final class GuideHolder implements InventoryHolder {
        private final Inventory inventory = Bukkit.createInventory(this, 54, ChatColor.GOLD + "✦ DuckCatMine · Guide");
        @Override public Inventory getInventory() { return inventory; }
    }

    private void openGuide(Player player) {
        GuideHolder holder = new GuideHolder();
        Inventory gui = holder.getInventory();
        ItemStack frame = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) gui.setItem(i, frame);
        gui.setItem(4, item(Material.NETHER_STAR, "BIENVENUE SUR DUCKCATMINE", "Survie entre amis · 10 places", "Choisis une tuile pour agir."));
        gui.setItem(10, item(Material.COMPASS, "Revenir au spawn", "Téléporte vers la place d'accueil.", "Commande : /spawn"));
        gui.setItem(12, item(Material.RED_BED, "Ma maison", "Retourne au point enregistré.", "Commandes : /sethome · /home"));
        gui.setItem(14, item(Material.CRAFTING_TABLE, "Commencer à construire", "Zone de départ près du spawn.", "Commande : /construire"));
        gui.setItem(16, item(Material.ENDER_PEARL, "Voyager", "Liste les destinations du serveur.", "Commande : /warp"));
        gui.setItem(20, item(Material.OAK_SIGN, "Commandes utiles", "/spawn · /sethome · /home", "/construire · /warp · /aides"));
        gui.setItem(22, item(Material.WRITABLE_BOOK, "Quoi de neuf ?", "Les nouveautés en quelques lignes.", "Commande : /nouveautes"));
        gui.setItem(24, item(Material.PLAYER_HEAD, "Joueurs prêts à aider", "Affiche les volontaires connectés.", "Commande : /aides · /aider"));
        gui.setItem(30, item(Material.FILLED_MAP, "Carte et panneau web", "Ouvre le lien de la carte dans le chat.", "La carte du panneau d administration"));
        gui.setItem(32, item(Material.ARMOR_STAND, "Personnaliser son personnage", "Skins : galerie visuelle sur le panneau web.", "Autres fonctions après chargement des plugins."));
        gui.setItem(40, item(Material.LIME_DYE, "J'ai compris !", "Ferme ce guide.", "Tu peux le rouvrir avec /guide."));
        player.openInventory(gui);
    }

    @EventHandler
    public void onGuideClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuideHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (slot == 40) { player.closeInventory(); return; }
        if (slot == 20) {
            player.closeInventory();
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Commandes : " + ChatColor.AQUA + "/spawn, /sethome, /home, /delhome, /construire, /warp, /nouveautes, /aides, /aider, /guide");
            return;
        }
        if (slot == 22) { player.closeInventory(); showNews(player); return; }
        if (slot == 24) { player.closeInventory(); showHelpers(player); return; }
        if (slot == 30) {
            player.closeInventory();
            player.sendMessage(PREFIX + ChatColor.AQUA + "Carte : " + getConfig().getString("panel-url", "") + "/map/");
            player.sendMessage(ChatColor.GRAY + "Panneau : " + getConfig().getString("panel-url", ""));
            return;
        }
        if (slot == 32) { player.closeInventory(); player.sendMessage(PREFIX + "Galerie de skins : " + getConfig().getString("panel-url", "")); return; }
        if (slot == 12) {
            player.closeInventory();
            Location home = data.getLocation("homes." + player.getUniqueId());
            if (home == null) player.sendMessage(PREFIX + ChatColor.YELLOW + "Place-toi dans ta base puis tape /sethome.");
            else teleport(player, home, "Maison");
            return;
        }
        if (slot == 16) { player.closeInventory(); player.performCommand("warp"); return; }
        if (slot == 10 || slot == 14) {
            player.closeInventory();
            if (slot == 10) teleport(player, spawn(), "Spawn");
            else goWarp(player, "construire");
        }
    }

}
