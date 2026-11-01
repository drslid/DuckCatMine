package org.duckcatmine.apocalypse.session;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Captures and applies the parts of a player that belong to one profile (classic or survivor). */
public final class ProfileCodec {
    private ProfileCodec() {
    }

    public static ProfileSnapshot capture(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] items = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) items[i] = contents[i] == null ? ItemStack.empty() : contents[i];
        List<ProfileSnapshot.Effect> effects = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            effects.add(new ProfileSnapshot.Effect(effect.getType().getKey().toString(), effect.getDuration(), effect.getAmplifier(),
                    effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
        }
        Location location = player.getLocation();
        return new ProfileSnapshot(ItemStack.serializeItemsAsBytes(items), player.getLevel(), player.getExp(),
                player.getTotalExperience(), player.getHealth(), player.getFoodLevel(), player.getSaturation(),
                player.getExhaustion(), player.getFireTicks(), player.getRemainingAir(), effects,
                location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(),
                location.getPitch(), player.getGameMode().name());
    }

    public static ItemStack[] items(ProfileSnapshot snapshot) {
        return ItemStack.deserializeItemsFromBytes(snapshot.items());
    }

    public static void apply(Player player, ProfileSnapshot snapshot) {
        ItemStack[] stored = items(snapshot);
        ItemStack[] contents = new ItemStack[player.getInventory().getSize()];
        System.arraycopy(stored, 0, contents, 0, Math.min(stored.length, contents.length));
        player.getInventory().setContents(contents);
        player.setItemOnCursor(null);
        player.setLevel(Math.max(0, snapshot.level()));
        player.setExp(Math.max(0f, Math.min(0.9999f, snapshot.exp())));
        player.setTotalExperience(Math.max(0, snapshot.totalExperience()));
        player.setHealth(Math.max(0.5, Math.min(maxHealth(player), snapshot.health())));
        player.setFoodLevel(snapshot.food());
        player.setSaturation(snapshot.saturation());
        player.setExhaustion(snapshot.exhaustion());
        player.setFireTicks(snapshot.fireTicks());
        player.setRemainingAir(snapshot.air());
        player.setFallDistance(0);
        clearEffects(player);
        for (ProfileSnapshot.Effect effect : snapshot.effects()) {
            NamespacedKey key = NamespacedKey.fromString(effect.key());
            PotionEffectType type = key == null ? null : Registry.MOB_EFFECT.get(key);
            if (type != null) player.addPotionEffect(new PotionEffect(type, effect.duration(), effect.amplifier(),
                    effect.ambient(), effect.particles(), effect.icon()));
        }
        player.setGameMode(gameMode(snapshot.gameMode()));
    }

    /** A new life starts healthy and empty-handed. */
    public static void resetForNewLife(Player player) {
        clearCarried(player);
        player.setLevel(0);
        player.setExp(0);
        player.setTotalExperience(0);
        clearEffects(player);
        player.setHealth(maxHealth(player));
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.setExhaustion(0);
        player.setFireTicks(0);
        player.setRemainingAir(player.getMaximumAir());
        player.setFallDistance(0);
        player.setGameMode(GameMode.SURVIVAL);
    }

    public static void clearCarried(Player player) {
        player.getInventory().clear();
        player.setItemOnCursor(null);
    }

    public static Location location(ProfileSnapshot snapshot) {
        World world = Bukkit.getWorld(snapshot.world());
        return world == null ? null : new Location(world, snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch());
    }

    private static void clearEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
    }

    private static double maxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20 : attribute.getValue();
    }

    private static GameMode gameMode(String name) {
        try {
            return GameMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return GameMode.SURVIVAL;
        }
    }
}
