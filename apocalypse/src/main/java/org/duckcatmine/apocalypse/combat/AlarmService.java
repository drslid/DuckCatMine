package org.duckcatmine.apocalypse.combat;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;
import org.duckcatmine.apocalypse.items.ItemRegistry;

/**
 * Sound lures with a delay: the alarm clock rings loud and long at the set time, the kitchen timer ticks softly then
 * rings. Both are thrown, lie where they land and drop back as an item once they have rung.
 */
public final class AlarmService implements Listener {
    public static final int[] CLOCK_DELAYS = {30, 60, 120, 300}, TIMER_DELAYS = {10, 20, 30, 60};
    public static final int MAX_ACTIVE = 24;
    private record Alarm(ItemDisplay display, String id, long ringsAt, long until, double radius) {}
    private final DuckCatApocalypse plugin;
    private final NamespacedKey delay;
    private final List<Alarm> alarms = new ArrayList<>();

    public AlarmService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
        delay = new NamespacedKey(plugin, "alarm_delay");
    }

    public int count() {
        return alarms.size();
    }

    public static int nextDelay(int[] delays, int current) {
        for (int n = 0; n < delays.length; n++) if (delays[n] == current) return delays[(n + 1) % delays.length];
        return delays[0];
    }

    private int[] delays(String id) {
        return ItemRegistry.ALARM_CLOCK.equals(id) ? CLOCK_DELAYS : TIMER_DELAYS;
    }

    private int delayOf(ItemStack item, String id) {
        Integer value = item.getPersistentDataContainer().get(delay, PersistentDataType.INTEGER);
        return value == null ? delays(id)[0] : value;
    }

    private static String label(int seconds) {
        return seconds < 60 ? seconds + " s" : seconds / 60 + " min";
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSet(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getPlayer().isSneaking()
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        ItemStack item = event.getItem();
        String id = plugin.items().idOf(item);
        if (!ItemRegistry.ALARM_CLOCK.equals(id) && !ItemRegistry.TIMER.equals(id)) return;
        event.setCancelled(true);
        int next = nextDelay(delays(id), delayOf(item, id));
        item.editPersistentDataContainer(data -> data.set(delay, PersistentDataType.INTEGER, next));
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(plugin.items().label(id) + " (" + label(next) + ")", NamedTextColor.AQUA));
        event.getPlayer().getInventory().setItemInMainHand(item);
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_LEVER_CLICK, 0.7f, 1.6f);
        Text.info(event.getPlayer(), "Réglé sur " + label(next) + ". Lance-le pour l'armer.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball ball)) return;
        String id = plugin.items().idOf(ball.getItem());
        if (!ItemRegistry.ALARM_CLOCK.equals(id) && !ItemRegistry.TIMER.equals(id)) return;
        ball.getPersistentDataContainer().set(delay, PersistentDataType.INTEGER, delayOf(ball.getItem(), id));
    }

    @EventHandler
    public void onLand(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball ball) || !ball.getPersistentDataContainer().has(delay)) return;
        String id = plugin.items().idOf(ball.getItem());
        int seconds = ball.getPersistentDataContainer().getOrDefault(delay, PersistentDataType.INTEGER, 30);
        Location at = ball.getLocation();
        ItemStack look = plugin.items().create(id, 1);
        if (look == null || !plugin.scenario().isScenarioWorld(at.getWorld())) return;
        look.editPersistentDataContainer(data -> data.set(delay, PersistentDataType.INTEGER, seconds));
        if (alarms.size() >= MAX_ACTIVE) { at.getWorld().dropItemNaturally(at, look); return; }
        ItemDisplay display = at.getWorld().spawn(at.clone().add(0, 0.15, 0), ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(look);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
        });
        boolean clock = ItemRegistry.ALARM_CLOCK.equals(id);
        long now = plugin.clock().now();
        alarms.add(new Alarm(display, id, now + seconds * 1000L, now + seconds * 1000L + (clock ? 15_000 : 6_000), clock ? 48 : 28));
        if (ball.getShooter() instanceof Player owner) Text.info(owner, (clock ? "Le réveil" : "Le minuteur") + " sonnera dans " + label(seconds) + ".");
    }

    /** Every 5 ticks. */
    public void tick() {
        long now = plugin.clock().now();
        for (Alarm alarm : new ArrayList<>(alarms)) {
            ItemDisplay display = alarm.display();
            if (!display.isValid() || !plugin.scenario().isEnabled()) { alarms.remove(alarm); display.remove(); continue; }
            Location at = display.getLocation();
            boolean clock = ItemRegistry.ALARM_CLOCK.equals(alarm.id());
            if (now < alarm.ringsAt()) {
                if (!clock && (now / 250) % 4 == 0) at.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.BLOCKS, 0.5f, 1.8f);
                continue;
            }
            if (now >= alarm.until()) {
                alarms.remove(alarm);
                ItemStack back = display.getItemStack();
                display.remove();
                at.getWorld().dropItemNaturally(at, back);
                continue;
            }
            if ((now / 250) % 2 == 0) {
                at.getWorld().playSound(at, clock ? Sound.BLOCK_BELL_USE : Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.BLOCKS, clock ? 3f : 2f, clock ? 1.4f : 1.9f);
                at.getWorld().spawnParticle(Particle.NOTE, at.clone().add(0, 0.5, 0), 2, 0.3, 0.2, 0.3, 1);
            }
            if ((now / 250) % 4 == 0) plugin.noise().emit(at, alarm.radius(), null);
        }
    }

    public void stop() {
        for (Alarm alarm : alarms) {
            if (!alarm.display().isValid()) continue;
            alarm.display().getWorld().dropItemNaturally(alarm.display().getLocation(), alarm.display().getItemStack());
            alarm.display().remove();
        }
        alarms.clear();
    }
}
