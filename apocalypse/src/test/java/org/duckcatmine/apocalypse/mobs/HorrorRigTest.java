package org.duckcatmine.apocalypse.mobs;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HorrorRigTest {
    @Test void facingMatchesAllFourTravelDirectionsWithoutInitialEntityYaw(){
        for(double[] move:new double[][]{{0,1},{1,0},{0,-1},{-1,0},{1,1}}){
            float yaw=HorrorRig.motionYaw(move[0],move[1],37);
            Vector3f face=new Vector3f(0,0,1).rotate(HorrorRig.rotation(yaw,new HorrorRig.Pose(0,0)));
            double length=Math.hypot(move[0],move[1]);
            assertEquals(move[0]/length,face.x,1e-5);assertEquals(move[1]/length,face.z,1e-5);
        }
        assertEquals(37,HorrorRig.motionYaw(0,0,37));
    }
    @Test void armsLegsAndHeadHaveSeparateJointsAndOppositeStrides(){
        for(String kind:HorrorRig.kinds()){
            var layout=HorrorRig.layout(kind);assertEquals(6,layout.pivots().size());assertTrue(layout.height()>0);
            assertNotEquals(layout.pivots().get("head"),layout.pivots().get("body"));
            var left=HorrorRig.pose(kind,"left",Math.PI/2,true,false,false,false);
            var right=HorrorRig.pose(kind,"right",Math.PI/2,true,false,false,false);
            assertEquals(-left.pitch(),right.pitch(),1e-6);
            assertTrue(Math.abs(HorrorRig.pose(kind,"arm_left",Math.PI/2,true,false,false,false).pitch())>0);
            assertEquals(0,HorrorRig.pose(kind,"left",Math.PI/2,false,false,false,false).pitch());
            assertTrue(Math.abs(HorrorRig.pose(kind,"left",Math.PI/2,true,true,false,false).pitch())>Math.abs(left.pitch()));
        }
    }
    @Test void jointOffsetsRotateWithBodyAndSpecialAttacksHaveAnAnimation(){
        var a=HorrorRig.offset("spitter","head",1,0);var b=HorrorRig.offset("spitter","head",1,180);
        assertEquals(a.y,b.y);assertEquals(-a.x,b.x,1e-6);assertEquals(-a.z,b.z,1e-6);
        assertNotEquals(0,HorrorRig.pose("spitter","head",0,false,false,true,false).pitch());
        assertTrue(HorrorRig.pose("breaker","arm_right",0,false,false,false,true).pitch()<-.5);
    }
}
