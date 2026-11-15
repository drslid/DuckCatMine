package org.duckcatmine.apocalypse.scenario.city;

import java.util.Locale;

/** Danger zones of Ground Zero; config.yml sets their local caps and infected mix. */
public enum Sector {
    REFUGE("Refuge"),
    STARTER("Quartier du refuge"),
    RESIDENTIAL("Quartiers résidentiels"),
    DOWNTOWN("Centre-ville"),
    INDUSTRIAL("Zone industrielle"),
    MEDICAL("Abords de l'hôpital"),
    OUTSKIRTS("Périphérie"),
    COUNTRYSIDE("Campagne"),
    LAB("Complexe NEXUS");

    private final String label;

    Sector(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
