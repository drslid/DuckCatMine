package org.duckcatmine.apocalypse.spawn;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.session.SessionService;

/** Optional shared lobby. Inventories are untouched until the common countdown has finished. */
public final class DepartureService implements Listener {
    private static final int SECONDS = 5;
    private final DuckCatApocalypse plugin;
    private final DepartureGroup group = new DepartureGroup();
    private BukkitTask timer;

    private static final class Menu implements InventoryHolder {
        Inventory inventory;
        final Map<Integer, UUID> targets = new HashMap<>();
        @Override public Inventory getInventory() { return inventory; }
    }

    public DepartureService(DuckCatApocalypse plugin) { this.plugin = plugin; }

    private boolean eligible(Player player) {
        return player != null && player.isOnline() && !player.isDead() && !plugin.sessions().isJoining(player)
                && player.hasPermission("duckcat.apocalypse.play")
                && plugin.sessions().profile(player) == SessionService.Profile.CLASSIC
                && !plugin.scenario().isScenarioWorld(player.getWorld());
    }

    public void open(Player player) {
        if (!eligible(player)) {
            Text.error(player, "Quitte d'abord Ground Zero pour préparer un nouveau départ, ou attends la préparation en cours.");
            return;
        }
        Menu menu = new Menu();
        menu.inventory = Bukkit.createInventory(menu, 36, Component.text("Ground Zero · Votre départ"));
        menu.inventory.setItem(10, item(Material.COMPASS, "Départ solo", "Reprendre ta vie, ou partir seul dans la ville."));
        menu.inventory.setItem(12, item(Material.CAMPFIRE, "Attendre le groupe", "Tous les participants apparaîtront au même endroit.", "En attente : " + names()));
        menu.inventory.setItem(14, item(Material.CLOCK, "Lancer le départ · 5 secondes", "Compte à rebours commun à tout le groupe.", "Destination : " + targetName()));
        menu.inventory.setItem(16, item(Material.BARRIER, "Quitter la salle d'attente", "Ta survie habituelle reste intacte."));
        int slot = 27;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!plugin.sessions().isSurvivor(target) || target.isDead() || plugin.intro().playing(target) || slot >= 36) continue;
            ItemStack head = item(Material.PLAYER_HEAD, "Départ près de " + target.getName(), "Rejoindre la salle d'attente et choisir ce survivant", "comme destination du départ groupé.");
            head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(target));
            menu.inventory.setItem(slot, head);
            menu.targets.put(slot++, target.getUniqueId());
        }
        player.openInventory(menu.inventory);
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(Component.text(name, NamedTextColor.GREEN));
            meta.lore(java.util.Arrays.stream(lore).map(s -> Component.text(s, NamedTextColor.GRAY)).toList());
        });
        return stack;
    }

    public void join(Player player, Player target) {
        if (!eligible(player) || !plugin.scenario().isEnabled() || plugin.scenario().world() == null) {
            Text.error(player, "Départ indisponible : reste hors de Ground Zero et vérifie qu'il est ouvert.");
            return;
        }
        if (target != null && (!plugin.sessions().isSurvivor(target) || target.isDead() || plugin.intro().playing(target))) {
            Text.error(player, "Ce survivant n'est plus disponible.");
            return;
        }
        if (!group.join(player.getUniqueId(), target == null ? null : target.getUniqueId())) {
            Text.error(player, "Le départ est déjà en préparation. Attends le prochain groupe.");
            return;
        }
        inform(player.getName() + " rejoint le départ. Groupe : " + names() + ". Destination : " + targetName()
                + ". /apocalypse start pour lancer le compte à rebours.");
        plugin.status().tick();
        open(player);
    }

    public boolean queued(Player player) { return group.members().contains(player.getUniqueId()); }

    public void leave(Player player) {
        group.remove(player.getUniqueId());
        player.clearTitle();
        if (group.members().isEmpty()) stopTimer();
        Text.info(player, "Tu as quitté le départ groupé. Rien n'a changé dans ton inventaire.");
        inform(player.getName() + " a quitté le groupe.");
        plugin.status().tick();
    }

    public String start(String actor) {
        if (group.phase() != DepartureGroup.Phase.WAITING) return "Le compte à rebours est déjà en préparation.";
        prune();
        if (group.members().isEmpty()) return "Aucun joueur dans la salle d'attente. /apocalypse join pour la rejoindre.";
        if (!plugin.scenario().isEnabled() || plugin.scenario().world() == null || !plugin.database().healthy()) return "Ground Zero n'est pas prêt.";
        Player target = group.target() == null ? null : Bukkit.getPlayer(group.target());
        if (group.target() != null && !validTarget(target)) return "Le survivant choisi n'est plus disponible : choisis une autre destination.";
        long token = group.prepare();
        inform("Préparation du départ groupé…");
        CompletableFuture<Location> prepared = target == null ? plugin.drops().pick(plugin.scenario().world())
                : CompletableFuture.completedFuture(target.getLocation().clone());
        prepared.whenComplete((spot, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!group.countdown(token, SECONDS)) return;
                if (error != null || spot == null) { cancel("Impossible de préparer un emplacement sûr."); return; }
                plugin.events().add("departure", "Départ groupé lancé par " + actor + " : " + names());
                timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> countdown(spot), 0, 20);
                plugin.status().tick();
            });
        });
        return null;
    }

    private boolean validTarget(Player target) {
        return target != null && target.isOnline() && !target.isDead() && plugin.sessions().isSurvivor(target) && !plugin.intro().playing(target);
    }

    private void prune() {
        for (UUID id : group.members()) if (!eligible(Bukkit.getPlayer(id))) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.clearTitle();
            group.remove(id);
        }
    }

    private void countdown(Location prepared) {
        prune();
        if (group.members().isEmpty()) { stopTimer(); return; }
        if (!plugin.scenario().isEnabled() || !plugin.database().healthy()) { cancel("Ground Zero n'est plus disponible."); return; }
        int seconds = group.seconds();
        if (seconds == 0) {
            Player target = group.target() == null ? null : Bukkit.getPlayer(group.target());
            if (group.target() != null && !validTarget(target)) { cancel("Le survivant choisi a quitté la partie."); return; }
            Location center = target == null ? prepared : target.getLocation().clone();
            List<Player> players = group.members().stream().map(Bukkit::getPlayer).toList();
            List<Location> spots = new ArrayList<>();
            var reserved = new HashSet<String>();
            for (Player player : players) {
                Location at = SafeArrival.near(center, reserved);
                if (at == null) { cancel("Pas assez de place sûre près du point choisi. Aucun inventaire n'a changé."); return; }
                spots.add(at);
            }
            stopTimer();
            group.cancel();
            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                player.clearTitle();
                plugin.sessions().joinTogether(player, spots.get(i));
            }
            plugin.status().tick();
            return;
        }
        for (UUID id : group.members()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            player.closeInventory();
            player.showTitle(Title.title(Component.text(String.valueOf(seconds), NamedTextColor.GREEN),
                    Component.text("Départ ensemble · " + targetName(), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, .6f, seconds == 1 ? 1.6f : 1f);
        }
        group.elapseSecond();
    }

    public void cancel(String reason) {
        inform("Départ annulé : " + reason);
        for (UUID id : group.members()) { Player player = Bukkit.getPlayer(id); if (player != null) player.clearTitle(); }
        group.cancel();
        stopTimer();
        plugin.status().tick();
    }

    public void stop() { group.cancel(); stopTimer(); }
    private void stopTimer() { if (timer != null) { timer.cancel(); timer = null; } }
    private void inform(String text) { for (UUID id : group.members()) { Player p = Bukkit.getPlayer(id); if (p != null) Text.info(p, text); } }
    private String names() { return group.members().stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull).map(Player::getName).collect(java.util.stream.Collectors.joining(", ")); }
    private String targetName() { Player p = group.target() == null ? null : Bukkit.getPlayer(group.target()); return p == null ? "point commun dans la ville" : "près de " + p.getName(); }

    public Map<String, Object> snapshot() {
        return Map.of("phase", group.phase().name().toLowerCase(Locale.ROOT), "seconds", group.seconds(), "target", targetName(),
                "members", group.members().stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull).map(Player::getName).toList());
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != menu.inventory) return;
        int slot = event.getRawSlot();
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            if (slot == 10) {
                if (group.phase() != DepartureGroup.Phase.WAITING && queued(player)) { Text.error(player, "Quitte d'abord le départ en cours."); return; }
                group.remove(player.getUniqueId());
                plugin.sessions().join(player);
            } else if (slot == 12) join(player, null);
            else if (slot == 14) {
                if (!queued(player)) { Text.error(player, "Rejoins d'abord la salle d'attente."); return; }
                String error = start(player.getName()); if (error != null) Text.error(player, error);
            } else if (slot == 16) leave(player);
            else if (menu.targets.containsKey(slot)) {
                Player target = Bukkit.getPlayer(menu.targets.get(slot));
                if (!validTarget(target)) Text.error(player, "Ce survivant n'est plus disponible.");
                else join(player, target);
            }
        });
    }
    @EventHandler public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { group.remove(event.getPlayer().getUniqueId()); if (group.members().isEmpty()) stopTimer(); }
}
