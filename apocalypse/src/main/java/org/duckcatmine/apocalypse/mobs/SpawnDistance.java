package org.duckcatmine.apocalypse.mobs;

/** Horizontal spawn exclusion: standing on a roof must not allow an infected to appear underneath. */
public final class SpawnDistance {
    public static final int MINIMUM = 101;
    public static final int MAXIMUM = 200;
    private SpawnDistance() {}
    public static boolean outside(double x,double z,double playerX,double playerZ,double distance) {
        double dx=x-playerX,dz=z-playerZ;
        return dx*dx+dz*dz>=distance*distance;
    }
}
