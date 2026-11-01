package org.duckcatmine.apocalypse.mobs;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.scenario.GroundZeroGenerator;
import org.duckcatmine.apocalypse.scenario.city.CityLayout;
import org.duckcatmine.apocalypse.scenario.city.Sector;

/**
 * Keeps a bounded population around each active survivor: local cap and archetype mix by sector, a little more at
 * night, outdoors or in selected abandoned interiors, out of reach, never in the refuge or unloaded chunks. Hordes and tension cycles come with the threat director.
 */
public final class SpawnDirector {
    private static final int LOCAL_RADIUS = 240;
    private static final int ATTEMPTS = 8;

    private final DuckCatApocalypse plugin;
    private final Random random = new Random();
    private final ApproachChunks chunks;
    private final IndoorSpawns indoor;
    private static final class Group {
        final java.util.Set<UUID> members=new java.util.HashSet<>();
        final Location goal;
        final long until;
        ApproachChunks.Lease lease;
        int cx,cz,cursor;boolean moving;
        CityLayout.InteriorSite interior;
        Group(Location goal,long until,ApproachChunks.Lease lease,int x,int z){this.goal=goal;this.until=until;this.lease=lease;cx=x;cz=z;}
    }
    private final java.util.List<Group> groups=new java.util.ArrayList<>();
    private int reserved;
    private boolean closed;

    public SpawnDirector(DuckCatApocalypse plugin) {
        this.plugin = plugin;
        chunks=new ApproachChunks(plugin);
        indoor=new IndoorSpawns(plugin);
    }
    public void restore(){chunks.recover();}
    public void close(){closed=true;for(Group g:groups)g.lease.close();groups.clear();chunks.close();}
    public int approachChunks(){return chunks.count();}
    public IndoorSpawns indoor(){return indoor;}
    public ApproachChunks approachLeases(){return chunks;}
    /** Offline-only integration fixture, using the same leases, navigation and cleanup as real distant spawns. */
    public boolean trialApproach(Mob mob,Location goal){
        if(!mob.getWorld().getPlayers().isEmpty())throw new IllegalStateException("Test réservé au monde vide");
        var at=mob.getLocation();
        return chunks.acquire(mob.getWorld(),at.getBlockX()>>4,at.getBlockZ()>>4,lease->{
            if(lease==null)return;Group group=new Group(goal.clone(),System.currentTimeMillis()+30_000,lease,at.getBlockX()>>4,at.getBlockZ()>>4);
            group.members.add(mob.getUniqueId());groups.add(group);
        });
    }
    public boolean approaching(Entity e){return groups.stream().anyMatch(g->g.members.contains(e.getUniqueId()));}
    public int approachingCount(){return groups.stream().mapToInt(g->g.members.size()).sum();}

    private CityLayout layout(World world) {
        return world.getGenerator() instanceof GroundZeroGenerator ? CityLayout.of(world.getSeed()) : null;
    }

    public Sector sectorAt(Location location) {
        CityLayout layout = layout(location.getWorld());
        if (plugin.scenario().inRefuge(location)) return Sector.REFUGE;
        return layout == null ? Sector.RESIDENTIAL : layout.sectorAt(location.getBlockX(), location.getBlockZ());
    }

    public void tick() {
        ApocalypseConfig settings = plugin.settings();
        ApocalypseConfig.Spawning spawning = settings.spawning();
        World world = plugin.scenario().world();
        if(world==null)return;
        despawnFar(world, Math.min(SpawnDistance.MAXIMUM,spawning.despawnDistance()));
        if (!plugin.scenario().isEnabled() || !spawning.enabled()) return;
        int total = plugin.mobs().count()+reserved;
        boolean night = !world.isDayTime();
        for (Player player : world.getPlayers()) {
            if (total >= spawning.globalCap()) return;
            if (player.isDead() || !plugin.sessions().isSurvivor(player) || plugin.scenario().inRefuge(player.getLocation())) continue;
            Sector sector = sectorAt(player.getLocation());
            ApocalypseConfig.SectorRule rule = settings.sector(sector.id());
            if (rule == null || rule.mix().isEmpty()) continue;
            int cap = plugin.threat().localCap(localCap(rule, spawning, sector) + (night ? spawning.nightBonus() : 0));
            int near = plugin.mobs().countNear(player.getLocation(), LOCAL_RADIUS)+reserved;
            int count=Math.min(spawning.perTick(),Math.min(cap-near,spawning.globalCap()-total));
            if(count>0&&enqueue(player,count,false)>0)return; // one bounded async preparation at a time
        }
    }

