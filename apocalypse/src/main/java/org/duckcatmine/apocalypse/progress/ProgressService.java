package org.duckcatmine.apocalypse.progress;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.core.Durations;
import org.duckcatmine.apocalypse.persistence.PersistenceException;
import org.duckcatmine.apocalypse.persistence.Store;
import org.duckcatmine.apocalypse.scenario.ProtectionListener;

/**
 * Points and skills of a life (spec §17). Points are shared by valid damage contributions within a bounded window (no
 * overkill, no cancelled hits); skills are bounded attribute modifiers that exist only in Ground Zero and vanish with the
 * life. Career totals are kept as recognition only.
 */
public final class ProgressService implements Listener {
    public enum Skill {
        ENDURANCE("endurance", "Endurance", "vitesse de déplacement"),
        MELEE("melee", "Mêlée", "dégâts au corps à corps"),
        BREATH("breath", "Souffle", "respiration sous l'eau");

        final String id;
        final String label;
        final String effect;

        Skill(String id, String label, String effect) {
            this.id = id;
            this.label = label;
            this.effect = effect;
        }

        static Skill byId(String id) {
            for (Skill skill : values()) if (skill.id.equalsIgnoreCase(id)) return skill;
            return null;
        }
    }

    private static final class Life {
        final String run;
        int points;
        long startedAt;
        final Map<Skill, Integer> ranks = new LinkedHashMap<>();

        Life(String run) {
            this.run = run;
        }
    }

    private record Hit(double damage, long at, String run) {
    }

    private final DuckCatApocalypse plugin;
    private final Map<UUID, Life> lives = new HashMap<>();
    private final Map<UUID, Map<UUID, Hit>> contributions = new HashMap<>();
    private final NamespacedKey enduranceKey;
    private final NamespacedKey meleeKey;
    private final NamespacedKey breathKey;

