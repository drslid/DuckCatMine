package org.duckcatmine.apocalypse.scenario;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.duckcatmine.apocalypse.DuckCatApocalypse;
import org.duckcatmine.apocalypse.Text;

/** Short protection at the start of a life; it ends early on the first hostile action. */
public final class ProtectionService {
    private final DuckCatApocalypse plugin;
    private final Map<UUID, Long> until = new HashMap<>();

    public ProtectionService(DuckCatApocalypse plugin) {
        this.plugin = plugin;
    }

    public void start(Player player) {
        long duration = plugin.settings().protectionMs();
        if (duration <= 0) return;
        until.put(player.getUniqueId(), plugin.clock().now() + duration);
        Text.info(player, "Protection de départ : " + duration / 1000 + " s. Elle cesse dès que tu attaques.");
    }

    public boolean isProtected(Player player) {
        Long end = until.get(player.getUniqueId());
        return end != null && plugin.clock().now() < end;
    }

    public void endByAction(Player player) {
        if (until.remove(player.getUniqueId()) != null) Text.warn(player, "Protection de départ terminée : tu as attaqué.");
    }

    public void clear(UUID id) {
        until.remove(id);
    }

    public void tick() {
        long now = plugin.clock().now();
        Iterator<Map.Entry<UUID, Long>> entries = until.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Long> entry = entries.next();
            if (now < entry.getValue()) continue;
            entries.remove();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) Text.warn(player, "Protection de départ terminée.");
        }
    }
}
