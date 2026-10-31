package org.duckcatmine.apocalypse.commands;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.inventory.ItemStack;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.fauna.FaunaService;
import org.duckcatmine.apocalypse.items.ItemRegistry;
import org.duckcatmine.apocalypse.mobs.Appearance;
import org.duckcatmine.apocalypse.persistence.Store;
import org.duckcatmine.apocalypse.scenario.GroundZeroGenerator;
import org.duckcatmine.apocalypse.scenario.city.CityLayout;
import org.duckcatmine.apocalypse.siege.SiegeManager;

/** Exercises the server APIs the scenario relies on, on the real Paper build, without any player. */
final class SelfTest {
    private SelfTest() {
    }

    static List<String> run(DuckCatApocalypse plugin) {
        List<String> lines = new ArrayList<>();
        check(lines, "objets du scénario", () -> {
            for (String id : plugin.items().ids()) {
                if (!id.equals(plugin.items().idOf(plugin.items().create(id, 2)))) throw new IllegalStateException("identité perdue : " + id);
            }
            ItemStack antidote = plugin.items().create(ItemRegistry.ANTIDOTE, 1);
            if (!antidote.hasData(DataComponentTypes.CONSUMABLE) || !antidote.hasData(DataComponentTypes.USE_EFFECTS))
                throw new IllegalStateException("composants de l'antidote absents");
            return plugin.items().ids().size() + " objets authentifiés, antidote de "
                    + antidote.getData(DataComponentTypes.CONSUMABLE).consumeSeconds() + " s";
        });
        check(lines, "kit de première entrée et sonar", () -> {
            java.util.Set<String> rolls = new java.util.HashSet<>();
            for (int n = 0; n < 40; n++) {
                var kit = plugin.drops().rollKit();
                if (kit.stream().noneMatch(i -> ItemRegistry.isMelee(plugin.items().idOf(i)))
                        || kit.stream().noneMatch(i -> plugin.items().is(i, ItemRegistry.BANDAGE) && i.getAmount() >= 2)
                        || kit.stream().noneMatch(i -> i.getType() == Material.STONE_PICKAXE)
                        || kit.stream().noneMatch(i -> i.getType().isEdible() && i.getAmount() >= 4)) throw new IllegalStateException("minimum manquant");
                rolls.add(kit.toString());
            }
            if (rolls.size() < 2) throw new IllegalStateException("butin non aléatoire");
            var radar = plugin.items().create(ItemRegistry.RADAR, 1);
            if (!(radar.getItemMeta() instanceof org.bukkit.inventory.meta.MapMeta meta) || meta.getMapView() == null
                    || meta.getMapView().getRenderers().size() != 1 || !meta.getMapView().getRenderers().getFirst().isContextual()) throw new IllegalStateException("sonar non lié à une carte contextuelle");
            return "40 kits complets et variés, radar individuel sans mod";
        });
        check(lines, "intérieurs et rayons v2", org.duckcatmine.apocalypse.scenario.city.CityRenovator::verifyBlueprints);
        check(lines, "sérialisation d'inventaire", () -> {
            ItemStack[] items = {plugin.items().create("scrap", 3), ItemStack.empty(),
                    plugin.items().create(ItemRegistry.ANTIDOTE, 1), ItemStack.of(Material.BREAD, 5)};
            ItemStack[] back = ItemStack.deserializeItemsFromBytes(ItemStack.serializeItemsAsBytes(items));
            if (back.length != items.length) throw new IllegalStateException(back.length + " emplacements relus au lieu de " + items.length);
            for (int i = 0; i < items.length; i++) {
                boolean same = items[i].isEmpty() ? back[i] == null || back[i].isEmpty() : items[i].equals(back[i]);
                if (!same) throw new IllegalStateException("emplacement " + i + " modifié");
            }
            if (!items[2].equals(ItemStack.deserializeBytes(items[2].serializeAsBytes()))) throw new IllegalStateException("objet seul modifié");
            return items.length + " emplacements relus à l'identique, vide compris";
        });
        check(lines, "armes et fabrication", () -> {
            for (String id : ApocalypseConfig.WEAPONS) {
                ItemStack weapon = plugin.items().create(id, 1);
                if (!weapon.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS) || !weapon.hasData(DataComponentTypes.WEAPON)
                        || !weapon.hasData(DataComponentTypes.MAX_DAMAGE)) throw new IllegalStateException(id + " : composants d'arme absents");
            }
            if (!plugin.items().create("spear", 1).hasData(DataComponentTypes.ATTACK_RANGE)) throw new IllegalStateException("portée de la lance absente");
            ItemStack pistol = plugin.items().create(ItemRegistry.PISTOL, 1);
            if (pistol.getPersistentDataContainer().get(plugin.keys().gunId, org.bukkit.persistence.PersistentDataType.STRING) == null)
                throw new IllegalStateException("pistolet sans identité");
            for (String id : ItemRegistry.GUNS) {
                var gun = plugin.items().create(id, 1);
                if (!gun.hasData(DataComponentTypes.MAX_DAMAGE) || gun.getData(DataComponentTypes.DAMAGE) != 0) throw new IllegalStateException("usure incorrecte : " + id);
            }
            if (!plugin.items().create(ItemRegistry.SMG,1).hasData(DataComponentTypes.CONSUMABLE)) throw new IllegalStateException("gâchette automatique absente");
            int recipes = 0;
            for (ApocalypseConfig.Recipe recipe : plugin.settings().play().recipes().values()) {
                if (plugin.items().resolve(recipe.result().item(), recipe.result().amount()) == null)
                    throw new IllegalStateException("recette " + recipe.id() + " sans résultat");
                recipes++;
            }
            return ApocalypseConfig.WEAPONS.size() + " armes de mêlée (dégâts, cadence, usure), lance à portée longue, pistolet, "
                    + recipes + " recettes";
        });
        check(lines, "base de données", () -> {
            plugin.database().call(c -> {
                Store.putMeta(c, "selftest", Long.toString(System.currentTimeMillis()));
                return null;
            });
            return "écriture validée";
        });
        World world = plugin.scenario().world();
        if (world == null) {
            lines.add("--  monde : ignoré, aucun monde associé");
            return lines;
        }
        check(lines, "règles du monde", () -> {
            if (Boolean.TRUE.equals(world.getGameRuleValue(GameRules.KEEP_INVENTORY))
                    || Boolean.TRUE.equals(world.getGameRuleValue(GameRules.LOCATOR_BAR))
                    || Boolean.TRUE.equals(world.getGameRuleValue(GameRules.SPAWN_MOBS)))
                throw new IllegalStateException("une règle du monde a été modifiée");
            return "inventaire non conservé, barre de localisation et apparitions vanilla coupées";
        });
        check(lines, "infecté errant", () -> {
            ApocalypseConfig.Refuge refuge = plugin.settings().refuge();
            int x = refuge.centerX(), z = refuge.centerZ() + refuge.radius() + 12;
            Location at = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
            Zombie walker = plugin.mobs().spawnWalker(at);
            try {
                if (!ApocalypseConfig.WALKER.equals(plugin.mobs().archetypeOf(walker))) throw new IllegalStateException("archétype absent");
                if (walker.getCanPickupItems() || walker.canBreakDoors()) throw new IllegalStateException("comportement vanilla resté actif");
                if (new EntityCombustEvent(walker, 8f).callEvent()) throw new IllegalStateException("brûle encore au soleil");
                if (walker.getAttribute(Attribute.SPAWN_REINFORCEMENTS).getBaseValue() != 0) throw new IllegalStateException("renforts actifs");
                return "apparu, protégé du soleil, puis retiré, vitesse " + walker.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue();
            } finally {
                walker.remove();
                plugin.mobs().forget(walker.getUniqueId());
            }
        });
        check(lines, "positions des infectés pour la carte", () -> {
            Location at = street(plugin, world);
            Zombie first = plugin.mobs().spawnWalker(at), second = plugin.mobs().spawnWalker(at.clone().add(2, 0, 0));
            try {
                var points = plugin.mobs().mapPositions(world, 512);
                var position = points.stream().filter(p -> p.id().equals(first.getUniqueId().toString())).findFirst().orElseThrow();
                if (!position.type().equals(ApocalypseConfig.WALKER) || position.x() != at.getBlockX() || position.z() != at.getBlockZ())
                    throw new IllegalStateException("coordonnées ou type perdus");
                if (plugin.mobs().mapPositions(world, 1).size() != 1 || !plugin.mobs().mapPositions(world, 0).isEmpty())
                    throw new IllegalStateException("limite de publication ignorée");
                World classic = Bukkit.getWorlds().getFirst();
                if (!classic.equals(world) && plugin.mobs().mapPositions(classic, 512).stream().anyMatch(p -> p.id().equals(first.getUniqueId().toString())))
                    throw new IllegalStateException("infecté publié dans un autre monde");
                first.remove();
                if (plugin.mobs().mapPositions(world, 512).stream().anyMatch(p -> p.id().equals(first.getUniqueId().toString())))
                    throw new IllegalStateException("infecté retiré encore publié");
                return "positions et types conservés, limite respectée, autres mondes et entités retirées exclus";
            } finally {
                first.remove(); second.remove();
                plugin.mobs().forget(first.getUniqueId()); plugin.mobs().forget(second.getUniqueId());
            }
        });
        check(lines, "archétypes", () -> {
            Location at = street(plugin, world);
            List<String> done = new ArrayList<>();
            for (ApocalypseConfig.Archetype archetype : plugin.settings().archetypes().values()) {
                Appearance appearance = Appearance.byId(archetype.appearances().getFirst());
                Zombie mob = plugin.mobs().spawn(archetype.id(), appearance, at);
                try {
                    if (!archetype.id().equals(plugin.mobs().archetypeOf(mob))) throw new IllegalStateException(archetype.id() + " : archétype absent");
                    if (Math.abs(mob.getAttribute(Attribute.ARMOR).getValue() - archetype.armor()) > 1e-6)
                        throw new IllegalStateException(archetype.id() + " : l'équipement cosmétique donne de l'armure");
                    if (Math.abs(mob.getHealth() - archetype.health()) > 1e-6) throw new IllegalStateException(archetype.id() + " : santé");
                    double speed = mob.getAttribute(Attribute.MOVEMENT_SPEED).getValue();
                    if (Math.abs(speed - archetype.speed()) > archetype.speedVariation() + 1e-6)
                        throw new IllegalStateException(archetype.id() + " : vitesse " + speed);
                    done.add(archetype.id() + " " + String.format(java.util.Locale.ROOT, "%.3f", speed));
                } finally {
                    mob.remove();
                    plugin.mobs().forget(mob.getUniqueId());
                }
            }
            return String.join(", ", done);
        });
        check(lines, "apparences", () -> {
            Location at = street(plugin, world);
            for (Appearance appearance : Appearance.values()) {
                Zombie mob = plugin.mobs().spawn(ApocalypseConfig.WALKER, appearance, at);
                try {
                    if (!appearance.id().equals(plugin.mobs().appearanceOf(mob))) throw new IllegalStateException(appearance.id() + " absente");
                    if (appearance == Appearance.CHILD && (mob.isAdult()
                            || mob.getAttribute(Attribute.MOVEMENT_SPEED).getValue() > plugin.settings().archetype(ApocalypseConfig.WALKER).speed() + 0.02))
                        throw new IllegalStateException("un enfant ne doit pas être plus rapide");
                } finally {
                    mob.remove();
                    plugin.mobs().forget(mob.getUniqueId());
                }
            }
            return Appearance.values().length + " apparences appliquées sans effet de jeu";
        });
        check(lines, "siège et menace", () -> {
            java.util.Map<Material, String> expected = java.util.Map.of(Material.OAK_DOOR, "wood", Material.GLASS_PANE, "glass",
                    Material.IRON_BARS, "metal", Material.BRICKS, "stone", Material.WHITE_CONCRETE, "stone", Material.COBBLESTONE_WALL, "stone",
                    Material.HAY_BLOCK, "soft", Material.SPRUCE_LOG, "wood");
            for (java.util.Map.Entry<Material, String> entry : expected.entrySet()) {
                if (!entry.getValue().equals(SiegeManager.category(entry.getKey())))
                    throw new IllegalStateException(entry.getKey() + " classé " + SiegeManager.category(entry.getKey()));
            }
            for (Material natural : List.of(Material.STONE, Material.DIRT, Material.GRASS_BLOCK, Material.DEEPSLATE, Material.IRON_ORE,
                    Material.BEDROCK, Material.OBSIDIAN, Material.SAND, Material.GRAVEL, Material.ANDESITE)) {
                if (SiegeManager.category(natural) != null) throw new IllegalStateException("terrain naturel attaquable : " + natural);
            }
            var threat = plugin.threat();
            if (threat.level() < 1) throw new IllegalStateException("menace " + threat.level());
            return String.format(java.util.Locale.ROOT, "portes, vitres, métal, maçonnerie attaquables, terrain naturel jamais ; menace %.2f (%s), plafond local 4 -> %d",
                    threat.level(), threat.phase().label(), threat.localCap(4));
        });
        check(lines, "siège en situation", () -> {
            if (!plugin.siege().enabled()) return "ignoré : destruction coupée par un administrateur";
            Location base = street(plugin, world).getBlock().getLocation().add(0, 200, 0);
            List<Block> built = new ArrayList<>();
            Zombie mob = null;
            org.bukkit.entity.Pig target = null;
            try {
                for (int dz = -1; dz <= 4; dz++) {
                    Block floor = base.clone().add(0, -1, dz).getBlock();
                    if (!floor.getType().isAir()) throw new IllegalStateException("emplacement de test occupé");
                    floor.setType(Material.SMOOTH_STONE, false);
                    built.add(floor);
                }
                Block lower = base.clone().add(0, 0, 1).getBlock(), upper = lower.getRelative(0, 1, 0);
                org.bukkit.block.data.type.Door door = (org.bukkit.block.data.type.Door) Material.OAK_DOOR.createBlockData();
                door.setHalf(org.bukkit.block.data.Bisected.Half.BOTTOM);
                lower.setBlockData(door, false);
                door.setHalf(org.bukkit.block.data.Bisected.Half.TOP);
                upper.setBlockData(door, false);
                built.add(lower);
                built.add(upper);
                mob = plugin.mobs().spawnWalker(base.clone().add(0.5, 0, 0.5));
                mob.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0);
                target = world.spawn(base.clone().add(0.5, 0, 4.5), org.bukkit.entity.Pig.class, pig -> {
                    pig.setAI(false);
                    pig.setSilent(true);
                });
                mob.setTarget(target);
                if (!target.equals(mob.getTarget())) throw new IllegalStateException("cible refusée par le serveur");
                ApocalypseConfig.Archetype walker = plugin.settings().archetype(ApocalypseConfig.WALKER);
                long now = plugin.clock().now() + 60_000;
                int hits = 0;
                String refused = null;
                while (lower.getType() == Material.OAK_DOOR && hits < 100) {
                    refused = plugin.siege().attempt(mob, walker, 0, now);
                    if (refused != null) break;
                    now += 1100;
                    hits++;
                }
                if (refused != null) throw new IllegalStateException("coup refusé après " + hits + " coups : " + refused);
                if (lower.getType() != Material.AIR || upper.getType() != Material.AIR) throw new IllegalStateException("porte encore là après " + hits + " coups");
                int expected = (int) Math.ceil(plugin.settings().play().siege().resistance().get("wood") / walker.siegePower());
                if (hits != expected) throw new IllegalStateException(hits + " coups au lieu de " + expected);
                lower.setType(Material.STONE, false);
                for (int k = 0; k < 10; k++, now += 1100) plugin.siege().consider(mob, walker, 0, now);
                if (lower.getType() != Material.STONE) throw new IllegalStateException("roche naturelle détruite");
                return "porte en bois enfoncée par un errant en " + hits + " coups (deux moitiés, sans objet lâché), roche intacte";
            } finally {
                if (mob != null) {
                    mob.remove();
                    plugin.mobs().forget(mob.getUniqueId());
                }
                if (target != null) target.remove();
                for (Block block : built) block.setType(Material.AIR, false);
                plugin.siege().clear();
            }
        });
        check(lines, "faune contaminée", () -> {
            Cow cow = world.spawn(street(plugin, world), Cow.class);
            try {
                plugin.fauna().transform(cow);
                if (!FaunaService.LIVESTOCK.equals(plugin.mobs().archetypeOf(cow)) || !plugin.mobs().tracks(cow))
                    throw new IllegalStateException("vache non convertie");
                if (Math.abs(cow.getHealth() - plugin.settings().archetype(FaunaService.LIVESTOCK).health()) > 1e-6)
                    throw new IllegalStateException("santé " + cow.getHealth());
                if (cow.canBreed()) throw new IllegalStateException("reproduction encore possible");
                if (!org.bukkit.Bukkit.getMobGoals().getAllGoals(cow).isEmpty()) throw new IllegalStateException("comportement vanilla resté actif");
                return "vache convertie en bétail infecté (IA vanilla retirée, reproduction bloquée), puis retirée";
            } finally {
                cow.remove();
                plugin.mobs().forget(cow.getUniqueId());
            }
        });
        check(lines, "arrivée groupée et téléportation sûre", () -> {
            Location center = street(plugin, world).getBlock().getLocation().add(0, 200, 0);
            List<org.bukkit.block.BlockState> before = new ArrayList<>();
            try {
                for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                    Block block = center.clone().add(dx,-1,dz).getBlock();
                    if (!block.getType().isAir()) throw new IllegalStateException("Zone de test occupée");
                    before.add(block.getState()); block.setType(Material.STONE,false);
                }
                center.clone().add(-2,-1,-2).getBlock().setType(Material.MAGMA_BLOCK,false);
                var reserved = new java.util.HashSet<String>();
                Location first = org.duckcatmine.apocalypse.spawn.SafeArrival.near(center,reserved);
                Location second = org.duckcatmine.apocalypse.spawn.SafeArrival.near(center,reserved);
                if (first == null || second == null || first.distanceSquared(second) < 1
                        || first.getBlock().getRelative(0,-1,0).getType() != Material.STONE)
                    throw new IllegalStateException("Arrivées superposées ou sol dangereux accepté");
                for (var block : before) block.getBlock().setType(Material.MAGMA_BLOCK,false);
                if (org.duckcatmine.apocalypse.spawn.SafeArrival.near(center,new java.util.HashSet<>()) != null)
                    throw new IllegalStateException("Destination dangereuse acceptée");
                return "positions distinctes, magma refusé, aucun repli forcé, terrain restauré";
            } finally { for (var block : before) block.update(true,false); }
        });
        check(lines, "tirs et sécurité de la carte", () -> {
            Location base = street(plugin, world).getBlock().getLocation().add(20, 200, 0);
            List<Block> touched = new ArrayList<>();
            org.bukkit.entity.Pig target = null;
            try {
                for (int z = 0; z <= 8; z++) {
                    Block b = base.clone().add(0, -1, z).getBlock();
                    if (!b.getType().isAir()) throw new IllegalStateException("terrain de test occupé");
                    b.setType(Material.SMOOTH_STONE, false); touched.add(b);
                }
                Location eye = base.clone().add(.5, .6, .5);
                target = world.spawn(base.clone().add(.5, 0, 6.5), org.bukkit.entity.Pig.class, e -> { e.setAI(false); e.setGravity(false); e.setSilent(true); });
                var hit = org.duckcatmine.apocalypse.combat.FirearmService.trace(eye,new org.bukkit.util.Vector(0,0,1),12,null);
                if (hit == null || !target.equals(hit.getHitEntity())) throw new IllegalStateException("cible non touchée");
                Block wall = base.clone().add(0,0,3).getBlock(); wall.setType(Material.BRICKS,false); touched.add(wall);
                hit = org.duckcatmine.apocalypse.combat.FirearmService.trace(eye,new org.bukkit.util.Vector(0,0,1),12,null);
                if (hit == null || hit.getHitBlock() == null || hit.getHitEntity() != null) throw new IllegalStateException("tir à travers le mur");
                if (org.duckcatmine.apocalypse.spawn.MapTeleportService.safeColumn(world,base.getBlockX(),base.getBlockY(),base.getBlockZ()) == null) throw new IllegalStateException("sol sûr refusé");
                Block floor = base.clone().add(0,-1,0).getBlock(); floor.setType(Material.MAGMA_BLOCK,false);
                if (org.duckcatmine.apocalypse.spawn.MapTeleportService.safeColumn(world,base.getBlockX(),base.getBlockY(),base.getBlockZ()) != null) throw new IllegalStateException("arrivée sur magma acceptée");
                return "rayon bloqué par un mur ; arrivée dégagée acceptée, magma refusé";
            } finally { if (target != null) target.remove(); for (Block b : touched) b.setType(Material.AIR,false); }
        });
        check(lines, "rénovation sans perte", () -> plugin.renovator().testPreservation(street(plugin,world).getBlock().getLocation().add(30,200,20)));
        check(lines, "voiture", () -> plugin.vehicles().selfTest(street(plugin, world).getBlock().getLocation().add(0, 200, 0)));
        check(lines, "bombe artisanale", () -> {
            Location center = street(plugin, world).getBlock().getLocation().add(8.5, 205, 0.5);
            Block glass = center.clone().add(1, -1, 0).getBlock();
            if (!glass.getType().isAir()) throw new IllegalStateException("emplacement de test occupé");
            glass.setType(Material.GLASS, false);
            org.bukkit.entity.Pig pig = world.spawn(center.clone().add(2, 0, 0), org.bukkit.entity.Pig.class, p -> {
                p.setAI(false);
                p.setSilent(true);
            });
            try {
                double before = pig.getHealth();
                plugin.throwables().explode(center, plugin.settings().play().explosives().bombRadius(),
                        plugin.settings().play().explosives().bombDamage(), null);
                double after = pig.isDead() ? 0 : pig.getHealth();
                if (after >= before) throw new IllegalStateException("cochon indemne à 2 blocs");
                if (glass.getType() != Material.GLASS) throw new IllegalStateException("bloc détruit par l'explosion");
                return String.format(java.util.Locale.ROOT, "cochon à 2 blocs : %.0f -> %.0f PV, aucun bloc détruit", before, after);
            } finally {
                pig.remove();
                glass.setType(Material.AIR, false);
            }
        });
        if (!(world.getGenerator() instanceof GroundZeroGenerator)) {
            lines.add("--  ville : ignorée, monde non généré par Ground Zero");
            return lines;
        }
        CityLayout layout = CityLayout.of(world.getSeed());
        check(lines, "largage", () -> {
            int city = 0;
            for (int k = 0; k < 40; k++) {
                int[] column = plugin.drops().candidate(world);
                if (column == null) continue;
                if (plugin.scenario().refugeArea().near(column[0], column[1], 4)) throw new IllegalStateException("largage dans le refuge");
                if (!layout.openGround(column[0], column[1])) throw new IllegalStateException("largage dans un bâtiment");
                if (layout.sectorAt(column[0], column[1]) == org.duckcatmine.apocalypse.scenario.city.Sector.LAB)
                    throw new IllegalStateException("largage au laboratoire");
                if (Math.max(Math.abs(column[0]), Math.abs(column[1])) <= CityLayout.CITY_EDGE) city++;
            }
            return "40 tirages hors refuge, bâtiments, eau et laboratoire, dont " + city + " en ville";
        });
        List<CityLayout.ContainerSpot> spots = new ArrayList<>();
        check(lines, "ville et conteneurs", () -> {
            int[][] cells = {{0, 1}, {1, 0}, {-1, -1}, {1, 1}, {3, -3}, {-3, 2}, {2, 1}, {-2, -3}};
            for (int[] cell : cells) {
                for (CityLayout.ContainerSpot spot : layout.containers(cell[0], cell[1])) {
                    Material type = world.getBlockAt(spot.x(), spot.y(), spot.z()).getType();
                    if (type != Material.CHEST && type != Material.BARREL)
                        throw new IllegalStateException("conteneur attendu en " + spot.x() + " " + spot.y() + " " + spot.z() + ", trouvé " + type);
                    if (layout.placeAt(spot.x(), spot.y(), spot.z()) != spot.place())
                        throw new IllegalStateException("identification incohérente en " + spot.x() + " " + spot.y() + " " + spot.z());
                    spots.add(spot);
                }
            }
            if (spots.isEmpty()) throw new IllegalStateException("aucun conteneur dans les îlots testés");
            return spots.size() + " conteneurs générés retrouvés et identifiés (" + cells.length + " îlots, hôpital compris)";
        });
        check(lines, "fouille", () -> {
            if (spots.isEmpty()) throw new IllegalStateException("aucun conteneur à tester");
            CityLayout.ContainerSpot spot = spots.getLast();
            Block block = world.getBlockAt(spot.x(), spot.y(), spot.z());
            if (!(block.getState(false) instanceof Container container)) throw new IllegalStateException("pas un conteneur");
            plugin.search().reset(block);
            if (!plugin.search().roll(block, container.getInventory())) throw new IllegalStateException("premier tirage refusé");
            int stacks = 0;
            for (ItemStack stack : container.getInventory().getContents()) if (stack != null && !stack.isEmpty()) stacks++;
            boolean again = plugin.search().roll(block, container.getInventory());
            // Simulate a pre-upgrade generated chest: existing items survive a single stock top-up.
            container.getInventory().setItem(0, ItemStack.of(Material.DIAMOND, 3));
            var tile = (org.bukkit.block.TileState) block.getState();
            tile.getPersistentDataContainer().remove(new org.bukkit.NamespacedKey(plugin, "stock_v2")); tile.update(true,false);
            if (!plugin.search().roll(block, container.getInventory()) || plugin.search().roll(block, container.getInventory())) throw new IllegalStateException("réassort v2 répété ou absent");
            if (container.getInventory().getItem(0) == null || container.getInventory().getItem(0).getType() != Material.DIAMOND || container.getInventory().getItem(0).getAmount() != 3) throw new IllegalStateException("ancien stock écrasé");
            plugin.search().reset(block);
            if (again) throw new IllegalStateException("second tirage accepté sans renouvellement");
            if (stacks == 0 && plugin.settings().place(spot.place().id()).rollsMin() > 0) throw new IllegalStateException("rien tiré");
            return spot.place().label() + " : " + stacks + " pile(s) tirée(s), pas de second tirage, conteneur remis à zéro";
        });
        return lines;
    }

    private static Location street(DuckCatApocalypse plugin, World world) {
        ApocalypseConfig.Refuge refuge = plugin.settings().refuge();
        int x = refuge.centerX(), z = refuge.centerZ() + refuge.radius() + 12;
        return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
    }

    private static void check(List<String> lines, String name, Callable<String> test) {
        try {
            lines.add("OK  " + name + " : " + test.call());
        } catch (Exception | LinkageError e) {
            lines.add("ÉCHEC " + name + " : " + e);
        }
    }
}
