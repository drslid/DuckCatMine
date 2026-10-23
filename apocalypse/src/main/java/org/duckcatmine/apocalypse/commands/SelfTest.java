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
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.inventory.ItemStack;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.items.ItemRegistry;
import org.duckcatmine.apocalypse.mobs.Appearance;
import org.duckcatmine.apocalypse.persistence.Store;
import org.duckcatmine.apocalypse.scenario.GroundZeroGenerator;
import org.duckcatmine.apocalypse.scenario.city.CityLayout;

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
        if (!(world.getGenerator() instanceof GroundZeroGenerator)) {
            lines.add("--  ville : ignorée, monde non généré par Ground Zero");
            return lines;
        }
        CityLayout layout = CityLayout.of(world.getSeed());
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
