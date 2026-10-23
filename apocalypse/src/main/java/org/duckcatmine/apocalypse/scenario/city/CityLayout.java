package org.duckcatmine.apocalypse.scenario.city;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;

/**
 * Deterministic city plan of Ground Zero for one world seed. A square street grid of 48-block cells around the
 * refuge (cell 0,0), districts by distance, then countryside with farms, cabins, checkpoints and the NEXUS
 * complex. Generation and loot identification replay the same plan, so they always agree.
 */
public final class CityLayout {
    public static final int CELL = 48;
    public static final int CITY_CELLS = 12;
    public static final int CITY_EDGE = CITY_CELLS * CELL + CELL / 2;
    static final int ROAD_HALF = 3;
    static final int COUNTRY_ROAD_HALF = 2;
    static final int REGION = 192;
    public static final int LAB_X = 960;
    public static final int LAB_Z = -960;
    static final int LAB_SIZE = 64;

    private static final Map<Long, CityLayout> LAYOUTS = new ConcurrentHashMap<>();

    enum Kind {
        HOUSE_SMALL, HOUSE_LARGE, FARMHOUSE, APARTMENT, SHOP, SUPERMARKET, PHARMACY, HOSPITAL, POLICE, GARAGE,
        WAREHOUSE, GAS_STATION, PARK, RUIN, PLAZA, YARD, FARM, CABIN, LAB, CHECKPOINT
    }

    enum Ground { GRASS, PAVED, GRAVEL, PLAZA, FIELD }

    record Placement(Kind kind, int minX, int minZ, int width, int depth, BlockFace front, long seed, int floorY) {
        int maxX() {
            return minX + Frame.spanX(width, depth, front) - 1;
        }

        int maxZ() {
            return minZ + Frame.spanZ(width, depth, front) - 1;
        }

        boolean contains(int x, int z) {
            return x >= minX - 1 && x <= maxX() + 1 && z >= minZ - 1 && z <= maxZ() + 1;
        }
    }

    record Cell(Ground ground, Sector sector, List<Placement> placements) {
    }

    private final long seed;
    private final Terrain terrain;
    private final Map<Long, Cell> cells = new ConcurrentHashMap<>();
    private final Map<Long, List<Placement>> regions = new ConcurrentHashMap<>();

    private CityLayout(long seed) {
        this.seed = seed;
        this.terrain = new Terrain(seed, CITY_EDGE);
    }

    public static CityLayout of(long seed) {
        return LAYOUTS.computeIfAbsent(seed, CityLayout::new);
    }

    /** Uncached instance, for determinism tests. */
    static CityLayout fresh(long seed) {
        return new CityLayout(seed);
    }

    public Terrain terrain() {
        return terrain;
    }

