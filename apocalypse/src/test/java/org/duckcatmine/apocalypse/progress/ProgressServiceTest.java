package org.duckcatmine.apocalypse.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProgressServiceTest {
    @Test
    void skillsRoundTripAndStayBounded() {
        Map<ProgressService.Skill, Integer> ranks = ProgressService.decode("melee=2,breath=1", 3);
        assertEquals(2, ranks.get(ProgressService.Skill.MELEE));
        assertEquals(1, ranks.get(ProgressService.Skill.BREATH));
        assertEquals(0, ranks.get(ProgressService.Skill.ENDURANCE));
        assertEquals("melee=2,breath=1", ProgressService.encode(ranks));

        Map<ProgressService.Skill, Integer> damaged = ProgressService.decode("melee=9,flight=3,endurance=x,breath", 3);
        assertEquals(3, damaged.get(ProgressService.Skill.MELEE), "a rank never exceeds the configured maximum");
        assertEquals(0, damaged.get(ProgressService.Skill.ENDURANCE));
        assertEquals(0, damaged.get(ProgressService.Skill.BREATH));
        assertEquals("", ProgressService.encode(ProgressService.decode("", 3)));
    }

    @Test
    void spentPointsFollowTheRankCosts() {
        List<Integer> costs = List.of(150, 300, 600);
        assertEquals(0, ProgressService.spent(ProgressService.decode("", 3), costs));
        assertEquals(150 + 300 + 150, ProgressService.spent(ProgressService.decode("melee=2,endurance=1", 3), costs));
        assertEquals(3 * (150 + 300 + 600), ProgressService.spent(ProgressService.decode("melee=3,endurance=3,breath=3", 3), costs));
    }
}
