package org.duckcatmine.apocalypse.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.duckcatmine.apocalypse.death.SlotState;
import org.duckcatmine.apocalypse.journal.OpState;
import org.duckcatmine.apocalypse.journal.OpType;
import org.duckcatmine.apocalypse.journal.PlayerOp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoreTest {
    private static final Logger LOG = Logger.getLogger("test");

    @TempDir
    Path dir;
    private Database db;
    private final UUID alex = UUID.randomUUID();

    @BeforeEach
    void open() {
        db = Database.open(dir.resolve("apocalypse.db"), LOG);
    }

    @AfterEach
    void close() {
        db.close();
    }

    private String life() {
        String run = UUID.randomUUID().toString();
        db.call(c -> {
            Store.ensurePlayer(c, alex, "Alex", 0);
            Store.createRun(c, alex, run, "ground_zero", 1_000);
            return null;
        });
        return run;
    }

    private void die(String run, String deathId) {
        db.call(c -> {
            Store.insertDeath(c, new Store.DeathRow(deathId, alex, run, "TEST", "ground_zero", 1, 65, 2, 5_000, null));
            Store.endRun(c, run, 5_000, deathId);
            return null;
        });
    }

    @Test
    void migrationRunsOnceAndReopens() {
        db.close();
        db = Database.open(dir.resolve("apocalypse.db"), LOG);
        assertEquals(Integer.toString(Schema.VERSION), db.call(c -> Store.meta(c, "schema_version")));
    }

    @Test
    void sequencesNeverGoBackBelowThePlayerFile() {
        long first = db.call(c -> {
            Store.ensurePlayer(c, alex, "Alex", 0);
            return Store.nextSeq(c, alex);
        });
        assertEquals(1, first);
        long afterDatabaseLoss = db.call(c -> {
            Store.ensurePlayer(c, alex, "Alex", 57);
            return Store.nextSeq(c, alex);
        });
        assertEquals(58, afterDatabaseLoss);
    }

    @Test
    void replayingANewLifeIsIdempotent() {
        String run = "run-from-op-12";
        for (int i = 0; i < 2; i++) {
            db.call(c -> {
                Store.ensurePlayer(c, alex, "Alex", 0);
                Store.createRun(c, alex, run, "ground_zero", 1_000);
                return null;
            });
        }
        Store.PlayerRow row = db.call(c -> Store.player(c, alex));
        assertEquals(run, row.currentRun());
        assertEquals(1, row.lives());
    }

    @Test
    void aLifeEndsExactlyOnce() {
        String run = life();
        die(run, "death-1");
        assertThrows(PersistenceException.class, () -> die(run, "death-2"));
        assertNull(db.call(c -> Store.death(c, "death-2")), "the whole second transaction was rolled back");
        assertEquals("DEAD", db.call(c -> Store.run(c, run)).state());
    }

    @Test
    void aFailedTransactionLeavesNothingAndHealthRecovers() {
        assertThrows(PersistenceException.class, () -> db.call(c -> {
            Store.putMeta(c, "partial", "1");
            throw new SQLException("boom");
        }));
        assertFalse(db.healthy());
        assertNull(db.call(c -> Store.meta(c, "partial")));
        assertTrue(db.healthy());
    }

    @Test
    void anAbandonedCallNeverCommitsLater() throws Exception {
        db.close();
        db = Database.open(dir.resolve("apocalypse.db"), LOG, 100);
        assertThrows(PersistenceException.class, () -> db.call(c -> {
            Store.putMeta(c, "slow", "1");
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        Thread.sleep(600);
        assertNull(db.call(c -> Store.meta(c, "slow")));
    }

    @Test
    void oneActiveInfectionPerLife() {
        String run = life();
        db.call(c -> {
            Store.insertInfection(c, run, alex, 1_000, 61_000);
            return null;
        });
        assertThrows(PersistenceException.class, () -> db.call(c -> {
            Store.insertInfection(c, run, alex, 2_000, 62_000);
            return null;
        }));
        int cured = db.call(c -> Store.resolveInfection(c, run, "CURED", 30_000));
        int curedAgain = db.call(c -> Store.resolveInfection(c, run, "CURED", 31_000));
        assertEquals(1, cured);
        assertEquals(0, curedAgain, "a second dose finds nothing to cure");
        db.call(c -> {
            Store.insertInfection(c, run, alex, 40_000, 100_000);
            return null;
        });
        assertEquals(1, db.call(Store::activeInfections).size(), "a later bite starts a new countdown");
    }

    @Test
    void bagWithdrawalsAreGuardedAndExpiryKeepsInFlightItems() {
        String run = life();
        die(run, "death-1");
        Store.BagRow bag = new Store.BagRow("bag-1", "death-1", alex, "Alex", "ground_zero", 1, 65, 2, 5_000, 305_000, "ACTIVE");
        db.call(c -> {
            Store.insertBag(c, bag, List.of(new byte[]{1}, new byte[]{2}, new byte[]{3}));
            return null;
        });
        boolean first = db.call(c -> Store.moveBagItem(c, "bag-1", 0, SlotState.PRESENT, SlotState.MOVING, 4L));
        boolean doubleClick = db.call(c -> Store.moveBagItem(c, "bag-1", 0, SlotState.PRESENT, SlotState.MOVING, 5L));
        assertTrue(first);
        assertFalse(doubleClick);
        db.call(c -> {
            Store.expireBag(c, "bag-1");
            Store.settleBag(c, "bag-1");
            return null;
        });
        List<Store.BagItemRow> items = db.call(c -> Store.bagItems(c, "bag-1"));
        assertEquals(SlotState.MOVING, items.get(0).state());
        assertEquals(SlotState.PURGED, items.get(1).state());
        assertEquals(SlotState.PURGED, items.get(2).state());
        assertEquals("EXPIRED", db.call(c -> Store.bag(c, "bag-1")).state());
        assertTrue(db.call(Store::activeBags).isEmpty());
    }

    @Test
    void anEmptiedBagIsSettled() {
        String run = life();
        die(run, "death-1");
        db.call(c -> {
            Store.insertBag(c, new Store.BagRow("bag-1", "death-1", alex, "Alex", "ground_zero", 1, 65, 2, 5_000, 305_000, "ACTIVE"),
                    List.of(new byte[]{1}));
            Store.moveBagItem(c, "bag-1", 0, SlotState.PRESENT, SlotState.MOVING, 3L);
            Store.settleBag(c, "bag-1");
            return null;
        });
        assertEquals("ACTIVE", db.call(c -> Store.bag(c, "bag-1")).state(), "an in-flight item keeps the bag open");
        db.call(c -> {
            Store.moveBagItem(c, "bag-1", 0, SlotState.MOVING, SlotState.TAKEN, 3L);
            Store.settleBag(c, "bag-1");
            return null;
        });
        assertEquals("EMPTY", db.call(c -> Store.bag(c, "bag-1")).state());
    }

    @Test
    void profileCleanupNeverDropsANewerCopy() {
        db.call(c -> {
            Store.putProfile(c, alex, "CLASSIC", null, new byte[]{1}, 1_000, 3);
            Store.deleteProfileBefore(c, alex, "CLASSIC", 3);
            return null;
        });
        assertTrue(db.call(c -> Store.profile(c, alex, "CLASSIC")) != null, "written by the operation being confirmed");
        db.call(c -> {
            Store.deleteProfileBefore(c, alex, "CLASSIC", 4);
            Store.putProfile(c, alex, "APOCALYPSE", "run", new byte[]{2}, 2_000, 5);
            Store.deleteProfileWrittenBy(c, alex, "APOCALYPSE", 4);
            return null;
        });
        assertNull(db.call(c -> Store.profile(c, alex, "CLASSIC")));
        assertTrue(db.call(c -> Store.profile(c, alex, "APOCALYPSE")) != null);
    }

    @Test
    void snapshotIsConsistentAndPromotedWhenTheLiveBaseIsMissing() throws Exception {
        String run = life();
        Path live = dir.resolve("apocalypse.db");
        Path snapshot = Database.snapshotPath(live);
        db.snapshotTo(snapshot);
        db.call(c -> {
            Store.putMeta(c, "after_snapshot", "1");
            return null;
        });
        db.close();
        for (String suffix : new String[]{"", "-wal", "-shm"}) java.nio.file.Files.deleteIfExists(dir.resolve("apocalypse.db" + suffix));
        db = Database.open(live, LOG);
        assertEquals(run, db.call(c -> Store.player(c, alex)).currentRun());
        assertNull(db.call(c -> Store.meta(c, "after_snapshot")), "the restored base matches the archived instant");
    }

    @Test
    void pendingOperationsComeBackInOrder() {
        db.call(c -> {
            Store.ensurePlayer(c, alex, "Alex", 0);
            Store.insertOp(c, alex, 2, OpType.BAG_TAKE, "bag:1", 10);
            Store.insertOp(c, alex, 1, OpType.ENTER, null, 5);
            Store.insertOp(c, alex, 3, OpType.LEAVE, null, 20);
            Store.setOpState(c, alex, 3, OpState.DONE);
            return null;
        });
        List<PlayerOp> pending = db.call(c -> Store.pendingOps(c, alex));
        assertEquals(List.of(1L, 2L), pending.stream().map(PlayerOp::seq).toList());
        assertEquals(OpType.BAG_TAKE, pending.get(1).type());
        assertEquals("bag:1", pending.get(1).payload());
    }
}
