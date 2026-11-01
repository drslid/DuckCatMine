package org.duckcatmine.apocalypse.scenario.city;

import java.util.Locale;

/** Kind of searchable place; each has its own loot table in config.yml. */
public enum PlaceType {
    HOUSE("Logement"),
    SHOP("Commerce"),
    SUPERMARKET("Supermarché"),
    PHARMACY("Pharmacie"),
    HOSPITAL("Hôpital"),
    POLICE("Poste de police"),
    GARAGE("Garage"),
    WAREHOUSE("Entrepôt"),
    GAS_STATION("Station-service"),
    FARM("Ferme"),
    LAB("Laboratoire NEXUS"),
    MILITARY("Barrage militaire");

    private final String label;

    PlaceType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PlaceType byId(String id) {
        for (PlaceType type : values()) if (type.id().equals(id)) return type;
        return null;
    }
}