    /** Sector cap scaled by the density setting (at least one where a sector has any); the starting district is never scaled up. */
    static int localCap(ApocalypseConfig.SectorRule rule, ApocalypseConfig.Spawning spawning, Sector sector) {
        if (rule.localCap() == 0) return 0;
        int scaled = Math.max(1, (int) Math.round(rule.localCap() * spawning.density()));
        return sector == Sector.STARTER ? Math.min(rule.localCap(), scaled) : scaled;
    }

    /**
     * Infected animals among new spawns: stray dogs in town, livestock and wolves in the countryside and outskirts. Never
     * in the gentle starting sector around the refuge nor inside buildings.
     */
    private boolean animalSpawn(Sector sector) {
        ApocalypseConfig.Fauna fauna = plugin.settings().play().fauna();
        if (!fauna.enabled() || sector == Sector.REFUGE || sector == Sector.STARTER) return false;
        double share = sector == Sector.COUNTRYSIDE || sector == Sector.OUTSKIRTS ? fauna.spawnShare() * 2 : fauna.spawnShare();
        return random.nextDouble() < Math.min(0.9, share);
    }

    private boolean wolfFor(Sector sector) {
        return switch (sector) {
            case COUNTRYSIDE -> random.nextDouble() < 0.35;
            case OUTSKIRTS -> random.nextDouble() < 0.6;
            default -> true;
        };
    }

    /** Small capped reinforcement (howler call): walkers only, inside the global budget, never next to the survivor. */
    public void reinforce(Player player, int count) {
        enqueue(player,count,true);
    }

    /**
     * A horde: a group gathered out of sight, further than usual, that walks to where the survivor is now. Inside the
     * global budget; the mix follows the sector where the group appears. Returns how many infected came.
     */
    public int horde(Player player, int size) {
        return enqueue(player,size,false);
    }

    /** Returns reserved capacity, not an invented synchronous spawn count. Each individual is rechecked after loading. */
    private int enqueue(Player player,int requested,boolean walkersOnly){
        var rules=plugin.settings().spawning();World world=player.getWorld();
        if(closed||chunks.busy()||groups.size()>=3||!plugin.scenario().isEnabled()||!rules.enabled()
                ||!plugin.sessions().isSurvivor(player)||!plugin.scenario().isScenarioWorld(world))return 0;
        int count=Math.min(20,Math.min(requested,rules.globalCap()-plugin.mobs().count()-reserved));if(count<=0)return 0;
        Location goal=player.getLocation().clone();
        int min=Math.max(SpawnDistance.MINIMUM,rules.minDistance()),max=Math.min(199,Math.max(min+1,rules.maxDistance()));
        int[] candidate=null;
        CityLayout.InteriorSite interior=null;
        boolean preferIndoor=!walkersOnly&&groups.stream().noneMatch(g->g.interior!=null)&&random.nextDouble()<rules.indoorChance();
        for(int attempt=0;attempt<16;attempt++){
            double angle=random.nextDouble()*Math.PI*2,distance=min+random.nextDouble()*(max-min);
            int x=(int)Math.floor(goal.getX()+Math.cos(angle)*distance),z=(int)Math.floor(goal.getZ()+Math.sin(angle)*distance);
            Location at=new Location(world,x+.5,goal.getY(),z+.5);
            CityLayout layout=layout(world);
            boolean inside=preferIndoor&&attempt<12;
            var site=inside&&layout!=null?layout.interiorAt(x,z):null;
            if(!world.getWorldBorder().isInside(at)||nearAnySurvivor(world,at,min))continue;
            if(inside?(site==null||!site.infested(world.getSeed())):(layout!=null&&!layout.openGround(x,z)))continue;
            candidate=new int[]{x,z};interior=site;break;
        }
        if(candidate==null)return 0;
        int x=candidate[0],z=candidate[1];var site=interior;UUID requester=player.getUniqueId();reserved+=count;
        boolean accepted=chunks.acquire(world,x>>4,z>>4,lease->{
            reserved-=count;if(lease==null)return;
            Player current=plugin.getServer().getPlayer(requester);
            if(closed||!plugin.scenario().isEnabled()||!plugin.settings().spawning().enabled()||current==null
                    ||!current.getWorld().equals(world)||!plugin.sessions().isSurvivor(current)){lease.close();return;}
            Group group=new Group(goal,System.currentTimeMillis()+120_000,lease,x>>4,z>>4);
            group.interior=site;
            if(site!=null&&!indoor.allowed(world,site)){lease.close();return;}
            var sector=sectorAt(new Location(world,x,goal.getY(),z));var rule=plugin.settings().sector(sector.id());
            Map<String,Double> mix=rule==null?Map.of(ApocalypseConfig.WALKER,1.0):rule.mix();
            if(site!=null){var small=new java.util.LinkedHashMap<>(mix);small.keySet().removeIf(id->!java.util.Set.of("walker","runner","crawler","stalker","spitter").contains(id));mix=small;}
            for(int n=0;n<count&&plugin.mobs().count()<plugin.settings().spawning().globalCap();n++){
                int sx=x+random.nextInt(7)-3,sz=z+random.nextInt(7)-3;
                Location spot=site==null?outdoorSpot(world,sx,sz):indoor.spot(world,sx,sz,site);
                if(spot==null||nearAnySurvivor(world,spot,Math.max(SpawnDistance.MINIMUM,plugin.settings().spawning().minDistance())))continue;
                if(world.getPlayers().stream().noneMatch(p->!SpawnDistance.outside(spot.getX(),spot.getZ(),p.getX(),p.getZ(),SpawnDistance.MAXIMUM)))continue;
                String id=walkersOnly?ApocalypseConfig.WALKER:pickArchetype(mix);if(id==null)continue;
                if(walkersOnly&&!allowed(id,plugin.mobs().countByArchetype()))continue;
                org.bukkit.entity.LivingEntity entity=null;
                if(site==null&&!walkersOnly&&animalSpawn(sector))entity=plugin.fauna().spawnInfected(spot,wolfFor(sector));
                if(entity==null)entity=plugin.mobs().spawn(id,pickAppearance(plugin.settings().archetype(id),sector),spot);
                if(entity!=null){entity.setRemoveWhenFarAway(false);group.members.add(entity.getUniqueId());}
            }
            if(group.members.isEmpty())lease.close();else {groups.add(group);if(site!=null)indoor.prepared(site);}
        });
        if(!accepted){reserved-=count;return 0;}return count;
    }

