package org.duckcatmine.apocalypse.items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.AttackRange;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.UseEffects;
import io.papermc.paper.datacomponent.item.Weapon;
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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
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

    private record Definition(String name, NamedTextColor color, Material base, String model, int maxStack, List<String> lore) {
        Definition(String name, NamedTextColor color, String model, int maxStack, List<String> lore) {
            this(name, color, Material.PAPER, model, maxStack, lore);
        }
    }

    public static final String PISTOL = "pistol";
    public static final String AMMO = "ammo";
    public static final String DECOY = "decoy";
    public static final String WORKSHOP = "workshop";
    public static final String MEDICAL_STATION = "medical_station";
    public static final String BARRICADE = "barricade";
    public static final String BARRICADE_REINFORCED = "barricade_reinforced";
    public static final String SPIKE_TRAP = "spike_trap";
    public static final String BEACON = "beacon";
    public static final String PIPE_BOMB = "pipe_bomb";
    public static final String MOLOTOV = "molotov";
    public static final String BOW = "bow";
    public static final String ARROW = "arrow";
    public static final String JERRYCAN = "jerrycan";
    /** Items that become a tracked block when placed (station, defence or claim). */
    public static final Set<String> PLACEABLE = Set.of(WORKSHOP, MEDICAL_STATION, BARRICADE, BARRICADE_REINFORCED, SPIKE_TRAP, BEACON);

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
        definitions.put("club", new Definition("Massue improvisée", NamedTextColor.WHITE, "minecraft:wooden_shovel", 1,
                List.of("Arme de mêlée bon marché, portée courte.")));
        definitions.put("blade", new Definition("Lame de récupération", NamedTextColor.WHITE, "minecraft:stone_sword", 1,
                List.of("Rapide mais fragile.")));
        definitions.put("spear", new Definition("Lance courte", NamedTextColor.WHITE, "minecraft:stone_spear", 1,
                List.of("Frappe de plus loin, cadence réduite.")));
        definitions.put("heavy_club", new Definition("Massue renforcée", NamedTextColor.YELLOW, "minecraft:mace", 1,
                List.of("Lourde : frappe fort et repousse.")));
        definitions.put(PISTOL, new Definition("Pistolet de fortune", NamedTextColor.GOLD, "minecraft:crossbow", 1,
                List.of("Clic droit : tirer. Touche d'échange de main (F) : recharger.", "Bruyant : chaque tir attire les infectés.")));
        definitions.put(AMMO, new Definition("Munitions de pistolet", NamedTextColor.WHITE, "minecraft:copper_nugget", 32,
                List.of("Pour le pistolet de fortune.")));
        definitions.put(DECOY, new Definition("Leurre sonore", NamedTextColor.AQUA, Material.SNOWBALL, "minecraft:note_block", 8,
                List.of("À lancer (clic droit) : sonne quelques secondes", "et attire les infectés vers lui.")));
        definitions.put(WORKSHOP, new Definition("Atelier mécanique", NamedTextColor.AQUA, Material.SMITHING_TABLE, null, 1,
                List.of("À poser : armes à feu, munitions, barricades renforcées,", "leurres et réparations.")));
        definitions.put(MEDICAL_STATION, new Definition("Poste médical", NamedTextColor.AQUA, Material.BREWING_STAND, null, 1,
                List.of("À poser : bandages et antidote NEXUS.")));
        definitions.put(BARRICADE, new Definition("Barricade légère", NamedTextColor.WHITE, Material.DARK_OAK_FENCE, null, 16,
                List.of("Retarde les infectés ; ils finissent par la casser.")));
        definitions.put(BARRICADE_REINFORCED, new Definition("Barricade renforcée", NamedTextColor.YELLOW,
                Material.WAXED_WEATHERED_COPPER_GRATE, null, 16, List.of("Résiste longtemps aux coups des infectés.")));
        definitions.put(SPIKE_TRAP, new Definition("Piège à pointes", NamedTextColor.WHITE, Material.POINTED_DRIPSTONE, null, 8,
                List.of("Blesse les infectés qui marchent dessus.", "Charges limitées ; jamais dans le refuge.")));
        definitions.put(BEACON, new Definition("Balise de base", NamedTextColor.AQUA, Material.LODESTONE, null, 1,
                List.of("Revendique ta base (rayon 12) pour toi et ton équipe.", "Pas de siège quand vous êtes tous absents.")));
        definitions.put(PIPE_BOMB, new Definition("Bombe artisanale", NamedTextColor.RED, Material.SNOWBALL, "minecraft:tnt", 4,
                List.of("À lancer (clic droit) : explose 3,5 s plus tard.", "Blesse tout ce qui est proche, toi compris.",
                        "L'explosion s'entend dans tout le quartier.")));
        definitions.put(MOLOTOV, new Definition("Cocktail Molotov", NamedTextColor.GOLD, Material.SNOWBALL, "minecraft:splash_potion", 4,
                List.of("À lancer (clic droit) : enflamme une zone", "pendant quelques secondes.")));
        definitions.put("fire_axe", new Definition("Hache d'incendie", NamedTextColor.YELLOW, "minecraft:iron_axe", 1,
                List.of("Lourde et lente : frappe très fort.")));
        definitions.put(BOW, new Definition("Arc de fortune", NamedTextColor.WHITE, Material.BOW, null, 1,
                List.of("Silencieux : ne réveille pas le quartier.", "Tire les flèches de ton inventaire ; elles se ramassent.")));
        definitions.put(ARROW, new Definition("Flèche artisanale", NamedTextColor.WHITE, Material.ARROW, null, 32,
                List.of("Pour l'arc de fortune.")));
        definitions.put(JERRYCAN, new Definition("Jerrican", NamedTextColor.RED, "minecraft:bucket", 1,
                List.of("Accroupi + clic droit sur une voiture : siphonner.", "Clic droit sur une voiture : faire le plein.")));
    }

    public static boolean isMelee(String id) {
        return ApocalypseConfig.WEAPONS.contains(id);
    }

    public void reload(ApocalypseConfig config) {
        this.config = config;
    }

    public Set<String> ids() {
        return definitions.keySet();
    }

    /** Display name of a scenario item, or null. */
    public String label(String id) {
        Definition definition = definitions.get(id);
        return definition == null ? null : definition.name();
    }

    public ItemStack create(String id, int amount) {
        Definition definition = definitions.get(id);
        if (definition == null) return null;
        ItemStack stack = ItemStack.of(definition.base());
        stack.setData(DataComponentTypes.ITEM_NAME, Component.text(definition.name(), definition.color()));
        if (definition.model() != null) stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(definition.model()));
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
        if (isMelee(id)) melee(stack, config.play().weapons().get(id));
        if (PISTOL.equals(id)) {
            stack.setData(DataComponentTypes.MAX_DAMAGE, config.play().pistol().durability());
            stack.setData(DataComponentTypes.DAMAGE, 0);
            stack.editPersistentDataContainer(data -> {
                data.set(keys.magazine, PersistentDataType.INTEGER, 0);
                data.set(keys.gunId, PersistentDataType.STRING, java.util.UUID.randomUUID().toString());
            });
        }
        if (SPIKE_TRAP.equals(id))
            stack.editPersistentDataContainer(data -> data.set(keys.charges, PersistentDataType.INTEGER, config.play().traps().charges()));
        if (BOW.equals(id)) {
            stack.setData(DataComponentTypes.MAX_DAMAGE, 150);
            stack.setData(DataComponentTypes.DAMAGE, 0);
        }
        if (JERRYCAN.equals(id)) setFuel(stack, 0);
        stack.editPersistentDataContainer(data -> data.set(keys.item, PersistentDataType.STRING, id));
        stack.setAmount(Math.max(1, Math.min(amount, definition.maxStack())));
        return stack;
    }

    /** Real attack values on an inert base: vanilla applies them, wears the weapon per hit and breaks it at the end. */
    private void melee(ItemStack stack, ApocalypseConfig.Weapon weapon) {
        ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.itemAttributes()
                .addModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(keys.weaponDamage, weapon.damage() - 1,
                        AttributeModifier.Operation.ADD_NUMBER), EquipmentSlotGroup.MAINHAND)
                .addModifier(Attribute.ATTACK_SPEED, new AttributeModifier(keys.weaponSpeed, weapon.attacksPerSecond() - 4,
                        AttributeModifier.Operation.ADD_NUMBER), EquipmentSlotGroup.MAINHAND);
        if (weapon.knockback() > 0) attributes.addModifier(Attribute.ATTACK_KNOCKBACK, new AttributeModifier(keys.weaponKnockback,
                weapon.knockback(), AttributeModifier.Operation.ADD_NUMBER), EquipmentSlotGroup.MAINHAND);
        stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, attributes.build());
        stack.setData(DataComponentTypes.MAX_DAMAGE, weapon.durability());
        stack.setData(DataComponentTypes.DAMAGE, 0);
        stack.setData(DataComponentTypes.WEAPON, Weapon.weapon().itemDamagePerAttack(1).build());
        if (weapon.reach() > 3.01) stack.setData(DataComponentTypes.ATTACK_RANGE, AttackRange.attackRange()
                .minReach(0).maxReach((float) weapon.reach()).minCreativeReach(0).maxCreativeReach((float) weapon.reach() + 1)
                .hitboxMargin(0.1f).mobFactor(1).build());
    }

    public boolean is(ItemStack stack, String id) {
        return id.equals(idOf(stack));
    }

    /** Litres in a jerrycan (0 for anything else). */
    public double fuel(ItemStack stack) {
        if (!is(stack, JERRYCAN)) return 0;
        Double litres = stack.getPersistentDataContainer().get(keys.fuel, PersistentDataType.DOUBLE);
        return litres == null ? 0 : litres;
    }

    /** Sets the litres of a jerrycan and shows them in its name and look. */
    public void setFuel(ItemStack stack, double litres) {
        double value = Math.max(0, litres);
        stack.editPersistentDataContainer(data -> data.set(keys.fuel, PersistentDataType.DOUBLE, value));
        stack.setData(DataComponentTypes.ITEM_NAME, Component.text(value < 0.5 ? "Jerrican (vide)"
                : "Jerrican (" + Math.round(value) + " L d'essence)", NamedTextColor.RED));
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(value < 0.5 ? "minecraft:bucket" : "minecraft:lava_bucket"));
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
        check(errors, "start-kit.loot", config.play().kit().loot());
        config.play().kit().weaponKits().forEach((kit, stacks) -> stacks.forEach(stack -> {
            if (resolve(stack.item(), stack.amount()) == null) errors.add("start-kit.weapon-kits." + kit + " : objet inconnu " + stack.item() + ".");
        }));
        for (ApocalypseConfig.Recipe recipe : config.play().recipes().values()) {
            if (resolve(recipe.result().item(), recipe.result().amount()) == null)
                errors.add("recipes." + recipe.id() + ".result : objet inconnu " + recipe.result().item() + ".");
            for (String ingredient : recipe.ingredients().keySet()) {
                if (!ingredient.startsWith("#") && resolve(ingredient, 1) == null)
                    errors.add("recipes." + recipe.id() + ".ingredients : objet inconnu " + ingredient + ".");
            }
        }
        return errors;
    }

    private void check(List<String> errors, String path, LootTable table) {
        for (LootTable.Entry entry : table.entries()) {
            if (resolve(entry.item(), entry.amount()) == null) errors.add(path + " : objet inconnu " + entry.item() + ".");
        }
    }
}
