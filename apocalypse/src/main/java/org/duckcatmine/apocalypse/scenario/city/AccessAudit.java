package org.duckcatmine.apocalypse.scenario.city;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Stairs;

/** A bounded walk graph: two-block headroom, operable doors, stairs, loot and bedroom access. */
final class AccessAudit {
    private AccessAudit() {}
    static void verify(CityLayout.Placement p,BlueprintCanvas plan) {
        Frame f=new Frame(plan,p.minX(),p.minZ(),p.width(),p.depth(),p.front());
        int u=AccessKit.entry(p),y=p.floorY()+1;
        var start=new BlueprintCanvas.Pos(f.worldX(u,0),y,f.worldZ(u,0));
        if(!stand(plan.blocks,start)) throw new IllegalStateException("Entrée obstruée : "+p.kind()+" "+p.width()+"×"+p.depth());
        for(int v=0;v<=2;v++) if(!stand(plan.blocks,new BlueprintCanvas.Pos(f.worldX(u,v),y,f.worldZ(u,v))))
            throw new IllegalStateException("Passage derrière la porte obstrué : "+p.kind());
        Set<BlueprintCanvas.Pos> reachable=new HashSet<>();
        ArrayDeque<BlueprintCanvas.Pos> queue=new ArrayDeque<>();queue.add(start);reachable.add(start);
        while(!queue.isEmpty()) {
            var at=queue.removeFirst();
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) for(int dy:new int[]{0,1,-1}) {
                var next=new BlueprintCanvas.Pos(at.x()+d[0],at.y()+dy,at.z()+d[1]);
                if(next.y()<p.floorY()+1 || next.y()>p.floorY()+25 || next.x()<p.minX()-AccessKit.MARGIN || next.x()>p.maxX()+AccessKit.MARGIN
                        || next.z()<p.minZ()-AccessKit.MARGIN || next.z()>p.maxZ()+AccessKit.MARGIN || reachable.contains(next)) continue;
                if(dy==1 && !(data(plan.blocks,next.x(),next.y()-1,next.z()) instanceof Stairs)) continue;
                if(stand(plan.blocks,next)) { reachable.add(next);queue.add(next); }
            }
            if(reachable.size()>50000) throw new IllegalStateException("Contrôle d'accès trop grand.");
        }
        int outside=0;
        for(int step=1;step<=AccessKit.MARGIN;step++) {
            var floor=plan.blocks.get(new BlueprintCanvas.Pos(f.worldX(u,-step),p.floorY(),f.worldZ(u,-step)));
            if(floor!=null && floor.getMaterial().isSolid()) outside=step; else break;
        }
        if(outside>0 && !reachable.contains(new BlueprintCanvas.Pos(f.worldX(u,-outside),y,f.worldZ(u,-outside))))
            throw new IllegalStateException("Chemin vers la rue inaccessible : "+p.kind());
        for(var entry:plan.containers.entrySet()) {
            var pos=entry.getKey();
            if(!adjacent(reachable,pos)) throw new IllegalStateException("Coffre/rayon inaccessible : "+p.kind()+" "+pos);
            BlockData block=data(plan.blocks,pos.x(),pos.y(),pos.z());
            if(block.getMaterial()==Material.CHEST && data(plan.blocks,pos.x(),pos.y()+1,pos.z()).getMaterial().isOccluding())
                throw new IllegalStateException("Couvercle de coffre bloqué : "+p.kind()+" "+pos);
        }
        for(var entry:plan.blocks.entrySet()) {
            var material=entry.getValue().getMaterial();
            if(material.name().endsWith("_BED") && !adjacent(reachable,entry.getKey()))
                throw new IllegalStateException("Lit inaccessible : "+p.kind()+" "+entry.getKey());
            if(entry.getValue() instanceof Door door && door.getHalf()==org.bukkit.block.data.Bisected.Half.BOTTOM) {
                var pos=entry.getKey();var upper=data(plan.blocks,pos.x(),pos.y()+1,pos.z());
                if(!(upper instanceof Door other) || other.getHalf()!=org.bukkit.block.data.Bisected.Half.TOP || other.isOpen()!=door.isOpen() || other.getFacing()!=door.getFacing())
                    throw new IllegalStateException("Moitiés de porte incohérentes : "+p.kind());
            }
        }
    }
    private static boolean adjacent(Set<BlueprintCanvas.Pos> points,BlueprintCanvas.Pos goal) {
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}}) for(int dy=-1;dy<=1;dy++)
            if(points.contains(new BlueprintCanvas.Pos(goal.x()+d[0],goal.y()+dy,goal.z()+d[1]))) return true;
        return false;
    }
    private static BlockData data(Map<BlueprintCanvas.Pos,BlockData> plan,int x,int y,int z) {
        return plan.getOrDefault(new BlueprintCanvas.Pos(x,y,z),B.AIR);
    }
    private static boolean stand(Map<BlueprintCanvas.Pos,BlockData> plan,BlueprintCanvas.Pos pos) {
        BlockData floor=data(plan,pos.x(),pos.y()-1,pos.z());
        return (floor.getMaterial().isSolid() || floor instanceof Stairs || floor instanceof org.bukkit.block.data.type.Slab) && !(floor instanceof Door) && clear(data(plan,pos.x(),pos.y(),pos.z())) && clear(data(plan,pos.x(),pos.y()+1,pos.z()));
    }
    private static boolean clear(BlockData block) {
        Material m=block.getMaterial();
        return m.isAir() || block instanceof Door || !m.isSolid() && m!=Material.WATER && m!=Material.LAVA && m!=Material.LADDER;
    }
}
