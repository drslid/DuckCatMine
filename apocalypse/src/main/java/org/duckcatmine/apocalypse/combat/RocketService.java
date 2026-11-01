package org.duckcatmine.apocalypse.combat;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.items.GunModels;

/** Visible finite rockets, swept collision each tick, no vanilla explosion or chunk generation. */
public final class RocketService implements AutoCloseable {
    public static final double DAMAGE=80,RADIUS=6,RANGE=96,SPEED=1.2;
    public static final int MAX_ACTIVE=8;
    private static final class Shot {
        final UUID owner;final ItemDisplay display;final Vector velocity;final boolean trial;
        Location at;double travelled;
        Shot(UUID owner,ItemDisplay display,Location at,Vector velocity,boolean trial){this.owner=owner;this.display=display;this.at=at;this.velocity=velocity;this.trial=trial;}
    }
    private final DuckCatApocalypse plugin;
    private final Map<UUID,Shot> shots=new HashMap<>();
    private BukkitTask task;
    private Location lastImpact;
    public RocketService(DuckCatApocalypse plugin){this.plugin=plugin;}
    public int count(){return shots.size();}
    public Location lastImpact(){return lastImpact==null?null:lastImpact.clone();}
    public boolean launch(Player player){
        if(!plugin.sessions().isSurvivor(player)||player.isDead()||plugin.scenario().inRefuge(player.getLocation()))return false;
        return launch(player.getEyeLocation(),player.getEyeLocation().getDirection(),player.getUniqueId(),false);
    }
    public boolean trial(Location at,Vector direction){
        if(!at.getWorld().getPlayers().isEmpty())throw new IllegalStateException("Test réservé au monde vide");
        return launch(at,direction,null,true);
    }
    private boolean launch(Location at,Vector direction,UUID owner,boolean trial){
        if(shots.size()>=MAX_ACTIVE||direction.lengthSquared()<.001||!plugin.scenario().isScenarioWorld(at.getWorld())||!plugin.scenario().isEnabled())return false;
        Vector velocity=direction.clone().normalize().multiply(SPEED);
        Location from=at.clone().add(direction.clone().normalize().multiply(.35));
        if(!loaded(from,from))return false;
        ItemDisplay display=from.getWorld().spawn(from,ItemDisplay.class,e->{
            e.setPersistent(false);e.setInvulnerable(true);e.setGravity(false);e.setItemStack(GunModels.projectile());
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);e.setTeleportDuration(1);e.setViewRange(1.5f);e.setDisplayWidth(1);e.setDisplayHeight(1);
            e.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),new org.joml.Quaternionf(),new org.joml.Vector3f(.35f),new org.joml.Quaternionf()));
        });
        if(!display.isValid()){display.remove();return false;}
        shots.put(display.getUniqueId(),new Shot(owner,display,from,velocity,trial));
        at.getWorld().playSound(at,Sound.ENTITY_FIREWORK_ROCKET_LAUNCH,1.6f,.65f);plugin.horrorModels().sound(at,"rocket_launch",1f);
        if(task==null)task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);return true;
    }
    private void tick(){
        for(var entry:new ArrayList<>(shots.entrySet())){
            Shot shot=entry.getValue();Player owner=shot.owner==null?null:Bukkit.getPlayer(shot.owner);
            if(!shot.display.isValid()||!plugin.scenario().isEnabled()||!plugin.clock().running()
                    ||(shot.trial?!shot.at.getWorld().getPlayers().isEmpty():owner==null||!owner.isOnline()||owner.isDead()||!plugin.sessions().isSurvivor(owner)||!owner.getWorld().equals(shot.at.getWorld()))){remove(entry.getKey());continue;}
            Location next=shot.at.clone().add(shot.velocity).setDirection(shot.velocity);
            if(!loaded(shot.at,next)||!next.getWorld().getWorldBorder().isInside(next)){remove(entry.getKey());continue;}
            var hit=FirearmService.trace(shot.at,shot.velocity.clone().normalize(),SPEED,owner);
            if(hit!=null||shot.travelled+SPEED>=RANGE){
                Location impact=hit==null?next:hit.getHitPosition().toLocation(next.getWorld());
                remove(entry.getKey());lastImpact=impact.clone();
                if(loaded(impact.clone().subtract(RADIUS,0,RADIUS),impact.clone().add(RADIUS,0,RADIUS))){
                    plugin.throwables().explode(impact,RADIUS,DAMAGE,owner);plugin.horrorModels().sound(impact,"rocket_impact",1f);
                }else impact.getWorld().spawnParticle(Particle.SMOKE,impact,8,.1,.1,.1,.02);
                continue;
            }
            shot.travelled+=SPEED;shot.at=next;shot.display.teleport(next);
            next.getWorld().spawnParticle(Particle.SMOKE,next,2,.04,.04,.04,0);
            next.getWorld().spawnParticle(Particle.FLAME,next,1,.02,.02,.02,0);
        }
        if(shots.isEmpty()&&task!=null){task.cancel();task=null;}
    }
    /** Conservatively check the segment's chunk rectangle before ray tracing. At most four chunks per step. */
    public static boolean loaded(Location from,Location to){
        if(!from.getWorld().equals(to.getWorld()))return false;
        for(int x=Math.min(from.getBlockX(),to.getBlockX())>>4;x<=(Math.max(from.getBlockX(),to.getBlockX())>>4);x++)
            for(int z=Math.min(from.getBlockZ(),to.getBlockZ())>>4;z<=(Math.max(from.getBlockZ(),to.getBlockZ())>>4);z++)if(!from.getWorld().isChunkLoaded(x,z))return false;
        return true;
    }
    private void remove(UUID id){Shot shot=shots.remove(id);if(shot!=null)shot.display.remove();}
    @Override public void close(){if(task!=null)task.cancel();task=null;for(UUID id:new ArrayList<>(shots.keySet()))remove(id);}
}
