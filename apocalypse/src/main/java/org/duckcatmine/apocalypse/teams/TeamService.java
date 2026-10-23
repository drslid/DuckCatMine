package org.duckcatmine.apocalypse.teams;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.crafting.PlacedBlocks;
import org.duckcatmine.apocalypse.items.ItemRegistry;
import org.duckcatmine.apocalypse.persistence.PersistenceException;
import org.duckcatmine.apocalypse.persistence.Store;
import org.duckcatmine.apocalypse.scenario.GroundZeroGenerator;
import org.duckcatmine.apocalypse.scenario.city.CityLayout;
import org.duckcatmine.apocalypse.session.SessionService;

/**
 * Teams (spec §18): ownership by UUID, invitations, leader rights. Also the rules between survivors: PvP off by default,
 * never between teammates or in the refuge; claimed bases protected from other survivors; bag access for teammates; an
 * antidote injected by another survivor.
 */
public final class TeamService implements Listener {
    private static final String NAME = "[A-Za-z0-9_-]{3,16}";
    private static final long INJECT_TICKS = 40;

    public static final class Team {
        final String id;
        final String name;
        UUID leader;
        boolean bagAccess;
        final Set<UUID> members = new LinkedHashSet<>();

        Team(String id, String name, UUID leader, boolean bagAccess) {
            this.id = id;
            this.name = name;
            this.leader = leader;
            this.bagAccess = bagAccess;
        }

        public String name() {
            return name;
        }

        public Set<UUID> members() {
            return Set.copyOf(members);
        }
    }

    private record Invite(String teamId, UUID inviter, long expiresAt) {
    }

    private final DuckCatApocalypse plugin;
    private final Map<String, Team> teams = new HashMap<>();
    private final Map<UUID, String> memberOf = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Map<UUID, BukkitTask> injections = new HashMap<>();

    public TeamService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public void load() throws PersistenceException {
        teams.clear();
        memberOf.clear();
        for (Store.TeamRow row : plugin.database().call(Store::teams)) teams.put(row.id(), new Team(row.id(), row.name(), row.leader(), row.bagAccess()));
        for (Map.Entry<UUID, String> member : plugin.database().call(Store::teamMembers).entrySet()) {
            Team team = teams.get(member.getValue());
            if (team == null) continue;
            team.members.add(member.getKey());
            memberOf.put(member.getKey(), team.id);
        }
    }

    public int count() {
        return teams.size();
    }

    public Team teamOf(UUID player) {
        String id = memberOf.get(player);
        return id == null ? null : teams.get(id);
    }

    public boolean sameTeam(UUID a, UUID b) {
        String team = memberOf.get(a);
        return team != null && team.equals(memberOf.get(b));
    }

