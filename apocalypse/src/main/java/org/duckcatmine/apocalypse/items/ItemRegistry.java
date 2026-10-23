package org.duckcatmine.apocalypse.items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.UseEffects;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.duckcatmine.apocalypse.Keys;
import org.duckcatmine.apocalypse.config.ApocalypseConfig;
import org.duckcatmine.apocalypse.loot.LootTable;

/**
 * Scenario items are authenticated by a persistent data key, never by their name.
 * They all use an inert base material; their look comes from vanilla item models.
 */
public final class ItemRegistry {
    public static final String NAMESPACE = "apocalypse";
    public static final String ANTIDOTE = "antidote";
    public static final String BANDAGE = "bandage";
    public static final String NOTE = "note";

    /** Documents found in Ground Zero: story and hints, never game rules that the code does not apply. */
    private static final List<List<String>> NOTES = List.of(
            List.of("Journal, jour 3", "Ils ne dorment pas. Ils écoutent.", "Le moindre bruit les rameute.", "Je ne cours plus, je marche accroupi."),
            List.of("Consigne NEXUS-13", "Morsure : 60 secondes pour l'antidote.", "Toutes les morsures n'infectent pas,", "mais une seule suffit."),
            List.of("Message griffonné", "Le laboratoire est au nord-est,", "au bout de la route de terre.", "N'y va pas seul."),
            List.of("Liste de courses", "Pain, piles, ruban adhésif,", "alcool à 90°…", "Tout est parti en deux heures."),
            List.of("Rapport de police", "Armurerie forcée, barreaux sciés.", "Il reste peut-être des douilles", "dans les caisses du fond."),
            List.of("Note d'infirmière", "Le stock de l'hôpital est au fond", "du rez-de-chaussée, aile arrière.", "Ne traîne pas dans les couloirs."),
            List.of("Carte postale", "Bisous de Ground Zero !", "Il fait beau, les rues sont calmes.", "(cachet du 2 juin)"),
            List.of("Avis d'évacuation", "Rejoignez le refuge de la place centrale.", "Barrages militaires sur les quatre", "routes de sortie de la ville."),
            List.of("Fiche d'atelier", "Ressorts et ruban : les garages.", "Pièces d'arme : police et barrages.", "Douilles : armureries."),
            List.of("Dossier NEXUS, page 12", "Sujet 7 : croissance anormale.", "Masse supérieure à 200 kg.", "Confinement perdu."),
            List.of("Mot d'enfant", "Papa a dit de pas ouvrir la porte.", "Dehors, quelqu'un court très vite", "et puis il s'arrête pour souffler."),
            List.of("Note du gardien", "Le grand costaud encaisse tout.", "Il est lent, très lent :", "frappe à plusieurs ou fais le tour."));

    private record Definition(String name, NamedTextColor color, String model, int maxStack, List<String> lore) {
    }

    private final Keys keys;
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private volatile ApocalypseConfig config;

    public ItemRegistry(Keys keys, ApocalypseConfig config) {
        this.keys = keys;
        this.config = config;
        definitions.put(ANTIDOTE, new Definition("Antidote NEXUS", NamedTextColor.GREEN, "minecraft:experience_bottle", 4,
                List.of("Arrête l'infection NEXUS-13 si l'administration", "se termine avant la fin du compte à rebours.",
                        "Maintiens le clic droit. Sans effet si tu n'es pas infecté.")));
        definitions.put("scrap", new Definition("Ferraille", NamedTextColor.WHITE, "minecraft:iron_nugget", 32,
                List.of("Composant de récupération.")));
        definitions.put("cloth", new Definition("Tissu récupéré", NamedTextColor.WHITE, "minecraft:white_carpet", 32,
                List.of("Composant de récupération.")));
        definitions.put("tape", new Definition("Ruban technique", NamedTextColor.WHITE, "minecraft:gray_dye", 16,
                List.of("Composant : fixations et réparations.")));
        definitions.put("spring", new Definition("Ressort", NamedTextColor.WHITE, "minecraft:tripwire_hook", 16,
                List.of("Composant mécanique.")));
        definitions.put("gun_parts", new Definition("Pièces d'arme", NamedTextColor.YELLOW, "minecraft:netherite_scrap", 16,
                List.of("Composant rare : armes de fortune.")));
        definitions.put("casings", new Definition("Douilles", NamedTextColor.WHITE, "minecraft:gold_nugget", 32,
                List.of("Composant : munitions.")));
        definitions.put("propellant", new Definition("Charge propulsive", NamedTextColor.WHITE, "minecraft:gunpowder", 16,
                List.of("Composant : munitions. Objet de jeu abstrait.")));
        definitions.put("alcohol", new Definition("Alcool médical", NamedTextColor.WHITE, "minecraft:honey_bottle", 8,
                List.of("Composant médical : bandages.")));
        definitions.put("syringe", new Definition("Seringue", NamedTextColor.AQUA, "minecraft:end_rod", 8,
                List.of("Composant médical : antidote.")));
        definitions.put("serum", new Definition("Sérum NEXUS scellé", NamedTextColor.LIGHT_PURPLE, "minecraft:dragon_breath", 4,
                List.of("Composant rare : antidote.", "Hôpital et laboratoire.")));
        definitions.put("inhibitor", new Definition("Inhibiteur", NamedTextColor.LIGHT_PURPLE, "minecraft:prismarine_crystals", 8,
                List.of("Composant rare : antidote.", "Hôpital et laboratoire.")));
        definitions.put(BANDAGE, new Definition("Bandage", NamedTextColor.GREEN, "minecraft:paper", 16,
                List.of("Rend 2 cœurs. Maintiens le clic droit.", "Ne soigne pas une morsure.")));
        definitions.put(NOTE, new Definition("Note", NamedTextColor.GOLD, "minecraft:writable_book", 1, List.of()));
    }

