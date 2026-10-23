package org.duckcatmine.apocalypse.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.core.Durations;
import org.duckcatmine.apocalypse.persistence.Store;
import org.duckcatmine.apocalypse.scenario.MapRenderer;

public final class ApocalypseCommand implements TabExecutor {
    private static final String PLAY = "duckcat.apocalypse.play";
    private static final String ADMIN = "duckcat.apocalypse.admin";

    private final DuckCatApocalypse plugin;
    private final MapRenderer renderer;

    public ApocalypseCommand(DuckCatApocalypse plugin) {
        this.plugin = plugin;
        this.renderer = new MapRenderer(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            if (!sender.hasPermission(ADMIN)) Text.error(sender, "Réservé aux administrateurs.");
            else admin(sender, args);
            return true;
        }
        if (sub.equals("help")) {
            help(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.error(sender, "Commande réservée aux joueurs. Console : /apocalypse admin …");
            return true;
        }
        if (!player.hasPermission(PLAY)) {
            Text.error(player, "Tu n'as pas accès au scénario.");
            return true;
        }
        switch (sub) {
            case "join" -> {
                if (args.length > 1 && !args[1].equalsIgnoreCase(plugin.settings().scenario().id())) Text.error(player, "Scénario inconnu : " + args[1] + ".");
                else plugin.sessions().join(player);
            }
            case "leave" -> plugin.sessions().leave(player, false, true);
            case "status" -> status(player);
            case "infection" -> infection(player);
            case "bag" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("open")) plugin.bags().openNearest(player);
                else bags(player);
            }
            default -> help(player);
        }
        return true;
    }

    private void help(CommandSender sender) {
        Text.info(sender, "/apocalypse join ground_zero — entrer (ton inventaire habituel est mis de côté)");
        Text.info(sender, "/apocalypse leave — quitter depuis le refuge (impossible avec une morsure)");
        Text.info(sender, "/apocalypse status | infection | bag [open]");
        if (sender.hasPermission(ADMIN))
            Text.info(sender, "/apocalypse admin status | enable | disable | reload | selftest | map [rayon] | world create | world bind <monde> confirm | give <joueur> <objet> [n]");
    }

    private void status(Player player) {
        if (!plugin.sessions().isSurvivor(player)) {
            Text.info(player, "Tu n'es pas dans Ground Zero. " + (plugin.scenario().isEnabled() ? "Entre avec /apocalypse join." : "Le scénario est fermé."));
            return;
        }
        Text.info(player, "Vie en cours : " + plugin.sessions().runOf(player).substring(0, 8) + ".");
        infection(player);
        bags(player);
    }

    private void infection(Player player) {
        Long remaining = plugin.infections().remaining(player.getUniqueId());
        if (remaining == null) Text.good(player, "Aucune infection.");
        else Text.error(player, "MORSURE — antidote à terminer dans " + Durations.clock(remaining) + ".");
    }

    private void bags(Player player) {
        List<String> lines = plugin.bags().describe(player.getUniqueId());
        if (lines.isEmpty()) Text.info(player, "Aucun sac en attente.");
        for (String line : lines) Text.warn(player, "Sac : " + line + " (aucune téléportation ; /apocalypse bag open à portée).");
    }

    private void admin(CommandSender sender, String[] args) {
        String action = args.length < 2 ? "status" : args[1].toLowerCase(Locale.ROOT);
        String actor = sender instanceof Player player ? player.getName() : "console";
        switch (action) {
            case "status" -> adminStatus(sender);
            case "enable", "disable" -> {
                String error = plugin.scenario().setEnabled(action.equals("enable"), actor);
                if (error != null) Text.error(sender, error);
                else Text.good(sender, action.equals("enable") ? "Ground Zero est ouvert." : "Ground Zero est suspendu : états conservés, horloges figées.");
            }
            case "reload" -> {
                List<String> errors = plugin.reloadSettings();
                if (errors.isEmpty()) Text.good(sender, "Configuration rechargée. Les échéances en cours ne changent pas.");
                else {
                    Text.error(sender, "Configuration refusée, la précédente reste active :");
                    errors.forEach(error -> Text.error(sender, "• " + error));
                }
            }
            case "world" -> world(sender, args);
            case "give" -> give(sender, args, actor);
            case "map" -> {
                int radius;
                Integer slice = null;
                try {
                    radius = args.length > 2 ? Integer.parseInt(args[2]) : 320;
                    if (args.length > 3) slice = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    radius = -1;
                }
                if (radius < 32 || radius > 1536) {
                    Text.error(sender, "Rayon entre 32 et 1536 blocs.");
                    return;
                }
                String error = renderer.start(radius, slice, line -> {
                    plugin.getLogger().info(line);
                    if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) Text.info(sender, line);
                });
                if (error != null) Text.error(sender, error);
            }
            case "selftest" -> {
                boolean failed = false;
                for (String line : SelfTest.run(plugin)) {
                    if (line.startsWith("ÉCHEC")) {
                        failed = true;
                        Text.error(sender, line);
                    } else {
                        Text.info(sender, line);
                    }
                }
                if (failed) Text.error(sender, "Autotest en échec : envoie ces lignes et les journaux du serveur.");
                else Text.good(sender, "Autotest réussi.");
            }
            default -> help(sender);
        }
    }

    private void adminStatus(CommandSender sender) {
        var scenario = plugin.scenario();
        Text.info(sender, "Monde : " + (scenario.world() == null ? "aucun" : scenario.world().getName())
                + " | ouvert : " + (scenario.isEnabled() ? "oui" : "non")
                + " | base : " + (plugin.database().healthy() ? "OK" : "EN ERREUR"));
        if (scenario.problem() != null) Text.warn(sender, scenario.problem());
        if (plugin.configProblem() != null) Text.warn(sender, plugin.configProblem());
        long players = scenario.world() == null ? 0 : scenario.world().getPlayers().stream().filter(plugin.sessions()::isSurvivor).count();
        Map<String, Integer> mobs = plugin.mobs().countByArchetype();
        Text.info(sender, "Survivants présents : " + players + " | infectés : " + mobs + " | infections actives : "
                + plugin.infections().count() + " | sacs actifs : " + plugin.bags().count()
                + " | horloge : " + plugin.clock().now() / 1000 + " s" + (plugin.clock().running() ? "" : " (figée)"));
        int held = plugin.database().call(c -> Store.countProfiles(c, "CLASSIC"));
        Text.info(sender, "Inventaires classiques gardés par le scénario : " + held
                + (held == 0 ? " — désinstallation sans risque." : " — garder le plugin installé (même désactivé) jusqu'à 0."));
    }

    private void world(CommandSender sender, String[] args) {
        String mode = args.length < 3 ? "" : args[2].toLowerCase(Locale.ROOT);
        if (mode.equals("create")) {
            String error = plugin.scenario().create();
            if (error != null) Text.error(sender, error);
            else Text.good(sender, "Monde Ground Zero créé avec son refuge. Ouvre-le avec /apocalypse admin enable.");
        } else if (mode.equals("bind") && args.length == 5 && args[4].equals("confirm")) {
            String error = plugin.scenario().bindExisting(args[3]);
            if (error != null) Text.error(sender, error);
            else Text.good(sender, "Monde " + args[3] + " associé. Vérifie la position du refuge dans config.yml avant d'ouvrir.");
        } else {
            Text.info(sender, "/apocalypse admin world create — crée un monde neuf dédié (aucun monde existant n'est touché)");
            Text.info(sender, "/apocalypse admin world bind <monde> confirm — associe un monde déjà chargé (règles de jeu modifiées)");
        }
    }

    private void give(CommandSender sender, String[] args, String actor) {
        if (args.length < 4) {
            Text.info(sender, "/apocalypse admin give <joueur> <" + String.join("|", plugin.items().ids()) + "> [quantité]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null || !plugin.sessions().isSurvivor(target)) {
            Text.error(sender, "Le joueur doit être connecté et dans Ground Zero.");
            return;
        }
        int amount;
        try {
            amount = args.length > 4 ? Integer.parseInt(args[4]) : 1;
        } catch (NumberFormatException e) {
            amount = -1;
        }
        ItemStack stack = amount < 1 || amount > 64 ? null : plugin.items().create(args[3].toLowerCase(Locale.ROOT), amount);
        if (stack == null) {
            Text.error(sender, "Objet ou quantité invalide.");
            return;
        }
        target.getInventory().addItem(stack).values().forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        long now = plugin.clock().now();
        String detail = target.getName() + " " + args[3] + " x" + stack.getAmount();
        plugin.database().submit(c -> {
            Store.audit(c, now, actor, "admin_give", detail);
            return null;
        });
        plugin.getLogger().info("Don administrateur (" + actor + ") : " + detail);
        Text.good(sender, "Donné : " + detail + " (tracé).");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("join", "leave", "status", "infection", "bag", "help"));
            if (sender.hasPermission(ADMIN)) options.add("admin");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            options.add(plugin.settings().scenario().id());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("bag")) {
            options.add("open");
        } else if (args[0].equalsIgnoreCase("admin") && sender.hasPermission(ADMIN)) {
            if (args.length == 2) options.addAll(List.of("status", "enable", "disable", "reload", "selftest", "world", "give", "map"));
            else if (args.length == 3 && args[1].equalsIgnoreCase("world")) options.addAll(List.of("create", "bind"));
            else if (args.length == 3 && args[1].equalsIgnoreCase("give")) Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            else if (args.length == 4 && args[1].equalsIgnoreCase("give")) options.addAll(plugin.items().ids());
            else if (args.length == 5 && args[1].equalsIgnoreCase("world")) options.add("confirm");
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