    /** The owner or one of their teammates plays in the scenario right now. */
    public boolean anyPresent(UUID owner, World world) {
        if (owner == null) return false;
        Team team = teamOf(owner);
        for (UUID id : team == null ? Set.of(owner) : team.members) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && !player.isDead() && player.getWorld().equals(world) && plugin.sessions().isSurvivor(player)) return true;
        }
        return false;
    }

    public boolean canOpenBag(Player player, UUID owner) {
        if (player.getUniqueId().equals(owner)) return true;
        Team team = teamOf(owner);
        return team != null && team.bagAccess && team.members.contains(player.getUniqueId());
    }

    // ------------------------------------------------------------------ commands

    public void command(Player player, String[] args) {
        String action = args.length < 2 ? "info" : args[1].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "create" -> create(player, args.length > 2 ? args[2] : "");
                case "invite" -> invite(player, args.length > 2 ? args[2] : "");
                case "accept" -> accept(player);
                case "decline" -> decline(player);
                case "leave" -> leave(player);
                case "kick" -> kick(player, args.length > 2 ? args[2] : "");
                case "disband" -> disband(player);
                case "bags" -> bags(player, args.length > 2 ? args[2] : "");
                default -> info(player);
            }
        } catch (PersistenceException e) {
            plugin.getLogger().warning("Équipe : écriture impossible (" + e.getMessage() + ")");
            Text.error(player, "Sauvegarde indisponible : rien n'a changé.");
        }
    }

    private void create(Player player, String name) {
        if (teamOf(player.getUniqueId()) != null) {
            Text.error(player, "Tu fais déjà partie d'une équipe (/apocalypse team leave).");
            return;
        }
        if (!name.matches(NAME)) {
            Text.error(player, "Nom d'équipe : 3 à 16 caractères, lettres, chiffres, - ou _.");
            return;
        }
        for (Team team : teams.values()) {
            if (team.name.equalsIgnoreCase(name)) {
                Text.error(player, "Ce nom est déjà pris.");
                return;
            }
        }
        long now = plugin.clock().now();
        Store.TeamRow row = new Store.TeamRow(UUID.randomUUID().toString(), name, player.getUniqueId(), now, true);
        UUID id = player.getUniqueId();
        String playerName = player.getName();
        long applied = plugin.sessions().appliedSeq(player);
        plugin.database().call(c -> {
            Store.ensurePlayer(c, id, playerName, applied);
            Store.insertTeam(c, row);
            Store.addTeamMember(c, id, row.id(), now);
            return null;
        });
        Team team = new Team(row.id(), name, id, true);
        team.members.add(id);
        teams.put(team.id, team);
        memberOf.put(id, team.id);
        Text.good(player, "Équipe " + name + " créée. Invite tes compagnons : /apocalypse team invite <joueur>.");
    }

    private void invite(Player player, String targetName) {
        Team team = led(player);
        if (team == null) return;
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || target.equals(player)) {
            Text.error(player, "Joueur introuvable (il doit être connecté).");
            return;
        }
        if (teamOf(target.getUniqueId()) != null) {
            Text.error(player, target.getName() + " fait déjà partie d'une équipe.");
            return;
        }
        if (team.members.size() >= plugin.settings().play().social().teamMaxSize()) {
            Text.error(player, "Équipe complète (" + team.members.size() + " membres).");
            return;
        }
        long seconds = plugin.settings().play().social().inviteSeconds();
        invites.put(target.getUniqueId(), new Invite(team.id, player.getUniqueId(), System.currentTimeMillis() + seconds * 1000));
        Text.good(player, "Invitation envoyée à " + target.getName() + " (" + seconds + " s).");
        target.sendMessage(Component.text("☣ " + player.getName() + " t'invite dans l'équipe " + team.name + ". ", NamedTextColor.GOLD)
                .append(Component.text("[Accepter]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/apocalypse team accept")))
                .append(Component.text(" "))
                .append(Component.text("[Refuser]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/apocalypse team decline"))));
    }

    private void accept(Player player) {
        Invite invite = invites.remove(player.getUniqueId());
        Team team = invite == null ? null : teams.get(invite.teamId);
        if (team == null || invite.expiresAt < System.currentTimeMillis()) {
            Text.error(player, "Aucune invitation valable.");
            return;
        }
        if (teamOf(player.getUniqueId()) != null) {
            Text.error(player, "Tu fais déjà partie d'une équipe.");
            return;
        }
        if (team.members.size() >= plugin.settings().play().social().teamMaxSize()) {
            Text.error(player, "L'équipe est complète.");
            return;
        }
        UUID id = player.getUniqueId();
        String playerName = player.getName();
        long applied = plugin.sessions().appliedSeq(player);
        long now = plugin.clock().now();
        plugin.database().call(c -> {
            Store.ensurePlayer(c, id, playerName, applied);
            Store.addTeamMember(c, id, team.id, now);
            return null;
        });
        team.members.add(id);
        memberOf.put(id, team.id);
        broadcast(team, player.getName() + " rejoint l'équipe " + team.name + ".");
    }

    private void decline(Player player) {
        Invite invite = invites.remove(player.getUniqueId());
        if (invite == null) {
            Text.info(player, "Aucune invitation en attente.");
            return;
        }
        Text.info(player, "Invitation refusée.");
        Player inviter = Bukkit.getPlayer(invite.inviter);
        if (inviter != null) Text.info(inviter, player.getName() + " a refusé ton invitation.");
    }

    private void leave(Player player) {
        Team team = teamOf(player.getUniqueId());
        if (team == null) {
            Text.info(player, "Tu n'as pas d'équipe.");
            return;
        }
        if (team.members.size() == 1) {
            dissolve(team);
            Text.info(player, "Équipe " + team.name + " dissoute.");
            return;
        }
        remove(team, player.getUniqueId());
        if (team.leader.equals(player.getUniqueId())) {
            UUID next = team.members.iterator().next();
            plugin.database().call(c -> {
                Store.setTeamLeader(c, team.id, next);
                return null;
            });
            team.leader = next;
        }
        Text.info(player, "Tu as quitté l'équipe " + team.name + ".");
        broadcast(team, player.getName() + " a quitté l'équipe. Chef : " + name(team.leader) + ".");
    }

    private void kick(Player player, String targetName) {
        Team team = led(player);
        if (team == null) return;
        UUID target = null;
        for (UUID member : team.members) if (name(member).equalsIgnoreCase(targetName)) target = member;
        if (target == null || target.equals(player.getUniqueId())) {
            Text.error(player, "Membre introuvable : " + targetName + ".");
            return;
        }
        remove(team, target);
        broadcast(team, targetName + " a été retiré de l'équipe.");
        Player kicked = Bukkit.getPlayer(target);
        if (kicked != null) Text.warn(kicked, "Tu as été retiré de l'équipe " + team.name + ".");
    }

    private void disband(Player player) {
        Team team = led(player);
        if (team == null) return;
        broadcast(team, "L'équipe " + team.name + " est dissoute par son chef.");
        dissolve(team);
    }

    private void bags(Player player, String mode) {
        Team team = led(player);
        if (team == null) return;
        if (!mode.equals("on") && !mode.equals("off")) {
            Text.info(player, "Sacs accessibles aux coéquipiers : " + (team.bagAccess ? "oui" : "non") + ". /apocalypse team bags on|off");
            return;
        }
        boolean value = mode.equals("on");
        plugin.database().call(c -> {
            Store.setTeamBagAccess(c, team.id, value);
            return null;
        });
        team.bagAccess = value;
        broadcast(team, value ? "Les coéquipiers peuvent récupérer les sacs de l'équipe." : "Chaque sac n'est plus accessible qu'à son propriétaire.");
    }

    private void info(Player player) {
        Team team = teamOf(player.getUniqueId());
        if (team == null) {
            Text.info(player, "Tu joues seul. /apocalypse team create <nom> | accept | decline");
            return;
        }
        Text.info(player, "Équipe " + team.name + " (" + team.members.size() + "/" + plugin.settings().play().social().teamMaxSize()
                + ") — sacs partagés : " + (team.bagAccess ? "oui" : "non"));
        for (UUID member : team.members) {
            Player online = Bukkit.getPlayer(member);
            String state = online == null ? "hors ligne" : !plugin.sessions().isSurvivor(online) ? "hors de Ground Zero"
                    : plugin.infections().isInfected(member) ? "MORDU" : "en vie";
            Text.info(player, (member.equals(team.leader) ? "★ " : "• ") + name(member) + " — " + state);
        }
        if (team.leader.equals(player.getUniqueId()))
            Text.info(player, "/apocalypse team invite <joueur> | kick <joueur> | bags on|off | disband | leave");
        else Text.info(player, "/apocalypse team leave");
    }

    private Team led(Player player) {
        Team team = teamOf(player.getUniqueId());
        if (team == null) {
            Text.error(player, "Tu n'as pas d'équipe : /apocalypse team create <nom>.");
            return null;
        }
        if (!team.leader.equals(player.getUniqueId())) {
            Text.error(player, "Réservé au chef de l'équipe (" + name(team.leader) + ").");
            return null;
        }
        return team;
    }

    private void remove(Team team, UUID member) {
        plugin.database().call(c -> {
            Store.removeTeamMember(c, member);
            return null;
        });
        team.members.remove(member);
        memberOf.remove(member);
    }

    private void dissolve(Team team) {
        plugin.database().call(c -> {
            Store.deleteTeam(c, team.id);
            return null;
        });
        for (UUID member : team.members) memberOf.remove(member);
        teams.remove(team.id);
        invites.values().removeIf(invite -> invite.teamId.equals(team.id));
    }

    private void broadcast(Team team, String message) {
        for (UUID member : team.members) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) Text.info(online, "[" + team.name + "] " + message);
        }
    }

    private String name(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) return online.getName();
        String stored = plugin.database().call(c -> Store.playerName(c, id));
        return stored == null ? id.toString().substring(0, 8) : stored;
    }

    public List<String> names(UUID player) {
        Team team = teamOf(player);
        List<String> result = new ArrayList<>();
        if (team != null) for (UUID member : team.members) result.add(name(member));
        return result;
    }

    // ------------------------------------------------------------------ rules between survivors

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamageSource().getCausingEntity() instanceof Player player) return player;
        Entity damager = event.getDamager();
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    /** PvP off by default; never between teammates, never in or from the refuge. Covers shots, thrown items and knockback. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !plugin.scenario().isScenarioWorld(victim.getWorld())) return;
        Player attacker = attacker(event);
        if (attacker == null || attacker.equals(victim)) return;
        if (!plugin.scenario().pvp() || sameTeam(attacker.getUniqueId(), victim.getUniqueId())
                || plugin.scenario().inRefuge(victim.getLocation()) || plugin.scenario().inRefuge(attacker.getLocation())) {
            event.setCancelled(true);
        }
    }

    /** A claim protects its blocks, placed containers and stations from survivors outside the owner's team. */
    private PlacedBlocks.Claim foreign(Player player, Block block) {
        if (!plugin.sessions().isSurvivor(player) || player.hasPermission(SessionService.BYPASS)) return null;
        for (PlacedBlocks.Claim claim : plugin.placed().claimsAt(block.getLocation())) {
            if (claim.owner() != null && !claim.owner().equals(player.getUniqueId()) && !sameTeam(claim.owner(), player.getUniqueId())) return claim;
        }
        return null;
    }

    private void refuse(Player player, PlacedBlocks.Claim claim) {
        player.sendActionBar(Component.text("Base protégée : balise de " + name(claim.owner()) + ".", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        PlacedBlocks.Claim claim = foreign(event.getPlayer(), event.getBlock());
        if (claim == null) return;
        event.setCancelled(true);
        refuse(event.getPlayer(), claim);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        PlacedBlocks.Claim claim = foreign(event.getPlayer(), event.getBlock());
        if (claim == null) return;
        event.setCancelled(true);
        refuse(event.getPlayer(), claim);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null) return;
        boolean guarded = block.getState(false) instanceof Container && !generated(block) || plugin.placed().get(block) != null;
        if (!guarded) return;
        PlacedBlocks.Claim claim = foreign(event.getPlayer(), block);
        if (claim == null) return;
        event.setCancelled(true);
        refuse(event.getPlayer(), claim);
    }

    /** Searchable containers of the generated city stay open to everyone, even inside a claim. */
    private boolean generated(Block block) {
        World world = block.getWorld();
        return world.getGenerator() instanceof GroundZeroGenerator
                && CityLayout.of(world.getSeed()).placeAt(block.getX(), block.getY(), block.getZ()) != null;
    }

    // ------------------------------------------------------------------ antidote given by another survivor

    /** Sneak and right-click a bitten survivor with an antidote in hand: a 2 s injection, interrupted if either moves away. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInject(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)) return;
        Player healer = event.getPlayer();
        if (!healer.isSneaking() || !plugin.items().is(healer.getInventory().getItemInMainHand(), ItemRegistry.ANTIDOTE)) return;
        if (!plugin.sessions().isSurvivor(healer) || !plugin.sessions().isSurvivor(target)) return;
        event.setCancelled(true);
        if (!plugin.infections().isInfected(target.getUniqueId())) {
            Text.info(healer, target.getName() + " n'est pas mordu : la dose est conservée.");
            return;
        }
        if (injections.containsKey(healer.getUniqueId())) return;
        healer.playSound(healer.getLocation(), Sound.ITEM_BOTTLE_FILL, 0.8f, 1.4f);
        target.sendActionBar(Component.text(healer.getName() + " t'injecte un antidote… ne bouge pas.", NamedTextColor.GREEN));
        long[] elapsed = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            elapsed[0] += 5;
            boolean valid = healer.isOnline() && target.isOnline() && !healer.isDead() && !target.isDead() && healer.isSneaking()
                    && healer.getWorld().equals(target.getWorld()) && healer.getLocation().distanceSquared(target.getLocation()) <= 9
                    && plugin.items().is(healer.getInventory().getItemInMainHand(), ItemRegistry.ANTIDOTE);
            if (!valid) {
                stopInjection(healer.getUniqueId());
                if (healer.isOnline()) healer.sendActionBar(Component.text("Injection interrompue.", NamedTextColor.RED));
                return;
            }
            int percent = (int) (100 * elapsed[0] / INJECT_TICKS);
            healer.sendActionBar(Component.text("Injection : " + Math.min(100, percent) + " %", NamedTextColor.GREEN));
            if (elapsed[0] < INJECT_TICKS) return;
            stopInjection(healer.getUniqueId());
            if (!plugin.infections().cure(target)) {
                Text.error(healer, "Trop tard : l'antidote n'agit plus.");
                return;
            }
            ItemStack hand = healer.getInventory().getItemInMainHand();
            hand.setAmount(hand.getAmount() - 1);
            Location at = target.getLocation().add(0, 1, 0);
            target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 10, 0.3, 0.4, 0.3, 0);
            Text.good(healer, "Antidote injecté à " + target.getName() + " : son infection est stoppée.");
            Text.good(target, healer.getName() + " t'a injecté un antidote à temps.");
        }, 5L, 5L);
        injections.put(healer.getUniqueId(), task);
    }

    private void stopInjection(UUID healer) {
        BukkitTask task = injections.remove(healer);
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopInjection(event.getPlayer().getUniqueId());
        invites.remove(event.getPlayer().getUniqueId());
    }
}
