package org.duckcatmine.apocalypse.mobs;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpawnDistanceTest {
    @Test void horizontalBoundaryIncludesExactly150Blocks() {
        assertFalse(SpawnDistance.outside(149.999,0,0,0,150));
        assertTrue(SpawnDistance.outside(150,0,0,0,150));
        assertTrue(SpawnDistance.outside(90,120,0,0,150));
        assertFalse(SpawnDistance.outside(90,119.9,0,0,150));
    }
    @Test void everyPlayerMustBeOutsideIncludingNegativeCoordinates() {
        assertTrue(SpawnDistance.outside(-200,-200,0,0,150));
        assertFalse(SpawnDistance.outside(-200,-200,-220,-200,150));
    }
}
