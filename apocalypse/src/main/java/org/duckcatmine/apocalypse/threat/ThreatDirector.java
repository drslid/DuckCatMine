package org.duckcatmine.apocalypse.threat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.persistence.PersistenceException;
import org.duckcatmine.apocalypse.persistence.Store;

/**
 * Persistent threat and tension cycle (spec §16). The level only rises while survivors actually play outside the
 * refuge (never during absences, never reset by a death); it raises the local cap and the share of special infected.
 * The cycle alternates calm, signs, pressure, a peak with a horde, then relief; nightfall and loud noises add pressure.
 */
public final class ThreatDirector {
    public enum Phase {
        CALM("calm", "calme"), SIGNS("signs", "indices"), PRESSURE("pressure", "pression"), PEAK("peak", "pic"), RELIEF("relief", "répit");

        final String key;
        final String label;

        Phase(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String label() {
            return label;
        }

        Phase next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final double START_LEVEL = 1.0;
    private static final double MAX_LEVEL = 50.0;
    private static final long PERSIST_MS = 30_000;
    private static final long NOISE_PEAK_DELAY_MS = 15_000;

    private final DuckCatApocalypse plugin;
    private final Random random = new Random();
    private double level = START_LEVEL;
    private Phase phase = Phase.CALM;
    private long phaseEnds;
    private long lastTick;
    private long lastPersist;
    private boolean wasNight;
    private int hordes;

    public ThreatDirector(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public void load() throws PersistenceException {
        String stored = plugin.database().call(c -> Store.meta(c, "threat_level"));
        try {
            level = stored == null ? START_LEVEL : Math.max(START_LEVEL, Math.min(MAX_LEVEL, Double.parseDouble(stored)));
        } catch (NumberFormatException e) {
            level = START_LEVEL;
        }
        long now = plugin.clock().now();
        phase = Phase.CALM;
        phaseEnds = now + seconds(Phase.CALM);
        lastTick = now;
    }

    public double level() {
        return level;
    }

    public Phase phase() {
        return phase;
    }

    public long secondsLeft() {
        return Math.max(0, (phaseEnds - plugin.clock().now()) / 1000);
    }

    public int hordes() {
        return hordes;
    }

    /** Extra local cap from the threat level, scaled by the current phase. */
    public int localCap(int base) {
        ApocalypseConfig.Threat rules = rules();
        int bonus = (int) Math.min(rules.maxLocalBonus(), Math.floor((level - START_LEVEL) / 2));
        return (int) Math.round((base + bonus) * rules.cycleFactors().getOrDefault(phase.key, 1.0));
    }

    /** Weight multiplier for special archetypes. */
    public double specialBoost() {
        ApocalypseConfig.Threat rules = rules();
        return Math.min(rules.specialBoostMax(), 1 + rules.specialBoostPerLevel() * (level - START_LEVEL));
    }

    public void setLevel(double value) throws PersistenceException {
        level = Math.max(START_LEVEL, Math.min(MAX_LEVEL, value));
        persist();
    }

    /** A gunshot or explosion during signs or pressure brings the peak forward. */
    public void loudNoise() {
        if (phase != Phase.SIGNS && phase != Phase.PRESSURE) return;
        long now = plugin.clock().now();
        long soon = now + NOISE_PEAK_DELAY_MS;
        if (phase == Phase.SIGNS) {
            phase = Phase.PRESSURE;
            phaseEnds = soon;
        } else {
            phaseEnds = Math.min(phaseEnds, soon);
        }
    }

    /** Every second. */
    public void tick() {
        World world = plugin.scenario().world();
        long now = plugin.clock().now();
        long delta = Math.max(0, Math.min(5_000, now - lastTick));
        lastTick = now;
        if (!plugin.scenario().isEnabled() || world == null) return;
        List<Player> active = active(world);
        if (active.isEmpty()) {
            phaseEnds += delta;
            return;
        }
        level = Math.min(MAX_LEVEL, level + rules().perHour() * delta / 3_600_000.0);
        if (now >= phaseEnds) enter(phase.next(), active, now);
        boolean night = !world.isDayTime();
        if (night && !wasNight) nightfall(active);
        wasNight = night;
        if (phase == Phase.SIGNS && random.nextInt(25) == 0) sign(active.get(random.nextInt(active.size())));
        if (now - lastPersist >= PERSIST_MS) {
            lastPersist = now;
            double snapshot = level;
            plugin.database().submit(c -> {
                Store.putMeta(c, "threat_level", String.format(Locale.ROOT, "%.4f", snapshot));
                return null;
            });
        }
    }

    public void persist() throws PersistenceException {
        double snapshot = level;
        plugin.database().call(c -> {
            Store.putMeta(c, "threat_level", String.format(Locale.ROOT, "%.4f", snapshot));
            return null;
        });
    }

    private void enter(Phase next, List<Player> active, long now) {
        phase = next;
        phaseEnds = now + seconds(next);
        switch (next) {
            case SIGNS -> {
                for (Player player : active) sign(player);
            }
            case PEAK -> {
                hordes++;
                for (Player player : active) horde(player, "Une horde approche…");
            }
            case RELIEF -> {
                for (Player player : active)
                    player.sendActionBar(Component.text("Le calme revient, pour l'instant.", NamedTextColor.GRAY));
            }
            default -> {
            }
        }
    }

    private void nightfall(List<Player> active) {
        for (Player player : active) {
            player.showTitle(Title.title(Component.empty(), Component.text("La nuit tombe. Ils sont plus nombreux.", NamedTextColor.DARK_RED),
                    Title.Times.times(java.time.Duration.ofMillis(500), java.time.Duration.ofSeconds(3), java.time.Duration.ofSeconds(1))));
            player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 1f, 0.6f);
        }
        if (rules().nightHorde()) {
            hordes++;
            for (Player player : active) horde(player, null);
        }
    }

    /** Distant groans from the direction the next wave will come from. */
    private void sign(Player player) {
        Location at = player.getLocation();
        double angle = random.nextDouble() * Math.PI * 2;
        Location far = at.clone().add(Math.cos(angle) * 14, 0, Math.sin(angle) * 14);
        player.playSound(far, random.nextBoolean() ? Sound.ENTITY_ZOMBIE_AMBIENT : Sound.ENTITY_ZOMBIE_VILLAGER_AMBIENT,
                SoundCategory.HOSTILE, 0.6f, 0.6f + random.nextFloat() * 0.2f);
    }

    /** A group spawned out of sight that walks to where the survivor is now, never homing in on them afterwards. */
    private void horde(Player player, String message) {
        ApocalypseConfig.Threat rules = rules();
        int size = rules.hordeMin() + random.nextInt(rules.hordeMax() - rules.hordeMin() + 1);
        size = (int) Math.round(size * Math.min(2.0, 1 + (level - START_LEVEL) * 0.05));
        int spawned = plugin.director().horde(player, size);
        if (spawned > 0 && message != null) {
            player.sendActionBar(Component.text(message, NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, SoundCategory.HOSTILE, 1f, 0.5f);
        }
    }

    private List<Player> active(World world) {
        List<Player> result = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (!player.isDead() && plugin.sessions().isSurvivor(player) && !plugin.scenario().inRefuge(player.getLocation()))
                result.add(player);
        }
        return result;
    }

    private long seconds(Phase value) {
        return (long) (rules().cycleSeconds().getOrDefault(value.key, 120.0) * 1000);
    }

    private ApocalypseConfig.Threat rules() {
        return plugin.settings().play().threat();
    }
}
