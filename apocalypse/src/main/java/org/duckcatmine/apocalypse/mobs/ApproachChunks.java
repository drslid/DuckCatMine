package org.duckcatmine.apocalypse.mobs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.duckcatmine.apocalypse.DuckCatApocalypse;

/** Bounded, journalled entity-ticking leases for distant approaches. Never unforces an existing manual ticket. */
public final class ApproachChunks implements AutoCloseable {
    public static final int BUDGET=36;
    public record Key(String world,int x,int z) {}
    public final class Lease implements AutoCloseable {
        private final List<Key> keys;
        private boolean closed;
        private Lease(List<Key> keys){this.keys=keys;}
        public boolean contains(int x,int z){return keys.stream().anyMatch(k->k.x()==x&&k.z()==z);}
        @Override public void close(){if(!closed){closed=true;release(keys);}}
    }
    private final DuckCatApocalypse plugin;
    private final Path journal;
    private final Map<Key,Integer> references=new HashMap<>();
    private final Set<Key> owned=new HashSet<>();
    private final Set<Key> tickets=new HashSet<>();
    private final Set<Key> pending=new HashSet<>();
    private boolean stopped,failed,loading;
    public ApproachChunks(DuckCatApocalypse plugin){this.plugin=plugin;journal=plugin.getDataFolder().toPath().resolve("approach-chunks.json");}
    public int count(){return references.size()+pending.size();}
    public boolean busy(){return loading||failed||stopped;}
    public void recover(){
        try {
            if(Files.exists(journal)){
                if(Files.size(journal)>32768)throw new java.io.IOException("journal trop grand");
                List<Key> keys=new Gson().fromJson(Files.readString(journal),new TypeToken<List<Key>>(){}.getType());
                if(keys==null||keys.size()>BUDGET)throw new java.io.IOException("journal invalide");
                for(Key k:keys){
                    World w=k==null?null:Bukkit.getWorld(k.world());
                    if(w==null||Math.abs((long)k.x())>1_875_000||Math.abs((long)k.z())>1_875_000)throw new java.io.IOException("monde ou coordonnées invalides");
                }
                for(Key k:keys)Bukkit.getWorld(k.world()).setChunkForceLoaded(k.x(),k.z(),false);
            }
            persist();
        }catch(Exception e){failed=true;plugin.getLogger().severe("Approches suspendues : journal de chunks à vérifier : "+e.getMessage());}
    }
    public boolean acquire(World world,int x,int z,Consumer<Lease> done){
        if(busy())return false;
        List<Key> keys=new ArrayList<>();for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)keys.add(new Key(world.getName(),x+dx,z+dz));
        long missing=keys.stream().filter(k->!references.containsKey(k)).count();
        if(references.size()+missing>BUDGET)return false;
        loading=true;pending.addAll(keys.stream().filter(k->!references.containsKey(k)).toList());
        List<CompletableFuture<Chunk>> loads=keys.stream().map(k->world.getChunkAtAsync(k.x(),k.z(),true)).toList();
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).orTimeout(20,java.util.concurrent.TimeUnit.SECONDS).whenComplete((v,error)->{
            if(!plugin.isEnabled()||stopped)return;
            Bukkit.getScheduler().runTask(plugin,()->{
                loading=false;pending.clear();
                if(error!=null||loads.stream().anyMatch(f->f.getNow(null)==null)){done.accept(null);return;}
                Set<Key> added=new HashSet<>();
                for(Key k:keys)if(!references.containsKey(k)&&!world.isChunkForceLoaded(k.x(),k.z()))added.add(k);
                // Write ownership BEFORE forcing; after a crash recovery may safely release only this set.
                owned.addAll(added);
                try{persist();}catch(Exception e){owned.removeAll(added);failed=true;done.accept(null);plugin.getLogger().severe("Journal des approches non écrit : "+e.getMessage());return;}
                for(Key k:keys){
                    if(added.contains(k))world.setChunkForceLoaded(k.x(),k.z(),true);
                    if(world.addPluginChunkTicket(k.x(),k.z(),plugin))tickets.add(k);
                    references.merge(k,1,Integer::sum);
                }
                done.accept(new Lease(keys));
            });
        });return true;
    }
    private void release(List<Key> keys){
        for(Key k:keys){
            Integer n=references.get(k);if(n==null)continue;if(n>1){references.put(k,n-1);continue;}
            references.remove(k);World w=Bukkit.getWorld(k.world());
            if(w!=null){if(tickets.remove(k))w.removePluginChunkTicket(k.x(),k.z(),plugin);if(owned.contains(k))w.setChunkForceLoaded(k.x(),k.z(),false);}
            owned.remove(k);
        }
        try{persist();}catch(Exception e){failed=true;plugin.getLogger().severe("Journal des approches non actualisé : "+e.getMessage());}
    }
    private void persist() throws java.io.IOException {
        Path temp=journal.resolveSibling(journal.getFileName()+".tmp");
        Files.writeString(temp,new Gson().toJson(owned));
        try(var channel=java.nio.channels.FileChannel.open(temp,java.nio.file.StandardOpenOption.WRITE)){channel.force(true);}
        Files.move(temp,journal,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    @Override public void close(){stopped=true;while(!references.isEmpty())release(new ArrayList<>(references.keySet()));pending.clear();}
}
