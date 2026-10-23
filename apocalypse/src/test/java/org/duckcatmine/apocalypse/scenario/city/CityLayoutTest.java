package org.duckcatmine.apocalypse.scenario.city;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CityLayoutTest {
    private static final long SEED = 4242L;

    @Test
    void samePlanForTheSameSeed() {
        CityLayout a = CityLayout.fresh(SEED), b = CityLayout.fresh(SEED);
        for (int i = -CityLayout.CITY_CELLS; i <= CityLayout.CITY_CELLS; i += 3) {
            for (int j = -CityLayout.CITY_CELLS; j <= CityLayout.CITY_CELLS; j += 2) assertEquals(a.cell(i, j), b.cell(i, j));
        }
        for (int r = -8; r <= 8; r += 4) assertEquals(a.region(r, -r), b.region(r, -r));
        assertFalse(a.cell(5, 7).equals(CityLayout.fresh(SEED + 1).cell(5, 7)) && a.cell(-6, 2).equals(CityLayout.fresh(SEED + 1).cell(-6, 2)),
                "another seed gives another city");
    }

    @Test
    void buildingsStayOnTheirLotAndDoNotOverlap() {
        CityLayout layout = CityLayout.fresh(SEED);
        for (int i = -CityLayout.CITY_CELLS; i <= CityLayout.CITY_CELLS; i++) {
            for (int j = -CityLayout.CITY_CELLS; j <= CityLayout.CITY_CELLS; j++) {
                List<CityLayout.Placement> buildings = new ArrayList<>();
                for (CityLayout.Placement p : layout.cell(i, j).placements()) {
                    for (int x = p.minX(); x <= p.maxX(); x++) {
                        assertTrue(CityLayout.roadDistance(x) > CityLayout.ROAD_HALF + 2, p + " touches a street at x=" + x);
                    }
                    for (int z = p.minZ(); z <= p.maxZ(); z++) {
                        assertTrue(CityLayout.roadDistance(z) > CityLayout.ROAD_HALF + 2, p + " touches a street at z=" + z);
                    }
                    if (p.kind() != CityLayout.Kind.YARD) buildings.add(p);
                }
                for (int a = 0; a < buildings.size(); a++) {
                    for (int b = a + 1; b < buildings.size(); b++) {
                        CityLayout.Placement p = buildings.get(a), q = buildings.get(b);
                        boolean overlap = p.minX() <= q.maxX() && q.minX() <= p.maxX() && p.minZ() <= q.maxZ() && q.minZ() <= p.maxZ();
                        assertFalse(overlap, p + " overlaps " + q);
                    }
                }
            }
        }
    }

    @Test
    void landmarksArePlanned() {
        CityLayout layout = CityLayout.fresh(SEED);
        assertEquals(CityLayout.Kind.PLAZA, layout.cell(0, 0).placements().getFirst().kind());
        assertEquals(CityLayout.Kind.HOSPITAL, layout.cell(3, -3).placements().getFirst().kind());
        assertEquals(Sector.MEDICAL, layout.sectorAt(3 * CityLayout.CELL, -3 * CityLayout.CELL));
        assertEquals(Sector.LAB, layout.sectorAt(CityLayout.LAB_X, CityLayout.LAB_Z));
        assertEquals(Sector.COUNTRYSIDE, layout.sectorAt(1200, 300));
        int labs = 0, checkpoints = 0;
        for (int a = -8; a <= 8; a++) {
            for (int b = -8; b <= 8; b++) {
                for (CityLayout.Placement p : layout.region(a, b)) {
                    if (p.kind() == CityLayout.Kind.LAB) labs++;
                    if (p.kind() == CityLayout.Kind.CHECKPOINT) checkpoints++;
                }
            }
        }
        assertEquals(1, labs);
        assertEquals(4, checkpoints);
    }

    @Test
    void cityIsFlatAndTheCountrysideIsNot() {
        Terrain terrain = CityLayout.fresh(SEED).terrain();
        for (int k = -CityLayout.CITY_EDGE; k <= CityLayout.CITY_EDGE; k += 37) {
            assertEquals(Terrain.CITY_Y, terrain.height(k, -k / 2));
        }
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int x = 1000; x < 1400; x += 8) {
            int h = terrain.height(x, x / 3);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        assertTrue(max - min >= 6, "countryside relief " + min + ".." + max);
    }
}
