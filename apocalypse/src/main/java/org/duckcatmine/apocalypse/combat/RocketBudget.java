package org.duckcatmine.apocalypse.combat;

/** Ten lifetime shots per launcher. Wear or repair cannot grant extra charges. */
public final class RocketBudget {
    public static final int CAPACITY=10;
    private RocketBudget(){}
    public static int remaining(Integer stored,int wear){return Math.max(0,Math.min(CAPACITY-Math.max(0,wear),stored==null?CAPACITY:stored));}
    public static boolean repairable(String id){return !"rocket_launcher".equals(id);}
}
