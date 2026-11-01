package org.duckcatmine.apocalypse.radar;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RadarMathTest {
    @Test void frontAndRightFollowMinecraftYaw() {
        assertEquals(new RadarMath.Dot(64, 16, false), RadarMath.project(0,100,0));
        assertEquals(new RadarMath.Dot(112,64,false), RadarMath.project(-100,0,0));
        assertEquals(new RadarMath.Dot(64,16,false), RadarMath.project(-100,0,90));
        assertEquals(new RadarMath.Dot(64,64,false), RadarMath.project(0,0,180));
    }
    @Test void farPlayersKeepTheirBearingOutsideTheDetectionCircle() {
        var far = RadarMath.project(3000, 4000, -40);
        assertTrue(far.far());
        assertEquals(58, Math.hypot(far.x()-64,far.y()-64), .8);
        assertFalse(RadarMath.project(60,80,0).far());
        assertTrue(RadarMath.project(60,80.1,0).far());
    }
    @Test void zoomAndBoundaries() {
        assertEquals(new RadarMath.Dot(64,16,false),RadarMath.project(0,50,0,50));
        assertTrue(RadarMath.project(0,50.01,0,50).far());
        assertEquals(new RadarMath.Dot(64,40,false),RadarMath.project(0,50,0,100));
        assertThrows(IllegalArgumentException.class,()->RadarMath.project(0,0,0,0));
    }
}
