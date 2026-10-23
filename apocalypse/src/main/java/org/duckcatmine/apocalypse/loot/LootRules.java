package org.duckcatmine.apocalypse.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class LootRules {
    private LootRules() {
    }

    /** One guaranteed entry for every eligible kill, plus an independent bonus roll. */
    public static List<LootTable.Entry> roll(LootTable guaranteed, LootTable bonus, double bonusChance, RandomGenerator random) {
        List<LootTable.Entry> drops = new ArrayList<>(2);
        drops.add(guaranteed.pick(random.nextDouble()));
        if (!bonus.isEmpty() && random.nextDouble() < bonusChance) drops.add(bonus.pick(random.nextDouble()));
        return drops;
    }
}
