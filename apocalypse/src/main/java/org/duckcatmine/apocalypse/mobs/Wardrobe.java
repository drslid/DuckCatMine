package org.duckcatmine.apocalypse.mobs;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import java.util.List;
import java.util.Random;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.duckcatmine.apocalypse.scenario.city.Sector;

/** Vanilla rendering of each appearance: zombie variant, size and purely cosmetic clothes (no armour value). */
final class Wardrobe {
    private static final Color[] SHIRTS = {rgb(0x5B6770), rgb(0x7A5C3E), rgb(0x3E5A7A), rgb(0x6E3B3B), rgb(0x4F6B3A),
            rgb(0x8A8A7A), rgb(0xB0413E), rgb(0x2E5E5A), rgb(0xC9B79C), rgb(0x6C4F7C)};
    private static final Color[] PANTS = {rgb(0x3B5578), rgb(0x2A2A2A), rgb(0x555555), rgb(0x8B7D5A), rgb(0x4A3B2C)};
    private static final Color[] SHOES = {rgb(0x2A2A2A), rgb(0x5A3A22), rgb(0xD8D8D8)};
    private static final Color[] BRIGHT = {rgb(0xF1C40F), rgb(0xF48FB1), rgb(0x81D4FA), rgb(0xA5D6A7), rgb(0xFF8A65)};
    private static final Color[] SPORT = {rgb(0x2ECC71), rgb(0xE91E63), rgb(0x00BCD4), rgb(0xFF5722), rgb(0x9C27B0)};
    private static final Color[] COATS = {rgb(0x3E2F25), rgb(0x2F3640), rgb(0x4B3A2A), rgb(0x1F2A44)};
    private static final List<Villager.Type> TYPES = List.of(Villager.Type.DESERT, Villager.Type.JUNGLE, Villager.Type.PLAINS,
            Villager.Type.SAVANNA, Villager.Type.SNOW, Villager.Type.SWAMP, Villager.Type.TAIGA);
    private static final List<Villager.Profession> JOBS = List.of(Villager.Profession.ARMORER, Villager.Profession.BUTCHER,
            Villager.Profession.CARTOGRAPHER, Villager.Profession.CLERIC, Villager.Profession.FISHERMAN, Villager.Profession.FLETCHER,
            Villager.Profession.LEATHERWORKER, Villager.Profession.LIBRARIAN, Villager.Profession.MASON, Villager.Profession.SHEPHERD,
            Villager.Profession.TOOLSMITH, Villager.Profession.WEAPONSMITH, Villager.Profession.NONE);
    private static final NamespacedKey BABY_SPEED = NamespacedKey.minecraft("baby");

    private Wardrobe() {
    }

    static Class<? extends Zombie> entityClass(Appearance appearance) {
        return switch (appearance) {
            case FARMER, VILLAGER, HOWLER -> ZombieVillager.class;
            case DRIFTER -> Husk.class;
            default -> Zombie.class;
        };
    }

    /** Weight multiplier of an appearance in a sector: nurses near the hospital, workers in the industrial zone… */
    static double affinity(Appearance appearance, Sector sector) {
        return switch (appearance) {
            case NURSE, PARAMEDIC -> sector == Sector.MEDICAL ? 5 : sector == Sector.LAB ? 2 : sector == Sector.DOWNTOWN ? 1.5 : 0.6;
            case WORKER -> sector == Sector.INDUSTRIAL ? 5 : 0.8;
            case SECURITY -> sector == Sector.LAB ? 3 : sector == Sector.DOWNTOWN ? 2 : 1;
            case FARMER -> sector == Sector.COUNTRYSIDE ? 6 : sector == Sector.OUTSKIRTS ? 2 : 0.2;
            case VILLAGER -> sector == Sector.COUNTRYSIDE ? 3 : sector == Sector.OUTSKIRTS ? 2 : 1;
            case DRIFTER -> sector == Sector.COUNTRYSIDE || sector == Sector.OUTSKIRTS ? 2 : 0.6;
            case CHILD -> sector == Sector.LAB ? 0 : sector == Sector.INDUSTRIAL ? 0.3 : sector == Sector.RESIDENTIAL || sector == Sector.STARTER ? 1.5 : 1;
            case ELDER -> sector == Sector.RESIDENTIAL ? 1.5 : 1;
            default -> 1;
        };
    }

