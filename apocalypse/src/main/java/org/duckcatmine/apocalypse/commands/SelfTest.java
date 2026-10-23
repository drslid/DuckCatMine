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
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.items.ItemRegistry;
import org.duckcatmine.apocalypse.persistence.Store;

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
            int x = refuge.centerX(), z = refuge.centerZ() + refuge.radius() + 24;
            Location at = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
            Zombie walker = plugin.mobs().spawnWalker(at);
            try {
                if (!ApocalypseConfig.WALKER.equals(plugin.mobs().archetypeOf(walker))) throw new IllegalStateException("archétype absent");
                if (walker.shouldBurnInDay() || walker.getCanPickupItems() || walker.canBreakDoors())
                    throw new IllegalStateException("comportement vanilla resté actif");
                if (walker.getAttribute(Attribute.SPAWN_REINFORCEMENTS).getBaseValue() != 0) throw new IllegalStateException("renforts actifs");
                return "apparu puis retiré, vitesse " + walker.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue();
            } finally {
                walker.remove();
                plugin.mobs().forget(walker.getUniqueId());
            }
        });
        return lines;
    }

    private static void check(List<String> lines, String name, Callable<String> test) {
        try {
            lines.add("OK  " + name + " : " + test.call());
        } catch (Exception | LinkageError e) {
            lines.add("ÉCHEC " + name + " : " + e);
        }
    }
}
