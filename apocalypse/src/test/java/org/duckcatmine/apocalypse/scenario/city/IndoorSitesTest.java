package org.duckcatmine.apocalypse.scenario.city;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndoorSitesTest {
    @Test void interiorsAreGeneratedBuildingsAwayFromStreetAndOnlySomeAreInfested(){
        CityLayout layout=CityLayout.of(482131);int inside=0,infested=0;
        for(int x=-260;x<=260;x+=3)for(int z=-260;z<=260;z+=3){
            var site=layout.interiorAt(x,z);if(site==null)continue;inside++;
            assertFalse(layout.openGround(x,z));assertTrue(site.contains(x,z));assertFalse(site.contains(site.minX(),site.minZ()));
            assertEquals(site.infested(482131),site.infested(482131));
            if(site.infested(482131))infested++;
            assertNotEquals("hospital",site.kind());
        }
        assertTrue(inside>100);assertTrue(infested>0&&infested<inside);
    }
}
