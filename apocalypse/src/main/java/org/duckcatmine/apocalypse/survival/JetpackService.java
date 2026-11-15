package org.duckcatmine.apocalypse.survival;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.items.ItemRegistry;

/**
 * Worn on the chest, the jetpack lifts its wearer while the jump key is held in the air, burning petrol from a jerrycan
 * of the inventory. Thrust is a strong levitation (no anti-fly kick), and a short slow fall after it lets you land.
 * Loud: the infected hear it from far away.
 */
public final class JetpackService {
    public static final String ID = "jetpack";
    private static final double LITRES_PER_TICK = 0.02;
    private final DuckCatApocalypse plugin;
    private final Map<UUID, Long> warned = new HashMap<>();
    private long ticks;

    public JetpackService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    /** Every tick. */
    public void tick() {
        ticks++;
        World world = plugin.scenario().world();
        if (world == null) return;
        for (Player player : world.getPlayers()) {
            ItemStack chest = player.getInventory().getChestplate();
            if (chest == null || !plugin.items().is(chest, ID) || player.isDead() || player.isInsideVehicle()) continue;
            boolean jump = player.getCurrentInput().isJump();
            if (!jump) continue;
            ItemStack can = fuel(player);
            if (can == null) {
                Long last = warned.get(player.getUniqueId());
                if (last == null || ticks - last > 100) {
                    warned.put(player.getUniqueId(), ticks);
                    Text.warn(player, "Jetpack à sec : garde un jerrican plein dans ton inventaire.");
                }
                continue;
            }
            plugin.items().setFuel(can, Math.max(0, plugin.items().fuel(can) - LITRES_PER_TICK));
            player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 4, 6, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 50, 0, false, false, false));
            var at = player.getLocation();
            world.spawnParticle(Particle.FLAME, at, 3, 0.15, 0.05, 0.15, 0.01);
            world.spawnParticle(Particle.LARGE_SMOKE, at, 1, 0.1, 0.05, 0.1, 0.01);
            if (ticks % 6 == 0) world.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 0.6f);
            if (ticks % 20 == 0) plugin.noise().emit(at, 48, player);
        }
    }

    private ItemStack fuel(Player player) {
        for (ItemStack stack : player.getInventory().getContents())
            if (stack != null && plugin.items().is(stack, ItemRegistry.JERRYCAN) && plugin.items().fuel(stack) > 0.01) return stack;
        return null;
    }
}
