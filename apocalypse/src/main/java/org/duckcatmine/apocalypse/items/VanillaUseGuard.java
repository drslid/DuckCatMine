package org.duckcatmine.apocalypse.items;

import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

/** Scenario items share a vanilla base material; they must never act as that material. */
public final class VanillaUseGuard implements Listener {
    private final ItemRegistry items;

    public VanillaUseGuard(ItemRegistry items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        if (containsScenarioItem(event.getInventory().getMatrix())) event.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareResultEvent event) {
        if (containsScenarioItem(event.getInventory().getContents())) event.setResult(null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (event.getBlock().getState(false) instanceof Container container && containsScenarioItem(container.getInventory().getContents()))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent event) {
        if (items.isScenarioItem(event.getSource())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFuel(FurnaceBurnEvent event) {
        if (items.isScenarioItem(event.getFuel())) event.setCancelled(true);
    }

    private boolean containsScenarioItem(ItemStack[] stacks) {
        for (ItemStack stack : stacks) if (items.isScenarioItem(stack)) return true;
        return false;
    }
}
