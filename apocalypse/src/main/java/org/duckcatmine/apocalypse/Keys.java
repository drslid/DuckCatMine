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
    public final NamespacedKey appearance;
    /** Place type of a generated searchable container, set the first time it is identified. */
    public final NamespacedKey searchPlace;
    /** Scenario clock (ms) of the last loot roll of a searchable container; absent = to roll at next opening. */
    public final NamespacedKey searchRolled;
    /** Containers placed by a player never become searchable, even on a generated spot. */
    public final NamespacedKey playerPlaced;
    public final NamespacedKey burst;
    public final NamespacedKey slowed;
    /** Generation of Ground Zero in which the player's survivor position is valid. */
    public final NamespacedKey worldEpoch;
    public final NamespacedKey magazine;
    public final NamespacedKey gunId;
    public final NamespacedKey charges;
    public final NamespacedKey weaponDamage;
    public final NamespacedKey weaponSpeed;
    public final NamespacedKey weaponKnockback;
    public final NamespacedKey decoy;

    public Keys(Plugin plugin) {
        worldId = new NamespacedKey(plugin, "world_id");
        item = new NamespacedKey(plugin, "item");
        archetype = new NamespacedKey(plugin, "archetype");
        bag = new NamespacedKey(plugin, "bag");
        profile = new NamespacedKey(plugin, "profile");
        appliedSeq = new NamespacedKey(plugin, "applied_seq");
        run = new NamespacedKey(plugin, "run");
        appearance = new NamespacedKey(plugin, "appearance");
        searchPlace = new NamespacedKey(plugin, "search_place");
        searchRolled = new NamespacedKey(plugin, "search_rolled");
        playerPlaced = new NamespacedKey(plugin, "player_placed");
        burst = new NamespacedKey(plugin, "burst");
        slowed = new NamespacedKey(plugin, "slowed");
        worldEpoch = new NamespacedKey(plugin, "world_epoch");
        magazine = new NamespacedKey(plugin, "magazine");
        gunId = new NamespacedKey(plugin, "gun_id");
        charges = new NamespacedKey(plugin, "charges");
        weaponDamage = new NamespacedKey(plugin, "weapon_damage");
        weaponSpeed = new NamespacedKey(plugin, "weapon_speed");
        weaponKnockback = new NamespacedKey(plugin, "weapon_knockback");
        decoy = new NamespacedKey(plugin, "decoy");
    }
}
