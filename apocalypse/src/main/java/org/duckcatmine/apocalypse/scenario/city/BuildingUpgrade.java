package org.duckcatmine.apocalypse.scenario.city;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.persistence.PersistentDataType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;

/** Prepare an upgrade against the revision actually present; retain inventories and player changes. */
final class BuildingUpgrade {
    record Edit(BlueprintCanvas.Pos pos,BlockData before,BlockData after,PlaceType place) {}
    record Batch(List<Edit> edits) {}
    record Plan(List<Batch> batches,int revision,int preserved) {}
    private BuildingUpgrade() {}

    static Plan prepare(DuckCatApocalypse plugin,World world,CityLayout.Placement p,CityLayout layout) {
        BlueprintCanvas one=reference(p,layout,1),two=reference(p,layout,2),next=new BlueprintCanvas();
        Structures.render(p,next,layout);
        int scoreOne=0,scoreTwo=0;
        for(var entry:one.blocks.entrySet()) {
            var other=two.blocks.get(entry.getKey());
            if(other==null || other.equals(entry.getValue())) continue;
            var at=entry.getKey();var actual=world.getBlockAt(at.x(),at.y(),at.z()).getBlockData();
            if(actual.equals(entry.getValue())) scoreOne++;
            if(actual.equals(other)) scoreTwo++;
        }
        int revision=scoreOne>scoreTwo?1:2;
        BlueprintCanvas before=revision==1?one:two;
        extendApproach(before,next,p,layout,revision);
        Map<BlueprintCanvas.Pos,Edit> edits=new LinkedHashMap<>();
        BlueprintCanvas effective=new BlueprintCanvas();
        Set<BlueprintCanvas.Pos> points=new HashSet<>(before.blocks.keySet());points.addAll(next.blocks.keySet());
        int preserved=0;
        for(var at:points) {
            var block=world.getBlockAt(at.x(),at.y(),at.z());
            BlockData actual=block.getBlockData(),expected=before.blocks.get(at),after=next.blocks.get(at);
            effective.blocks.put(at,actual);
            if(block.getState(false) instanceof Container) effective.containers.put(at,before.containers.getOrDefault(at,PlaceType.HOUSE));
            if(after==null || actual.equals(after)) continue;
            if(expected==null || !actual.equals(expected) || block.getState(false) instanceof TileState || plugin.placed().get(block)!=null) { preserved++;continue; }
            edits.put(at,new Edit(at,actual,after,next.containers.get(at)));
        }
        // A changed open flag is normal gameplay. Handle both halves as one atomic edit, preserving the user's open state.
        Set<BlueprintCanvas.Pos> doors=new HashSet<>();
        for(var at:points) {
            BlockData old=before.blocks.get(at),after=next.blocks.get(at);
            Door door=old instanceof Door beforeDoor?beforeDoor:after instanceof Door afterDoor?afterDoor:null;
            if(door==null) continue;
            var lower=door.getHalf()==org.bukkit.block.data.Bisected.Half.BOTTOM?at:new BlueprintCanvas.Pos(at.x(),at.y()-1,at.z());
            doors.add(lower);
        }
        for(var lower:doors) {
            var upper=new BlueprintCanvas.Pos(lower.x(),lower.y()+1,lower.z());
            var oldLow=before.blocks.get(lower);var oldHigh=before.blocks.get(upper);
            var actualLow=world.getBlockAt(lower.x(),lower.y(),lower.z()).getBlockData();
            var actualHigh=world.getBlockAt(upper.x(),upper.y(),upper.z()).getBlockData();
            if(oldLow instanceof Door && oldHigh instanceof Door && doorIdentity(actualLow,oldLow) && doorIdentity(actualHigh,oldHigh)) {
                boolean open=((Door)actualLow).isOpen();
                for(var point:List.of(lower,upper)) {
                    var after=next.blocks.get(point);var actual=world.getBlockAt(point.x(),point.y(),point.z()).getBlockData();
                    if(after==null) continue;
                    after=after.clone();
                    if(after instanceof Door d) { d.setOpen(open);d.setHinge(((Door)actualLow).getHinge()); }
                    if(!after.equals(actual)) edits.put(point,new Edit(point,actual,after,next.containers.get(point)));
                    else edits.remove(point);
                }
            } else if(edits.containsKey(lower)!=edits.containsKey(upper)) {
                // Never leave half a door when a player's edit prevents updating its counterpart.
                edits.remove(lower);edits.remove(upper);
            }
        }
        for(var edit:edits.values()) {
            effective.blocks.put(edit.pos(),edit.after());
            if(edit.place()!=null) effective.containers.put(edit.pos(),edit.place());
            else effective.containers.remove(edit.pos());
        }
        AccessAudit.verify(p,effective);
        List<Batch> batches=new ArrayList<>();Set<BlueprintCanvas.Pos> used=new HashSet<>();
        for(var lower:doors) {
            var upper=new BlueprintCanvas.Pos(lower.x(),lower.y()+1,lower.z());
            List<Edit> pair=new ArrayList<>();
            for(var point:List.of(lower,upper)) if(edits.containsKey(point)) { pair.add(edits.get(point));used.add(point); }
            if(!pair.isEmpty()) batches.add(new Batch(List.copyOf(pair)));
        }
        for(var edit:edits.values()) if(!used.contains(edit.pos())) batches.add(new Batch(List.of(edit)));
        return new Plan(List.copyOf(batches),revision,preserved);
    }
    private static boolean doorIdentity(BlockData actual,BlockData expected) {
        if(!(actual instanceof Door a) || !(expected instanceof Door e)) return false;
        return a.getMaterial()==e.getMaterial() && a.getFacing()==e.getFacing() && a.getHalf()==e.getHalf();
    }
    static int apply(DuckCatApocalypse plugin,World world,Batch batch,List<RenovationAreas.Area> excluded) {
        for(var edit:batch.edits()) {
            var p=edit.pos();
            if(!world.isChunkLoaded(p.x()>>4,p.z()>>4) || excluded.stream().anyMatch(a->a.contains(world.getName(),p.x(),p.z()))) return 0;
            var block=world.getBlockAt(p.x(),p.y(),p.z());
            if(block.getState(false) instanceof TileState || plugin.placed().get(block)!=null || !block.getBlockData().equals(edit.before())) return 0;
        }
        for(var edit:batch.edits()) {
            var p=edit.pos();var block=world.getBlockAt(p.x(),p.y(),p.z());block.setBlockData(edit.after(),false);
            if(edit.place()!=null && block.getState() instanceof TileState tile) {
                tile.getPersistentDataContainer().set(plugin.keys().searchPlace,PersistentDataType.STRING,edit.place().id());tile.update(true,false);
            }
        }
        return batch.edits().size();
    }
    static BlueprintCanvas reference(CityLayout.Placement p,CityLayout layout,int revision) {
        BlueprintCanvas result=new BlueprintCanvas();Structures.renderRevision(p,result,layout,revision);return result;
    }
    private static void extendApproach(BlueprintCanvas before,BlueprintCanvas next,CityLayout.Placement p,CityLayout layout,int revision) {
        Set<BlueprintCanvas.Pos> outside=new HashSet<>();
        for(var at:next.blocks.keySet()) if(at.x()<p.minX() || at.x()>p.maxX() || at.z()<p.minZ() || at.z()>p.maxZ()) {
            var terrain=layout.approachTerrain(at.x(),at.y(),at.z());
            if(terrain!=null) { before.blocks.put(at,terrain);outside.add(at); }
        }
        if(outside.isEmpty()) return;
        Canvas slice=new Canvas() {
            @Override public void set(int x,int y,int z,BlockData data) { var at=new BlueprintCanvas.Pos(x,y,z);if(outside.contains(at)) before.blocks.put(at,data); }
            @Override public void fill(int x1,int y1,int z1,int x2,int y2,int z2,BlockData data) {
                int ax=Math.min(x1,x2),bx=Math.max(x1,x2),ay=Math.min(y1,y2),by=Math.max(y1,y2),az=Math.min(z1,z2),bz=Math.max(z1,z2);
                for(var at:outside) if(at.x()>=ax && at.x()<=bx && at.y()>=ay && at.y()<=by && at.z()>=az && at.z()<=bz) before.blocks.put(at,data);
            }
            @Override public void container(int x,int y,int z,BlockData data,PlaceType place) { set(x,y,z,data); }
        };
        for(var neighbour:layout.placementsAt((p.minX()+p.maxX())/2,(p.minZ()+p.maxZ())/2)) Structures.renderRevision(neighbour,slice,layout,revision);
    }
}
