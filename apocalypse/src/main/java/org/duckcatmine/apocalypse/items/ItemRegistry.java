package org.duckcatmine.apocalypse.items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.UseEffects;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
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
        return errors;
    }

    private void check(List<String> errors, String path, LootTable table) {
        for (LootTable.Entry entry : table.entries()) {
            if (resolve(entry.item(), entry.amount()) == null) errors.add(path + " : objet inconnu " + entry.item() + ".");
        }
    }
}
