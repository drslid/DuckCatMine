package org.duckcatmine.apocalypse.journal;

import java.util.UUID;

/** One player-scoped transition; {@code seq} is strictly increasing per player. */
public record PlayerOp(UUID player, long seq, OpType type, OpState state, String payload) {
}