    public ProgressService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
        enduranceKey = new NamespacedKey(plugin, "skill_endurance");
        meleeKey = new NamespacedKey(plugin, "skill_melee");
        breathKey = new NamespacedKey(plugin, "skill_breath");
    }

    static String encode(Map<Skill, Integer> ranks) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<Skill, Integer> entry : ranks.entrySet()) {
            if (entry.getValue() <= 0) continue;
            if (!out.isEmpty()) out.append(',');
            out.append(entry.getKey().id).append('=').append(entry.getValue());
        }
        return out.toString();
    }

    static Map<Skill, Integer> decode(String text, int maxRank) {
        Map<Skill, Integer> ranks = new LinkedHashMap<>();
        for (Skill skill : Skill.values()) ranks.put(skill, 0);
        if (text == null || text.isBlank()) return ranks;
        for (String part : text.split(",")) {
            String[] pair = part.split("=", 2);
            Skill skill = pair.length == 2 ? Skill.byId(pair[0].trim()) : null;
            if (skill == null) continue;
            try {
                ranks.put(skill, Math.max(0, Math.min(maxRank, Integer.parseInt(pair[1].trim()))));
            } catch (NumberFormatException ignored) {
                // a damaged value counts as rank 0
            }
        }
        return ranks;
    }

    /** Points spent on the ranks a life already has. */
    static int spent(Map<Skill, Integer> ranks, java.util.List<Integer> costs) {
        int total = 0;
        for (int rank : ranks.values()) for (int k = 0; k < Math.min(rank, costs.size()); k++) total += costs.get(k);
        return total;
    }

    private ApocalypseConfig.Progression rules() {
        return plugin.settings().play().progression();
    }

    private Life life(Player player) {
        String run = plugin.sessions().runOf(player);
        if (run == null) return null;
        Life life = lives.get(player.getUniqueId());
        if (life != null && life.run.equals(run)) return life;
        Store.Progress stored = plugin.database().call(c -> Store.progress(c, run));
        life = new Life(run);
        if (stored != null && stored.alive()) {
            life.points = stored.points();
            life.startedAt = stored.startedAt();
            life.ranks.putAll(decode(stored.skills(), rules().maxRank()));
        } else {
            life.startedAt = plugin.clock().now();
            for (Skill skill : Skill.values()) life.ranks.put(skill, 0);
        }
        lives.put(player.getUniqueId(), life);
        return life;
    }

    /** Points of the current life (for the panel), or 0 outside Ground Zero. */
    public int points(Player player) {
        Life life = plugin.sessions().isSurvivor(player) ? life(player) : null;
        return life == null ? 0 : life.points;
    }

    /** How long the current life has lasted, in scenario time. */
    public long lifeMillis(Player player) {
        Life life = plugin.sessions().isSurvivor(player) ? life(player) : null;
        return life == null ? 0 : Math.max(0, plugin.clock().now() - life.startedAt);
    }

    // ------------------------------------------------------------------ points

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob) || !plugin.mobs().isScenarioMob(mob)) return;
        Player attacker = event.getDamageSource().getCausingEntity() instanceof Player player ? player : ProtectionListener.attacker(event.getDamager());
        if (attacker == null || !plugin.sessions().isSurvivor(attacker)) return;
        String run = plugin.sessions().runOf(attacker);
        if (run == null) return;
        double dealt = Math.min(event.getFinalDamage(), Math.max(0, mob.getHealth()));
        if (dealt <= 0) return;
        long now = plugin.clock().now();
        contributions.computeIfAbsent(mob.getUniqueId(), id -> new HashMap<>())
                .merge(attacker.getUniqueId(), new Hit(dealt, now, run), (old, add) -> new Hit(old.run.equals(add.run) ? old.damage + add.damage : add.damage, now, add.run));
    }

    public void forget(UUID mob) {
        contributions.remove(mob);
    }

    /** The infected died: its points are shared by recent contributions of survivors still in the same life. */
    public void onKill(LivingEntity mob, ApocalypseConfig.Archetype archetype) {
        Map<UUID, Hit> hits = contributions.remove(mob.getUniqueId());
        if (hits == null || archetype == null || archetype.points() <= 0) return;
        long now = plugin.clock().now();
        long window = rules().contributionWindowSeconds() * 1000L;
        Map<Player, Double> valid = new LinkedHashMap<>();
        double total = 0;
        for (Map.Entry<UUID, Hit> entry : hits.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            Hit hit = entry.getValue();
            if (player == null || player.isDead() || !plugin.sessions().isSurvivor(player) || now - hit.at > window
                    || !hit.run.equals(plugin.sessions().runOf(player))) continue;
            valid.put(player, hit.damage);
            total += hit.damage;
        }
        if (total <= 0) return;
        for (Map.Entry<Player, Double> entry : valid.entrySet()) {
            int share = (int) Math.round(archetype.points() * entry.getValue() / total);
            if (share > 0) award(entry.getKey(), share, archetype.name());
        }
    }

    private void award(Player player, int points, String what) {
        Life life = life(player);
        if (life == null) return;
        life.points += points;
        UUID id = player.getUniqueId();
        String run = life.run;
        plugin.database().submit(c -> {
            Store.addPoints(c, run, id, points);
            return null;
        });
        int available = life.points - spent(life.ranks, rules().skillCosts());
        Skill next = cheapest(life);
        String hint = next != null && available >= cost(life, next) ? " — compétence disponible : /apocalypse skills" : "";
        player.sendActionBar(Component.text("+" + points + " points (" + what + ")" + hint, NamedTextColor.YELLOW));
    }

    private Skill cheapest(Life life) {
        Skill best = null;
        for (Skill skill : Skill.values()) {
            if (life.ranks.getOrDefault(skill, 0) >= rules().maxRank()) continue;
            if (best == null || cost(life, skill) < cost(life, best)) best = skill;
        }
        return best;
    }

    private int cost(Life life, Skill skill) {
        int rank = life.ranks.getOrDefault(skill, 0);
        return rank >= rules().maxRank() ? Integer.MAX_VALUE : rules().skillCosts().get(rank);
    }

    // ------------------------------------------------------------------ skills

    public void skills(Player player, String[] args) {
        if (!plugin.sessions().isSurvivor(player)) {
            Text.info(player, "Les compétences n'existent que dans Ground Zero.");
            return;
        }
        Life life = life(player);
        if (life == null) return;
        if (args.length >= 3 && args[1].equalsIgnoreCase("up")) {
            Skill skill = Skill.byId(args[2]);
            if (skill == null) {
                Text.error(player, "Compétences : endurance, melee, breath.");
                return;
            }
            upgrade(player, life, skill);
            return;
        }
        int available = life.points - spent(life.ranks, rules().skillCosts());
        Text.info(player, "Points de cette vie : " + life.points + " (disponibles : " + available + "). Tout est perdu à la mort.");
        for (Skill skill : Skill.values()) {
            int rank = life.ranks.getOrDefault(skill, 0);
            Component line = Component.text("• " + skill.label + " " + rank + "/" + rules().maxRank() + " — " + skill.effect + " ", NamedTextColor.GRAY);
            if (rank < rules().maxRank()) {
                int cost = cost(life, skill);
                line = line.append(Component.text("[+1 : " + cost + " pts]", available >= cost ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                        .clickEvent(ClickEvent.runCommand("/apocalypse skills up " + skill.id)));
            }
            player.sendMessage(line);
        }
    }

    private void upgrade(Player player, Life life, Skill skill) {
        int rank = life.ranks.getOrDefault(skill, 0);
        if (rank >= rules().maxRank()) {
            Text.info(player, skill.label + " est déjà au maximum.");
            return;
        }
        int available = life.points - spent(life.ranks, rules().skillCosts());
        int cost = cost(life, skill);
        if (available < cost) {
            Text.error(player, "Il manque " + (cost - available) + " points pour " + skill.label + " " + (rank + 1) + ".");
            return;
        }
        Map<Skill, Integer> next = new LinkedHashMap<>(life.ranks);
        next.put(skill, rank + 1);
        String encoded = encode(next);
        String run = life.run;
        boolean saved;
        try {
            saved = plugin.database().call(c -> Store.setSkills(c, run, encoded));
        } catch (PersistenceException e) {
            saved = false;
        }
        if (!saved) {
            Text.error(player, "Sauvegarde indisponible : compétence non attribuée.");
            return;
        }
        life.ranks.put(skill, rank + 1);
        apply(player);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        Text.good(player, skill.label + " passe au rang " + (rank + 1) + ".");
    }

    /** Re-applies the life's bounded modifiers (or removes them outside Ground Zero). Transient: never saved with the player. */
    public void apply(Player player) {
        clear(player);
        if (!plugin.sessions().isSurvivor(player) || player.isDead()) return;
        Life life = life(player);
        if (life == null) return;
        ApocalypseConfig.Progression rules = rules();
        modifier(player, Attribute.MOVEMENT_SPEED, enduranceKey, life.ranks.getOrDefault(Skill.ENDURANCE, 0) * rules.endurancePerRank(),
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        modifier(player, Attribute.ATTACK_DAMAGE, meleeKey, life.ranks.getOrDefault(Skill.MELEE, 0) * rules.meleePerRank(),
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        modifier(player, Attribute.OXYGEN_BONUS, breathKey, life.ranks.getOrDefault(Skill.BREATH, 0) * rules.breathPerRank(),
                AttributeModifier.Operation.ADD_NUMBER);
    }

    public void clear(Player player) {
        for (Attribute attribute : new Attribute[] {Attribute.MOVEMENT_SPEED, Attribute.ATTACK_DAMAGE, Attribute.OXYGEN_BONUS}) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            instance.removeModifier(enduranceKey);
            instance.removeModifier(meleeKey);
            instance.removeModifier(breathKey);
        }
    }

    /** The life ended: progression is gone. */
    public void endLife(Player player) {
        clear(player);
        lives.remove(player.getUniqueId());
    }

    private static void modifier(Player player, Attribute attribute, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null || amount <= 0) return;
        instance.addTransientModifier(new AttributeModifier(key, amount, operation));
    }

    // ------------------------------------------------------------------ statistics

    public void stats(Player player) {
        UUID id = player.getUniqueId();
        Store.Career career = plugin.database().call(c -> Store.career(c, id));
        if (plugin.sessions().isSurvivor(player)) {
            Life life = life(player);
            String run = plugin.sessions().runOf(player);
            Store.Progress stored = run == null ? null : plugin.database().call(c -> Store.progress(c, run));
            if (life != null && stored != null) {
                long alive = Math.max(0, plugin.clock().now() - stored.startedAt());
                Text.info(player, "Vie en cours : " + Durations.clock(alive) + " | éliminations : " + stored.kills() + " | points : " + life.points
                        + " (disponibles : " + (life.points - spent(life.ranks, rules().skillCosts())) + ")");
                StringBuilder skills = new StringBuilder("Compétences :");
                for (Skill skill : Skill.values()) skills.append(' ').append(skill.label).append(' ').append(life.ranks.getOrDefault(skill, 0)).append('/').append(rules().maxRank());
                Text.info(player, skills + " — /apocalypse skills");
            }
        }
        Text.info(player, "Carrière : " + career.lives() + " vie(s) | meilleure survie " + Durations.clock(career.bestSurvivalMs())
                + " | points cumulés " + career.careerPoints());
    }
}
