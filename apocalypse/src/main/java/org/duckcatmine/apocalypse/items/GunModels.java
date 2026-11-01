package org.duckcatmine.apocalypse.items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;

/** Opt-in paper dispatcher: one tiny native fallback, no missing model when the optional pack is declined. */
public final class GunModels {
    private GunModels(){}
    public static boolean apply(ItemStack item,String id){
        if(item==null||!ItemRegistry.GUNS.contains(id==null?"":id))return false;
        var data=item.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        if(Key.key("minecraft:paper").equals(item.getData(DataComponentTypes.ITEM_MODEL))&&data!=null&&data.strings().equals(java.util.List.of("duckcatmine:"+id)))return false;
        item.setData(DataComponentTypes.ITEM_MODEL,Key.key("minecraft:paper"));
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA,CustomModelData.customModelData().addString("duckcatmine:"+id).build());
        return true;
    }
    public static ItemStack projectile(){
        ItemStack item=ItemStack.of(org.bukkit.Material.PAPER);
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA,CustomModelData.customModelData().addString("duckcatmine:rocket_projectile").build());return item;
    }
}
