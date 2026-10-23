package org.duckcatmine.apocalypse.siege;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.crafting.PlacedBlocks;
import org.duckcatmine.apocalypse.items.ItemRegistry;
import org.duckcatmine.apocalypse.persistence.PersistenceException;
import org.duckcatmine.apocalypse.persistence.Store;

/**
 * Infected that are blocked on the way to their prey hit the obstacle (spec §14): doors, barricades, built walls. Natural
 * rock and soil, containers, scenario stations, the refuge and claimed bases whose members are all away are never
 * attacked. Damage is shown as cracks, budgets bound the destruction, and survivors can patch a damaged block.
 */
public final class SiegeManager implements Listener {
    private static final double MAX_TARGET_DISTANCE = 16;
    private static final long FORGET_MS = 60_000;
    private static final long ATTACKER_MS = 3_000;
    private static final double VIEW_DISTANCE = 32;

    private static final class Damage {
        final Block block;
        final String category;
        final double resistance;
        double points;
        long lastHit;
        final Map<UUID, Long> attackers = new HashMap<>();

        Damage(Block block, String category, double resistance) {
            this.block = block;
            this.category = category;
            this.resistance = resistance;
        }

        float progress() {
            return (float) Math.max(0, Math.min(0.99, points / resistance));
        }
    }

    private final DuckCatApocalypse plugin;
    private final Map<Block, Damage> damage = new HashMap<>();
    private final Map<UUID, Long> nextHit = new HashMap<>();
    private final Map<UUID, Long> nextRepair = new HashMap<>();
    private Boolean override;
    private long second;
    private int brokenThisSecond;
    private int brokenSinceReport;
    private long nextReport;

