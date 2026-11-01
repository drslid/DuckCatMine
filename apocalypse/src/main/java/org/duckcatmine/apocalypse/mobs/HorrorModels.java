package org.duckcatmine.apocalypse.mobs;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.kyori.adventure.key.Key;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.duckcatmine.apocalypse.DuckCatApocalypse;

/** Optional original item-display rigs. Native targetable mob stays visible whenever a client lacks the pack. */
public final class HorrorModels implements Listener,AutoCloseable {
    public static final UUID PACK_ID=UUID.nameUUIDFromBytes("duckcatmine-ground-zero-horror-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    public static final int MAX_MODELS=16;
    public static final Set<String> KINDS=Set.of("spitter","jumper","crawler","stalker","bloated","breaker");
    private static final List<String> PARTS=List.of("body","left","right");
    private record Rig(Mob mob,String kind,List<ItemDisplay> parts){}
    private final DuckCatApocalypse plugin;
    private final Map<UUID,Rig> rigs=new HashMap<>();
    private final Set<UUID> ready=new HashSet<>(),requested=new HashSet<>();
    private String url,sha1,problem;
    private long frames;
    public HorrorModels(DuckCatApocalypse plugin){
        this.plugin=plugin;Path file=plugin.getDataFolder().toPath().resolve("resource-pack.json");
        if(!Files.isRegularFile(file))return;
        try{
            if(Files.size(file)>8192)throw new IllegalArgumentException("configuration trop grande");
            var value=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            String candidate=value.get("url").getAsString(),hash=value.get("sha1").getAsString();
            var uri=java.net.URI.create(candidate);
            if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null
                    ||!hash.matches("[a-f0-9]{40}")||!uri.getPath().matches("/packs/duckcatmine-horror-[a-f0-9]{16}\\.zip"))
                throw new IllegalArgumentException("URL HTTPS et SHA-1 du pack attendus");
            url=candidate;sha1=hash;
        }catch(Exception e){problem=e.getMessage();plugin.getLogger().warning("Pack visuel indisponible : "+problem);}
    }
    public boolean available(){return url!=null;}
    public int modelCount(){return rigs.size();}
    public int readyCount(){return ready.size();}
    public String problem(){return problem;}
    public String hash(){return sha1;}
    public void offer(Player player){
        if(!available()||!plugin.scenario().isScenarioWorld(player.getWorld())||!requested.add(player.getUniqueId()))return;
        player.addResourcePack(PACK_ID,url,HexFormat.of().parseHex(sha1),"Ground Zero · bestiaire et sons originaux (~100 Ko). Facultatif, aucun mod requis.",false);
    }
    @EventHandler(priority=EventPriority.LOWEST)public void onJoin(PlayerJoinEvent event){
        fallbackAll();ready.remove(event.getPlayer().getUniqueId());requested.remove(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(event.getPlayer().isOnline())offer(event.getPlayer());},20);
    }
    @EventHandler(priority=EventPriority.LOWEST)public void onWorld(PlayerChangedWorldEvent event){fallbackAll();offer(event.getPlayer());}
    @EventHandler public void onQuit(PlayerQuitEvent event){ready.remove(event.getPlayer().getUniqueId());requested.remove(event.getPlayer().getUniqueId());}
    @EventHandler public void onPack(PlayerResourcePackStatusEvent event){
        if(!PACK_ID.equals(event.getID()))return;
        if(event.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)ready.add(event.getPlayer().getUniqueId());
        else {ready.remove(event.getPlayer().getUniqueId());fallbackAll();}
    }
    public void tick(){
        frames++;
        World world=plugin.scenario().world();
        if(!available()||world==null||!plugin.scenario().isEnabled()){fallbackAll();return;}
        List<Player> viewers=world.getPlayers();
        // Consistent shared appearance. An unready client always sees a native creature with the same hitbox/stats.
        if(viewers.isEmpty()||viewers.stream().anyMatch(p->!ready.contains(p.getUniqueId()))){fallbackAll();return;}
        for(var e:new ArrayList<>(rigs.entrySet())){
            Rig rig=e.getValue();if(!rig.mob().isValid()||rig.mob().isDead()||viewers.stream().noneMatch(p->p.getLocation().distanceSquared(rig.mob().getLocation())<80*80)){remove(e.getKey());continue;}
            animate(rig);
        }
        for(UUID id:plugin.mobs().trackedIds()){
            if(rigs.size()>=MAX_MODELS)break;
            if(rigs.containsKey(id)||!(Bukkit.getEntity(id) instanceof Mob mob)||!mob.isValid()||mob.isDead())continue;
            String kind=plugin.mobs().archetypeOf(mob);
            if(!KINDS.contains(kind)||!world.equals(mob.getWorld())||viewers.stream().noneMatch(p->p.getLocation().distanceSquared(mob.getLocation())<64*64))continue;
            Rig rig=create(mob,kind);if(rig==null)continue;rigs.put(id,rig);animate(rig);
        }
        if(frames%40==0)for(Rig rig:rigs.values()){
            if("stalker".equals(rig.kind()))sound(rig.mob().getLocation(),"breath",.5f);
            if("breaker".equals(rig.kind())&&frames%80==0)sound(rig.mob().getLocation(),"growl",.7f);
        }
    }
    public boolean active(Entity mob){return rigs.containsKey(mob.getUniqueId());}
    /** Offline integration check: attach real rigs, then restore the native creature and its equipment. */
    public void verifyRig(Mob mob,String kind){
        var equipment=mob.getEquipment();Map<EquipmentSlot,ItemStack> before=new EnumMap<>(EquipmentSlot.class);
        for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))before.put(slot,equipment.getItem(slot).clone());
        double height=mob.getHeight();Rig rig=create(mob,kind);
        if(rig==null)throw new IllegalStateException("Rig non créé : "+kind);
        rigs.put(mob.getUniqueId(),rig);
        try{
            animate(rig);
            if(!mob.isInvisible()||mob.getHeight()!=height||rig.parts().size()!=3)throw new IllegalStateException("Corps du rig altéré");
            for(ItemDisplay part:rig.parts())if(!part.isValid()||part.getVehicle()!=mob||part.isVisibleByDefault()||!part.getItemStack().hasData(DataComponentTypes.ITEM_MODEL))throw new IllegalStateException("Pièce non attachée");
        }finally{remove(mob.getUniqueId());}
        if(mob.isInvisible()||!mob.getPassengers().isEmpty())throw new IllegalStateException("Repli natif incomplet");
        before.forEach((slot,item)->{if(!item.equals(equipment.getItem(slot)))throw new IllegalStateException("Équipement du rig modifié");});
    }
    private Rig create(Mob mob,String kind){
        List<ItemDisplay> displays=new ArrayList<>();
        try{
            for(String part:PARTS){
                ItemStack model=ItemStack.of(Material.PAPER);model.setData(DataComponentTypes.ITEM_MODEL,Key.key("duckcatmine:"+kind+"_"+part));
                ItemDisplay display=mob.getWorld().spawn(mob.getLocation(),ItemDisplay.class,e->{
                    e.setVisibleByDefault(false);e.setPersistent(false);e.setInvulnerable(true);e.setGravity(false);
                    e.setItemStack(model);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);e.setInterpolationDuration(5);e.setInterpolationDelay(0);
                    e.setViewRange(1f);e.setDisplayWidth(3);e.setDisplayHeight(4);e.setShadowRadius(0);
                });displays.add(display);
                if(!mob.addPassenger(display))throw new IllegalStateException("modèle non attaché");
                for(Player p:mob.getWorld().getPlayers())if(ready.contains(p.getUniqueId()))p.showEntity(plugin,display);
            }
            mob.setInvisible(true);hideEquipment(mob,true);return new Rig(mob,kind,displays);
        }catch(RuntimeException e){displays.forEach(Entity::remove);mob.setInvisible(false);hideEquipment(mob,false);return null;}
    }
    private void animate(Rig rig){
        Mob mob=rig.mob();double modelHeight=switch(rig.kind()){case "crawler"->.75;case "jumper"->23/16.;case "stalker","breaker"->2.;case "bloated"->28/16.;default->29/16.;};
        float scale=(float)(mob.getHeight()/modelHeight);
        boolean walking=mob.getVelocity().clone().setY(0).lengthSquared()>.002;
        for(int n=0;n<rig.parts().size();n++){
            ItemDisplay display=rig.parts().get(n);if(!display.isValid()){remove(mob.getUniqueId());return;}
            float seat=(float)(display.getY()-mob.getY());float swing=walking&&n>0?(float)(Math.sin(frames*1.3)*(n==1?.16:-.16)):0;
            Quaternionf rotation=new Quaternionf().rotateY((float)Math.toRadians(-mob.getBodyYaw())).rotateX(swing);
            display.setInterpolationDelay(0);display.setTransformation(new Transformation(new Vector3f((float)(mob.getX()-display.getX()),.5f*scale-seat,(float)(mob.getZ()-display.getZ())),rotation,new Vector3f(scale),new Quaternionf()));
        }
        if(frames%20==0)hideEquipment(mob,true);
    }
    private void hideEquipment(Mob mob,boolean hide){
        var eq=mob.getEquipment();if(eq==null)return;
        Map<EquipmentSlot,ItemStack> equipment=new EnumMap<>(EquipmentSlot.class);
        for(EquipmentSlot slot:List.of(EquipmentSlot.HAND,EquipmentSlot.OFF_HAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))equipment.put(slot,hide?ItemStack.empty():eq.getItem(slot));
        for(Player p:mob.getWorld().getPlayers())p.sendEquipmentChange(mob,equipment);
    }
    public void sound(Location at,String name,float volume){
        if(!available())return;
        for(Player p:at.getWorld().getPlayers())if(ready.contains(p.getUniqueId())&&p.getLocation().distanceSquared(at)<32*32)
            p.playSound(at,"duckcatmine:horror."+name,SoundCategory.HOSTILE,volume,1f);
    }
    private void remove(UUID id){Rig rig=rigs.remove(id);if(rig==null)return;rig.parts().forEach(Entity::remove);if(rig.mob().isValid()){rig.mob().setInvisible(false);hideEquipment(rig.mob(),false);}}
    public void fallbackAll(){for(UUID id:new ArrayList<>(rigs.keySet()))remove(id);}
    @Override public void close(){fallbackAll();ready.clear();requested.clear();}
}
