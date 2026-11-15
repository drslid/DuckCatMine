package org.duckcatmine.apocalypse.mobs;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.scenario.city.CityLayout;
import org.duckcatmine.apocalypse.scenario.city.RenovationAreas;

/** One encounter in some abandoned buildings. Surveyed rooms stay clear across unloads and restarts. */
public final class IndoorSpawns {
    private final DuckCatApocalypse plugin;
    private final List<RenovationAreas.Area> protectedAreas;
    private final boolean protectionValid;
    private final Map<String,Long> retry=new LinkedHashMap<>();
    public IndoorSpawns(DuckCatApocalypse plugin){
        this.plugin=plugin;List<RenovationAreas.Area> areas=List.of();boolean valid=true;
        try{areas=RenovationAreas.read(plugin.getDataFolder().toPath().resolve("renovation-exclusions.json"));}
        catch(java.io.IOException e){valid=false;plugin.getLogger().warning("Apparitions intérieures suspendues : "+e.getMessage());}
        protectedAreas=areas;protectionValid=valid;
    }
    private NamespacedKey key(CityLayout.InteriorSite site){return new NamespacedKey(plugin,"interior_clear_"+site.key());}
    public void mark(World world,CityLayout.InteriorSite site,Chunk chunk){chunk.getPersistentDataContainer().set(key(site),PersistentDataType.BYTE,(byte)1);}
    public void observe(World world){
        var layout=CityLayout.of(world.getSeed());
        for(var p:world.getPlayers()){
            var at=p.getLocation();var site=layout.interiorAt(at.getBlockX(),at.getBlockZ());
            if(site!=null&&at.getY()>=site.floorY()+1&&world.getHighestBlockYAt(at.getBlockX(),at.getBlockZ())>=at.getBlockY()+2)mark(world,site,p.getChunk());
        }
    }
    public boolean allowed(World world,CityLayout.InteriorSite site){
        if(site==null||!protectionValid||!site.infested(world.getSeed())||System.currentTimeMillis()<retry.getOrDefault(site.key(),0L))return false;
        if(protectedAreas.stream().anyMatch(a->a.intersects(world.getName(),site.minX(),site.minZ(),site.maxX(),site.maxZ())))return false;
        if(plugin.placed().claims().stream().anyMatch(c->c.world().equals(world.getName())&&c.x()>=site.minX()-12&&c.x()<=site.maxX()+12&&c.z()>=site.minZ()-12&&c.z()<=site.maxZ()+12))return false;
        for(int cx=site.minX()>>4;cx<=site.maxX()>>4;cx++)for(int cz=site.minZ()>>4;cz<=site.maxZ()>>4;cz++){
            if(!world.isChunkLoaded(cx,cz)||world.getChunkAt(cx,cz).getPersistentDataContainer().has(key(site),PersistentDataType.BYTE))return false;
        }
        return true;
    }
    public Location spot(World world,int x,int z,CityLayout.InteriorSite site){
        if(site==null||!site.contains(x,z)||!world.isChunkLoaded(x>>4,z>>4)||plugin.scenario().refugeArea().near(x,z,16))return null;
        int y=site.floorY()+1;
        if(world.getHighestBlockYAt(x,z)<y+2)return null;
        Location at=new Location(world,x+.5,y,z+.5);if(!world.getWorldBorder().isInside(at))return null;
        var feet=at.getBlock();
        if(!feet.isPassable()||feet.isLiquid()||!feet.getRelative(BlockFace.UP).isPassable()||!feet.getRelative(BlockFace.UP,2).isPassable()||!feet.getRelative(BlockFace.DOWN).isSolid())return null;
        return at;
    }
    public void prepared(CityLayout.InteriorSite site){
        retry.put(site.key(),System.currentTimeMillis()+300_000);
        while(retry.size()>256)retry.remove(retry.keySet().iterator().next());
    }
}
