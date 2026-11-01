package org.duckcatmine.apocalypse.commands;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.block.Block;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.mobs.*;

/** Native projectile, jump, optional-rig and chunk ownership integration check; never runs around players. */
final class HorrorSelfTest {
    private static boolean running;
    static String start(DuckCatApocalypse plugin){
        World world=plugin.scenario().world();
        if(running||world==null||!plugin.scenario().isEnabled()||!world.getPlayers().isEmpty())throw new IllegalArgumentException("Fais sortir les joueurs de Ground Zero avant le test du bestiaire.");
        Trial t=new Trial(plugin,world);running=true;t.step(t::setup);return t.id;
    }
    private static final class Trial{
        final DuckCatApocalypse p;final World w;final String id=UUID.randomUUID().toString();
        final List<Entity> entities=new ArrayList<>();final List<Block> blocks=new ArrayList<>(),wall=new ArrayList<>();
        final List<Chunk> forced=new ArrayList<>(),tickets=new ArrayList<>();final List<String> details=new ArrayList<>();
        final ApproachChunks leases;ApproachChunks.Lease first,second;
        Zombie spitter,jumper,crawler,approaching;Location approachStart;Cow target;int y,frames,waits;double peak;boolean ended;Chunk manual;
        Trial(DuckCatApocalypse p,World w){this.p=p;this.w=w;leases=p.director().approachLeases();}
        void step(Runnable r){if(ended)return;try{if(!w.getPlayers().isEmpty())throw new IllegalStateException("Un joueur est entré : test arrêté.");r.run();}catch(Exception e){finish(false,e.toString());}}
        void later(Runnable r,long ticks){p.getServer().getScheduler().runTaskLater(p,()->step(r),ticks);}
        void setup(){
            int x=400,z=400;y=Math.min(w.getMaxHeight()-12,260);
            for(int dx=-12;dx<=12;dx++)for(int dz=-6;dz<=16;dz++)for(int dy=-1;dy<=6;dy++)if(!w.getBlockAt(x+dx,y+dy,z+dz).getType().isAir())throw new IllegalStateException("Arène occupée");
            for(int cx=((x-12)>>4)-1;cx<=((x+12)>>4)+1;cx++)for(int cz=((z-6)>>4)-1;cz<=((z+16)>>4)+1;cz++){
                Chunk c=w.getChunkAt(cx,cz);if(c.addPluginChunkTicket(p))tickets.add(c);if(!c.isForceLoaded()){c.setForceLoaded(true);forced.add(c);}
            }
            for(int dx=-12;dx<=12;dx++)for(int dz=-6;dz<=16;dz++)put(w.getBlockAt(x+dx,y-1,z+dz),Material.SMOOTH_STONE);
            spitter=fixture("spitter",new Location(w,x+.5,y,z+.5));spitter.setAI(false);spitter.setGravity(false);
            jumper=fixture("jumper",new Location(w,x-7.5,y,z+.5));
            // NoAI disables native travel/physics in 26.3. Remove goals while keeping the real motion engine active.
            Bukkit.getMobGoals().removeAllGoals(jumper);
            target=w.spawn(new Location(w,x+.5,y,z+9.5),Cow.class,e->{e.setAI(false);e.setGravity(false);e.setSilent(true);e.setPersistent(false);});entities.add(target);
            for(int dx=-1;dx<=1;dx++)for(int dy=0;dy<=3;dy++){Block b=w.getBlockAt(x+dx,y+dy,z+4);put(b,Material.BRICKS);wall.add(b);}
            later(this::simulation,5);
        }
        Zombie fixture(String kind,Location at){
            Zombie mob=p.mobs().spawn(kind,Appearance.CITIZEN,at);if(mob==null)throw new IllegalStateException("Archétype absent : "+kind);
            p.mobs().forget(mob.getUniqueId());mob.setSilent(true);entities.add(mob);return mob;
        }
        void simulation(){
            if(!spitter.isTicking()||!jumper.isTicking()){if(waits++>60)throw new IllegalStateException("Simulation native inactive");later(this::simulation,5);return;}
            if(p.horror().spit(spitter,target,4))throw new IllegalStateException("Crachat à travers le mur");
            details.add("Acide : ligne de vue obstruée refusée.");wall.forEach(b->b.setType(Material.AIR,false));
            if(!p.horror().spit(spitter,target,4))throw new IllegalStateException("Crachat refusé sans mur");
            later(this::acid,30);
        }
        void acid(){
            if(p.horror().poolCount()<1||target.getHealth()!=target.getMaxHealth())throw new IllegalStateException("Projectile natif ou protection de la faune invalide");
            if(blocks.stream().filter(b->b.getY()==y-1).anyMatch(b->b.getType()!=Material.SMOOTH_STONE))throw new IllegalStateException("Acide a altéré le sol");
            details.add("Vrai crachat : impact puis flaque, animal sain et terrain intacts.");
            p.horror().jump(jumper,jumper.getLocation().add(0,0,10));peak=jumper.getY();frames=0;later(this::jump,1);
        }
        void jump(){
            peak=Math.max(peak,jumper.getY());if(++frames<40){later(this::jump,1);return;}
            if(peak-y<3.05||peak-y>5)throw new IllegalStateException("Saut natif trop bas/haut : "+(peak-y));
            details.add("Bondisseur : saut physique de "+String.format(Locale.ROOT,"%.2f",peak-y)+" blocs, sans téléportation.");
            crawler=fixture("crawler",new Location(w,405,y,400));crawler.setAI(false);crawler.setGravity(false);
            later(this::crawlerAndRigs,3);
        }
        void crawlerAndRigs(){
            if(crawler.getHeight()>=1)throw new IllegalStateException("Rampant trop haut : "+crawler.getHeight()+" scale="+crawler.getAttribute(org.bukkit.attribute.Attribute.SCALE).getValue());
            details.add("Rampant : collision native sous un bloc ("+String.format(Locale.ROOT,"%.2f",crawler.getHeight())+").");
            if(p.settings().archetype("breaker").siegePower()!=28)throw new IllegalStateException("Force de siège du briseur incorrecte pour la configuration de test");
            for(String kind:HorrorModels.KINDS){Zombie mob=fixture(kind,new Location(w,408,y,400));mob.setAI(false);mob.setGravity(false);p.horrorModels().verifyRig(mob,kind);}
            details.add("Sept rigs : six articulations, quatre orientations sans double rotation, apparence native restaurée et équipement conservé.");
            liveTuning();indoorEncounter();
            // A pre-existing manual force flag must survive two overlapping leases.
            manual=w.getChunkAt(50,50);if(!manual.isForceLoaded()){manual.setForceLoaded(true);forced.add(manual);}
            waits=0;
            if(!leases.acquire(w,50,50,l->{first=l;if(l==null){finish(false,"Première location indisponible");return;}later(this::secondLease,5);}))throw new IllegalStateException("Budget de chunks indisponible");
        }
        void liveTuning(){
            var before=p.settings().archetype("walker");Zombie mob=p.mobs().spawnWalker(new Location(w,409,y,400));entities.add(mob);mob.setAI(false);mob.setGravity(false);mob.setHealth(before.health()/2);
            try{
                if(!p.tuning().set("mob-walker-health","40","autotest").isEmpty())throw new IllegalStateException("Réglage de vie refusé");
                if(Math.abs(mob.getHealth()-20)>.01)throw new IllegalStateException("La vie restante a été réinitialisée");
                if(!p.tuning().set("mob-walker-damage","7","autotest").isEmpty()||mob.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).getBaseValue()!=7)throw new IllegalStateException("Force non appliquée au zombie vivant");
                if(!p.tuning().set("mob-walker-speed","0.3","autotest").isEmpty()||Math.abs(mob.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).getBaseValue()-.3)>.001)throw new IllegalStateException("Vitesse non appliquée au zombie vivant");
                details.add("Bestiaire à chaud : vie conservée à 50 %, force et vitesse appliquées à un vrai zombie déjà présent.");
            }finally{
                p.tuning().set("mob-walker-health",Double.toString(before.health()),"autotest");p.tuning().set("mob-walker-damage",Double.toString(before.damage()),"autotest");p.tuning().set("mob-walker-speed",Double.toString(before.speed()),"autotest");mob.remove();p.mobs().forget(mob.getUniqueId());
            }
        }
        void indoorEncounter(){
            org.duckcatmine.apocalypse.scenario.city.CityLayout.InteriorSite site=null;
            for(int z=395;z<=399&&site==null;z++){
                var candidate=new org.duckcatmine.apocalypse.scenario.city.CityLayout.InteriorSite("house_small",404,z,412,z+8,y-1);
                if(candidate.infested(w.getSeed()))site=candidate;
            }
            if(site==null)throw new IllegalStateException("Fixture intérieure non trouvée");
            var indoor=p.director().indoor();int x=408,z=site.minZ()+4;
            if(indoor.spot(w,x,z,site)!=null)throw new IllegalStateException("Intérieur accepté sans plafond");
            put(w.getBlockAt(x,y+3,z),Material.BRICKS);
            if(indoor.spot(w,x,z,site)==null||!indoor.allowed(w,site))throw new IllegalStateException("Pièce abandonnée admissible refusée");
            Chunk chunk=w.getChunkAt(x>>4,z>>4);NamespacedKey key=new NamespacedKey(p,"interior_clear_"+site.key());
            try{indoor.mark(w,site,chunk);if(indoor.allowed(w,site))throw new IllegalStateException("Réapparition dans une pièce sécurisée");}
            finally{chunk.getPersistentDataContainer().remove(key);}
            details.add("Pièce abandonnée : plafond et espace libres vérifiés, marque persistante interdisant les réapparitions.");
        }
        void secondLease(){
            if(leases.count()!=9)throw new IllegalStateException("Nombre de chunks incorrect : "+leases.count());
            if(w.getChunkAt(50,50).getLoadLevel()!=Chunk.LoadLevel.ENTITY_TICKING){
                if(waits++>60)throw new IllegalStateException("Chunks lointains non simulés : "+w.getChunkAt(50,50).getLoadLevel());
                later(this::secondLease,5);return;
            }
            if(!leases.acquire(w,50,50,l->{second=l;if(l==null){finish(false,"Deuxième location indisponible");return;}later(this::release,2);}))throw new IllegalStateException("Location partagée refusée");
        }
        void release(){
            first.close();if(leases.count()!=9||!w.isChunkForceLoaded(49,49))throw new IllegalStateException("Location partagée relâchée trop tôt");
            second.close();if(leases.count()!=0||w.isChunkForceLoaded(49,49)||!manual.isForceLoaded())throw new IllegalStateException("Force flags non restaurés");
            details.add("Approches : neuf chunks ENTITY_TICKING, références partagées et nettoyage vérifiés ; forçage manuel préservé.");
            // Actual native walking from outside player simulation; no teleport used by the director.
            Location spot=null;
            // Outside the sky arena's footprint: its floor correctly counts as a roof for outdoor spawns.
            for(int x=404;x<413&&spot==null;x++)for(int z=420;z<432&&spot==null;z++){
                Location a=p.director().outdoorSpot(w,x,z),b=p.director().outdoorSpot(w,x,z+14);
                if(a!=null&&b!=null&&Math.abs(a.getY()-b.getY())<=1)spot=a;
            }
            if(spot==null)throw new IllegalStateException("Pas de terrain pour l’approche native");
            approaching=fixture("walker",spot);Bukkit.getMobGoals().removeAllGoals(approaching);approachStart=spot.clone();
            if(!p.director().trialApproach(approaching,spot.clone().add(0,0,150)))throw new IllegalStateException("Approche native refusée");
            later(this::walked,70);
        }
        void walked(){
            double distance=approaching.getLocation().distance(approachStart);
            if(distance<1||!approaching.isTicking())throw new IllegalStateException("Approche immobile : "+distance+" ticking="+approaching.isTicking());
            approaching.remove();p.director().approachTick();
            if(p.director().approachChunks()!=0)throw new IllegalStateException("Chunks d’approche encore détenus");
            details.add("Déplacement natif hors simulation des joueurs : "+String.format(Locale.ROOT,"%.2f",distance)+" blocs parcourus ; location libérée sans téléportation.");
            finish(true,"Autotest du bestiaire réussi.");
        }
        void put(Block b,Material type){blocks.add(b);b.setType(type,false);}
        void finish(boolean ok,String message){
            if(ended)return;ended=true;
            try{if(first!=null)first.close();if(second!=null)second.close();p.horror().close();p.horrorModels().fallbackAll();entities.forEach(Entity::remove);p.director().approachTick();blocks.forEach(b->b.setType(Material.AIR,false));
                for(Chunk c:forced)c.setForceLoaded(false);
                for(Chunk c:tickets)c.removePluginChunkTicket(p);
            }catch(Exception e){ok=false;message+=" Nettoyage : "+e.getMessage();}
            details.add(message);running=false;
            try{Path file=p.getDataFolder().toPath().resolve("horror-selftest.json"),temp=file.resolveSibling("horror-selftest.tmp");Files.writeString(temp,new Gson().toJson(Map.of("id",id,"ok",ok,"at",System.currentTimeMillis(),"details",details)));Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception e){p.getLogger().severe(e.toString());}
        }
    }
}
