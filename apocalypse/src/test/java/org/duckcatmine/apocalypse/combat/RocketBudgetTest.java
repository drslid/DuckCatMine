package org.duckcatmine.apocalypse.combat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RocketBudgetTest {
    @Test void exactlyTenUsesAndNeverAnEleventh(){
        int stored=10;
        for(int wear=0;wear<10;wear++){assertEquals(10-wear,RocketBudget.remaining(stored,wear));stored--;}
        assertEquals(0,RocketBudget.remaining(stored,10));
    }
    @Test void changingWearCannotRestoreSpentRockets(){
        assertEquals(4,RocketBudget.remaining(4,0));assertEquals(0,RocketBudget.remaining(0,0));
        assertEquals(2,RocketBudget.remaining(100,8));assertEquals(0,RocketBudget.remaining(-1,0));
        assertEquals(0,RocketBudget.remaining(10,999));assertEquals(7,RocketBudget.remaining(null,3));
    }
    @Test void launcherIsNotRepairable(){assertFalse(RocketBudget.repairable("rocket_launcher"));assertTrue(RocketBudget.repairable("pistol"));}
}