    public SiegeManager(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public void load() throws PersistenceException {
        String stored = plugin.database().call(c -> Store.meta(c, "siege_enabled"));
        override = stored == null ? null : Boolean.valueOf(stored);
    }

    public boolean enabled() {
        return override != null ? override : plugin.settings().play().siege().enabled();
    }

    /** Admin switch, persisted: it outlives restarts and config reloads. */
    public void setEnabled(boolean value) throws PersistenceException {
        plugin.database().call(c -> {
            Store.putMeta(c, "siege_enabled", Boolean.toString(value));
            return null;
        });
        override = value;
        if (!value) clear();
    }

    public int damagedBlocks() {
        return damage.size();
    }

    // ------------------------------------------------------------------ attack (from the mob tick)

    /** Called for each tracked infected every 5 ticks. */
    public void consider(Mob mob, ApocalypseConfig.Archetype archetype, long lastMovedAt, long now) {
        attempt(mob, archetype, lastMovedAt, now);
    }

    /** One siege step; returns why nothing was hit (for the self-test), or null after a hit. */
    public String attempt(Mob mob, ApocalypseConfig.Archetype archetype, long lastMovedAt, long now) {
        if (!enabled() || archetype.siegePower() <= 0) return "désactivé";
        ApocalypseConfig.Siege rules = plugin.settings().play().siege();
        if (!(mob.getTarget() instanceof LivingEntity target) || !target.isValid() || !target.getWorld().equals(mob.getWorld())) return "sans cible";
        if (now - lastMovedAt < rules.blockedSeconds() * 1000) return "pas bloqué";
        if (nextHit.getOrDefault(mob.getUniqueId(), 0L) > now) return "cadence";
        double distance = mob.getLocation().distance(target.getLocation());
        if (distance > MAX_TARGET_DISTANCE || distance < 1.2) return "distance " + distance;
        Block obstacle = obstacle(mob, target);
        if (obstacle == null) return "aucun obstacle";
        String category = category(obstacle);
        if (category == null) return "bloc non attaquable " + obstacle.getType();
        if (protectedBlock(obstacle)) return "bloc protégé";
        Double resistance = rules.resistance().get(category);
        if (resistance == null) return "résistance inconnue " + category;
        Block key = obstacle.getBlockData() instanceof Door door && door.getHalf() == Bisected.Half.TOP ? obstacle.getRelative(0, -1, 0) : obstacle;
        Damage state = damage.computeIfAbsent(key, block -> new Damage(block, category, resistance));
        state.attackers.values().removeIf(at -> now - at > ATTACKER_MS);
        if (!state.attackers.containsKey(mob.getUniqueId()) && state.attackers.size() >= rules.maxAttackersPerBlock()) return "trop d'assaillants";
        state.attackers.put(mob.getUniqueId(), now);
        nextHit.put(mob.getUniqueId(), now + (long) (rules.hitSeconds() * 1000));
        hit(mob, state, archetype.siegePower(), now, rules);
        return null;
    }

    /** First non-passable block between the infected and its prey, at feet or head height, one step ahead. */
    private static Block obstacle(Mob mob, LivingEntity target) {
        Location from = mob.getLocation();
        Vector direction = target.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (direction.lengthSquared() < 0.04) return null;
        direction.normalize();
        for (double ahead : new double[] {0.7, 1.2}) {
            for (double height : new double[] {0.2, 1.2}) {
                Block block = from.clone().add(direction.clone().multiply(ahead)).add(0, height, 0).getBlock();
                if (!block.isPassable() && !block.isLiquid() && !block.equals(from.getBlock())) return block;
            }
        }
        return null;
    }

    private void hit(Mob mob, Damage state, double power, long now, ApocalypseConfig.Siege rules) {
        Block block = state.block;
        state.points += power;
        state.lastHit = now;
        mob.swingMainHand();
        World world = block.getWorld();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        world.playSound(center, switch (state.category) {
            case "wood", "barricade" -> Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR;
            case "metal", "reinforced" -> Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR;
            case "glass" -> Sound.BLOCK_GLASS_HIT;
            default -> Sound.BLOCK_STONE_HIT;
        }, 1.0f, 0.8f + (float) Math.random() * 0.3f);
        world.spawnParticle(Particle.BLOCK, center, 6, 0.3, 0.3, 0.3, block.getBlockData());
        plugin.noise().emit(center, plugin.settings().play().noise().siege(), null);
        if (state.points >= state.resistance) {
            if (second != now / 1000) {
                second = now / 1000;
                brokenThisSecond = 0;
            }
            if (brokenThisSecond >= rules.maxBlocksPerSecond()) {
                state.points = state.resistance - 0.01;
                show(state);
                return;
            }
            brokenThisSecond++;
            destroy(state);
        } else {
            show(state);
        }
    }

    private void destroy(Damage state) {
        Block block = state.block;
        World world = block.getWorld();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        damage.remove(block);
        hide(state);
        BlockData data = block.getBlockData();
        world.spawnParticle(Particle.BLOCK, center, 30, 0.35, 0.35, 0.35, data);
        world.playSound(center, switch (state.category) {
            case "wood", "barricade" -> Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR;
            case "glass" -> Sound.BLOCK_GLASS_BREAK;
            case "metal", "reinforced" -> Sound.BLOCK_ANVIL_LAND;
            default -> Sound.BLOCK_STONE_BREAK;
        }, 1.2f, 0.8f);
        if ("glass".equals(state.category)) plugin.noise().emit(center, plugin.settings().play().noise().glass(), null);
        if (data instanceof Door door) {
            Block other = block.getRelative(0, door.getHalf() == Bisected.Half.BOTTOM ? 1 : -1, 0);
            if (other.getType() == block.getType()) {
                plugin.placed().remove(other);
                other.setType(Material.AIR, false);
            }
            plugin.placed().remove(block);
            block.setType(Material.AIR, false);
        } else {
            plugin.placed().remove(block);
            block.setType(Material.AIR, true);
        }
        brokenSinceReport++;
    }

    // ------------------------------------------------------------------ rules

    /** Damage category of a block, or null when infected never attack it (natural ground, rock, furniture...). */
    String category(Block block) {
        PlacedBlocks.Placed placed = plugin.placed().get(block);
        if (placed != null) {
            return switch (placed.kind()) {
                case "barricade" -> "barricade";
                case "barricade_reinforced" -> "reinforced";
                default -> null;
            };
        }
        if (block.getState(false) instanceof TileState) return null;
        return category(block.getType());
    }

    public static String category(Material type) {
        String name = type.name();
        if (name.contains("ORE") || name.startsWith("RAW_") || name.startsWith("INFESTED_")) return null;
        if (Tag.WOOL.isTagged(type) || Tag.LEAVES.isTagged(type) || type == Material.HAY_BLOCK || type == Material.DRIED_KELP_BLOCK)
            return "soft";
        if (name.contains("GLASS")) return "glass";
        if (name.contains("IRON") || name.contains("COPPER") || name.contains("CHAIN") || name.contains("ANVIL")) return "metal";
        if (Tag.PLANKS.isTagged(type) || Tag.LOGS.isTagged(type) || Tag.WOODEN_DOORS.isTagged(type) || Tag.WOODEN_TRAPDOORS.isTagged(type)
                || Tag.WOODEN_FENCES.isTagged(type) || Tag.FENCE_GATES.isTagged(type) || Tag.WOODEN_SLABS.isTagged(type)
                || Tag.WOODEN_STAIRS.isTagged(type) || type == Material.BOOKSHELF) return "wood";
        if (name.contains("BRICK") || name.contains("CONCRETE") && !name.endsWith("_POWDER") || name.contains("TERRACOTTA")
                || name.contains("COBBLESTONE") || name.startsWith("POLISHED_") || name.startsWith("SMOOTH_") || name.contains("QUARTZ")
                || name.startsWith("CUT_") || name.startsWith("CHISELED_") || name.contains("TILES") || name.endsWith("_WALL")
                || name.contains("PURPUR")) return "stone";
        return null;
    }

    /** Refuge, world border, and claimed bases whose members are all away from the scenario. */
    private boolean protectedBlock(Block block) {
        Location location = block.getLocation();
        if (plugin.scenario().inRefuge(location) || !block.getWorld().getWorldBorder().isInside(location)) return true;
        List<PlacedBlocks.Claim> claims = plugin.placed().claimsAt(location);
        if (claims.isEmpty()) return false;
        for (PlacedBlocks.Claim claim : claims) {
            if (present(claim.owner(), block.getWorld())) return false;
        }
        return true;
    }

    /** The claim owner plays in the scenario right now. */
    private boolean present(UUID owner, World world) {
        Player player = owner == null ? null : plugin.getServer().getPlayer(owner);
        return player != null && !player.isDead() && player.getWorld().equals(world) && plugin.sessions().isSurvivor(player);
    }

    // ------------------------------------------------------------------ display, upkeep

    private void show(Damage state) {
        Location location = state.block.getLocation();
        float progress = state.progress();
        int source = sourceId(state.block);
        for (Player player : state.block.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= VIEW_DISTANCE * VIEW_DISTANCE)
                player.sendBlockDamage(location, Math.max(0.01f, progress), source);
        }
    }

