package org.duckcatmine.apocalypse.scenario.city;

import org.bukkit.Material;

/** Keep the front approach predictable; no furniture, fences or random decay on the entry route. */
final class AccessKit {
    static final int MARGIN=12;
    private AccessKit() {}
    static boolean eligible(CityLayout.Kind kind) {
        return switch(kind) {
            case HOUSE_SMALL,HOUSE_LARGE,FARMHOUSE,APARTMENT,SHOP,SUPERMARKET -> true;
            default -> false;
        };
    }
    static int entry(CityLayout.Placement p) {
        int mid=p.width()/2;
        return p.kind()==CityLayout.Kind.SUPERMARKET && mid%4==3?mid+1:mid;
    }
    static void approach(CityLayout.Placement p,Canvas out,CityLayout layout) {
        if(!eligible(p.kind()) || !CityLayout.urban(p.minX(),p.minZ())) return;
        Frame f=new Frame(out,p.minX(),p.minZ(),p.width(),p.depth(),p.front());
        int u=entry(p);
        for(int step=1;step<=MARGIN;step++) {
            int v=-step;
            f.fill(u-1,p.floorY(),v,u+1,p.floorY(),v,B.of(Material.STONE_BRICKS));
            f.fill(u-1,p.floorY()+1,v,u+1,p.floorY()+2,v,B.AIR);
            if(layout.isStreet(f.worldX(u,v),f.worldZ(u,v))) break;
        }
    }
}
