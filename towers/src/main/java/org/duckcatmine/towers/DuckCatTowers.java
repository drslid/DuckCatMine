package org.duckcatmine.towers;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.EditSession;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

public final class DuckCatTowers extends JavaPlugin implements CommandExecutor {
    private static final int DEFENSE_WIDTH = 3;
    private static final int DEFENSE_HEIGHT = 6;
    private static final int DEFENSE_RANGE = 32;
    private static final Pattern FILE_ID = Pattern.compile("[a-f0-9]{16}");
    private static final Set<String> STYLES = Set.of("medieval", "fantasy", "modern");
    private static final Set<String> AGED_COPPER = Set.of("EXPOSED_COPPER", "WEATHERED_COPPER", "OXIDIZED_COPPER",
        "WAXED_COPPER_BLOCK", "WAXED_EXPOSED_COPPER", "WAXED_WEATHERED_COPPER", "WAXED_OXIDIZED_COPPER");
    private static final Set<String> NATURAL_GROUND = Set.of("GRASS_BLOCK", "DIRT", "COARSE_DIRT", "PODZOL",
        "STONE", "DEEPSLATE", "SAND", "RED_SAND", "GRAVEL", "TUFF", "MOSS_BLOCK", "MUD", "CLAY", "SNOW_BLOCK");
    private static final Set<Material> REPLACEABLE = Set.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
        Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN, Material.DEAD_BUSH,
        Material.SNOW, Material.DANDELION, Material.POPPY);
    private final List<Tower> towers = new ArrayList<>();
    private final Map<Tower, Long> shotCounts = new HashMap<>();
    private File towerFile;

    private record Tower(String id, String world, int x, int y, int z, int height, int width, int length, boolean defensive) {}
    private record Ground(int y, int lowest, int[][] heights) {
        boolean needsSupports() { return y - lowest > 2; }
    }

    @Override public void onEnable() {
        getDataFolder().mkdirs();
        new File(getDataFolder(), "schematics").mkdirs();
        towerFile = new File(getDataFolder(), "towers.yml");
        loadTowers();
        getCommand("dctower").setExecutor(this);
        Bukkit.getScheduler().runTaskTimer(this, this::fireTowers, 20L, 20L);
        getLogger().info("Constructions chargées : " + towers.size());
    }

    @Override public void onDisable() { saveTowers(); }

    private void loadTowers() {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(towerFile);
        for (String entry : data.getStringList("towers")) {
            String[] p = entry.split(" ");
            if (p.length != 6 && p.length != 9) continue;
            try { towers.add(new Tower(p[0], p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]),
                Integer.parseInt(p[5]), p.length == 9 ? Integer.parseInt(p[6]) : 7,
                p.length == 9 ? Integer.parseInt(p[7]) : 7, p.length == 6 || Boolean.parseBoolean(p[8]))); }
            catch (NumberFormatException ignored) { }
        }
    }

    private void saveTowers() {
        YamlConfiguration data = new YamlConfiguration();
        List<String> entries = new ArrayList<>();
        for (Tower t : towers) entries.add(t.id + " " + t.world + " " + t.x + " " + t.y + " " + t.z + " " + t.height
            + " " + t.width + " " + t.length + " " + t.defensive);
        data.set("towers", entries);
        File temporary = new File(getDataFolder(), "towers.yml.tmp");
        try {
            data.save(temporary);
            Files.move(temporary.toPath(), towerFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) { throw new IllegalStateException("Impossible d'enregistrer les tours : " + error, error); }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("duckcat.towers.manage")) { sender.sendMessage("Accès refusé."); return true; }
        try {
            if (args.length == 2 && args[0].equals("resize") && Set.of("check", "apply").contains(args[1])) {
                resizeLegacyTowers(sender, args[1].equals("apply"));
                return true;
            }
            if (args.length == 7 && args[0].equals("relocate") && Set.of("check", "apply").contains(args[1])) {
                relocateLegacyTower(sender, args[1].equals("apply"), args[2], Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6]));
                return true;
            }
            if (args.length == 1 && args[0].equals("status")) {
                long defensive = towers.stream().filter(Tower::defensive).count();
                long shots = shotCounts.values().stream().mapToLong(Long::longValue).sum();
                sender.sendMessage("DCTOWER:" + defensive + " tours défensives, " + shots + " tirs depuis le démarrage.");
                for (Tower tower : towers) if (tower.defensive) {
                    World world = Bukkit.getWorld(tower.world);
                    boolean loaded = world != null && world.isChunkLoaded(tower.x >> 4, tower.z >> 4);
                    sender.sendMessage("DCTOWER:" + tower.id + " " + tower.world + " " + tower.x + " " + tower.z
                        + " : " + (loaded ? "chargée" : "hors portée des joueurs") + ", "
                        + shotCounts.getOrDefault(tower, 0L) + " tirs.");
                }
                return true;
            }
            if (args.length == 2 && args[0].equals("inspect")) {
                if (!FILE_ID.matcher(args[1]).matches()) throw new IllegalArgumentException("Fichier invalide.");
                sender.sendMessage("DCTOWER:" + inspect(readSchematic(args[1])));
                return true;
            }
            if (args.length == 5 && (args[0].equals("place") || args[0].equals("check"))) {
                String id = args[1], worldName = args[2];
                if (!STYLES.contains(id) && !FILE_ID.matcher(id).matches()) throw new IllegalArgumentException("Modèle inconnu.");
                if (!Set.of("world", "world_the_nether", "world_the_end").contains(worldName)) throw new IllegalArgumentException("Monde inconnu.");
                int x = Integer.parseInt(args[3]), z = Integer.parseInt(args[4]);
                if (Math.abs(x) > 29999900 || Math.abs(z) > 29999900) throw new IllegalArgumentException("Position invalide.");
                World world = Bukkit.getWorld(worldName);
                if (world == null) throw new IllegalArgumentException("Monde indisponible.");
                boolean defensive = STYLES.contains(id);
                if (towers.size() >= 100) throw new IllegalArgumentException("Maximum 100 constructions suivies.");
                if (defensive && towers.stream().filter(Tower::defensive).count() >= 12)
                    throw new IllegalArgumentException("Maximum 12 tours défensives actives.");
                Clipboard schematic = defensive ? null : readSchematic(id);
                int width = schematic == null ? DEFENSE_WIDTH : schematic.getDimensions().x();
                int height = schematic == null ? DEFENSE_HEIGHT : schematic.getDimensions().y();
                int length = schematic == null ? DEFENSE_WIDTH : schematic.getDimensions().z();
                for (Tower existing : towers) if (existing.world.equals(worldName)) {
                    if (Math.abs(existing.x - x) < (existing.width + width) / 2 + 2
                        && Math.abs(existing.z - z) < (existing.length + length) / 2 + 2)
                        throw new IllegalArgumentException("Trop proche d'une construction existante.");
                    if (defensive && existing.defensive && Math.abs(existing.x - x) < 20
                        && Math.abs(existing.z - z) < 20) throw new IllegalArgumentException("Trop proche d'une autre tour défensive.");
                }
                int x0 = x - width / 2, z0 = z - length / 2;
                Ground ground = groundAndCheck(world, x0, z0, width, height, length, schematic != null);
                if (args[0].equals("check")) {
                    sender.sendMessage("DCTOWER:Emplacement prêt en " + worldName + " X " + x + " Z " + z
                        + (ground.needsSupports() ? " avec plateforme et piliers." : " sur terrain plat."));
                    return true;
                }
                if (ground.needsSupports()) buildSupports(world, id, x0, z0, width, length, ground, schematic != null);
                int towerY = ground.y + 1 + (ground.needsSupports() && schematic != null ? 1 : 0);
                if (schematic == null) buildStyle(world, id, x, towerY, z, width, height);
                else pasteSchematic(world, schematic, x0, towerY, z0);
                towers.add(new Tower(id, worldName, x, towerY, z, height, width, length, defensive));
                saveTowers();
                sender.sendMessage("DCTOWER:" + (defensive ? "Tour posée" : "Construction posée") + " en " + worldName
                    + " X " + x + " Y " + towerY + " Z " + z
                    + (ground.needsSupports() ? " sur plateforme et piliers." : ".")
                    + (defensive ? " Défense active." : " Construction classique."));
                return true;
            }
            sender.sendMessage("Usage : dctower status | resize check/apply | relocate check/apply <monde> <ancienX> <ancienZ> <nouveauX> <nouveauZ> | inspect <id> | check/place <id> <monde> <x> <z>");
        } catch (Exception error) {
            sender.sendMessage("DCTOWER:Erreur : " + error.getMessage());
        }
        return true;
    }

    private Material legacyBlock(String style, int dx, int dy, int dz) {
        Material wall = style.equals("medieval") ? Material.STONE_BRICKS : style.equals("fantasy") ? Material.AMETHYST_BLOCK : Material.IRON_BLOCK;
        Material trim = style.equals("medieval") ? Material.SPRUCE_PLANKS : style.equals("fantasy") ? Material.PURPUR_BLOCK : Material.COPPER_BLOCK;
        Material floor = style.equals("medieval") ? Material.COBBLESTONE : style.equals("fantasy") ? Material.CALCITE : Material.SMOOTH_STONE;
        if (dy == 0) return floor;
        boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
        boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
        if (edge && dy >= 1 && dy <= 8 && !((dx == 0 || dz == 0) && dy >= 3 && dy <= 5))
            return corner ? trim : wall;
        if (edge && dy == 9) return trim;
        if (edge && dy == 10 && (corner || ((dx + dz) & 1) == 0)) return wall;
        if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2 && dy == 8) return floor;
        if (dx == 0 && dz == 0 && dy == 9) return style.equals("fantasy") ? Material.END_ROD : Material.LANTERN;
        return null;
    }

    private boolean matchesLegacy(Material expected, Material actual) {
        return actual == expected || (expected == Material.COPPER_BLOCK && AGED_COPPER.contains(actual.name()));
    }

    private void checkLegacyTower(Tower tower, boolean applying) {
        World world = Bukkit.getWorld(tower.world);
        if (world == null) throw new IllegalArgumentException("Monde indisponible pour la tour " + tower.x + ", " + tower.z + ".");
        world.getChunkAt(tower.x >> 4, tower.z >> 4);
        if (applying) for (Player player : world.getPlayers())
            if (player.getLocation().distanceSquared(new Location(world, tower.x, tower.y, tower.z)) < 16 * 16)
                throw new IllegalArgumentException("Un joueur est trop près de la tour " + tower.x + ", " + tower.z + ".");
        Material support = tower.id.equals("fantasy") ? Material.AMETHYST_BLOCK
            : tower.id.equals("modern") ? Material.IRON_BLOCK : Material.STONE_BRICKS;
        for (int dx : new int[] { -3, 3 }) for (int dz : new int[] { -3, 3 })
            if (world.getBlockAt(tower.x + dx, tower.y - 1, tower.z + dz).getType() == support)
                throw new IllegalArgumentException("La tour " + tower.x + ", " + tower.z + " a des piliers ou une fondation à vérifier manuellement.");
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = 0; dy < 11; dy++) {
            Material expected = legacyBlock(tower.id, dx, dy, dz);
            Block block = world.getBlockAt(tower.x + dx, tower.y + dy, tower.z + dz);
            Material actual = block.getType();
            boolean matches = expected == null ? REPLACEABLE.contains(actual)
                : matchesLegacy(expected, actual);
            if (!matches) throw new IllegalArgumentException("Tour " + tower.x + ", " + tower.z
                + " modifiée au bloc " + block.getX() + " " + block.getY() + " " + block.getZ()
                + " (" + actual + "). Migration annulée pour préserver cette construction.");
        }
    }

    private void resizeLegacyTowers(CommandSender sender, boolean apply) {
        List<Tower> legacy = towers.stream().filter(t -> t.defensive && t.width == 7 && t.height == 11 && t.length == 7).toList();
        if (legacy.isEmpty()) { sender.sendMessage("DCTOWER:Aucune ancienne tour 7 × 7 × 11 à remplacer."); return; }
        for (Tower tower : legacy) checkLegacyTower(tower, apply);
        if (!apply) { sender.sendMessage("DCTOWER:" + legacy.size() + " tours vérifiées, remplacement 3 × 3 × 6 possible."); return; }
        List<BlockState> snapshots = new ArrayList<>();
        for (Tower tower : legacy) {
            World world = Bukkit.getWorld(tower.world);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = 0; dy < 11; dy++)
                snapshots.add(world.getBlockAt(tower.x + dx, tower.y + dy, tower.z + dz).getState());
        }
        List<Tower> previous = new ArrayList<>(towers);
        Map<Tower, Long> previousShots = new HashMap<>(shotCounts);
        try {
            for (Tower tower : legacy) {
                World world = Bukkit.getWorld(tower.world);
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = 0; dy < 11; dy++)
                    if (legacyBlock(tower.id, dx, dy, dz) != null)
                        world.getBlockAt(tower.x + dx, tower.y + dy, tower.z + dz).setType(Material.AIR, false);
                buildStyle(world, tower.id, tower.x, tower.y, tower.z, DEFENSE_WIDTH, DEFENSE_HEIGHT);
                Tower resized = new Tower(tower.id, tower.world, tower.x, tower.y, tower.z,
                    DEFENSE_HEIGHT, DEFENSE_WIDTH, DEFENSE_WIDTH, true);
                towers.set(towers.indexOf(tower), resized);
                shotCounts.put(resized, shotCounts.getOrDefault(tower, 0L));
                shotCounts.remove(tower);
            }
            saveTowers();
        } catch (Exception error) {
            for (BlockState snapshot : snapshots) snapshot.update(true, false);
            towers.clear(); towers.addAll(previous);
            shotCounts.clear(); shotCounts.putAll(previousShots);
            throw new IllegalStateException("Migration annulée et blocs restaurés : " + error.getMessage(), error);
        }
        sender.sendMessage("DCTOWER:" + legacy.size() + " tours remplacées par le format 3 × 3 × 6. Portée " + DEFENSE_RANGE + " blocs.");
    }

    private void relocateLegacyTower(CommandSender sender, boolean apply, String worldName,
                                     int oldX, int oldZ, int newX, int newZ) {
        if (Math.abs(newX) > 29999900 || Math.abs(newZ) > 29999900)
            throw new IllegalArgumentException("Position invalide.");
        Tower old = towers.stream().filter(t -> t.defensive && t.world.equals(worldName)
            && t.x == oldX && t.z == oldZ && t.width == 7 && t.height == 11 && t.length == 7)
            .findFirst().orElseThrow(() -> new IllegalArgumentException("Ancienne tour introuvable ou déjà reconstruite."));
        World world = Bukkit.getWorld(worldName);
        if (world == null) throw new IllegalArgumentException("Monde indisponible.");
        for (Tower other : towers) if (other != old && other.world.equals(worldName)) {
            if (Math.abs(other.x - newX) < (other.width + DEFENSE_WIDTH) / 2 + 2
                && Math.abs(other.z - newZ) < (other.length + DEFENSE_WIDTH) / 2 + 2)
                throw new IllegalArgumentException("Nouvel emplacement trop proche d'une construction.");
            if (other.defensive && Math.abs(other.x - newX) < 20 && Math.abs(other.z - newZ) < 20)
                throw new IllegalArgumentException("Nouvel emplacement trop proche d'une autre tour.");
        }
        Ground ground = groundAndCheck(world, newX - 1, newZ - 1, DEFENSE_WIDTH, DEFENSE_HEIGHT, DEFENSE_WIDTH, false);
        if (ground.needsSupports()) throw new IllegalArgumentException("Choisis un emplacement plat pour cette migration.");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int surfaceY = world.getHighestBlockYAt(newX + dx, newZ + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Material surface = world.getBlockAt(newX + dx, surfaceY, newZ + dz).getType();
            if (!NATURAL_GROUND.contains(surface.name()))
                throw new IllegalArgumentException("Sol non naturel au nouvel emplacement (" + surface + "). Choisis une zone libre.");
        }
        int newY = ground.y + 1;
        int originalBlocks = 0, preservedBlocks = 0;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = 0; dy < 11; dy++) {
            Material expected = legacyBlock(old.id, dx, dy, dz);
            Material actual = world.getBlockAt(old.x + dx, old.y + dy, old.z + dz).getType();
            if (expected != null && matchesLegacy(expected, actual)) originalBlocks++;
            else if (!REPLACEABLE.contains(actual)) preservedBlocks++;
        }
        if (!apply) {
            sender.sendMessage("DCTOWER:Migration possible vers X " + newX + " Y " + newY + " Z " + newZ
                + ". " + originalBlocks + " blocs de l'ancienne tour à retirer, " + preservedBlocks + " autres blocs conservés.");
            return;
        }
        for (Player player : world.getPlayers())
            if (player.getLocation().distanceSquared(new Location(world, old.x, old.y, old.z)) < 16 * 16)
                throw new IllegalArgumentException("Un joueur est trop près de l'ancienne tour.");
        List<BlockState> snapshots = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = 0; dy < 11; dy++)
            snapshots.add(world.getBlockAt(old.x + dx, old.y + dy, old.z + dz).getState());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy < DEFENSE_HEIGHT; dy++)
            snapshots.add(world.getBlockAt(newX + dx, newY + dy, newZ + dz).getState());
        int oldIndex = towers.indexOf(old);
        Map<Tower, Long> previousShots = new HashMap<>(shotCounts);
        try {
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = 0; dy < 11; dy++) {
                Material expected = legacyBlock(old.id, dx, dy, dz);
                Block block = world.getBlockAt(old.x + dx, old.y + dy, old.z + dz);
                if (expected != null && matchesLegacy(expected, block.getType())) block.setType(Material.AIR, false);
            }
            buildStyle(world, old.id, newX, newY, newZ, DEFENSE_WIDTH, DEFENSE_HEIGHT);
            Tower replacement = new Tower(old.id, old.world, newX, newY, newZ,
                DEFENSE_HEIGHT, DEFENSE_WIDTH, DEFENSE_WIDTH, true);
            towers.set(oldIndex, replacement);
            shotCounts.put(replacement, shotCounts.getOrDefault(old, 0L));
            shotCounts.remove(old);
            saveTowers();
        } catch (Exception error) {
            for (BlockState snapshot : snapshots) snapshot.update(true, false);
            towers.set(oldIndex, old);
            shotCounts.clear(); shotCounts.putAll(previousShots);
            throw new IllegalStateException("Migration annulée et blocs restaurés : " + error.getMessage(), error);
        }
        sender.sendMessage("DCTOWER:Tour reconstruite en " + worldName + " X " + newX + " Y " + newY + " Z " + newZ
            + ". " + originalBlocks + " anciens blocs retirés, " + preservedBlocks + " blocs conservés. Portée " + DEFENSE_RANGE + " blocs.");
    }

    private Ground groundAndCheck(World world, int x0, int z0, int width, int height, int length, boolean schematic) {
        int centerX = x0 + width / 2, centerZ = z0 + length / 2;
        int ground = Integer.MIN_VALUE;
        int lowest = Integer.MAX_VALUE;
        int[][] heights = new int[width][length];
        for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < length; dz++) {
            int top = world.getHighestBlockYAt(x0 + dx, z0 + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            heights[dx][dz] = top;
            ground = Math.max(ground, top);
            lowest = Math.min(lowest, top);
        }
        boolean raised = ground - lowest > 2;
        int extra = raised && schematic ? 1 : 0;
        if (ground < world.getMinHeight() + 1 || ground + height + extra >= world.getMaxHeight()) throw new IllegalArgumentException("Hauteur hors limites.");
        if (ground - lowest > 48) throw new IllegalArgumentException("Terrain trop abrupt pour des piliers de 48 blocs. Choisis un point plus proche du sol.");
        for (Player player : world.getPlayers()) if (player.getLocation().distanceSquared(new Location(world, centerX, ground, centerZ)) < 16 * 16)
            throw new IllegalArgumentException("Un joueur est trop proche de la zone.");
        for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < length; dz++) {
            int gx = x0 + dx, gz = z0 + dz;
            for (int y = ground + 1; y <= ground + height + extra; y++) {
                Block block = world.getBlockAt(gx, y, gz);
                if (!REPLACEABLE.contains(block.getType())) throw new IllegalArgumentException("Des blocs occupent l'emplacement. Choisis une zone libre.");
            }
        }
        if (raised) for (int dx : supportOffsets(width)) for (int dz : supportOffsets(length))
            for (int y = heights[dx][dz] + 1; y <= ground; y++)
                if (!REPLACEABLE.contains(world.getBlockAt(x0 + dx, y, z0 + dz).getType()))
                    throw new IllegalArgumentException("Une construction occupe le passage des piliers. Choisis une zone libre.");
        return new Ground(ground, lowest, heights);
    }

    private int[] supportOffsets(int size) { return size < 3 ? new int[] { 0, size - 1 } : new int[] { 0, size / 2, size - 1 }; }

    private void buildSupports(World world, String style, int x0, int z0, int width, int length, Ground ground, boolean schematic) {
        Material support = style.equals("fantasy") ? Material.AMETHYST_BLOCK : style.equals("modern") ? Material.IRON_BLOCK : Material.STONE_BRICKS;
        for (int dx : supportOffsets(width)) for (int dz : supportOffsets(length))
            for (int y = ground.heights[dx][dz] + 1; y <= ground.y; y++)
                world.getBlockAt(x0 + dx, y, z0 + dz).setType(support, false);
        if (schematic) for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < length; dz++)
            world.getBlockAt(x0 + dx, ground.y + 1, z0 + dz).setType(support, false);
    }

    private void buildStyle(World world, String style, int x, int y, int z, int width, int height) {
        Material wall = style.equals("medieval") ? Material.STONE_BRICKS : style.equals("fantasy") ? Material.AMETHYST_BLOCK : Material.IRON_BLOCK;
        Material trim = style.equals("medieval") ? Material.SPRUCE_PLANKS : style.equals("fantasy") ? Material.PURPUR_BLOCK : Material.COPPER_BLOCK;
        Material floor = style.equals("medieval") ? Material.COBBLESTONE : style.equals("fantasy") ? Material.CALCITE : Material.SMOOTH_STONE;
        int radius = width / 2;
        int upperFloor = height - 3;
        int openingBottom = height / 3;
        int openingTop = Math.min(height - 4, openingBottom + 2);
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            world.getBlockAt(x + dx, y, z + dz).setType(floor, false);
            boolean edge = Math.abs(dx) == radius || Math.abs(dz) == radius;
            boolean corner = Math.abs(dx) == radius && Math.abs(dz) == radius;
            if (edge) for (int h = 1; h <= upperFloor; h++) {
                boolean opening = (dx == 0 || dz == 0) && h >= openingBottom && h <= openingTop;
                if (!opening) world.getBlockAt(x + dx, y + h, z + dz).setType(corner ? trim : wall, false);
            }
            if (edge) world.getBlockAt(x + dx, y + height - 2, z + dz).setType(trim, false);
            if (edge && (corner || ((dx + dz) & 1) == 0)) world.getBlockAt(x + dx, y + height - 1, z + dz).setType(wall, false);
            if (Math.abs(dx) < radius && Math.abs(dz) < radius) world.getBlockAt(x + dx, y + upperFloor, z + dz).setType(floor, false);
        }
        world.getBlockAt(x, y + height - 2, z).setType(style.equals("fantasy") ? Material.END_ROD : Material.LANTERN, false);
    }

    private Clipboard readSchematic(String id) throws IOException {
        File file = new File(new File(getDataFolder(), "schematics"), id + ".schem");
        if (!file.isFile() || file.length() > 1024 * 1024) throw new IllegalArgumentException("Fichier .schem introuvable ou trop volumineux.");
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IllegalArgumentException("Format .schem non reconnu.");
        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) { clipboard = reader.read(); }
        BlockVector3 d = clipboard.getDimensions();
        if (d.x() < 1 || d.y() < 1 || d.z() < 1 || d.x() > 24 || d.y() > 32 || d.z() > 24 ||
            (long) d.x() * d.y() * d.z() > 12000) throw new IllegalArgumentException(".schem limité à 24 × 32 × 24 blocs et 12 000 blocs au total.");
        return clipboard;
    }

    private String inspect(Clipboard clipboard) {
        BlockVector3 d = clipboard.getDimensions(), min = clipboard.getMinimumPoint();
        StringBuilder rows = new StringBuilder();
        for (int pz = 0; pz < 16; pz++) {
            if (pz > 0) rows.append('/');
            for (int px = 0; px < 16; px++) {
                int bx = Math.min(d.x() - 1, px * d.x() / 16), bz = Math.min(d.z() - 1, pz * d.z() / 16);
                char color = '.';
                for (int by = d.y() - 1; by >= 0; by--) {
                    String material = clipboard.getBlock(BlockVector3.at(min.x() + bx, min.y() + by, min.z() + bz)).getBlockType().id();
                    if (!material.equals("minecraft:air") && !material.equals("minecraft:cave_air")) {
                        color = material.contains("wood") || material.contains("plank") || material.contains("log") ? 'W'
                            : material.contains("glass") ? 'G' : material.contains("leaves") ? 'L'
                            : material.contains("copper") || material.contains("gold") ? 'M'
                            : material.contains("stone") || material.contains("brick") ? 'S' : 'B';
                        break;
                    }
                }
                rows.append(color);
            }
        }
        return "{\"width\":" + d.x() + ",\"height\":" + d.y() + ",\"length\":" + d.z()
            + ",\"preview\":\"" + rows + "\"}";
    }

    private void pasteSchematic(World world, Clipboard clipboard, int x0, int y, int z0) throws Exception {
        clipboard.setOrigin(clipboard.getMinimumPoint());
        try (EditSession edit = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            Operations.complete(new ClipboardHolder(clipboard).createPaste(edit)
                .to(BlockVector3.at(x0, y, z0)).ignoreAirBlocks(true).build());
        }
    }

    private void fireTowers() {
        for (Tower tower : towers) {
            if (!tower.defensive) continue;
            World world = Bukkit.getWorld(tower.world);
            if (world == null || !world.isChunkLoaded(tower.x >> 4, tower.z >> 4)) continue;
            // The roof has battlements: start the beam above them so it does not hit its own tower.
            Location origin = new Location(world, tower.x + .5, tower.y + tower.height + 4.5, tower.z + .5);
            Monster target = null;
            double nearest = Double.POSITIVE_INFINITY;
            for (Entity entity : world.getNearbyEntities(origin, DEFENSE_RANGE, 28, DEFENSE_RANGE)) if (entity instanceof Monster monster && !monster.isDead()) {
                Location eye = monster.getEyeLocation();
                double dx = eye.getX() - origin.getX(), dz = eye.getZ() - origin.getZ();
                if (dx * dx + dz * dz > DEFENSE_RANGE * DEFENSE_RANGE) continue;
                Vector path = eye.toVector().subtract(origin.toVector());
                double distance = path.lengthSquared();
                if (distance < .01 || distance >= nearest || world.rayTraceBlocks(origin, path, Math.sqrt(distance)) != null) continue;
                nearest = distance;
                target = monster;
            }
            if (target == null) continue;
            Location end = target.getEyeLocation();
            Vector path = end.toVector().subtract(origin.toVector());
            target.damage(3.0);
            shotCounts.merge(tower, 1L, Long::sum);
            for (int pulse = 0; pulse < 2; pulse++) Bukkit.getScheduler().runTaskLater(this, () -> {
                for (double step = 0; step <= 1; step += 0.9 / path.length())
                    world.spawnParticle(Particle.END_ROD, origin.clone().add(path.clone().multiply(step)), 1, 0, 0, 0, 0);
            }, pulse * 2L);
            world.playSound(origin, Sound.ENTITY_ARROW_SHOOT, .35f, 1.4f);
        }
    }
}
