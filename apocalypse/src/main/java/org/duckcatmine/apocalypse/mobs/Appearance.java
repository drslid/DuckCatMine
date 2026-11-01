package org.duckcatmine.apocalypse.mobs;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Look of an infected, independent of its archetype (behaviour). Without a resource pack these are vanilla zombie
 * variants, sizes and dyed clothes; the README manifest says what each one really shows.
 */
public enum Appearance {
    CITIZEN("Civil"),
    CITIZEN_TALL("Civil, grand"),
    CITIZEN_SHORT("Civil, petit"),
    CHILD("Enfant"),
    ELDER("Personne âgée"),
    NURSE("Soignant"),
    PARAMEDIC("Secouriste"),
    WORKER("Ouvrier"),
    SECURITY("Agent de sécurité"),
    FARMER("Fermier"),
    VILLAGER("Habitant"),
    DRIFTER("Vagabond du désert"),
    RUNNER("Joggeur"),
    HOWLER("Hurleur"),
    HEAVY_WORKER("Colosse de chantier"),
    HEAVY_SECURITY("Colosse anti-émeute"),
    ABOMINATION("Abomination");

    private final String label;

    Appearance(String label) {
        this.label = label;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label() {
        return label;
    }

    public static Appearance byId(String id) {
        for (Appearance appearance : values()) if (appearance.id().equals(id)) return appearance;
        return null;
    }

    public static String ids() {
        return Arrays.stream(values()).map(Appearance::id).collect(Collectors.joining(", "));
    }
}