    /** Dresses the infected and returns its size factor, combined later with the archetype scale. */
    static double dress(Zombie zombie, Appearance appearance, Random r) {
        EntityEquipment eq = zombie.getEquipment();
        switch (appearance) {
            case CITIZEN -> {
                eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, pick(SHIRTS, r)));
                if (r.nextInt(3) > 0) eq.setLeggings(leather(Material.LEATHER_LEGGINGS, pick(PANTS, r)));
                if (r.nextInt(3) == 0) eq.setBoots(leather(Material.LEATHER_BOOTS, pick(SHOES, r)));
                return 0.97 + r.nextDouble() * 0.06;
            }
            case CITIZEN_TALL -> {
                Color coat = pick(COATS, r);
                eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, coat));
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, r.nextBoolean() ? coat : pick(PANTS, r)));
                eq.setBoots(leather(Material.LEATHER_BOOTS, pick(SHOES, r)));
                return 1.08 + r.nextDouble() * 0.06;
            }
            case CITIZEN_SHORT -> {
                eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, pick(SHIRTS, r)));
                if (r.nextBoolean()) eq.setLeggings(leather(Material.LEATHER_LEGGINGS, pick(PANTS, r)));
                return 0.88 + r.nextDouble() * 0.05;
            }
            case CHILD -> {
                zombie.setBaby();
                AttributeInstance speed = zombie.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speed != null) speed.removeModifier(BABY_SPEED);
                eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, pick(BRIGHT, r)));
                return 1;
            }
            case ELDER -> {
                eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, r.nextBoolean() ? rgb(0xBDB59A) : rgb(0x7D6B5D)));
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, rgb(0x6B6B6B)));
                if (r.nextBoolean()) eq.setHelmet(leather(Material.LEATHER_HELMET, rgb(0xD5D5D5)));
                return 0.93 + r.nextDouble() * 0.04;
            }
            case NURSE -> {
                Color scrubs = r.nextBoolean() ? rgb(0xE8EEF0) : rgb(0x9FD3E6);
                eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, scrubs));
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, scrubs));
                eq.setBoots(leather(Material.LEATHER_BOOTS, rgb(0xF2F2F2)));
                return 0.94 + r.nextDouble() * 0.1;
            }
            case PARAMEDIC -> {
                eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, rgb(0xC0392B)));
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, rgb(0x2C3E50)));
                eq.setBoots(leather(Material.LEATHER_BOOTS, rgb(0x1C1C1C)));
                return 0.96 + r.nextDouble() * 0.08;
            }
            case WORKER -> {
                eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, rgb(0xF39C12)));
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, rgb(0x2E4A7A)));
                eq.setBoots(leather(Material.LEATHER_BOOTS, rgb(0x5A3A22)));
                eq.setHelmet(r.nextBoolean() ? cosmetic(Material.GOLDEN_HELMET) : leather(Material.LEATHER_HELMET, rgb(0xF1C40F)));
                return 0.97 + r.nextDouble() * 0.08;
            }
            case SECURITY -> {
                eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, rgb(0x1C1C1C)));
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, rgb(0x23272E)));
                eq.setBoots(leather(Material.LEATHER_BOOTS, rgb(0x111111)));
                if (r.nextBoolean()) eq.setHelmet(leather(Material.LEATHER_HELMET, rgb(0x1C1C1C)));
                return 1.0 + r.nextDouble() * 0.08;
            }
            case FARMER -> {
                if (zombie instanceof ZombieVillager villager) {
                    villager.setVillagerType(r.nextBoolean() ? Villager.Type.PLAINS : TYPES.get(r.nextInt(TYPES.size())));
                    villager.setVillagerProfession(Villager.Profession.FARMER);
                }
                return 0.97 + r.nextDouble() * 0.06;
            }
            case VILLAGER -> {
                if (zombie instanceof ZombieVillager villager) {
                    villager.setVillagerType(TYPES.get(r.nextInt(TYPES.size())));
                    villager.setVillagerProfession(JOBS.get(r.nextInt(JOBS.size())));
                }
                return 0.95 + r.nextDouble() * 0.1;
            }
            case DRIFTER -> {
                if (r.nextBoolean()) eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, rgb(0x6D5A43)));
                return 0.95 + r.nextDouble() * 0.1;
            }
            case RUNNER -> {
                eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, pick(SPORT, r)));
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, rgb(0x1C1C1C)));
                eq.setBoots(leather(Material.LEATHER_BOOTS, rgb(0xF5F5F5)));
                return 0.94 + r.nextDouble() * 0.08;
            }
            case HOWLER -> {
                if (zombie instanceof ZombieVillager villager) {
                    villager.setVillagerType(Villager.Type.SWAMP);
                    villager.setVillagerProfession(Villager.Profession.NITWIT);
                }
                return 1.06;
            }
            case HEAVY_WORKER -> {
                eq.setHelmet(cosmetic(Material.GOLDEN_HELMET));
                eq.setChestplate(cosmetic(Material.IRON_CHESTPLATE));
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, rgb(0x2E4A7A)));
                return 1.15 + r.nextDouble() * 0.08;
            }
            case HEAVY_SECURITY -> {
                eq.setHelmet(cosmetic(Material.IRON_HELMET));
                eq.setChestplate(cosmetic(Material.IRON_CHESTPLATE));
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, rgb(0x1C1C1C)));
                eq.setBoots(leather(Material.LEATHER_BOOTS, rgb(0x111111)));
                return 1.15 + r.nextDouble() * 0.08;
            }
            case ABOMINATION -> {
                eq.setLeggings(leather(Material.LEATHER_LEGGINGS, rgb(0x2B2B2B)));
                return 1;
            }
        }
        return 1;
    }

    private static ItemStack leather(Material material, Color color) {
        ItemStack stack = cosmetic(material);
        stack.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(color));
        return stack;
    }

    /** Worn for its look only: the armour attributes are removed. */
    private static ItemStack cosmetic(Material material) {
        ItemStack stack = ItemStack.of(material);
        stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build());
        return stack;
    }

    private static Color pick(Color[] colors, Random r) {
        return colors[r.nextInt(colors.length)];
    }

    private static Color rgb(int value) {
        return Color.fromRGB(value);
    }
}
