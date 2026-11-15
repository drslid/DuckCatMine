package org.duckcatmine.apocalypse.intro;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.persistence.Store;

/**
 * Individual introduction of about 50 seconds (spec §20): titles, sounds and particles only, played in the protected
 * refuge, skippable and replayable from a safe state. Seen once per player (kept across deaths); a disconnection stops it
 * cleanly and it plays again at the next entry.
 */
public final class IntroService implements Listener {
    private record Scene(int second, String title, String subtitle, NamedTextColor color, Sound sound, float pitch) {
    }

    private static final List<Scene> SCENES = List.of(
            new Scene(0, "PROJET NEXUS-13", "Laboratoire souterrain, niveau -4. 03 h 12.", NamedTextColor.DARK_RED, Sound.BLOCK_BELL_USE, 0.6f),
            new Scene(8, "ALERTE", "Rupture de confinement. Les sujets ne répondent plus.", NamedTextColor.RED, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.5f),
            new Scene(16, "JOUR 3", "La ville tombe. Les quartiers se taisent un à un.", NamedTextColor.GRAY, Sound.ENTITY_ZOMBIE_AMBIENT, 0.6f),
            new Scene(24, "« …ici le refuge, centre-ville… »", "« Une morsure : il reste une minute pour l'antidote. »", NamedTextColor.GOLD,
                    Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f),
            new Scene(33, "« …le bruit les attire… »", "« Fouillez, barricadez-vous, ne courez pas dans le noir. »", NamedTextColor.GOLD,
                    Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f),
            new Scene(41, "RÉVEIL", "Tu reprends connaissance, quelque part dans la ville.", NamedTextColor.WHITE, Sound.ENTITY_PLAYER_BREATH, 0.8f),
            new Scene(48, "Survis jusqu'à la prochaine aube.", "", NamedTextColor.DARK_RED, Sound.AMBIENT_CAVE, 0.7f));
    private static final int LENGTH = 54;

    private record Playing(BukkitTask task, Runnable then) {
    }

    private final DuckCatApocalypse plugin;
    private final Map<UUID, Playing> playing = new HashMap<>();

    public IntroService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.settings().play().intro();
    }

    public boolean seen(UUID player) {
        return plugin.database().call(c -> Store.introSeen(c, player));
    }

    public boolean playing(Player player) {
        return playing.containsKey(player.getUniqueId());
    }

    /** Plays the introduction; {@code then} runs when it ends or is skipped, never after a disconnection. */
    public void play(Player player, Runnable then) {
        stop(player, false);
        UUID id = player.getUniqueId();
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (LENGTH + 2) * 20, 3, false, false, false));
        player.sendMessage(Component.text("☣ Introduction (" + LENGTH + " s). ", NamedTextColor.GRAY)
                .append(Component.text("[Passer]", NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/apocalypse intro skip"))));
        int[] second = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stop(player, false);
                return;
            }
            for (Scene scene : SCENES) if (scene.second == second[0]) show(player, scene);
            ambience(player, second[0]);
            if (++second[0] > LENGTH) stop(player, true);
        }, 0L, 20L);
        playing.put(id, new Playing(task, then));
    }

    public boolean skip(Player player) {
        if (!playing(player)) return false;
        stop(player, true);
        return true;
    }

    private void stop(Player player, boolean completed) {
        Playing current = playing.remove(player.getUniqueId());
        if (current == null) return;
        current.task.cancel();
        if (!player.isOnline()) return;
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.DARKNESS);
        player.clearTitle();
        if (!completed) return;
        UUID id = player.getUniqueId();
        plugin.database().submit(c -> {
            Store.setIntroSeen(c, id);
            return null;
        });
        if (current.then != null) current.then.run();
    }

    private static void show(Player player, Scene scene) {
        player.showTitle(Title.title(Component.text(scene.title, scene.color), Component.text(scene.subtitle, NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(600), Duration.ofMillis(5200), Duration.ofMillis(900))));
        player.playSound(player.getLocation(), scene.sound, SoundCategory.MASTER, 0.9f, scene.pitch);
    }

    /** Alarm in the lab, then distant groans, radio static, then the city. */
    private static void ambience(Player player, int second) {
        Location at = player.getLocation();
        if (second < 16 && second % 2 == 0) player.playSound(at, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 0.35f, 0.5f);
        if (second == 1) player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 15 * 20, 0, false, false, false));
        if (second >= 8 && second < 24 && second % 3 == 0)
            player.playSound(at.clone().add(8, 0, 0), Sound.ENTITY_ZOMBIE_AMBIENT, SoundCategory.HOSTILE, 0.4f, 0.6f);
        if (second >= 24 && second < 41 && second % 2 == 1)
            player.playSound(at, Sound.BLOCK_NOTE_BLOCK_SNARE, SoundCategory.MASTER, 0.15f, 1.8f);
        if (second >= 41) player.spawnParticle(Particle.ASH, at.clone().add(0, 1.5, 0), 12, 2, 1, 2, 0);
    }

    /** Replay from a safe state only: in the refuge, without a bite. */
    public void replay(Player player) {
        if (!plugin.sessions().isSurvivor(player) || !plugin.scenario().inRefuge(player.getLocation())
                || plugin.infections().isInfected(player.getUniqueId())) {
            Text.error(player, "L'introduction se rejoue depuis le refuge, sans morsure.");
            return;
        }
        play(player, null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer(), false);
    }
}
