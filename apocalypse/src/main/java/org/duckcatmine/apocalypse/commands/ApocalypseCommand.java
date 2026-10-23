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
            case "leave" -> plugin.sessions().requestLeave(player);
            case "guide" -> guide(player);
            case "status" -> status(player);
            case "infection" -> infection(player);
            case "bag" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("open")) plugin.bags().openNearest(player);
                else bags(player);
            }
            case "recipes", "recettes" -> plugin.crafting().open(player, org.duckcatmine.apocalypse.crafting.CraftingService.Station.CATALOG,
                    args.length > 1 ? args[1] : null);
            case "team", "equipe" -> plugin.teams().command(player, args);
            case "stats" -> plugin.progress().stats(player);
            case "skills", "competences" -> plugin.progress().skills(player, args);
            case "intro" -> {
                if (args.length > 1 && (args[1].equalsIgnoreCase("skip") || args[1].equalsIgnoreCase("passer"))) {
                    if (!plugin.intro().skip(player)) Text.info(player, "Aucune introduction en cours.");
                } else {
                    plugin.intro().replay(player);
                }
            }
            default -> help(player);
        }
        return true;
    }

    private void help(CommandSender sender) {
        Text.info(sender, "/apocalypse join ground_zero — entrer (ton inventaire habituel est mis de côté)");
        Text.info(sender, "/apocalypse leave — quitter : immédiat au refuge, sinon après quelques secondes immobile (jamais avec une morsure)");
        Text.info(sender, "/apocalypse guide — règles, antidotes et pharmacie la plus proche, voitures");
        Text.info(sender, "/apocalypse status | infection | bag [open] | recipes [objet] | stats | skills | intro [skip]");
        Text.info(sender, "/apocalypse team create <nom> | invite <joueur> | accept | decline | leave | kick <joueur> | bags on|off | disband");
        Text.info(sender, "Injecter un antidote à un autre survivant mordu : accroupi, clic droit sur lui, antidote en main (2 s).");
        if (sender.hasPermission(ADMIN)) {
            Text.info(sender, "/apocalypse admin status | enable | disable | reload | selftest | map [rayon] | threat [valeur] | destruction on|off | horde <joueur> | pvp on|off | world create | world bind <monde> confirm | give <joueur> <objet> [n]");
            Text.info(sender, "/apocalypse admin settings | set <réglage> <valeur> | preset relaxed|normal|nightmare | evacuate <joueur> | lives reset confirm");
        }
    }

    /** Rules in short, where antidotes are, with the direction of the nearest pharmacy and the hospital. */
    private void guide(Player player) {
        Text.info(player, "— Guide de survie de Ground Zero —");
        Text.warn(player, "Morsure : " + plugin.settings().infection().durationMs() / 1000 + " s pour finir un antidote (barre rouge en haut)."
                + " À la mort, tout ce que tu portes reste " + plugin.settings().death().bagLifetimeMs() / 60000 + " min dans ton sac.");
        Text.info(player, "Antidotes : pharmacies, hôpital, laboratoire NEXUS. Ou au poste médical : sérum + inhibiteur + seringue (hôpital, laboratoire).");
        var world = plugin.scenario().world();
        if (plugin.sessions().isSurvivor(player) && world != null && world.getGenerator() instanceof org.duckcatmine.apocalypse.scenario.GroundZeroGenerator) {
            var layout = org.duckcatmine.apocalypse.scenario.city.CityLayout.of(world.getSeed());
            int x = player.getLocation().getBlockX(), z = player.getLocation().getBlockZ();
            for (String kind : List.of("pharmacy", "hospital", "lab")) {
                var place = layout.nearest(kind, x, z);
                if (place != null) Text.info(player, place.label() + (kind.equals("pharmacy") ? " la plus proche" : "") + " : "
                        + direction(place.x() - x, place.z() - z) + " (x " + place.x() + ", z " + place.z() + ").");
            }
        }
        Text.info(player, "Voitures : clic droit sur une portière pour monter, touches de déplacement pour conduire, saut = frein à main,"
                + " sprint = klaxon, Maj = descendre. Le moteur attire les infectés. Essence : jerrican (siphonner accroupi, puis faire le plein).");
        Text.info(player, "Armes : /apocalypse recipes (massues, hache, arc silencieux, pistolet, bombe artisanale, cocktail Molotov).");
        Text.info(player, "Partir : /apocalypse leave — au refuge tout de suite, ailleurs après " + plugin.settings().play().leaveSeconds()
                + " s immobile sans combattre. Ta vie t'attend pour ton retour.");
    }

    private static String direction(int dx, int dz) {
        long distance = Math.round(Math.hypot(dx, dz));
        if (distance < 12) return "tout près";
        String[] names = {"au sud", "au sud-ouest", "à l'ouest", "au nord-ouest", "au nord", "au nord-est", "à l'est", "au sud-est"};
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        int index = (int) Math.floorMod(Math.round(angle / 45.0), 8L);
        return distance + " blocs " + names[index];
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
            case "threat" -> threat(sender, args, actor);
            case "pvp" -> {
                String mode = args.length < 3 ? "" : args[2].toLowerCase(Locale.ROOT);
                if (!mode.equals("on") && !mode.equals("off")) {
                    Text.info(sender, "PvP dans Ground Zero : " + (plugin.scenario().pvp() ? "activé" : "désactivé") + ". /apocalypse admin pvp on|off");
                    return;
                }
                plugin.scenario().setPvp(mode.equals("on"), actor);
                plugin.getLogger().info("Administration (" + actor + ") : PvP " + mode);
                Text.good(sender, "PvP " + (mode.equals("on") ? "activé (jamais au refuge ni entre coéquipiers)" : "désactivé") + ", annoncé aux survivants (tracé).");
            }
            case "horde" -> {
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (target == null || !plugin.sessions().isSurvivor(target) || plugin.scenario().inRefuge(target.getLocation())) {
                    Text.error(sender, "/apocalypse admin horde <joueur> — le joueur doit être dans Ground Zero, hors du refuge.");
                    return;
                }
                int size = plugin.director().horde(target, plugin.settings().play().threat().hordeMax());
                audit(actor, "admin_horde", target.getName() + " x" + size);
                plugin.events().add("horde", "Horde de " + size + " infectés envoyée vers " + target.getName() + " par " + actor);
                Text.good(sender, size + " infecté(s) envoyé(s) vers " + target.getName() + " (tracé).");
            }
            case "destruction" -> destruction(sender, args, actor);
            case "upgrade" -> {
                int radius;
                try {
                    radius = args.length > 2 ? Integer.parseInt(args[2]) : 96;
                } catch (NumberFormatException e) {
                    radius = -1;
                }
                var world = plugin.scenario().world();
                if (world == null || radius < 16 || radius > 512) {
                    Text.error(sender, "/apocalypse admin upgrade [rayon 16-512] — voitures et arbres de rue tout de suite autour de toi (ou du refuge).");
                    return;
                }
                int x = plugin.settings().refuge().centerX(), z = plugin.settings().refuge().centerZ();
                if (sender instanceof Player player && plugin.scenario().isScenarioWorld(player.getWorld())) {
                    x = player.getLocation().getBlockX();
                    z = player.getLocation().getBlockZ();
                }
                int[] done = plugin.vehicles().upgradeArea(world, x, z, radius);
                audit(actor, "admin_upgrade", x + " " + z + " r" + radius);
                Text.good(sender, done[0] + " tronçon(s) mis à niveau ; " + done[1] + " voiture(s) chargée(s).");
            }
            case "settings" -> {
                var values = plugin.tuning().values();
                for (var setting : org.duckcatmine.apocalypse.config.SettingsService.CATALOG)
                    Text.info(sender, setting.key() + " = " + values.get(setting.key()) + " — " + setting.label());
                String preset = plugin.tuning().activePreset();
                Text.info(sender, "Préréglage : " + (preset == null ? "personnalisé" : preset) + ". /apocalypse admin set <réglage> <valeur>");
            }
            case "set" -> {
                if (args.length < 4) {
                    Text.info(sender, "/apocalypse admin set <réglage> <valeur> — liste : /apocalypse admin settings");
                    return;
                }
                List<String> errors = plugin.tuning().set(args[2].toLowerCase(Locale.ROOT), args[3], actor);
                if (errors.isEmpty()) Text.good(sender, "Réglage " + args[2] + " = " + plugin.tuning().values().get(args[2].toLowerCase(Locale.ROOT)) + " appliqué (tracé).");
                else errors.forEach(error -> Text.error(sender, error));
            }
            case "preset" -> {
                List<String> errors = args.length < 3 ? List.of("/apocalypse admin preset relaxed|normal|nightmare")
                        : plugin.tuning().preset(args[2].toLowerCase(Locale.ROOT), actor);
                if (errors.isEmpty()) Text.good(sender, "Préréglage " + args[2] + " appliqué (tracé).");
                else errors.forEach(error -> Text.error(sender, error));
            }
            case "evacuate" -> {
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (target == null || plugin.sessions().profile(target) != org.duckcatmine.apocalypse.session.SessionService.Profile.APOCALYPSE) {
                    Text.error(sender, "/apocalypse admin evacuate <joueur> — le joueur doit être connecté et dans Ground Zero.");
                    return;
                }
                if (plugin.sessions().evacuate(target)) {
                    audit(actor, "admin_evacuate", target.getName());
                    plugin.events().add("admin", target.getName() + " ramené hors de Ground Zero par " + actor);
                    Text.good(sender, target.getName() + " a retrouvé sa survie habituelle ; sa vie Ground Zero est gardée (tracé).");
                } else {
                    Text.error(sender, "Sortie impossible (voir les journaux du serveur).");
                }
            }
            case "lives" -> {
                if (args.length != 4 || !args[2].equalsIgnoreCase("reset") || !args[3].equals("confirm")) {
                    Text.info(sender, "/apocalypse admin lives reset confirm — termine toutes les vies en cours (nouvelle partie), sans sac.");
                    return;
                }
                String error = plugin.sessions().resetAllLives(actor);
                if (error != null) Text.error(sender, error);
                else Text.good(sender, "Toutes les vies sont terminées : chacun repartira de zéro à sa prochaine entrée (tracé).");
            }
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
        var threat = plugin.threat();
        Text.info(sender, String.format(Locale.ROOT, "Menace : %.2f | phase : %s (%d s) | hordes : %d | siège : %s, %d bloc(s) entamé(s) | animaux en incubation : %d",
                threat.level(), threat.phase().label(), threat.secondsLeft(), threat.hordes(),
                plugin.siege().enabled() ? "actif" : "coupé", plugin.siege().damagedBlocks(), plugin.fauna().incubatingCount()));
        Text.info(sender, "PvP : " + (scenario.pvp() ? "activé" : "désactivé") + " | équipes : " + plugin.teams().count());
        Text.info(sender, "Inventaires classiques gardés par le scénario : " + held
                + (held == 0 ? " — désinstallation sans risque." : " — garder le plugin installé (même désactivé) jusqu'à 0."));
    }

    private void threat(CommandSender sender, String[] args, String actor) {
        if (args.length < 3) {
            var threat = plugin.threat();
            Text.info(sender, String.format(Locale.ROOT, "Menace %.2f, phase %s encore %d s. /apocalypse admin threat <1-50> pour la fixer.",
                    threat.level(), threat.phase().label(), threat.secondsLeft()));
            return;
        }
        double value;
        try {
            value = Double.parseDouble(args[2].replace(',', '.'));
        } catch (NumberFormatException e) {
            value = -1;
        }
        if (!(value >= 1 && value <= 50)) {
            Text.error(sender, "Valeur entre 1 et 50.");
            return;
        }
        plugin.threat().setLevel(value);
        audit(actor, "admin_threat", args[2]);
        Text.good(sender, "Menace fixée à " + args[2] + " (tracé).");
    }

    private void destruction(CommandSender sender, String[] args, String actor) {
        String mode = args.length < 3 ? "" : args[2].toLowerCase(Locale.ROOT);
        if (!mode.equals("on") && !mode.equals("off")) {
            Text.info(sender, "Destruction de blocs par les infectés : " + (plugin.siege().enabled() ? "active" : "coupée")
                    + ". /apocalypse admin destruction on|off");
            return;
        }
        plugin.siege().setEnabled(mode.equals("on"));
        audit(actor, "admin_destruction", mode);
        Text.good(sender, "Destruction de blocs " + (mode.equals("on") ? "activée" : "coupée (dégâts en cours effacés)") + " (tracé).");
    }

    private void audit(String actor, String action, String detail) {
        long now = plugin.clock().now();
        plugin.database().submit(c -> {
            Store.audit(c, now, actor, action, detail);
            return null;
        });
        plugin.getLogger().info("Administration (" + actor + ") : " + action + " " + detail);
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
        } else if (mode.equals("recreate") && args.length == 4 && args[3].equals("confirm")) {
            String error = plugin.scenario().recreate();
            if (error != null) Text.error(sender, error);
            else Text.good(sender, "Ground Zero reconstruit (génération " + plugin.scenario().epoch()
                    + "). Les survivants reprendront au refuge avec ce qu'ils portent. Ouvre-le avec /apocalypse admin enable.");
        } else {
            Text.info(sender, "/apocalypse admin world create — crée un monde neuf dédié (aucun monde existant n'est touché)");
            Text.info(sender, "/apocalypse admin world bind <monde> confirm — associe un monde déjà chargé (règles de jeu modifiées)");
            Text.info(sender, "/apocalypse admin world recreate confirm — reconstruit le monde associé après avoir mis son dossier de côté (serveur arrêté)");
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
            options.addAll(List.of("join", "leave", "guide", "status", "infection", "bag", "recipes", "team", "stats", "skills", "intro", "help"));
            if (sender.hasPermission(ADMIN)) options.add("admin");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            options.add(plugin.settings().scenario().id());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("bag")) {
            options.add("open");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("team")) {
            options.addAll(List.of("create", "invite", "accept", "decline", "leave", "kick", "bags", "disband", "info"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("team") && args[1].equalsIgnoreCase("invite")) {
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("team") && args[1].equalsIgnoreCase("kick") && sender instanceof Player player) {
            options.addAll(plugin.teams().names(player.getUniqueId()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("team") && args[1].equalsIgnoreCase("bags")) {
            options.addAll(List.of("on", "off"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("skills")) {
            options.add("up");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("skills")) {
            options.addAll(List.of("endurance", "melee", "breath"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("intro")) {
            options.add("skip");
        } else if (args[0].equalsIgnoreCase("admin") && sender.hasPermission(ADMIN)) {
            if (args.length == 2) options.addAll(List.of("status", "enable", "disable", "reload", "selftest", "world", "give", "map", "threat",
                    "destruction", "horde", "pvp", "settings", "set", "preset", "evacuate", "lives"));
            else if (args.length == 3 && args[1].equalsIgnoreCase("set"))
                org.duckcatmine.apocalypse.config.SettingsService.CATALOG.forEach(setting -> options.add(setting.key()));
            else if (args.length == 3 && args[1].equalsIgnoreCase("preset")) options.addAll(org.duckcatmine.apocalypse.config.SettingsService.PRESETS.keySet());
            else if (args.length == 3 && args[1].equalsIgnoreCase("evacuate")) Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            else if (args.length == 3 && args[1].equalsIgnoreCase("lives")) options.add("reset");
            else if (args.length == 4 && args[1].equalsIgnoreCase("lives")) options.add("confirm");
            else if (args.length == 3 && args[1].equalsIgnoreCase("horde")) Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            else if (args.length == 3 && (args[1].equalsIgnoreCase("destruction") || args[1].equalsIgnoreCase("pvp"))) options.addAll(List.of("on", "off"));
            else if (args.length == 3 && args[1].equalsIgnoreCase("world")) options.addAll(List.of("create", "bind", "recreate"));
            else if (args.length == 3 && args[1].equalsIgnoreCase("give")) Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            else if (args.length == 4 && args[1].equalsIgnoreCase("give")) options.addAll(plugin.items().ids());
            else if (args.length == 5 && args[1].equalsIgnoreCase("world")) options.add("confirm");
            else if (args.length == 4 && args[1].equalsIgnoreCase("world") && args[2].equalsIgnoreCase("recreate")) options.add("confirm");
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
