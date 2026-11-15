package org.duckcatmine.apocalypse.commands;

import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.duckcatmine.apocalypse.DuckCatApocalypse;

/** Isolated native flight/impact test. Refused and cancelled whenever a player enters Ground Zero. */
final class RocketSelfTest {
    private static boolean running;
    static String start(DuckCatApocalypse p){
        World w=p.scenario().world();if(running||w==null||!p.scenario().isEnabled()||!w.getPlayers().isEmpty())throw new IllegalArgumentException("Fais sortir les joueurs de Ground Zero avant le test des roquettes.");
        var trial=new Trial(p,w);running=true;trial.step(trial::setup);return trial.id;
    }
    private static final class Trial{
        final DuckCatApocalypse p;final World w;final String id=UUID.randomUUID().toString();
        final List<Block> blocks=new ArrayList<>(),wall=new ArrayList<>();final List<Chunk> tickets=new ArrayList<>(),forced=new ArrayList<>();
        final List<String> details=new ArrayList<>();Cow target;org.bukkit.block.Chest chest;Location muzzle;double health;int waits;boolean ended;
        final int x=300,z=300,y=260;
        Trial(DuckCatApocalypse p,World w){this.p=p;this.w=w;}
        void step(Runnable task){if(ended)return;try{if(!w.getPlayers().isEmpty())throw new IllegalStateException("Un joueur est entré : test interrompu");task.run();}catch(Exception e){finish(false,e.toString());}}
        void later(Runnable task,long ticks){p.getServer().getScheduler().runTaskLater(p,()->step(task),ticks);}
        void put(Block b,Material material){blocks.add(b);b.setType(material,false);}
        void setup(){
            if(p.rockets().count()!=0)throw new IllegalStateException("Des roquettes sont déjà en vol");
            for(int dx=-10;dx<=10;dx++)for(int dz=-4;dz<=20;dz++)for(int dy=-1;dy<=4;dy++)if(!w.getBlockAt(x+dx,y+dy,z+dz).getType().isAir())throw new IllegalStateException("Arène occupée");
            for(int cx=((x-10)>>4)-1;cx<=((x+10)>>4)+1;cx++)for(int cz=((z-4)>>4)-1;cz<=((z+20)>>4)+1;cz++){
                Chunk c=w.getChunkAt(cx,cz);if(c.addPluginChunkTicket(p))tickets.add(c);if(!c.isForceLoaded()){c.setForceLoaded(true);forced.add(c);}
            }
            for(int dx=-10;dx<=10;dx++)for(int dz=-4;dz<=20;dz++)put(w.getBlockAt(x+dx,y-1,z+dz),Material.SMOOTH_STONE);
            for(int dx=-2;dx<=2;dx++)for(int dy=0;dy<=3;dy++){Block b=w.getBlockAt(x+dx,y+dy,z+4);put(b,Material.BRICKS);wall.add(b);}
            Block box=w.getBlockAt(x+2,y,z+2);put(box,Material.CHEST);chest=(org.bukkit.block.Chest)box.getState();chest.getBlockInventory().setItem(0,ItemStack.of(Material.DIAMOND,3));
            target=w.spawn(new Location(w,x+.5,y,z+12.5),Cow.class,e->{e.setAI(false);e.setGravity(false);e.setSilent(true);e.setPersistent(false);e.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(500);e.setHealth(500);});health=target.getHealth();
            muzzle=new Location(w,x+.5,y+1,z+.5);later(this::awaitSimulation,5);
        }
        void awaitSimulation(){
            if(!target.isTicking()){if(waits++>60)throw new IllegalStateException("Simulation native inactive");later(this::awaitSimulation,5);return;}
            if(!p.rockets().trial(muzzle,new Vector(0,0,1)))throw new IllegalStateException("Roquette de test refusée");later(this::wall,25);
        }
        void intact(){
            if(blocks.stream().filter(b->b.getY()==y-1).anyMatch(b->b.getType()!=Material.SMOOTH_STONE)||chest.getBlock().getType()!=Material.CHEST
                ||chest.getBlockInventory().getItem(0)==null||chest.getBlockInventory().getItem(0).getAmount()!=3)throw new IllegalStateException("Terrain ou coffre altéré");
        }
        void wall(){
            Location impact=p.rockets().lastImpact();
            if(impact==null||impact.getZ()>z+4.1||target.getHealth()!=health||wall.stream().anyMatch(b->b.getType()!=Material.BRICKS))throw new IllegalStateException("Mur traversé ou modifié");
            intact();details.add("Roquette visible arrêtée par le mur ; cible hors explosion épargnée, mur, sol et diamants du coffre intacts.");
            wall.forEach(b->b.setType(Material.AIR,false));
            if(!p.rockets().trial(muzzle,new Vector(0,0,1)))throw new IllegalStateException("Second tir refusé");later(this::impact,30);
        }
        void impact(){
            double dealt=health-target.getHealth();if(dealt<40||p.rockets().count()!=0||p.rockets().lastImpact().getZ()<z+10)throw new IllegalStateException("Impact natif incorrect : "+dealt);
            intact();details.add("Vol réel jusqu’à la cible : "+String.format(Locale.ROOT,"%.1f",dealt)+" dégâts ; aucun projectile restant ni dégât aux constructions.");
            for(int i=0;i<8;i++)if(!p.rockets().trial(muzzle,new Vector(0,1,0)))throw new IllegalStateException("Budget refusé trop tôt");
            if(p.rockets().trial(muzzle,new Vector(0,1,0)))throw new IllegalStateException("Neuvième roquette simultanée autorisée");
            p.rockets().close();if(p.rockets().count()!=0)throw new IllegalStateException("Projectiles non nettoyés");details.add("Plafond de huit projectiles et nettoyage vérifiés.");finish(true,"Autotest des roquettes réussi.");
        }
        void finish(boolean ok,String message){
            if(ended)return;ended=true;
            try{p.rockets().close();if(target!=null)target.remove();if(chest!=null)chest.getBlockInventory().clear();blocks.forEach(b->b.setType(Material.AIR,false));tickets.forEach(c->c.removePluginChunkTicket(p));forced.forEach(c->c.setForceLoaded(false));}
            catch(Exception e){ok=false;message+=" Nettoyage : "+e.getMessage();}
            details.add(message);running=false;
            try{Path file=p.getDataFolder().toPath().resolve("rocket-selftest.json"),temp=file.resolveSibling("rocket-selftest.tmp");Files.writeString(temp,new com.google.gson.Gson().toJson(Map.of("id",id,"ok",ok,"at",System.currentTimeMillis(),"details",details)));Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception e){p.getLogger().severe(e.toString());}
        }
    }
}
