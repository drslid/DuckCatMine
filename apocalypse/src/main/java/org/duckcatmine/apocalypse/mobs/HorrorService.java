package org.duckcatmine.apocalypse.mobs;

import java.util.*;
import org.bukkit.*;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;

/** Telegraphed horror abilities. Bounded projectiles/pools, no terrain or inventory mutation. */
public final class HorrorService implements Listener,AutoCloseable {
    public static final int MAX_SHOTS=12,MAX_POOLS=12;
    public static final double JUMP_VELOCITY=.78;
    private static final class Ability {long ready,launch;UUID target;}
    private record Shot(UUID source,long until,double damage){}
    private record Pool(Location at,double radius,double damage,long until,UUID source,boolean gas){}
    private final DuckCatApocalypse plugin;
    private final Map<UUID,Ability> abilities=new HashMap<>();
    private final Map<UUID,Shot> shots=new HashMap<>();
    private final List<Pool> pools=new ArrayList<>();
    private final Map<UUID,Long> nextDamage=new HashMap<>();
    private boolean applying;
    private long frames;
    public HorrorService(DuckCatApocalypse plugin){this.plugin=plugin;}
    public int poolCount(){return pools.size();}
    public int shotCount(){return shots.size();}
    public boolean windingUp(Entity mob){Ability state=abilities.get(mob.getUniqueId());return state!=null&&state.launch>plugin.clock().now();}
    public static void fallback(Zombie mob,String id){
        var eq=mob.getEquipment();
        int color=switch(id){case "spitter"->0x536a3e;case "jumper"->0x6d5449;case "crawler"->0x6f685b;case "stalker"->0x919689;case "bloated"->0x526b64;default->-1;};
        if(color<0)return;
        for(var entry:Map.of(org.bukkit.inventory.EquipmentSlot.CHEST,Material.LEATHER_CHESTPLATE,org.bukkit.inventory.EquipmentSlot.LEGS,Material.LEATHER_LEGGINGS).entrySet()){
            ItemStack item=ItemStack.of(entry.getValue());item.editMeta(org.bukkit.inventory.meta.LeatherArmorMeta.class,m->m.setColor(Color.fromRGB(color)));
            item.setData(io.papermc.paper.datacomponent.DataComponentTypes.ATTRIBUTE_MODIFIERS,io.papermc.paper.datacomponent.item.ItemAttributeModifiers.itemAttributes().build());
            eq.setItem(entry.getKey(),item);
        }
        if("stalker".equals(id))mob.setSilent(true);
    }
    public boolean eligible(Player p){return p.isValid()&&!p.isDead()&&plugin.sessions().isSurvivor(p)
            &&p.getGameMode()!=GameMode.CREATIVE&&p.getGameMode()!=GameMode.SPECTATOR
            &&!plugin.scenario().inRefuge(p.getLocation())&&!plugin.protection().isProtected(p);}
    public void tick(){
        long now=plugin.clock().now();frames++;
        for(var e:new ArrayList<>(shots.entrySet())){
            Entity projectile=Bukkit.getEntity(e.getKey());
            if(projectile==null||!projectile.isValid()||now>=e.getValue().until()||!plugin.clock().running()){
                if(projectile!=null)projectile.remove();shots.remove(e.getKey());
            }
        }
        if(!plugin.clock().running()||!plugin.scenario().isEnabled()){pools.clear();return;}
        abilities.keySet().removeIf(id->!(Bukkit.getEntity(id) instanceof Mob));
        nextDamage.values().removeIf(until->until<now-5000);
        for(UUID id:plugin.mobs().trackedIds()){
            if(!(Bukkit.getEntity(id) instanceof Mob mob)||!mob.isValid()||mob.isDead()||plugin.director().approaching(mob))continue;
            String kind=plugin.mobs().archetypeOf(mob);
            if(!"spitter".equals(kind)&&!"jumper".equals(kind))continue;
            var config=plugin.settings().archetype(kind);if(config==null)continue;
            Ability state=abilities.computeIfAbsent(id,k->new Ability());
            if(state.launch>0){
                Player target=state.target==null?null:Bukkit.getPlayer(state.target);
                if(target==null||!eligible(target)||!target.getWorld().equals(mob.getWorld())||!mob.hasLineOfSight(target)){
                    state.launch=0;state.ready=now+2000;continue;
                }
                if(now<state.launch){telegraph(mob,kind);continue;}
                state.launch=0;
                if("spitter".equals(kind))spit(mob,target,config.ability("acid-damage",4));
                else jump(mob,target.getLocation());
                state.ready=now+(long)(config.ability("spitter".equals(kind)?"acid-cooldown-seconds":"jump-cooldown-seconds",7)*1000);
                continue;
            }
            if(now<state.ready||!(mob.getTarget() instanceof Player target)||!eligible(target)||!mob.hasLineOfSight(target))continue;
            double d=mob.getLocation().distanceSquared(target.getLocation()),range=config.ability("spitter".equals(kind)?"acid-range":"jump-range", "spitter".equals(kind)?20:14);
            if(d>range*range||d<9||("jumper".equals(kind)&&!mob.isOnGround()))continue;
            state.target=target.getUniqueId();state.launch=now+(long)(config.ability("spitter".equals(kind)?"acid-windup-seconds":"jump-windup-seconds","spitter".equals(kind)?.8:.6)*1000);
            mob.getWorld().playSound(mob.getLocation(),"spitter".equals(kind)?Sound.ENTITY_SLIME_SQUISH:Sound.ENTITY_FOX_SCREECH,1f,.6f);
            sound(mob.getLocation(),"charge",.7f);
        }
        pools.removeIf(p->now>=p.until()||!p.at().getWorld().isChunkLoaded(p.at().getBlockX()>>4,p.at().getBlockZ()>>4));
        for(Pool p:new ArrayList<>(pools)){
            if(frames%2==0)p.at().getWorld().spawnParticle(Particle.DUST,p.at().clone().add(0,.2,0),8,p.radius()*.55,.18,p.radius()*.55,new Particle.DustOptions(Color.fromRGB(p.gas()?112:158,p.gas()?126:195,55),1.1f));
            for(Player player:p.at().getWorld().getPlayers()){
                if(!eligible(player)||player.getLocation().distanceSquared(p.at())>p.radius()*p.radius())continue;
                Location start=p.at().clone().add(0,.4,0),end=player.getEyeLocation();Vector delta=end.toVector().subtract(start.toVector());
                if(delta.lengthSquared()>.01&&start.getWorld().rayTraceBlocks(start,delta.clone().normalize(),delta.length(),FluidCollisionMode.NEVER,true)!=null)continue;
                hurt(player,p.damage(),p.source(),null,now);
            }
        }
    }
    private void telegraph(Mob mob,String kind){
        mob.getWorld().spawnParticle("spitter".equals(kind)?Particle.SNEEZE:Particle.ANGRY_VILLAGER,mob.getEyeLocation(),2,.12,.1,.12,0);
    }
    public boolean spit(Mob source,LivingEntity target,double damage){
        if(shots.size()>=MAX_SHOTS||!source.getWorld().equals(target.getWorld())||!source.hasLineOfSight(target))return false;
        Location origin=source.getEyeLocation();Vector direction=target.getEyeLocation().toVector().subtract(origin.toVector());
        if(direction.lengthSquared()<.01)return false;
        Snowball ball=source.getWorld().spawn(origin.clone().add(direction.clone().normalize().multiply(.8)),Snowball.class,e->{
            e.setShooter(source);e.setItem(ItemStack.of(Material.SLIME_BALL));e.setGravity(false);e.setPersistent(false);e.setVelocity(direction.normalize().multiply(.9));
        });
        shots.put(ball.getUniqueId(),new Shot(source.getUniqueId(),plugin.clock().now()+2500,Math.min(20,Math.max(0,damage))));
        source.getWorld().playSound(origin,Sound.ENTITY_LLAMA_SPIT,1f,.65f);sound(origin,"spit",.8f);return true;
    }
    public void jump(Mob mob,Location target){
        Vector direction=target.toVector().subtract(mob.getLocation().toVector()).setY(0);
        if(direction.lengthSquared()>.001)direction.normalize().multiply(.48);
        mob.setVelocity(direction.setY(JUMP_VELOCITY));mob.getWorld().playSound(mob.getLocation(),Sound.ENTITY_GOAT_LONG_JUMP,1f,.7f);sound(mob.getLocation(),"jump",.6f);
    }
    private void sound(Location at,String sound,float volume){if(plugin.horrorModels()!=null)plugin.horrorModels().sound(at,sound,volume);}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)public void onProjectileDamage(EntityDamageByEntityEvent e){if(!applying&&shots.containsKey(e.getDamager().getUniqueId()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)public void onHit(ProjectileHitEvent event){
        Shot shot=shots.remove(event.getEntity().getUniqueId());if(shot==null)return;
        Location at=event.getHitEntity()!=null?event.getHitEntity().getLocation():event.getEntity().getLocation();
        event.setCancelled(true);event.getEntity().remove();
        if(event.getHitEntity() instanceof Player p&&eligible(p))hurt(p,shot.damage(),shot.source(),null,plugin.clock().now());
        // Settle the visual on a floor at most three blocks below; never scan or load a distant column.
        for(int drop=0;drop<3;drop++){
            var below=at.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
            if(below.isSolid()){at.setY(below.getY()+1.05);break;}
            if(!below.isPassable()||below.isLiquid())break;
            at.subtract(0,1,0);
        }
        pool(at,1.7,2,3000,shot.source(),false);
    }
    public void pool(Location at,double radius,double damage,long duration,UUID source,boolean gas){
        if(pools.size()>=MAX_POOLS||plugin.scenario().inRefuge(at))return;
        pools.add(new Pool(at.clone(),Math.min(4,Math.max(.5,radius)),Math.min(10,Math.max(0,damage)),plugin.clock().now()+Math.min(8000,duration),source,gas));
    }
    private void hurt(Player target,double amount,UUID owner,Entity direct,long now){
        if(!eligible(target)||now<nextDamage.getOrDefault(target.getUniqueId(),0L))return;
        nextDamage.put(target.getUniqueId(),now+1000);
        var builder=DamageSource.builder(DamageType.MAGIC);Entity source=Bukkit.getEntity(owner);
        if(source!=null)builder.withCausingEntity(source);if(direct!=null)builder.withDirectEntity(direct);
        applying=true;try{target.damage(amount,builder.build());}finally{applying=false;}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void onDamage(EntityDamageEvent e){
        Ability state=abilities.get(e.getEntity().getUniqueId());if(state==null||state.launch<=0||e.getFinalDamage()<=0)return;
        state.launch=0;state.ready=plugin.clock().now()+2000;
    }
    @EventHandler(priority=EventPriority.MONITOR)public void onDeath(EntityDeathEvent event){
        String id=plugin.mobs().archetypeOf(event.getEntity());if(!"bloated".equals(id))return;
        var config=plugin.settings().archetype(id);if(config==null)return;
        Location at=event.getEntity().getLocation();
        pool(at,config.ability("gas-radius",3),config.ability("gas-damage",3),(long)(config.ability("gas-seconds",4)*1000),event.getEntity().getUniqueId(),true);
        at.getWorld().playSound(at,Sound.ENTITY_SLIME_DEATH,1f,.5f);sound(at,"burst",.8f);
    }
    @Override public void close(){for(UUID id:new ArrayList<>(shots.keySet())){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}shots.clear();pools.clear();abilities.clear();nextDamage.clear();}
}
