package org.duckcatmine.apocalypse.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoilTest {
    @Test void successfulShotsKickUpWithDistinctStrengthsAndCrouchingHelps() {
        var pistol=Recoil.shot("pistol",0,false,1);
        var shotgun=Recoil.shot("shotgun",0,false,1);
        var smg=Recoil.shot("smg",0,false,1);
        var rocket=Recoil.shot("rocket_launcher",0,false,1);
        assertTrue(rocket.pitch()<shotgun.pitch()&&shotgun.pitch()<pistol.pitch()&&pistol.pitch()<smg.pitch());
        assertTrue(smg.pitch()<0);
        assertTrue(Recoil.shot("smg",0,true,1).pitch()>smg.pitch());
        assertEquals(-pistol.yaw(),Recoil.shot("pistol",0,false,-1).yaw());
    }
    @Test void verticalLimitAndUnknownItemsNeverWhipTheCameraAround() {
        assertEquals(-.5f,Recoil.shot("rocket_launcher",-84.5f,false,0).pitch());
        assertEquals(0,Recoil.shot("rocket_launcher",-89f,false,0).pitch());
        assertEquals(0,Recoil.shot("other",0,false,1).pitch());
        assertEquals(0,Recoil.shot("other",0,false,1).yaw());
        for(int n=0;n<360;n++)assertTrue(Recoil.shot("smg",-85,false,1).pitch()>=0);
    }
}
