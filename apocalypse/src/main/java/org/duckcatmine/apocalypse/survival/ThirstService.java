package org.duckcatmine.apocalypse.survival;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.items.ItemRegistry;

/**
 * Thirst of a Ground Zero life, shown in the experience bar (number = %, bar = fill). The bar belongs to the scenario
 * profile, so it is saved and restored with it; vanilla experience is not collected in Ground Zero.
 */
public final class ThirstService implements Listener {
    public static final double BOTTLE = 0.5, TAP_WATER = 0.35, MILK = 0.2;
    private final DuckCatApocalypse plugin;
    private final NamespacedKey life;
    private final Map<UUID, Long> warned = new HashMap<>();
    private long seconds;

    public ThirstService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
        life = new NamespacedKey(plugin, "thirst_life");
    }

    /** Fraction drunk per second of play: empty after {@code minutes}, faster while sprinting. */
    public static double drain(double minutes, boolean sprinting) {
        return (sprinting ? 1.5 : 1) / (Math.max(1, minutes) * 60);
    }

    public double level(Player player) {
        return Math.max(0, Math.min(1, player.getExp()));
    }

    public void set(Player player, double value) {
        double clamped = Math.max(0, Math.min(1, value));
        player.setExp((float) Math.min(0.9999, clamped));
        player.setLevel((int) Math.round(clamped * 100));
    }

    /** Every second. */
    public void tick() {
        seconds++;
        World world = plugin.scenario().world();
        var rules = plugin.settings().play().survival();
        if (world == null || !plugin.scenario().isEnabled() || !plugin.clock().running()) return;
        for (Player player : world.getPlayers()) {
            if (!plugin.sessions().isSurvivor(player) || player.isDead()
                    || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) continue;
            String run = plugin.sessions().runOf(player);
            if (run != null && !run.equals(player.getPersistentDataContainer().get(life, PersistentDataType.STRING))) {
                player.getPersistentDataContainer().set(life, PersistentDataType.STRING, run);
                set(player, 1);
                continue;
            }
            if (!rules.thirst()) continue;
            double level = Math.max(0, level(player) - drain(rules.thirstMinutes(), player.isSprinting()));
            set(player, level);
            effects(player, level);
        }
    }

    private void effects(Player player, double level) {
        long now = plugin.clock().now();
        if (level < 0.25) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 0, true, false, true));
            if (now - warned.getOrDefault(player.getUniqueId(), 0L) > 30_000) {
                warned.put(player.getUniqueId(), now);
                player.sendActionBar(Component.text(level <= 0 ? "Déshydraté ! Bois de l'eau tout de suite." : "Tu as soif : trouve de l'eau.",
                        NamedTextColor.AQUA));
            }
        }
        if (level < 0.10) player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 50, 0, true, false, true));
        if (level <= 0 && seconds % 4 == 0 && !plugin.scenario().inRefuge(player.getLocation()))
            player.damage(1.0, DamageSource.builder(DamageType.DRY_OUT).build());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExperience(PlayerExpChangeEvent event) {
        if (plugin.sessions().isSurvivor(event.getPlayer())) event.setAmount(0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!plugin.sessions().isSurvivor(player) || !plugin.settings().play().survival().thirst()) return;
        double gain = gain(event.getItem());
        if (gain <= 0) return;
        double level = Math.min(1, level(player) + gain);
        set(player, level);
        player.sendActionBar(Component.text("Hydratation : " + Math.round(level * 100) + " %", NamedTextColor.AQUA));
    }

    private double gain(ItemStack item) {
        if (plugin.items().is(item, ItemRegistry.WATER)) return BOTTLE;
        if (item.getType() == Material.MILK_BUCKET) return MILK;
        if (item.getType() == Material.POTION) {
            var contents = item.getData(DataComponentTypes.POTION_CONTENTS);
            if (contents != null && contents.potion() == PotionType.WATER) return TAP_WATER;
        }
        return 0;
    }

    public void forget(UUID id) {
        warned.remove(id);
    }
}
