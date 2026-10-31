package org.duckcatmine.apocalypse.perception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FootstepsTest {
    @Test void walkingAndRunningAreDifferentNoises() {
        assertEquals(Footsteps.Sound.WALK,Footsteps.sound(16,true,false,false,false));
        assertEquals(Footsteps.Sound.SPRINT,Footsteps.sound(30,true,false,true,false));
    }
    @Test void holdingSprintWithoutMovingIsSilent() {
        assertEquals(Footsteps.Sound.NONE,Footsteps.sound(0,true,false,true,false));
    }
    @Test void crouchingRidingAndFlyingDoNotMakeFootsteps() {
        assertEquals(Footsteps.Sound.NONE,Footsteps.sound(1,true,true,false,false));
        assertEquals(Footsteps.Sound.NONE,Footsteps.sound(16,true,false,true,true));
        assertEquals(Footsteps.Sound.NONE,Footsteps.sound(16,false,false,true,false));
    }
    @Test void teleportsAreNotInterpretedAsRunning() {
        assertEquals(Footsteps.Sound.NONE,Footsteps.sound(10000,true,false,true,false));
    }
}
