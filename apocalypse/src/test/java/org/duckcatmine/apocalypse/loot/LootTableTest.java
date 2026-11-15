package org.duckcatmine.apocalypse.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class LootTableTest {
    private final LootTable guaranteed = new LootTable(List.of(
            new LootTable.Entry("apocalypse:scrap", 50, 1), new LootTable.Entry("apocalypse:cloth", 50, 1)));
    private final LootTable bonus = new LootTable(List.of(
            new LootTable.Entry("minecraft:bread", 65, 1), new LootTable.Entry("apocalypse:antidote", 35, 1)));

    private static RandomGenerator sequence(double... values) {
        return new RandomGenerator() {
            private int index;

            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public double nextDouble() {
                return values[index++];
            }
        };
    }

    @Test
    void picksFollowCumulativeWeights() {
        assertEquals("apocalypse:scrap", guaranteed.pick(0.0).item());
        assertEquals("apocalypse:scrap", guaranteed.pick(0.499).item());
        assertEquals("apocalypse:cloth", guaranteed.pick(0.5).item());
        assertEquals("apocalypse:cloth", guaranteed.pick(0.999_999).item());
        assertEquals(0.65, bonus.chance(bonus.entries().getFirst()), 1e-9);
    }

    @Test
    void bonusIsAnIndependentSecondRoll() {
        assertEquals(1, LootRules.roll(guaranteed, bonus, 0.10, sequence(0.2, 0.95)).size());
        List<LootTable.Entry> both = LootRules.roll(guaranteed, bonus, 0.10, sequence(0.7, 0.05, 0.9));
        assertEquals(List.of("apocalypse:cloth", "apocalypse:antidote"), both.stream().map(LootTable.Entry::item).toList());
    }

    @Test
    void everyEligibleKillDropsAtLeastOneItem() {
        Random random = new Random(13);
        int bonuses = 0;
        int kills = 20_000;
        for (int i = 0; i < kills; i++) {
            List<LootTable.Entry> drops = LootRules.roll(guaranteed, bonus, 0.10, random);
            assertFalse(drops.isEmpty());
            if (drops.size() == 2) bonuses++;
        }
        double rate = bonuses / (double) kills;
        assertTrue(rate > 0.09 && rate < 0.11, "bonus rate " + rate);
    }

    @Test
    void noBonusTableMeansOnlyTheGuaranteedItem() {
        assertEquals(1, LootRules.roll(guaranteed, new LootTable(List.of()), 1.0, sequence(0.1, 0.0)).size());
    }
}
