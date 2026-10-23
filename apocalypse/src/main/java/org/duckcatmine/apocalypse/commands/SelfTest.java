package org.duckcatmine.apocalypse.commands;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.bukkit.GameRules;
import org.bukkit.Location;
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
