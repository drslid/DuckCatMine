package org.duckcatmine.apocalypse.spawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.loot.LootTable;
import org.duckcatmine.apocalypse.persistence.Store;
import org.duckcatmine.apocalypse.scenario.GroundZeroGenerator;
import org.duckcatmine.apocalypse.scenario.RefugeArea;
import org.duckcatmine.apocalypse.scenario.city.CityLayout;
import org.duckcatmine.apocalypse.scenario.city.Terrain;

/**
 * Drops a new life anywhere on the map (city streets, yards, parks, countryside), never inside the refuge walls, in a
 * building, in water or near the NEXUS lab, and away from other survivors. Gives the random common start kit.
 */
public final class DropService {
    private static final int ATTEMPTS = 12;

    private final DuckCatApocalypse plugin;
    private final Random random = new Random();

    public DropService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    /** Completes on the main thread with a safe standing spot, or the refuge spawn if none was found. */
    public CompletableFuture<Location> pick(World world) {
        CompletableFuture<Location> result = new CompletableFuture<>();
        attempt(world, 0, result);
        return result;
    }

    private void attempt(World world, int tries, CompletableFuture<Location> result) {
        int[] column = tries >= ATTEMPTS ? null : candidate(world);
        if (column == null) {
            if (tries >= ATTEMPTS) result.complete(plugin.scenario().refugeSpawn());
            else attempt(world, tries + 1, result);
            return;
        }
        world.getChunkAtAsync(column[0] >> 4, column[1] >> 4).whenComplete((chunk, error) -> {
            Location spot = chunk == null ? null : standingSpot(world, column[0], column[1]);
            if (spot != null) result.complete(spot);
            else attempt(world, tries + 1, result);
        });
    }

    private CityLayout layout(World world) {
        return world.getGenerator() instanceof GroundZeroGenerator ? CityLayout.of(world.getSeed()) : null;
    }

    /** A column chosen from the city plan alone (no chunk loaded): open ground, dry land, far from the lab. */
    public int[] candidate(World world) {
        CityLayout layout = layout(world);
        ApocalypseConfig.Spawn spawn = plugin.settings().play().spawn();
        RefugeArea refuge = plugin.scenario().refugeArea();
        WorldBorder border = world.getWorldBorder();
        int half = (int) (border.getSize() / 2) - spawn.borderMargin();
        int cx = border.getCenter().getBlockX(), cz = border.getCenter().getBlockZ();
        for (int k = 0; k < 24; k++) {
            boolean city = layout != null && random.nextDouble() < spawn.cityChance();
            int range = city ? Math.min(half, CityLayout.CITY_EDGE - 6) : half;
            if (range <= 8) range = half;
            int x = cx + random.nextInt(range * 2 + 1) - range;
            int z = cz + random.nextInt(range * 2 + 1) - range;
            if (refuge.near(x, z, spawn.refugeMargin())) continue;
            if (layout != null) {
                if (Math.abs(x - CityLayout.LAB_X) < 140 && Math.abs(z - CityLayout.LAB_Z) < 140) continue;
                if (!layout.openGround(x, z)) continue;
                if (layout.floorAt(x, z, layout.terrain().height(x, z)) <= Terrain.WATER_Y + 1) continue;
            }
            if (nearSurvivor(world, x, z, 40)) continue;
            return new int[]{x, z};
        }
        return null;
    }

    private boolean nearSurvivor(World world, int x, int z, int distance) {
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            if (Math.abs(at.getBlockX() - x) < distance && Math.abs(at.getBlockZ() - z) < distance) return true;
        }
        return false;
    }

    private Location standingSpot(World world, int x, int z) {
        CityLayout layout = layout(world);
        for (int radius = 0; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int bx = x + dx, bz = z + dz;
                    int top = world.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                    int ground = layout == null ? top : layout.floorAt(bx, bz, top);
                    if (top > ground + 1) continue;
                    Block feet = world.getBlockAt(bx, ground + 1, bz);
                    Block below = feet.getRelative(BlockFace.DOWN);
                    if (!feet.isPassable() || feet.isLiquid() || !feet.getRelative(BlockFace.UP).isPassable() || !below.isSolid()) continue;
                    Material floor = below.getType();
                    if (floor == Material.MAGMA_BLOCK || floor == Material.CACTUS || floor == Material.CAMPFIRE || floor == Material.POINTED_DRIPSTONE)
                        continue;
                    return new Location(world, bx + 0.5, ground + 1, bz + 0.5, random.nextFloat() * 360f - 180f, 0f);
                }
            }
        }
        return null;
    }

    /** Random common provisions for a new life; nothing if the previous life ended too quickly (no farming by dying). */
    public void giveKit(Player player, String previousRun) {
        ApocalypseConfig.StartKit kit = plugin.settings().play().kit();
        if (previousRun != null && kit.minPreviousLifeMs() > 0) {
            Store.RunRow previous = plugin.database().call(c -> Store.run(c, previousRun));
            if (previous != null && previous.endedAt() != null && previous.endedAt() - previous.startedAt() < kit.minPreviousLifeMs()) {
                Text.warn(player, "Ta vie précédente a été trop courte : aucune provision cette fois. Fouille vite les maisons.");
                return;
            }
        }
        List<ItemStack> given = new ArrayList<>();
        int rolls = kit.rollsMin() + (kit.rollsMax() > kit.rollsMin() ? random.nextInt(kit.rollsMax() - kit.rollsMin() + 1) : 0);
        LootTable table = kit.loot();
        for (int k = 0; k < rolls && !table.isEmpty(); k++) {
            LootTable.Entry entry = table.pick(random.nextDouble());
            ItemStack stack = plugin.items().resolve(entry.item(), entry.amount());
            if (stack != null) given.add(stack);
        }
        String weapon = null;
        if (!kit.weaponKits().isEmpty() && random.nextDouble() < kit.weaponChance()) {
            List<Map.Entry<String, List<ApocalypseConfig.Stack>>> kits = new ArrayList<>(kit.weaponKits().entrySet());
            Map.Entry<String, List<ApocalypseConfig.Stack>> chosen = kits.get(random.nextInt(kits.size()));
            weapon = chosen.getKey();
            for (ApocalypseConfig.Stack part : chosen.getValue()) {
                ItemStack stack = plugin.items().resolve(part.item(), part.amount());
                if (stack != null) given.add(stack);
            }
        }
        for (ItemStack stack : given)
            player.getInventory().addItem(stack).values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        if (given.isEmpty()) return;
        Text.info(player, "Tu as trouvé quelques provisions sur toi en te réveillant.");
        if (weapon != null) {
            ApocalypseConfig.Recipe recipe = plugin.settings().play().recipes().get(weapon);
            String result = recipe == null ? weapon : plugin.items().resolve(recipe.result().item(), 1) == null ? weapon : recipe.id();
            Text.good(player, "Avec un peu de chance, de quoi fabriquer une arme (" + label(result)
                    + ") : clic droit sur un établi, tu en trouveras dans les maisons.");
        }
    }

    private static String label(String recipe) {
        return switch (recipe) {
            case "club" -> "massue improvisée";
            case "blade" -> "lame de récupération";
            case "spear" -> "lance courte";
            default -> recipe;
        };
    }
}
