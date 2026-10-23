package org.duckcatmine.apocalypse.scenario;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.duckcatmine.apocalypse.DuckCatApocalypse;

/** Top-down picture of the generated Ground Zero (one pixel per block), for admins and the web panel. */
public final class MapRenderer {
    private static final int IN_FLIGHT = 12;

    private final DuckCatApocalypse plugin;
    private boolean running;

    public MapRenderer(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public File output() {
        return new File(plugin.getDataFolder(), "map.png");
    }

    /**
     * Loads (and if needed generates) every chunk in the square, then writes map.png. With {@code sliceY}, draws the
     * blocks at that height instead of the top surface (interiors), chests in red and barrels in magenta.
     */
    public String start(int radius, Integer sliceY, Consumer<String> report) {
        World world = plugin.scenario().world();
        if (world == null) return "Aucun monde associé.";
        if (running) return "Un rendu est déjà en cours.";
        running = true;
        RefugeArea refuge = plugin.scenario().refugeArea();
        int minX = refuge.centerX() - radius, minZ = refuge.centerZ() - radius, size = radius * 2;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Deque<long[]> queue = new ArrayDeque<>();
        for (int cx = minX >> 4; cx <= (minX + size - 1) >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= (minZ + size - 1) >> 4; cz++) queue.add(new long[]{cx, cz});
        }
        int total = queue.size();
        int[] done = {0};
        Runnable[] pump = new Runnable[1];
        pump[0] = () -> {
            while (!queue.isEmpty() && done[0] + IN_FLIGHT > total - queue.size()) {
                long[] next = queue.poll();
                world.getChunkAtAsync((int) next[0], (int) next[1], true).whenComplete((chunk, error) -> {
                    if (chunk != null) paint(chunk, image, minX, minZ, size, sliceY);
                    done[0]++;
                    if (done[0] % Math.max(1, total / 10) == 0 && done[0] < total) report.accept("Rendu : " + done[0] * 100 / total + " %");
                    if (done[0] == total) finish(image, report, minX, minZ, size, sliceY);
                    else Bukkit.getScheduler().runTask(plugin, pump[0]);
                });
            }
        };
        report.accept("Rendu de " + total + " tronçons (" + size + " × " + size + " blocs)…");
        pump[0].run();
        return null;
    }

    private void finish(BufferedImage image, Consumer<String> report, int minX, int minZ, int size, Integer sliceY) {
        File target = output();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String message;
            try {
                File temp = new File(target.getParentFile(), "map.png.tmp");
                ImageIO.write(image, "png", temp);
                if (!temp.renameTo(target)) throw new IOException("renommage impossible");
                String meta = "{\"minX\":" + minX + ",\"minZ\":" + minZ + ",\"size\":" + size + ",\"sliceY\":"
                        + (sliceY == null ? "null" : sliceY) + ",\"renderedAt\":" + System.currentTimeMillis() + "}";
                java.nio.file.Path metaTemp = new File(target.getParentFile(), "map.json.tmp").toPath();
                java.nio.file.Files.writeString(metaTemp, meta);
                java.nio.file.Files.move(metaTemp, new File(target.getParentFile(), "map.json").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                message = "Carte écrite : " + target.getPath();
            } catch (IOException e) {
                message = "Écriture de la carte impossible : " + e.getMessage();
            }
            String result = message;
            Bukkit.getScheduler().runTask(plugin, () -> {
                running = false;
                report.accept(result);
            });
        });
    }

    private static void paint(Chunk chunk, BufferedImage image, int minX, int minZ, int size, Integer sliceY) {
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(true, false, false);
        int baseX = chunk.getX() << 4, baseZ = chunk.getZ() << 4;
        int[] heights = new int[16 * 17];
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int px = baseX + dx - minX, pz = baseZ + dz - minZ;
                if (px < 0 || pz < 0 || px >= size || pz >= size) continue;
                if (sliceY != null) {
                    BlockData data = snapshot.getBlockData(dx, sliceY, dz);
                    Material material = data.getMaterial();
                    int rgb = material == Material.CHEST ? 0xFF2020 : material == Material.BARREL ? 0xFF00FF
                            : material.isAir() ? 0x101010 : data.getMapColor().asRGB();
                    image.setRGB(px, pz, rgb);
                    continue;
                }
                int y = snapshot.getHighestBlockYAt(dx, dz);
                BlockData data = snapshot.getBlockData(dx, y, dz);
                while (y > chunk.getWorld().getMinHeight() && (data.getMaterial().isAir() || data.getMaterial() == Material.LIGHT)) {
                    y--;
                    data = snapshot.getBlockData(dx, y, dz);
                }
                heights[dx * 17 + dz + 1] = y;
                Color color = data.getMaterial() == Material.BARRIER ? Color.fromRGB(0x3A3F47) : data.getMapColor();
                int north = dz > 0 ? heights[dx * 17 + dz] : y;
                double shade = y > north ? 1.0 : y < north ? 0.72 : 0.86;
                if (data.getMaterial() == Material.WATER) shade = 0.86;
                int rgb = ((int) (color.getRed() * shade) << 16) | ((int) (color.getGreen() * shade) << 8) | (int) (color.getBlue() * shade);
                image.setRGB(px, pz, rgb);
            }
        }
    }
}
