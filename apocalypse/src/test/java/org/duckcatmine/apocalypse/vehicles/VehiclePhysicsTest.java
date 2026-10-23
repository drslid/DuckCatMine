package org.duckcatmine.apocalypse.vehicles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VehiclePhysicsTest {
    private static final double ROAD = 0.75;

    @Test
    void reachesTopSpeedInAFewSecondsAndNeverMore() {
        double speed = 0;
        int ticks = 0;
        while (speed < ROAD * 0.95 && ticks < 400) {
            speed = VehiclePhysics.speed(speed, true, false, false, true, ROAD);
            ticks++;
        }
        assertTrue(ticks > 40 && ticks < 200, "0 to 95 % in " + ticks + " ticks");
        for (int k = 0; k < 200; k++) speed = VehiclePhysics.speed(speed, true, false, false, true, ROAD);
        assertTrue(speed <= ROAD + 1e-9);
        assertEquals(54, VehiclePhysics.kmh(ROAD));
    }

    @Test
    void noEngineNoPullAndBrakesStopQuickly() {
        assertEquals(0, VehiclePhysics.speed(0, true, false, false, false, ROAD));
        double speed = ROAD;
        int ticks = 0;
        while (speed > 0 && ticks < 100) {
            speed = VehiclePhysics.speed(speed, false, true, false, true, ROAD);
            ticks++;
        }
        assertEquals(0, speed);
        assertTrue(ticks <= 25, "stops in " + ticks + " ticks");
    }

    @Test
    void reverseIsSlowAndOffroadSlowsDown() {
        double speed = 0;
        for (int k = 0; k < 200; k++) speed = VehiclePhysics.speed(speed, false, true, false, true, ROAD);
        assertEquals(-VehiclePhysics.MAX_REVERSE, speed, 1e-9);
        double fast = ROAD;
        for (int k = 0; k < 40; k++) fast = VehiclePhysics.speed(fast, true, false, false, true, 0.42);
        assertEquals(0.42, fast, 1e-9);
    }

    @Test
    void steeringNeedsSpeedAndReversesBackwards() {
        assertEquals(0f, VehiclePhysics.steer(0, true, false));
        assertEquals(0f, VehiclePhysics.steer(0.5, true, true));
        assertTrue(VehiclePhysics.steer(0.5, true, false) < 0);
        assertTrue(VehiclePhysics.steer(-0.15, true, false) > 0);
        assertTrue(Math.abs(VehiclePhysics.steer(0.05, false, true)) < Math.abs(VehiclePhysics.steer(0.3, false, true)));
    }

    @Test
    void stateSurvivesARoundTrip() {
        Vehicle car = new Vehicle(java.util.UUID.randomUUID(), null, VehicleModel.SEDAN, org.bukkit.Material.RED_CONCRETE,
                10.5, 65, -3, 90f, 12.34, 77.5);
        car.salvaged = true;
        car.barriers.add(new int[]{10, 65, -3});
        car.barriers.add(new int[]{11, 66, -2});
        Vehicle copy = Vehicle.decode(car.id, null, car.encode(), 0, 0, 0, 0);
        assertEquals(VehicleModel.SEDAN, copy.model);
        assertEquals(org.bukkit.Material.RED_CONCRETE, copy.paint);
        assertEquals(12.34, copy.fuel, 1e-9);
        assertEquals(77.5, copy.health, 1e-9);
        assertTrue(copy.salvaged);
        assertEquals(2, copy.barriers.size());
        assertEquals(66, copy.barriers.get(1)[1]);
        assertEquals(VehicleModel.SEDAN, Vehicle.decode(car.id, null, "garbage;;;", 0, 0, 0, 0).model);
    }
}
