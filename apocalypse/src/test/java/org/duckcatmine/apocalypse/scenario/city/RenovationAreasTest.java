package org.duckcatmine.apocalypse.scenario.city;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RenovationAreasTest {
    @TempDir Path temp;
    @Test void hospitalDistrictAndIntersectingApproachesAreExcluded() {
        var a=new RenovationAreas.Area("ground_zero","Quartier de l'hôpital",64,-224,223,-65);
        assertTrue(a.contains("ground_zero",142,-142));
        assertTrue(a.contains("ground_zero",64,-224));
        assertTrue(a.intersects("ground_zero",60,-180,70,-170));
        assertFalse(a.contains("ground_zero",63,-142));
        assertFalse(a.contains("world",142,-142));
    }
    @Test void invalidProtectionFileStopsMaintenance() throws Exception {
        Path file=temp.resolve("areas.json");
        assertTrue(RenovationAreas.read(file).isEmpty());
        Files.writeString(file,"{bad");
        assertThrows(java.io.IOException.class,()->RenovationAreas.read(file));
        Files.writeString(file,"""
            {"version":1,"areas":[{"world":"ground_zero","label":"Hôpital","minX":64,"maxX":223,"minZ":-224,"maxZ":-65}]}
            """);
        assertEquals(1,RenovationAreas.read(file).size());
    }
}
