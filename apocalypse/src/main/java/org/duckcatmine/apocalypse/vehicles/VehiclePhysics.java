package org.duckcatmine.apocalypse.vehicles;

/** Speed and steering of a car, per server tick. Speeds are blocks per tick (x 72 = km/h). */
final class VehiclePhysics {
    static final double ACCELERATION = 0.011;
    static final double BRAKING = 0.04;
    static final double REVERSE_ACCELERATION = 0.006;
    static final double MAX_REVERSE = 0.2;
    static final double ROLLING = 0.982;
    static final double HANDBRAKE = 0.86;
    static final double TURN_DEGREES = 4.2;

    private VehiclePhysics() {
    }

    /** Next speed: the engine pulls less near top speed, a car above the ground's limit slows down. */
    static double speed(double speed, boolean forward, boolean backward, boolean handbrake, boolean engine, double max) {
        double next = speed;
        if (forward && engine) {
            next = speed < 0 ? Math.min(0, speed + BRAKING) : Math.min(max, speed + ACCELERATION * (1 - 0.6 * speed / max));
        } else if (backward) {
            if (speed > 0) next = Math.max(0, speed - BRAKING);
            else if (engine) next = Math.max(-MAX_REVERSE, speed - REVERSE_ACCELERATION);
            else next = speed * ROLLING;
        } else {
            next = speed * ROLLING;
        }
        if (handbrake) next *= HANDBRAKE;
        if (next > max) next = Math.max(max, next - BRAKING);
        boolean pulling = engine && (forward || backward);
        if (!pulling && Math.abs(next) < 0.004) next = 0;
        return next;
    }

    /** Yaw change in degrees: none at rest, full lock from walking pace, a little less at high speed; reversed backwards. */
    static float steer(double speed, boolean left, boolean right) {
        if (left == right || speed == 0) return 0;
        double grip = Math.min(1, Math.abs(speed) / 0.2) * (Math.abs(speed) > 0.55 ? 0.8 : 1);
        double rate = TURN_DEGREES * grip * Math.signum(speed);
        return (float) (left ? -rate : rate);
    }

    static int kmh(double speed) {
        return (int) Math.round(Math.abs(speed) * 72);
    }
}
