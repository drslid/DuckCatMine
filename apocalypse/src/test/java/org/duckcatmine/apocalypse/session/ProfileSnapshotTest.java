package org.duckcatmine.apocalypse.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileSnapshotTest {
    @Test
    void roundTripKeepsEveryField() {
        ProfileSnapshot original = new ProfileSnapshot(new byte[]{1, 2, 3, 4}, 12, 0.5f, 340, 17.5, 18, 3.2f, 1.1f, 40, 280,
                List.of(new ProfileSnapshot.Effect("minecraft:speed", 600, 1, false, true, true)),
                "ground_zero", 10.5, 65, -20.5, 90f, 10f, "SURVIVAL");
        ProfileSnapshot copy = ProfileSnapshot.decode(original.encode());
        assertArrayEquals(original.items(), copy.items());
        assertEquals(original.level(), copy.level());
        assertEquals(original.exp(), copy.exp());
        assertEquals(original.totalExperience(), copy.totalExperience());
        assertEquals(original.health(), copy.health());
        assertEquals(original.food(), copy.food());
        assertEquals(original.saturation(), copy.saturation());
        assertEquals(original.exhaustion(), copy.exhaustion());
        assertEquals(original.fireTicks(), copy.fireTicks());
        assertEquals(original.air(), copy.air());
        assertEquals(original.effects(), copy.effects());
        assertEquals(original.world(), copy.world());
        assertEquals(original.x(), copy.x());
        assertEquals(original.y(), copy.y());
        assertEquals(original.z(), copy.z());
        assertEquals(original.yaw(), copy.yaw());
        assertEquals(original.pitch(), copy.pitch());
        assertEquals(original.gameMode(), copy.gameMode());
    }

    @Test
    void unknownFormatIsRejected() {
        assertThrows(UncheckedIOException.class, () -> ProfileSnapshot.decode(new byte[]{0, 0, 0, 9}));
    }
}