    /** Every second. Distant mobs walk towards the original encounter, never a hidden player's live position. */
    public void approachTick(){
        for(Group g:new java.util.ArrayList<>(groups)){
            boolean expired=closed||!plugin.scenario().isEnabled()||System.currentTimeMillis()>g.until;
            g.members.removeIf(id->{
                Entity e=plugin.getServer().getEntity(id);
                if(!(e instanceof Mob mob)||!mob.isValid()||mob.isDead())return true;
                if(expired){mob.remove();plugin.mobs().forget(id);return true;}
                for(Player p:mob.getWorld().getPlayers())if(!p.isDead()&&plugin.sessions().isSurvivor(p)
                        &&!SpawnDistance.outside(mob.getX(),mob.getZ(),p.getX(),p.getZ(),80)){
                    if(g.interior==null)plugin.mobs().send(mob,g.goal,30_000);
                    else indoor.mark(mob.getWorld(),g.interior,mob.getChunk());
                    return true;
                }
                return false;
            });
            if(g.members.isEmpty()){g.lease.close();groups.remove(g);continue;}
            if(g.interior!=null)continue; // Occupants wait inside; they never track a hidden survivor outside.
            Mob leader=(Mob)plugin.getServer().getEntity(g.members.iterator().next());
            Location at=leader.getLocation();int cx=at.getBlockX()>>4,cz=at.getBlockZ()>>4;
            if((cx!=g.cx||cz!=g.cz)&&!g.moving&&!chunks.busy()){
                g.moving=true;
                if(!chunks.acquire(leader.getWorld(),cx,cz,next->{g.moving=false;if(next==null)return;if(!groups.contains(g)){next.close();return;}g.lease.close();g.lease=next;g.cx=cx;g.cz=cz;}))g.moving=false;
            }
            List<UUID> members=new java.util.ArrayList<>(g.members);
            for(int n=0;n<Math.min(6,members.size());n++){
                UUID id=members.get(Math.floorMod(g.cursor++,members.size()));
                if(!(plugin.getServer().getEntity(id) instanceof Mob mob))continue;
                Location here=mob.getLocation();double dx=g.goal.getX()-here.getX(),dz=g.goal.getZ()-here.getZ(),d=Math.hypot(dx,dz);
                if(d<4)continue;
                double base=Math.atan2(dz,dx);
                for(int attempt=0;attempt<3;attempt++){
                    double angle=base+(attempt==0?0:(attempt==1?.7:-.7));
                    int wx=(int)Math.floor(here.getX()+Math.cos(angle)*Math.min(14,d)),wz=(int)Math.floor(here.getZ()+Math.sin(angle)*Math.min(14,d));
                    if(!g.lease.contains(wx>>4,wz>>4))continue;
                    Location waypoint=outdoorSpot(mob.getWorld(),wx,wz);
                    if(waypoint!=null&&mob.getPathfinder().moveTo(waypoint,1.0))break;
                }
            }
        }
    }

    private Location nearby(World world, Location center) {
        for (int attempt = 0; attempt < 4; attempt++) {
            int x = center.getBlockX() + random.nextInt(7) - 3, z = center.getBlockZ() + random.nextInt(7) - 3;
            Location spot = outdoorSpot(world,x,z);
            if (spot != null) return spot;
        }
        return null;
    }

