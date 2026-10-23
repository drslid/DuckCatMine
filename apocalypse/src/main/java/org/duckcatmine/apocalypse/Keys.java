package org.duckcatmine.apocalypse;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class Keys {
    /** World marker shared with DuckCatWelcome and the panel bridge, without a compile dependency. */
    public static final NamespacedKey SCENARIO = new NamespacedKey("duckcatmine", "scenario");

    public final NamespacedKey worldId;
    public final NamespacedKey item;
    public final NamespacedKey archetype;
    public final NamespacedKey bag;
    public final NamespacedKey profile;
    public final NamespacedKey appliedSeq;
    public final NamespacedKey run;

    public Keys(Plugin plugin) {
        worldId = new NamespacedKey(plugin, "world_id");
        item = new NamespacedKey(plugin, "item");
        archetype = new NamespacedKey(plugin, "archetype");
        bag = new NamespacedKey(plugin, "bag");
        profile = new NamespacedKey(plugin, "profile");
        appliedSeq = new NamespacedKey(plugin, "applied_seq");
        run = new NamespacedKey(plugin, "run");
    }
}
