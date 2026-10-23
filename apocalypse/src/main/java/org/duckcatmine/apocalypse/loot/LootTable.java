package org.duckcatmine.apocalypse.loot;

import java.util.List;

public record LootTable(List<Entry> entries) {
    public record Entry(String item, double weight, int amount) {
    }

    public LootTable {
        entries = List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public double totalWeight() {
        return entries.stream().mapToDouble(Entry::weight).sum();
    }

    /** @param roll uniform value in [0, 1) */
    public Entry pick(double roll) {
        if (entries.isEmpty()) throw new IllegalStateException("Empty loot table");
        double target = roll * totalWeight();
        double cumulated = 0;
        for (Entry entry : entries) {
            cumulated += entry.weight();
            if (target < cumulated) return entry;
        }
        return entries.getLast();
    }

    public double chance(Entry entry) {
        return entry.weight() / totalWeight();
    }
}