    /** Common gate for ordinary spawns, animals, howler reinforcements and every member of a horde. */
    public Location outdoorSpot(World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4,z >> 4) || plugin.scenario().refugeArea().near(x,z,8)) return null;
        CityLayout layout = layout(world);
        if (layout != null && !layout.openGround(x,z)) return null;
        int top = world.getHighestBlockYAt(x,z,HeightMap.WORLD_SURFACE);
        // Use the planned terrain, not the roof height, also for player-built homes in the countryside.
        int ground = layout == null ? top : layout.floorAt(x,z,layout.groundY(x,z));
        if (top > ground + 1) return null;
        Location spot = new Location(world,x+.5,ground+1,z+.5);
        if (!world.getWorldBorder().isInside(spot)) return null;
        Block feet = spot.getBlock();
        if (!feet.isPassable() || feet.isLiquid() || !feet.getRelative(BlockFace.UP).isPassable()
                || !feet.getRelative(BlockFace.DOWN).isSolid()) return null;
        return spot;
    }

    private String pickArchetype(Map<String, Double> mix) {
        Map<String, Integer> alive = plugin.mobs().countByArchetype();
        double boost = plugin.threat().specialBoost();
        double total = 0;
        for (Map.Entry<String, Double> entry : mix.entrySet()) if (allowed(entry.getKey(), alive)) total += weight(entry, boost);
        if (total <= 0) return null;
        double roll = random.nextDouble() * total;
        for (Map.Entry<String, Double> entry : mix.entrySet()) {
            if (!allowed(entry.getKey(), alive)) continue;
            roll -= weight(entry, boost);
            if (roll < 0) return entry.getKey();
        }
        return null;
    }

    /** Special infected become more common as the threat rises. */
    private double weight(Map.Entry<String, Double> entry, double boost) {
        return (ApocalypseConfig.WALKER.equals(entry.getKey()) ? entry.getValue() : entry.getValue() * boost)*plugin.settings().archetype(entry.getKey()).ability("spawn-weight",1);
    }

    private boolean allowed(String archetypeId, Map<String, Integer> alive) {
        ApocalypseConfig.Archetype archetype = plugin.settings().archetype(archetypeId);
        return archetype != null && archetype.ability("spawn-weight",1)>0 && archetype.minThreat() <= plugin.threat().level()
                && (archetype.maxAlive() == 0 || alive.getOrDefault(archetypeId, 0) < archetype.maxAlive());
    }

    private Appearance pickAppearance(ApocalypseConfig.Archetype archetype, Sector sector) {
        List<String> ids = archetype.appearances();
        double total = 0;
        for (String id : ids) total += Wardrobe.affinity(Appearance.byId(id), sector);
        double roll = random.nextDouble() * total;
        for (String id : ids) {
            Appearance appearance = Appearance.byId(id);
            roll -= Wardrobe.affinity(appearance, sector);
            if (roll < 0) return appearance;
        }
        return Appearance.byId(ids.getFirst());
    }

    private Location findSpot(World world, Player player, double minDistance, double maxDistance) {
        Location origin = player.getLocation();
        double facing = Math.toRadians(origin.getYaw() + 90);
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = attempt < ATTEMPTS / 2 ? facing + Math.PI + (random.nextDouble() - 0.5) * Math.PI / 1.5
                    : random.nextDouble() * Math.PI * 2;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            int x = (int) Math.floor(origin.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(origin.getZ() + Math.sin(angle) * distance);
            Location spot = outdoorSpot(world,x,z);
            if (spot == null) continue;
            if (nearAnySurvivor(world, spot, minDistance)) continue;
            return spot;
        }
        return null;
    }

    private boolean nearAnySurvivor(World world, Location spot, double distance) {
        double squared = distance * distance;
        for (Player player : world.getPlayers()) {
            if (!SpawnDistance.outside(spot.getX(),spot.getZ(),player.getX(),player.getZ(),distance)) return true;
        }
        return false;
    }

    private void despawnFar(World world, int distance) {
        double squared = (double) distance * distance;
        for (UUID id : plugin.mobs().trackedIds()) {
            Entity entity = plugin.getServer().getEntity(id);
            if (!(entity instanceof Mob mob) || !(mob instanceof Zombie || plugin.fauna().spawnedByDirector(mob))
                    || !mob.getWorld().equals(world)) continue;
            boolean near = false;
            for (Player player : world.getPlayers()) {
                double dx=mob.getX()-player.getX(),dz=mob.getZ()-player.getZ();
                if (dx*dx+dz*dz<=squared) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                mob.remove();
                plugin.mobs().forget(id);
            }
        }
    }
}