    public void reload(ApocalypseConfig config) {
        this.config = config;
    }

    public Set<String> ids() {
        return definitions.keySet();
    }

    public ItemStack create(String id, int amount) {
        Definition definition = definitions.get(id);
        if (definition == null) return null;
        ItemStack stack = ItemStack.of(Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_NAME, Component.text(definition.name(), definition.color()));
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(definition.model()));
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, definition.maxStack());
        List<Component> lore = new ArrayList<>();
        for (String line : definition.lore()) lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        if (NOTE.equals(id)) {
            List<String> note = NOTES.get(ThreadLocalRandom.current().nextInt(NOTES.size()));
            stack.setData(DataComponentTypes.ITEM_NAME, Component.text("Note : " + note.getFirst(), definition.color()));
            List<Component> text = new ArrayList<>();
            for (String line : note.subList(1, note.size())) text.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(text));
        }
        if (BANDAGE.equals(id)) {
            stack.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                    .consumeSeconds(1.6f)
                    .animation(ItemUseAnimation.BRUSH)
                    .sound(Key.key("minecraft:item.armor.equip_leather"))
                    .hasConsumeParticles(false)
                    .addEffect(ConsumeEffect.applyStatusEffects(List.of(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 0)), 1.0f))
                    .build());
        }
        if (ANTIDOTE.equals(id)) {
            ApocalypseConfig.Infection infection = config.infection();
            stack.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                    .consumeSeconds(infection.antidoteUseSeconds())
                    .animation(ItemUseAnimation.DRINK)
                    .sound(Key.key("minecraft:entity.generic.drink"))
                    .hasConsumeParticles(false)
                    .build());
            stack.setData(DataComponentTypes.USE_EFFECTS, UseEffects.useEffects()
                    .canSprint(infection.antidoteAllowSprint())
                    .speedMultiplier(infection.antidoteSpeedMultiplier())
                    .build());
        }
        stack.editPersistentDataContainer(data -> data.set(keys.item, PersistentDataType.STRING, id));
        stack.setAmount(Math.max(1, Math.min(amount, definition.maxStack())));
        return stack;
    }

    public String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return stack.getPersistentDataContainer().get(keys.item, PersistentDataType.STRING);
    }

    public boolean isScenarioItem(ItemStack stack) {
        return idOf(stack) != null;
    }

    /** Resolves {@code apocalypse:<id>} or a vanilla {@code minecraft:<item>} identifier. */
    public ItemStack resolve(String namespaced, int amount) {
        if (namespaced.startsWith(NAMESPACE + ":")) return create(namespaced.substring(NAMESPACE.length() + 1), amount);
        Material material = Material.matchMaterial(namespaced);
        if (material == null || !material.isItem() || material.isAir()) return null;
        return ItemStack.of(material, Math.max(1, Math.min(amount, material.getMaxStackSize())));
    }

    /** Needs a running server: vanilla identifiers are checked against the real item registry. */
    public List<String> validate(ApocalypseConfig config) {
        List<String> errors = new ArrayList<>();
        for (ApocalypseConfig.Archetype archetype : config.archetypes().values()) {
            check(errors, "archetypes." + archetype.id() + ".guaranteed-loot", archetype.guaranteed());
            check(errors, "archetypes." + archetype.id() + ".bonus-loot", archetype.bonus());
        }
        for (ApocalypseConfig.Place place : config.places().values()) check(errors, "places." + place.id() + ".loot", place.loot());
        return errors;
    }

    private void check(List<String> errors, String path, LootTable table) {
        for (LootTable.Entry entry : table.entries()) {
            if (resolve(entry.item(), entry.amount()) == null) errors.add(path + " : objet inconnu " + entry.item() + ".");
        }
    }
}
