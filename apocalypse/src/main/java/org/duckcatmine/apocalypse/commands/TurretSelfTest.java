package org.duckcatmine.apocalypse.commands;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Zombie;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.crafting.PlacedBlocks;
import org.duckcatmine.apocalypse.items.ItemRegistry;

/** Real projectile integration check, scheduled across server ticks; refused while players are in the scenario. */
final class TurretSelfTest {
    private static boolean running;
    private TurretSelfTest() {}
    static String start(DuckCatApocalypse plugin) {
        World world = plugin.scenario().world();
        if (running || world == null || !plugin.scenario().isEnabled() || !world.getPlayers().isEmpty())
            throw new IllegalArgumentException("Ouvre Ground Zero et fais sortir ses joueurs avant le test de tourelle.");
        Trial trial = new Trial(plugin, world);
        running = true;
        trial.step(trial::setup);
        return trial.id;
    }
    private static final class Trial {
        final DuckCatApocalypse plugin;
        final World world;
        final String id = UUID.randomUUID().toString();
        final UUID owner = UUID.randomUUID();
        final List<Block> blocks = new ArrayList<>();
        final List<Entity> entities = new ArrayList<>();
        final List<String> details = new ArrayList<>();
        final List<Block> wall = new ArrayList<>();
        final List<org.bukkit.Chunk> tickets = new ArrayList<>();
        final List<org.bukkit.Chunk> forced = new ArrayList<>();
        Block base;
        Zombie near, far;
        Cow friend;
        double hp, farHp, friendHp, pausedHp;
        int before, waits;
        boolean ended;
        Trial(DuckCatApocalypse plugin, World world) { this.plugin = plugin; this.world = world; }
        void step(Runnable run) {
            if (ended) return;
            try {
                if (!world.getPlayers().isEmpty()) throw new IllegalStateException("Test interrompu : un joueur vient d'entrer.");
                run.run();
            } catch (Exception e) { finish(false, e.getMessage()); }
        }
        void later(Runnable run, long ticks) { plugin.getServer().getScheduler().runTaskLater(plugin, () -> step(run), ticks); }
        void setup() {
            int x = plugin.settings().refuge().centerX() + plugin.settings().refuge().radius() + 120;
            int z = plugin.settings().refuge().centerZ() + plugin.settings().refuge().radius() + 120;
            int y = Math.min(world.getMaxHeight() - 12, world.getHighestBlockYAt(x,z) + 180);
            // Verify the complete arena before changing a single block.
            for (int dx=-2;dx<=2;dx++) for (int dz=-1;dz<=12;dz++) for (int dy=-1;dy<=3;dy++)
                if (!world.getBlockAt(x+dx,y+dy,z+dz).getType().isAir()) throw new IllegalStateException("Zone de test occupée.");
            for (int cx=((x-2)>>4)-1;cx<=((x+2)>>4)+1;cx++) for (int cz=((z-1)>>4)-1;cz<=((z+12)>>4)+1;cz++) {
                var chunk=world.getChunkAt(cx,cz); chunk.addPluginChunkTicket(plugin); tickets.add(chunk);
                // Plugin tickets keep data resident; vanilla forced chunks also activate entity simulation with no players.
                if (!chunk.isForceLoaded()) { chunk.setForceLoaded(true); forced.add(chunk); }
            }
            for (int dx=-2;dx<=2;dx++) for (int dz=-1;dz<=12;dz++) put(world.getBlockAt(x+dx,y-1,z+dz),Material.SMOOTH_STONE);
            base=world.getBlockAt(x,y,z); put(base,Material.DISPENSER);
            for (int dx=-1;dx<=1;dx++) for (int dy=0;dy<=2;dy++) { Block b=world.getBlockAt(x+dx,y+dy,z+3); put(b,Material.BRICKS); wall.add(b); }
            near=infected(new Location(world,x+.5,y,z+5.5)); far=infected(new Location(world,x+.5,y,z+10.5));
            friend=world.spawn(new Location(world,x+2.2,y,z+5.5),Cow.class,e -> { e.setAI(false); e.setGravity(false); e.setSilent(true); e.setPersistent(false); }); entities.add(friend);
            hp=near.getHealth(); farHp=far.getHealth(); friendHp=friend.getHealth(); before=plugin.turrets().count();
            plugin.placed().put(base,new PlacedBlocks.Placed(ItemRegistry.AUTO_TURRET,owner,0));
            later(this::awaitSimulation,5L);
        }
        void awaitSimulation() {
            if (!near.isTicking() || !far.isTicking()) {
                if (waits++ >= 80) throw new IllegalStateException("Simulation des entités indisponible : forced="+base.getChunk().isForceLoaded()+" level="+base.getChunk().getLoadLevel()+" tickets="+tickets.size());
                later(this::awaitSimulation,5L); return;
            }
            details.add("Simulation native active : "+base.getChunk().getLoadLevel());
            later(this::blocked,35L);
        }
        Zombie infected(Location at) {
            // The director deliberately despawns registered infected far from players. These isolated fixtures
            // carry the same enemy identity without entering the director's population registry.
            Zombie z=world.spawn(at,Zombie.class,e -> {
                e.getPersistentDataContainer().set(plugin.keys().archetype,org.bukkit.persistence.PersistentDataType.STRING,"walker");
                e.setAI(false); e.setGravity(false); e.setSilent(true); e.setRemoveWhenFarAway(false); e.setPersistent(false);
            });
            entities.add(z); return z;
        }
        void put(Block block, Material material) { block.setType(material,false); blocks.add(block); }
        void blocked() {
            require(near.isValid(),"La cible de test a disparu avant le contrôle du mur.");
            require(near.getHealth()==hp,"La tourelle tire à travers le mur : " + hp + " -> " + near.getHealth());
            require(plugin.turrets().count()==before+1 && plugin.turrets().hasVisual(base),"Tourelle non indexée ou modèle absent.");
            details.add("Mur : aucun dégât derrière l'obstacle.");
            for (Block b:wall) b.setType(Material.AIR,false);
            later(this::fired,40L);
        }
        void fired() {
            require(near.isValid(),"Cible proche disparue avant le contrôle du tir.");
            require(near.getHealth()<hp,"Aucun tir automatique réel : hp="+near.getHealth()+" "+plugin.turrets().diagnostics(base));
            require(far.isValid() && far.getHealth()==farHp,"Un infecté à 10 blocs a été blessé.");
            require(friend.isValid() && friend.getHealth()==friendHp,"Animal non infecté blessé.");
            details.add("Vrais carreaux : infecté proche touché ; cible à 10 blocs et animal sain épargnés.");
            friend.teleport(base.getLocation().add(.5,0,2.5));
            later(() -> { pausedHp=near.getHealth(); later(this::intercepted,45L); },10L);
        }
        void intercepted() {
            require(friend.isValid() && friend.getHealth()==friendHp,"Carreau intercepté : animal sain blessé.");
            require(near.isValid() && near.getHealth()==pausedHp,"Le carreau traverse l'animal qui l'intercepte.");
            details.add("Un animal sain intercepte les carreaux sans subir de dégât.");
            plugin.placed().put(base,new PlacedBlocks.Placed(ItemRegistry.AUTO_TURRET,owner,1));
            friend.teleport(base.getLocation().add(2.2,0,5.5));
            later(() -> { pausedHp=near.getHealth(); later(this::paused,35L); },10L);
        }
        void paused() {
            require(near.isValid() && near.getHealth()==pausedHp,"La pause n'arrête pas les tirs.");
            plugin.turrets().untrack(base);
            require(plugin.turrets().count()==before,"Ancienne représentation non retirée.");
            plugin.placed().resumeLoaded();
            require(plugin.turrets().count()==before+1 && plugin.placed().get(base).data()==1,"Reprise du marqueur persistant incorrecte.");
            later(() -> {
                require(plugin.turrets().hasVisual(base),"Modèle non reconstruit après réindexation.");
                details.add("Pause conservée après réindexation ; retrait et reconstruction sans doublon.");
                finish(true,"Autotest de la mini-arbalète réussi.");
            },5L);
        }
        static void require(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
        void finish(boolean ok,String message) {
            if (ended) return;
            ended=true;
            if (base!=null) plugin.placed().remove(base);
            for (Entity e:entities) { e.remove(); plugin.mobs().forget(e.getUniqueId()); }
            for (Block b:blocks) b.setType(Material.AIR,false);
            for (var chunk:forced) chunk.setForceLoaded(false);
            for (var chunk:tickets) chunk.removePluginChunkTicket(plugin);
            running=false;
            details.add(message == null ? "Erreur sans détail" : message);
            try {
                var file=plugin.getDataFolder().toPath().resolve("turret-selftest.json");
                var temp=file.resolveSibling("turret-selftest.json.tmp");
                Files.writeString(temp,new Gson().toJson(Map.of("id",id,"ok",ok,"details",details,"at",System.currentTimeMillis())));
                Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
            } catch(Exception e) { plugin.getLogger().severe("Résultat du test de tourelle non écrit : "+e.getMessage()); }
            plugin.getLogger().info((ok?"OK":"ÉCHEC")+" mini-arbalète : "+details);
        }
    }
}