    private void hide(Damage state) {
        Location location = state.block.getLocation();
        int source = sourceId(state.block);
        for (Player player : state.block.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= (VIEW_DISTANCE + 16) * (VIEW_DISTANCE + 16))
                player.sendBlockDamage(location, 0f, source);
        }
    }

    private static int sourceId(Block block) {
        return 0x5E000000 | (block.getX() * 31 * 31 + block.getY() * 31 + block.getZ()) & 0x00FFFFFF;
    }

    /** Every second: forgets old damage (cracks fade), drops gone blocks, logs a summary once a minute. */
    public void tick() {
        long now = plugin.clock().now();
        Iterator<Damage> states = damage.values().iterator();
        while (states.hasNext()) {
            Damage state = states.next();
            boolean gone = state.block.getType().isAir() || category(state.block) == null;
            if (gone || now - state.lastHit > FORGET_MS) {
                states.remove();
                if (!gone) hide(state);
            }
        }
        nextHit.values().removeIf(at -> at < now - 10_000);
        nextRepair.values().removeIf(at -> at < now);
        if (now >= nextReport) {
            if (brokenSinceReport > 0)
                plugin.getLogger().info("Siège : " + brokenSinceReport + " bloc(s) détruit(s) par les infectés depuis le dernier relevé.");
            brokenSinceReport = 0;
            nextReport = now + 60_000;
        }
    }

    public void clear() {
        for (Damage state : List.copyOf(damage.values())) hide(state);
        damage.clear();
        nextHit.clear();
    }

    // ------------------------------------------------------------------ repairs

    /** Right-click a damaged block with the matching material: planks for wood, scrap for metal, stone for masonry. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRepair(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) return;
        Block clicked = event.getClickedBlock();
        Block key = clicked.getBlockData() instanceof Door door && door.getHalf() == Bisected.Half.TOP ? clicked.getRelative(0, -1, 0) : clicked;
        Damage state = damage.get(key);
        if (state == null) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        double restore = restore(state.category, hand);
        if (restore <= 0) {
            if (!hand.getType().isAir() || "glass".equals(state.category)) return;
            player.sendActionBar(Component.text(hint(state.category), NamedTextColor.GRAY));
            return;
        }
        event.setCancelled(true);
        long now = plugin.clock().now();
        if (nextRepair.getOrDefault(player.getUniqueId(), 0L) > now) return;
        nextRepair.put(player.getUniqueId(), now + 1000);
        hand.setAmount(hand.getAmount() - 1);
        state.points = Math.max(0, state.points - restore);
        Location center = key.getLocation().add(0.5, 0.5, 0.5);
        key.getWorld().playSound(center, "metal".equals(state.category) || "reinforced".equals(state.category)
                ? Sound.BLOCK_ANVIL_USE : Sound.BLOCK_WOOD_PLACE, 0.8f, 1.2f);
        plugin.noise().emit(center, plugin.settings().play().noise().crafting(), player);
        int percent = (int) Math.round(100 * (1 - state.points / state.resistance));
        player.sendActionBar(Component.text("Bloc consolidé : " + percent + " %", NamedTextColor.GREEN));
        if (state.points <= 0) {
            damage.remove(key);
            hide(state);
        } else {
            show(state);
        }
    }

    private double restore(String category, ItemStack hand) {
        if (hand == null || hand.getType().isAir()) return 0;
        ItemRegistry items = plugin.items();
        return switch (category) {
            case "wood", "barricade", "soft" -> Tag.PLANKS.isTagged(hand.getType()) && items.idOf(hand) == null ? 12 : 0;
            case "metal", "reinforced" -> items.is(hand, "scrap") ? 60 : 0;
            case "stone" -> (hand.getType() == Material.COBBLESTONE || hand.getType() == Material.BRICKS) && items.idOf(hand) == null ? 30 : 0;
            default -> 0;
        };
    }

    private static String hint(String category) {
        return switch (category) {
            case "wood", "barricade", "soft" -> "Endommagé : clic droit avec des planches pour consolider.";
            case "metal", "reinforced" -> "Endommagé : clic droit avec de la ferraille pour consolider.";
            case "stone" -> "Endommagé : clic droit avec de la pierre ou des briques pour consolider.";
            default -> "Endommagé.";
        };
    }
}