    static long mix(long seed, long a, long b) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        return h ^ (h >>> 33);
    }

    static int roadDistance(int v) {
        int m = Math.floorMod(v - CELL / 2, CELL);
        return Math.min(m, CELL - m);
    }

    static boolean urban(int x, int z) {
        return Math.max(Math.abs(x), Math.abs(z)) <= CITY_EDGE + ROAD_HALF;
    }

    static int cellOf(int v) {
        return Math.floorDiv(v + CELL / 2, CELL);
    }

    // ------------------------------------------------------------------ queries

    /** Y of the walkable ground (street level) at a column, ignoring buildings. */
    public int groundY(int x, int z) {
        if (urban(x, z)) return Terrain.CITY_Y;
        int h = terrain.height(x, z);
        return countryRoad(x, z) && h < Terrain.WATER_Y + 1 ? Terrain.WATER_Y + 1 : Math.max(h, Terrain.WATER_Y);
    }

    public Sector sectorAt(int x, int z) {
        if (Math.abs(x - LAB_X) <= LAB_SIZE && Math.abs(z - LAB_Z) <= LAB_SIZE) return Sector.LAB;
        if (!urban(x, z)) return Sector.COUNTRYSIDE;
        return cell(cellOf(x), cellOf(z)).sector();
    }

    /** Place type of a generated searchable container at this exact block, or {@code null}. */
    public PlaceType placeAt(int x, int y, int z) {
        for (Placement placement : placementsAt(x, z)) {
            ProbeCanvas probe = new ProbeCanvas(x, y, z);
            Structures.render(placement, probe, this);
            if (probe.found() != null) return probe.found();
        }
        return null;
    }

    List<Placement> placementsAt(int x, int z) {
        List<Placement> found = new ArrayList<>();
        if (urban(x, z)) {
            for (Placement placement : cell(cellOf(x), cellOf(z)).placements()) if (placement.contains(x, z)) found.add(placement);
        } else {
            for (int a = Math.floorDiv(x - LAB_SIZE, REGION); a <= Math.floorDiv(x + LAB_SIZE, REGION); a++) {
                for (int b = Math.floorDiv(z - LAB_SIZE, REGION); b <= Math.floorDiv(z + LAB_SIZE, REGION); b++) {
                    for (Placement placement : region(a, b)) if (placement.contains(x, z)) found.add(placement);
                }
            }
        }
        return found;
    }

    /** A searchable container of the plan, in world coordinates. */
    public record ContainerSpot(int x, int y, int z, PlaceType place) {
    }

    /** Searchable containers of the city block (i, j) as the generator leaves them (needs a running server). */
    public List<ContainerSpot> containers(int i, int j) {
        CollectCanvas canvas = new CollectCanvas();
        for (Placement placement : cell(i, j).placements()) Structures.render(placement, canvas, this);
        List<ContainerSpot> spots = new ArrayList<>();
        canvas.found().forEach((spot, place) -> spots.add(new ContainerSpot(spot.x(), spot.y(), spot.z(), place)));
        return spots;
    }

    /** Walkable ground of a column ignoring roofs: the site floor inside a placement, street level in the city, else {@code terrainTop}. */
    public int floorAt(int x, int z, int terrainTop) {
        for (Placement placement : placementsAt(x, z)) {
            if (x >= placement.minX() && x <= placement.maxX() && z >= placement.minZ() && z <= placement.maxZ()) return placement.floorY();
        }
        return urban(x, z) ? Terrain.CITY_Y : terrainTop;
    }

    /** True when the column belongs to a public street or path, where infected may roam. */
    public boolean isStreet(int x, int z) {
        if (urban(x, z)) return roadDistance(x) <= ROAD_HALF + 2 || roadDistance(z) <= ROAD_HALF + 2;
        return countryRoad(x, z);
    }

    boolean countryRoad(int x, int z) {
        if (urban(x, z)) return false;
        if (Math.abs(x - CELL / 2) <= COUNTRY_ROAD_HALF || Math.abs(z - CELL / 2) <= COUNTRY_ROAD_HALF) return true;
        return Math.abs(x - LAB_X) <= COUNTRY_ROAD_HALF && z <= CELL / 2 && z >= LAB_Z + LAB_SIZE / 2;
    }

    // ------------------------------------------------------------------ plan

    Cell cell(int i, int j) {
        return cells.computeIfAbsent(i * 1_000_003L + j, key -> planCell(i, j));
    }

    private Sector sector(int i, int j) {
        int ring = Math.max(Math.abs(i), Math.abs(j));
        if (ring <= 1) return Sector.STARTER;
        if (industrial(i, j)) return Sector.INDUSTRIAL;
        if (Math.abs(i - 3) <= 1 && Math.abs(j + 3) <= 1) return Sector.MEDICAL;
        if (ring <= 5) return Sector.DOWNTOWN;
        if (ring <= 10) return Sector.RESIDENTIAL;
        return Sector.OUTSKIRTS;
    }

    private static boolean industrial(int i, int j) {
        return i >= 6 && i <= 11 && j >= -4 && j <= 3;
    }

    private Kind special(int i, int j) {
        if (i == 0 && j == 0) return Kind.PLAZA;
        if (i == 3 && j == -3) return Kind.HOSPITAL;
        if ((i == -3 && j == 2) || (i == 6 && j == 6)) return Kind.POLICE;
        if ((i == -2 && j == -3) || (i == 4 && j == 4) || (i == -7 && j == 6) || (i == 8 && j == -7)) return Kind.SUPERMARKET;
        if ((i == 2 && j == 1) || (i == -6 && j == -5) || (i == 7 && j == 5) || (i == -4 && j == 8) || (i == 2 && j == -9))
            return Kind.PHARMACY;
        if ((i == 0 && j == -11) || (i == 11 && j == 0) || (i == -11 && j == 0) || (i == 0 && j == 11)) return Kind.GAS_STATION;
        if ((i == -1 && j == 1) || (i == 5 && j == -6) || (i == -8 && j == -8)) return Kind.PARK;
        return null;
    }

    private Cell planCell(int i, int j) {
        Random r = new Random(mix(seed, i, j));
        int cx = i * CELL, cz = j * CELL;
        int minX = cx - 18, minZ = cz - 18;
        Sector sector = sector(i, j);
        List<Placement> list = new ArrayList<>();
        Kind special = special(i, j);
        int y = Terrain.CITY_Y;
        if (special != null) {
            switch (special) {
                case PLAZA -> list.add(new Placement(Kind.PLAZA, minX, minZ, 37, 37, BlockFace.NORTH, r.nextLong(), y));
                case HOSPITAL -> list.add(new Placement(Kind.HOSPITAL, minX + 2, minZ + 4, 33, 29, BlockFace.WEST, r.nextLong(), y));
                case POLICE -> list.add(centered(Kind.POLICE, cx, cz, 21, 17, face(r), r, y));
                case SUPERMARKET -> list.add(centered(Kind.SUPERMARKET, cx, cz, 31, 25, face(r), r, y));
                case PHARMACY -> {
                    shopRow(list, r, cx, cz, y, sector);
                    list.removeFirst();
                    list.addFirst(new Placement(Kind.PHARMACY, minX, minZ, 12, 15, BlockFace.NORTH, r.nextLong(), y));
                }
                case GAS_STATION -> list.add(new Placement(Kind.GAS_STATION, minX + 1, minZ + 1, 35, 35, gasFront(i, j), r.nextLong(), y));
                case PARK -> list.add(new Placement(Kind.PARK, minX, minZ, 37, 37, BlockFace.NORTH, r.nextLong(), y));
                default -> {
                }
            }
            Ground ground = switch (special) {
                case PLAZA -> Ground.PLAZA;
                case PARK -> Ground.GRASS;
                case GAS_STATION, SUPERMARKET, POLICE -> Ground.PAVED;
                default -> Ground.PAVED;
            };
            return new Cell(ground, sector, List.copyOf(list));
        }
        int roll = r.nextInt(100);
        Ground ground;
        switch (sector) {
            case INDUSTRIAL -> {
                ground = Ground.GRAVEL;
                if (roll < 55) list.add(centered(Kind.WAREHOUSE, cx, cz, 33, 25, face(r), r, y));
                else if (roll < 85) garages(list, r, cx, cz, y);
                else ruins(list, r, cx, cz, y);
            }
            case DOWNTOWN, MEDICAL -> {
                ground = Ground.PAVED;
                if (roll < 40) apartments(list, r, cx, cz, y);
                else if (roll < 82) shopRow(list, r, cx, cz, y, sector);
                else if (roll < 90) list.add(new Placement(Kind.PARK, minX, minZ, 37, 37, BlockFace.NORTH, r.nextLong(), y));
                else ruins(list, r, cx, cz, y);
            }
            case STARTER -> {
                ground = Ground.GRASS;
                if (roll < 70) houses(list, r, cx, cz, y, 0);
                else {
                    ground = Ground.PAVED;
                    shopRow(list, r, cx, cz, y, sector);
                }
            }
            case OUTSKIRTS -> {
                ground = roll < 25 ? Ground.FIELD : Ground.GRASS;
                if (roll >= 25) houses(list, r, cx, cz, y, 45);
            }
            default -> {
                ground = Ground.GRASS;
                if (roll < 72) houses(list, r, cx, cz, y, 8);
                else if (roll < 84) shopRow(list, r, cx, cz, y, sector);
                else if (roll < 90) list.add(new Placement(Kind.PARK, minX, minZ, 37, 37, BlockFace.NORTH, r.nextLong(), y));
                else ruins(list, r, cx, cz, y);
            }
        }
        return new Cell(ground, sector, List.copyOf(list));
    }

    private static BlockFace gasFront(int i, int j) {
        if (i == 0) return BlockFace.EAST;
        return BlockFace.SOUTH;
    }

    private static BlockFace face(Random r) {
        return switch (r.nextInt(4)) {
            case 0 -> BlockFace.NORTH;
            case 1 -> BlockFace.EAST;
            case 2 -> BlockFace.SOUTH;
            default -> BlockFace.WEST;
        };
    }

    private static Placement centered(Kind kind, int cx, int cz, int width, int depth, BlockFace front, Random r, int y) {
        int sx = Frame.spanX(width, depth, front), sz = Frame.spanZ(width, depth, front);
        return new Placement(kind, cx - sx / 2, cz - sz / 2, width, depth, front, r.nextLong(), y);
    }

    /** Four parcels of 18 x 18; each house faces one of its two streets. */
    private static void houses(List<Placement> list, Random r, int cx, int cz, int y, int emptyPercent) {
        int[][] parcels = {{cx - 18, cz - 18, 0}, {cx + 1, cz - 18, 1}, {cx - 18, cz + 1, 2}, {cx + 1, cz + 1, 3}};
        for (int[] parcel : parcels) {
            long s = r.nextLong();
            int roll = r.nextInt(100);
            boolean large = r.nextInt(100) < 35;
            int w = large ? 9 + r.nextInt(3) : 7 + r.nextInt(3);
            int d = large ? 9 + r.nextInt(3) : 8 + r.nextInt(3);
            int corner = parcel[2];
            boolean northSouth = r.nextBoolean();
            BlockFace front = northSouth ? (corner < 2 ? BlockFace.NORTH : BlockFace.SOUTH) : (corner % 2 == 0 ? BlockFace.WEST : BlockFace.EAST);
            if (roll < emptyPercent) continue;
            list.add(new Placement(Kind.YARD, parcel[0], parcel[1], 18, 18, front, s, y));
            Kind kind = roll < emptyPercent + 6 ? Kind.RUIN : roll < emptyPercent + 10 ? Kind.GARAGE : large ? Kind.HOUSE_LARGE : Kind.HOUSE_SMALL;
            if (kind == Kind.GARAGE) {
                w = 12;
                d = 13;
            }
            int setback = 3 + r.nextInt(2);
            int sx = Frame.spanX(w, d, front), sz = Frame.spanZ(w, d, front);
            int x = switch (front) {
                case WEST -> parcel[0] + setback;
                case EAST -> parcel[0] + 18 - setback - sx;
                default -> parcel[0] + (18 - sx) / 2;
            };
            int z = switch (front) {
                case NORTH -> parcel[1] + setback;
                case SOUTH -> parcel[1] + 18 - setback - sz;
                default -> parcel[1] + (18 - sz) / 2;
            };
            list.add(new Placement(kind, x, z, w, d, front, r.nextLong(), y));
        }
    }

    /** Two rows of three narrow buildings on the sidewalk line, back to back with an alley. */
    private static void shopRow(List<Placement> list, Random r, int cx, int cz, int y, Sector sector) {
        int[] xs = {cx - 18, cx - 6, cx + 6};
        int[] ws = {12, 12, 13};
        for (int row = 0; row < 2; row++) {
            BlockFace front = row == 0 ? BlockFace.NORTH : BlockFace.SOUTH;
            for (int k = 0; k < 3; k++) {
                int roll = r.nextInt(100);
                long s = r.nextLong();
                int depth = 15 + r.nextInt(2);
                Kind kind = roll < 46 ? Kind.SHOP : roll < 66 ? Kind.HOUSE_LARGE : roll < 80 ? Kind.GARAGE
                        : roll < 90 ? Kind.RUIN : sector == Sector.DOWNTOWN ? Kind.SHOP : Kind.HOUSE_SMALL;
                int z = row == 0 ? cz - 18 : cz + 18 - depth + 1;
                list.add(new Placement(kind, xs[k], z, ws[k], depth, front, s, y));
            }
        }
    }

    private static void apartments(List<Placement> list, Random r, int cx, int cz, int y) {
        int[][] parcels = {{cx - 18, cz - 18, 0}, {cx + 1, cz - 18, 1}, {cx - 18, cz + 1, 2}, {cx + 1, cz + 1, 3}};
        for (int[] parcel : parcels) {
            int corner = parcel[2];
            BlockFace front = r.nextBoolean() ? (corner < 2 ? BlockFace.NORTH : BlockFace.SOUTH) : (corner % 2 == 0 ? BlockFace.WEST : BlockFace.EAST);
            int roll = r.nextInt(100);
            long s = r.nextLong();
            Kind kind = roll < 82 ? Kind.APARTMENT : roll < 92 ? Kind.SHOP : Kind.RUIN;
            int size = kind == Kind.APARTMENT ? 15 : 12;
            int offset = (18 - size) / 2;
            int x = front == BlockFace.WEST ? parcel[0] + 1 : front == BlockFace.EAST ? parcel[0] + 17 - size : parcel[0] + offset;
            int z = front == BlockFace.NORTH ? parcel[1] + 1 : front == BlockFace.SOUTH ? parcel[1] + 17 - size : parcel[1] + offset;
            list.add(new Placement(kind, x, z, size, size, front, s, y));
        }
    }

    private static void garages(List<Placement> list, Random r, int cx, int cz, int y) {
        list.add(new Placement(Kind.GARAGE, cx - 18, cz - 18, 13, 14, BlockFace.NORTH, r.nextLong(), y));
        list.add(new Placement(Kind.GARAGE, cx + 5, cz - 18, 13, 14, BlockFace.NORTH, r.nextLong(), y));
        list.add(new Placement(Kind.WAREHOUSE, cx - 16, cz + 2, 31, 15, BlockFace.SOUTH, r.nextLong(), y));
    }

    private static void ruins(List<Placement> list, Random r, int cx, int cz, int y) {
        int[][] parcels = {{cx - 16, cz - 16}, {cx + 3, cz - 16}, {cx - 16, cz + 3}, {cx + 3, cz + 3}};
        for (int[] parcel : parcels) {
            long s = r.nextLong();
            if (r.nextInt(100) < 25) continue;
            list.add(new Placement(Kind.RUIN, parcel[0], parcel[1], 13, 13, face(r), s, y));
        }
    }

    List<Placement> region(int a, int b) {
        return regions.computeIfAbsent(a * 1_000_003L + b, key -> planRegion(a, b));
    }

    private List<Placement> planRegion(int a, int b) {
        List<Placement> list = new ArrayList<>();
        int x0 = a * REGION, z0 = b * REGION;
        Random r = new Random(mix(seed ^ 0x51ED270B27L, a, b));
        addFixed(list, x0, z0);
        int far = CITY_EDGE + Terrain.BLEND + 24;
        boolean nearCity = Math.max(Math.abs(x0 + REGION / 2), Math.abs(z0 + REGION / 2)) < far + REGION / 2;
        int roll = r.nextInt(100);
        if (nearCity || roll >= 62) return List.copyOf(list);
        boolean farm = roll < 34;
        int size = farm ? 44 : 11;
        int x = x0 + 24 + r.nextInt(REGION - 48 - size);
        int z = z0 + 24 + r.nextInt(REGION - 48 - size);
        if (Math.max(Math.abs(x), Math.abs(z)) < far || touchesRoad(x, z, size, size) || touchesLab(x, z, size)) return List.copyOf(list);
        int pad = pad(x, z, size);
        list.add(new Placement(farm ? Kind.FARM : Kind.CABIN, x, z, size, size, face(r), r.nextLong(), pad));
        return List.copyOf(list);
    }

    private void addFixed(List<Placement> list, int x0, int z0) {
        int edge = CITY_EDGE + 80;
        int[][] checkpoints = {{CELL / 2 - 10, -edge - 10, 0}, {CELL / 2 - 10, edge - 10, 2}, {edge - 10, CELL / 2 - 10, 1}, {-edge - 10, CELL / 2 - 10, 3}};
        for (int[] spot : checkpoints) {
            if (inRegion(spot[0] + 10, spot[1] + 10, x0, z0)) {
                BlockFace front = switch (spot[2]) {
                    case 0 -> BlockFace.NORTH;
                    case 1 -> BlockFace.EAST;
                    case 2 -> BlockFace.SOUTH;
                    default -> BlockFace.WEST;
                };
                list.add(new Placement(Kind.CHECKPOINT, spot[0], spot[1], 21, 21, front, mix(seed, spot[0], spot[1]), pad(spot[0], spot[1], 21)));
            }
        }
        int far = 1100;
        int[][] stations = {{CELL / 2 + 6, -far, 3}, {far, CELL / 2 + 6, 0}, {CELL / 2 + 6, far, 3}, {-far, CELL / 2 + 6, 0}};
        for (int[] spot : stations) {
            if (inRegion(spot[0] + 15, spot[1] + 15, x0, z0)) {
                BlockFace front = spot[2] == 3 ? BlockFace.WEST : BlockFace.NORTH;
                list.add(new Placement(Kind.GAS_STATION, spot[0], spot[1], 31, 31, front, mix(seed, spot[0], spot[1]), pad(spot[0], spot[1], 31)));
            }
        }
        int labMinX = LAB_X - LAB_SIZE / 2, labMinZ = LAB_Z - LAB_SIZE / 2;
        if (inRegion(LAB_X, LAB_Z, x0, z0))
            list.add(new Placement(Kind.LAB, labMinX, labMinZ, LAB_SIZE, LAB_SIZE, BlockFace.SOUTH, mix(seed, LAB_X, LAB_Z), pad(labMinX, labMinZ, LAB_SIZE)));
    }

    private static boolean inRegion(int x, int z, int x0, int z0) {
        return x >= x0 && x < x0 + REGION && z >= z0 && z < z0 + REGION;
    }

    private boolean touchesRoad(int x, int z, int sx, int sz) {
        int margin = COUNTRY_ROAD_HALF + 4;
        int road = CELL / 2;
        if (x - margin <= road && x + sx + margin >= road) return true;
        if (z - margin <= road && z + sz + margin >= road) return true;
        return x - margin <= LAB_X && x + sx + margin >= LAB_X && z + sz >= LAB_Z && z <= road;
    }

    private static boolean touchesLab(int x, int z, int size) {
        int labMinX = LAB_X - LAB_SIZE / 2 - 30, labMinZ = LAB_Z - LAB_SIZE / 2 - 30;
        return x + size >= labMinX && x <= labMinX + LAB_SIZE + 60 && z + size >= labMinZ && z <= labMinZ + LAB_SIZE + 60;
    }

    /** Floor level of a countryside site: median-ish terrain height, never below water. */
    int pad(int x, int z, int size) {
        int[] samples = {
                terrain.height(x, z), terrain.height(x + size, z), terrain.height(x, z + size),
                terrain.height(x + size, z + size), terrain.height(x + size / 2, z + size / 2)};
        java.util.Arrays.sort(samples);
        return Math.max(samples[2], Terrain.WATER_Y + 1);
    }

    // ------------------------------------------------------------------ chunk generation

    public void generate(ChunkGenerator.ChunkData data, int chunkX, int chunkZ) {
        ChunkCanvas canvas = new ChunkCanvas(data, chunkX, chunkZ);
        int x0 = chunkX << 4, z0 = chunkZ << 4, min = data.getMinHeight();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx, z = z0 + dz;
                int h = terrain.height(x, z);
                data.setBlock(dx, min, dz, B.BEDROCK);
                data.setRegion(dx, min + 1, dz, dx + 1, Math.min(0, h - 3), dz + 1, B.DEEPSLATE);
                if (h - 3 > 0) data.setRegion(dx, Math.max(0, min + 1), dz, dx + 1, h - 3, dz + 1, B.STONE);
                data.setRegion(dx, h - 3, dz, dx + 1, h, dz + 1, B.DIRT);
                surface(data, dx, dz, x, z, h);
            }
        }
        int ci1 = cellOf(x0), ci2 = cellOf(x0 + 15), cj1 = cellOf(z0), cj2 = cellOf(z0 + 15);
        for (int i = ci1; i <= ci2; i++) {
            for (int j = cj1; j <= cj2; j++) {
                if (Math.max(Math.abs(i), Math.abs(j)) > CITY_CELLS) continue;
                for (Placement placement : cell(i, j).placements()) {
                    if (canvas.touches(placement.minX() - 1, placement.minZ() - 1, placement.maxX() + 1, placement.maxZ() + 1))
                        Structures.render(placement, canvas, this);
                }
            }
        }
        if (Math.max(Math.abs(x0), Math.abs(z0)) <= CITY_EDGE + CELL) Streets.furnish(canvas, this, x0, z0);
        int ra1 = Math.floorDiv(x0 - LAB_SIZE, REGION), ra2 = Math.floorDiv(x0 + 15 + LAB_SIZE, REGION);
        int rb1 = Math.floorDiv(z0 - LAB_SIZE, REGION), rb2 = Math.floorDiv(z0 + 15 + LAB_SIZE, REGION);
        for (int a = ra1; a <= ra2; a++) {
            for (int b = rb1; b <= rb2; b++) {
                for (Placement placement : region(a, b)) {
                    int margin = 8;
                    if (canvas.touches(placement.minX() - margin, placement.minZ() - margin, placement.maxX() + margin, placement.maxZ() + margin))
                        Structures.render(placement, canvas, this);
                }
            }
        }
    }

    private void surface(ChunkGenerator.ChunkData data, int dx, int dz, int x, int z, int h) {
        long hash = mix(seed, x, z);
        int noise = (int) (hash & 1023);
        if (urban(x, z)) {
            int rx = roadDistance(x), rz = roadDistance(z);
            BlockData top;
            if (rx <= ROAD_HALF || rz <= ROAD_HALF) top = Streets.road(x, z, rx, rz, noise);
            else if (rx <= ROAD_HALF + 2 || rz <= ROAD_HALF + 2) top = Streets.sidewalk(noise);
            else top = lot(cell(cellOf(x), cellOf(z)).ground(), noise);
            data.setBlock(dx, h, dz, top);
            return;
        }
        if (countryRoad(x, z)) {
            if (h <= Terrain.WATER_Y) {
                data.setRegion(dx, h + 1, dz, dx + 1, Terrain.WATER_Y + 1, dz + 1, B.WATER);
                data.setBlock(dx, Terrain.WATER_Y + 1, dz, B.of(Material.SPRUCE_PLANKS));
            } else {
                Material material = noise < 520 ? Material.DIRT_PATH : noise < 820 ? Material.GRAVEL : noise < 960 ? Material.COARSE_DIRT : Material.COBBLESTONE;
                data.setBlock(dx, h, dz, B.of(material));
            }
            return;
        }
        if (h <= Terrain.WATER_Y) {
            data.setBlock(dx, h, dz, noise < 600 ? B.SAND : noise < 900 ? B.GRAVEL : B.of(Material.CLAY));
            data.setRegion(dx, h + 1, dz, dx + 1, Terrain.WATER_Y + 1, dz + 1, B.WATER);
            return;
        }
        data.setBlock(dx, h, dz, h <= Terrain.WATER_Y + 1 && noise < 700 ? B.SAND : B.GRASS);
        if (noise < 90) data.setBlock(dx, h + 1, dz, B.of(Material.SHORT_GRASS));
        else if (noise < 100) data.setBlock(dx, h + 1, dz, B.of(noise < 95 ? Material.DANDELION : Material.POPPY));
    }

    private static BlockData lot(Ground ground, int noise) {
        return switch (ground) {
            case PAVED -> B.of(noise < 700 ? Material.STONE_BRICKS : noise < 900 ? Material.CRACKED_STONE_BRICKS : Material.MOSSY_STONE_BRICKS);
            case PLAZA -> B.of(noise < 800 ? Material.POLISHED_ANDESITE : Material.ANDESITE);
            case GRAVEL -> B.of(noise < 500 ? Material.GRAVEL : noise < 850 ? Material.COARSE_DIRT : Material.ANDESITE);
            case FIELD -> B.of(noise < 900 ? Material.COARSE_DIRT : Material.GRASS_BLOCK);
            default -> B.of(noise < 960 ? Material.GRASS_BLOCK : Material.COARSE_DIRT);
        };
    }

    long seed() {
        return seed;
    }
}
