package org.duckcatmine.apocalypse.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.duckcatmine.apocalypse.death.SlotState;
import org.duckcatmine.apocalypse.journal.OpState;
import org.duckcatmine.apocalypse.journal.OpType;
import org.duckcatmine.apocalypse.journal.PlayerOp;

/** SQL statements; always called inside a {@link Database} transaction. */
public final class Store {
    public record PlayerRow(UUID id, String name, String currentRun, long opSeq, int lives, long bestSurvivalMs, String lastSafe) {
    }

    public record RunRow(String id, UUID player, String state, long startedAt, Long endedAt, String deathId, int kills) {
        public boolean alive() {
            return "ALIVE".equals(state);
        }
    }

    public record InfectionRow(String runId, UUID player, long startedAt, long deadline) {
    }

    public record DeathRow(String id, UUID player, String runId, String cause, String world, double x, double y, double z,
                           long at, String bagId) {
    }

    public record BagRow(String id, String deathId, UUID owner, String ownerName, String world, double x, double y, double z,
                         long createdAt, long expiresAt, String state) {
    }

    public record BagItemRow(String bagId, int slot, byte[] item, SlotState state, Long opSeq) {
    }

    private Store() {
    }

    public static String meta(Connection c, String key) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT value FROM meta WHERE key = ?")) {
            s.setString(1, key);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getString(1) : null;
            }
        }
    }

    public static void putMeta(Connection c, String key, String value) throws SQLException {
        update(c, "INSERT INTO meta(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value", key, value);
    }

    public static void ensurePlayer(Connection c, UUID id, String name, long minimumSeq) throws SQLException {
        update(c, "INSERT INTO players(uuid, name, op_seq) VALUES(?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET"
                + " name = excluded.name, op_seq = MAX(players.op_seq, excluded.op_seq)", id.toString(), name, minimumSeq);
    }

    public static PlayerRow player(Connection c, UUID id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT name, current_run, op_seq, lives, best_survival_ms, last_safe FROM players WHERE uuid = ?")) {
            s.setString(1, id.toString());
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return null;
                return new PlayerRow(id, r.getString(1), r.getString(2), r.getLong(3), r.getInt(4), r.getLong(5), r.getString(6));
            }
        }
    }

    /** Requires {@link #ensurePlayer}; sequences never go back, even after a database restore. */
    public static long nextSeq(Connection c, UUID id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("UPDATE players SET op_seq = op_seq + 1 WHERE uuid = ? RETURNING op_seq")) {
            s.setString(1, id.toString());
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new SQLException("Joueur inconnu " + id);
                return r.getLong(1);
            }
        }
    }

    public static void setLastSafe(Connection c, UUID id, String encoded) throws SQLException {
        update(c, "UPDATE players SET last_safe = ? WHERE uuid = ?", encoded, id.toString());
    }

    public static void recordSurvival(Connection c, UUID id, long survivedMs) throws SQLException {
        update(c, "UPDATE players SET best_survival_ms = MAX(best_survival_ms, ?) WHERE uuid = ?", survivedMs, id.toString());
    }

    /** Idempotent for a given run id: replaying the same transition never creates a second life. */
    public static void createRun(Connection c, UUID player, String runId, String scenario, long now) throws SQLException {
        int inserted = update(c, "INSERT OR IGNORE INTO runs(run_id, uuid, scenario, state, started_sys_ms) VALUES(?, ?, ?, 'ALIVE', ?)",
                runId, player.toString(), scenario, now);
        update(c, "UPDATE players SET current_run = ?, lives = lives + ? WHERE uuid = ?", runId, inserted, player.toString());
    }

    public static RunRow run(Connection c, String runId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT uuid, state, started_sys_ms, ended_sys_ms, death_id, kills FROM runs WHERE run_id = ?")) {
            s.setString(1, runId);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return null;
                long ended = r.getLong(4);
                return new RunRow(runId, UUID.fromString(r.getString(1)), r.getString(2), r.getLong(3),
                        r.wasNull() ? null : ended, r.getString(5), r.getInt(6));
            }
        }
    }

    /** Fails if the life already ended: a second death for one life rolls the whole transaction back. */
    public static void endRun(Connection c, String runId, long now, String deathId) throws SQLException {
        if (update(c, "UPDATE runs SET state = 'DEAD', ended_sys_ms = ?, death_id = ? WHERE run_id = ? AND state = 'ALIVE'", now, deathId, runId) != 1)
            throw new SQLException("La vie " + runId + " est déjà terminée.");
    }

    public static void addKill(Connection c, String runId) throws SQLException {
        update(c, "UPDATE runs SET kills = kills + 1 WHERE run_id = ? AND state = 'ALIVE'", runId);
    }

    public static void insertOp(Connection c, UUID player, long seq, OpType type, String payload, long now) throws SQLException {
        update(c, "INSERT INTO ops(uuid, seq, type, state, payload, created_sys_ms) VALUES(?, ?, ?, 'PENDING', ?, ?)",
                player.toString(), seq, type.name(), payload, now);
    }

    public static void setOpState(Connection c, UUID player, long seq, OpState state) throws SQLException {
        update(c, "UPDATE ops SET state = ? WHERE uuid = ? AND seq = ?", state.name(), player.toString(), seq);
    }

    public static List<PlayerOp> pendingOps(Connection c, UUID player) throws SQLException {
        List<PlayerOp> ops = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("SELECT seq, type, payload FROM ops WHERE uuid = ? AND state = 'PENDING' ORDER BY seq")) {
            s.setString(1, player.toString());
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) ops.add(new PlayerOp(player, r.getLong(1), OpType.valueOf(r.getString(2)), OpState.PENDING, r.getString(3)));
            }
        }
        return ops;
    }

    public static void putProfile(Connection c, UUID player, String kind, String runId, byte[] data, long now, long opSeq) throws SQLException {
        update(c, "INSERT INTO profiles(uuid, kind, run_id, data, saved_sys_ms, op_seq) VALUES(?, ?, ?, ?, ?, ?) ON CONFLICT(uuid, kind)"
                + " DO UPDATE SET run_id = excluded.run_id, data = excluded.data, saved_sys_ms = excluded.saved_sys_ms,"
                + " op_seq = excluded.op_seq", player.toString(), kind, runId, data, now, opSeq);
    }

    public static byte[] profile(Connection c, UUID player, String kind) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT data FROM profiles WHERE uuid = ? AND kind = ?")) {
            s.setString(1, player.toString());
            s.setString(2, kind);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getBytes(1) : null;
            }
        }
    }

    public static void deleteProfile(Connection c, UUID player, String kind) throws SQLException {
        update(c, "DELETE FROM profiles WHERE uuid = ? AND kind = ?", player.toString(), kind);
    }

    public static int countProfiles(Connection c, String kind) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT COUNT(*) FROM profiles WHERE kind = ?")) {
            s.setString(1, kind);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getInt(1) : 0;
            }
        }
    }

    /** Drops the copy written by this operation (rollback). */
    public static void deleteProfileWrittenBy(Connection c, UUID player, String kind, long opSeq) throws SQLException {
        update(c, "DELETE FROM profiles WHERE uuid = ? AND kind = ? AND op_seq = ?", player.toString(), kind, opSeq);
    }

    /** Drops the copy an operation consumed; a copy written by a later operation is kept. */
    public static void deleteProfileBefore(Connection c, UUID player, String kind, long opSeq) throws SQLException {
        update(c, "DELETE FROM profiles WHERE uuid = ? AND kind = ? AND op_seq < ?", player.toString(), kind, opSeq);
    }

    public static void insertInfection(Connection c, String runId, UUID player, long startedAt, long deadline) throws SQLException {
        update(c, "INSERT INTO infections(run_id, uuid, started_sys_ms, deadline_sys_ms, state) VALUES(?, ?, ?, ?, 'ACTIVE')",
                runId, player.toString(), startedAt, deadline);
    }

    public static void updateDeadline(Connection c, String runId, long deadline) throws SQLException {
        update(c, "UPDATE infections SET deadline_sys_ms = ? WHERE run_id = ? AND state = 'ACTIVE'", deadline, runId);
    }

    public static int resolveInfection(Connection c, String runId, String state, long now) throws SQLException {
        return update(c, "UPDATE infections SET state = ?, resolved_sys_ms = ? WHERE run_id = ? AND state = 'ACTIVE'", state, now, runId);
    }

    public static List<InfectionRow> activeInfections(Connection c) throws SQLException {
        List<InfectionRow> rows = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("SELECT run_id, uuid, started_sys_ms, deadline_sys_ms FROM infections WHERE state = 'ACTIVE'");
             ResultSet r = s.executeQuery()) {
            while (r.next()) rows.add(new InfectionRow(r.getString(1), UUID.fromString(r.getString(2)), r.getLong(3), r.getLong(4)));
        }
        return rows;
    }

    public static void insertDeath(Connection c, DeathRow death) throws SQLException {
        update(c, "INSERT INTO deaths(death_id, uuid, run_id, cause, world, x, y, z, at_sys_ms, bag_id) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                death.id(), death.player().toString(), death.runId(), death.cause(), death.world(), death.x(), death.y(), death.z(),
                death.at(), death.bagId());
    }

    public static DeathRow death(Connection c, String deathId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT uuid, run_id, cause, world, x, y, z, at_sys_ms, bag_id FROM deaths WHERE death_id = ?")) {
            s.setString(1, deathId);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return null;
                return new DeathRow(deathId, UUID.fromString(r.getString(1)), r.getString(2), r.getString(3), r.getString(4),
                        r.getDouble(5), r.getDouble(6), r.getDouble(7), r.getLong(8), r.getString(9));
            }
        }
    }

    public static void attachBag(Connection c, String deathId, String bagId) throws SQLException {
        update(c, "UPDATE deaths SET bag_id = ? WHERE death_id = ? AND bag_id IS NULL", bagId, deathId);
    }

    public static void insertBag(Connection c, BagRow bag, List<byte[]> items) throws SQLException {
        update(c, "INSERT INTO bags(bag_id, death_id, owner, owner_name, world, x, y, z, created_sys_ms, expires_sys_ms, state)"
                        + " VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                bag.id(), bag.deathId(), bag.owner().toString(), bag.ownerName(), bag.world(), bag.x(), bag.y(), bag.z(),
                bag.createdAt(), bag.expiresAt());
        for (int slot = 0; slot < items.size(); slot++) {
            update(c, "INSERT INTO bag_items(bag_id, slot, item, state) VALUES(?, ?, ?, 'PRESENT')", bag.id(), slot, items.get(slot));
        }
    }

    public static List<BagRow> activeBags(Connection c) throws SQLException {
        List<BagRow> rows = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("SELECT bag_id, death_id, owner, owner_name, world, x, y, z, created_sys_ms,"
                + " expires_sys_ms, state FROM bags WHERE state = 'ACTIVE'"); ResultSet r = s.executeQuery()) {
            while (r.next()) rows.add(bagRow(r));
        }
        return rows;
    }

    public static BagRow bag(Connection c, String bagId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT bag_id, death_id, owner, owner_name, world, x, y, z, created_sys_ms,"
                + " expires_sys_ms, state FROM bags WHERE bag_id = ?")) {
            s.setString(1, bagId);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? bagRow(r) : null;
            }
        }
    }

    private static BagRow bagRow(ResultSet r) throws SQLException {
        return new BagRow(r.getString(1), r.getString(2), UUID.fromString(r.getString(3)), r.getString(4), r.getString(5),
                r.getDouble(6), r.getDouble(7), r.getDouble(8), r.getLong(9), r.getLong(10), r.getString(11));
    }

    public static List<BagItemRow> bagItems(Connection c, String bagId) throws SQLException {
        List<BagItemRow> rows = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("SELECT slot, item, state, op_seq FROM bag_items WHERE bag_id = ? ORDER BY slot")) {
            s.setString(1, bagId);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    long seq = r.getLong(4);
                    rows.add(new BagItemRow(bagId, r.getInt(1), r.getBytes(2), SlotState.valueOf(r.getString(3)), r.wasNull() ? null : seq));
                }
            }
        }
        return rows;
    }

    /** Guarded transition: returns false when the slot is not in the expected state. */
    public static boolean moveBagItem(Connection c, String bagId, int slot, SlotState from, SlotState to, Long opSeq) throws SQLException {
        return update(c, "UPDATE bag_items SET state = ?, op_seq = ? WHERE bag_id = ? AND slot = ? AND state = ?",
                to.name(), opSeq, bagId, slot, from.name()) == 1;
    }

    /** Purges what is still present; in-flight withdrawals stay for reconciliation. */
    public static void expireBag(Connection c, String bagId) throws SQLException {
        update(c, "UPDATE bags SET state = 'EXPIRED' WHERE bag_id = ? AND state = 'ACTIVE'", bagId);
        update(c, "UPDATE bag_items SET state = 'PURGED' WHERE bag_id = ? AND state = 'PRESENT'", bagId);
    }

    public static void settleBag(Connection c, String bagId) throws SQLException {
        update(c, "UPDATE bags SET state = 'EMPTY' WHERE bag_id = ? AND state = 'ACTIVE' AND NOT EXISTS"
                + " (SELECT 1 FROM bag_items WHERE bag_id = ? AND state IN ('PRESENT', 'MOVING'))", bagId, bagId);
    }

    public static void audit(Connection c, long sysNow, String actor, String action, String detail) throws SQLException {
        update(c, "INSERT INTO audit(at_sys_ms, at_wall_ms, actor, action, detail) VALUES(?, ?, ?, ?, ?)",
                sysNow, System.currentTimeMillis(), actor, action, detail);
    }

    // ------------------------------------------------------------------ teams

    public record TeamRow(String id, String name, UUID leader, long createdAt, boolean bagAccess) {
    }

    public static List<TeamRow> teams(Connection c) throws SQLException {
        List<TeamRow> teams = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("SELECT team_id, name, leader, created_sys_ms, bag_access FROM teams");
             ResultSet r = s.executeQuery()) {
            while (r.next()) teams.add(new TeamRow(r.getString(1), r.getString(2), UUID.fromString(r.getString(3)), r.getLong(4), r.getInt(5) != 0));
        }
        return teams;
    }

    /** Member to team id. */
    public static java.util.Map<UUID, String> teamMembers(Connection c) throws SQLException {
        java.util.Map<UUID, String> members = new java.util.LinkedHashMap<>();
        try (PreparedStatement s = c.prepareStatement("SELECT uuid, team_id FROM team_members ORDER BY joined_sys_ms"); ResultSet r = s.executeQuery()) {
            while (r.next()) members.put(UUID.fromString(r.getString(1)), r.getString(2));
        }
        return members;
    }

    public static void insertTeam(Connection c, TeamRow team) throws SQLException {
        update(c, "INSERT INTO teams(team_id, name, leader, created_sys_ms, bag_access) VALUES(?, ?, ?, ?, ?)",
                team.id(), team.name(), team.leader().toString(), team.createdAt(), team.bagAccess() ? 1 : 0);
    }

    public static void deleteTeam(Connection c, String teamId) throws SQLException {
        update(c, "DELETE FROM team_members WHERE team_id = ?", teamId);
        update(c, "DELETE FROM teams WHERE team_id = ?", teamId);
    }

    public static void setTeamLeader(Connection c, String teamId, UUID leader) throws SQLException {
        update(c, "UPDATE teams SET leader = ? WHERE team_id = ?", leader.toString(), teamId);
    }

    public static void setTeamBagAccess(Connection c, String teamId, boolean value) throws SQLException {
        update(c, "UPDATE teams SET bag_access = ? WHERE team_id = ?", value ? 1 : 0, teamId);
    }

    /** Fails if the player already belongs to a team (primary key). */
    public static void addTeamMember(Connection c, UUID player, String teamId, long now) throws SQLException {
        update(c, "INSERT INTO team_members(uuid, team_id, joined_sys_ms) VALUES(?, ?, ?)", player.toString(), teamId, now);
    }

    public static void removeTeamMember(Connection c, UUID player) throws SQLException {
        update(c, "DELETE FROM team_members WHERE uuid = ?", player.toString());
    }

    public static String playerName(Connection c, UUID id) throws SQLException {
        PlayerRow row = player(c, id);
        return row == null ? null : row.name();
    }

    // ------------------------------------------------------------------ progression

    public record Progress(int points, String skills, int kills, long startedAt, boolean alive) {
    }

    public record Career(int lives, long bestSurvivalMs, long careerPoints) {
    }

    public static Progress progress(Connection c, String runId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT points, skills, kills, started_sys_ms, state FROM runs WHERE run_id = ?")) {
            s.setString(1, runId);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? new Progress(r.getInt(1), r.getString(2), r.getInt(3), r.getLong(4), "ALIVE".equals(r.getString(5))) : null;
            }
        }
    }

    /** Points go to a living life only; the career total keeps them as recognition. */
    public static void addPoints(Connection c, String runId, UUID player, int points) throws SQLException {
        if (update(c, "UPDATE runs SET points = points + ? WHERE run_id = ? AND state = 'ALIVE'", points, runId) == 1)
            update(c, "UPDATE players SET career_points = career_points + ? WHERE uuid = ?", points, player.toString());
    }

    public static boolean setSkills(Connection c, String runId, String skills) throws SQLException {
        return update(c, "UPDATE runs SET skills = ? WHERE run_id = ? AND state = 'ALIVE'", skills, runId) == 1;
    }

    public static Career career(Connection c, UUID player) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT lives, best_survival_ms, career_points FROM players WHERE uuid = ?")) {
            s.setString(1, player.toString());
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? new Career(r.getInt(1), r.getLong(2), r.getLong(3)) : new Career(0, 0, 0);
            }
        }
    }

    public static boolean introSeen(Connection c, UUID player) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT intro_seen FROM players WHERE uuid = ?")) {
            s.setString(1, player.toString());
            try (ResultSet r = s.executeQuery()) {
                return r.next() && r.getInt(1) != 0;
            }
        }
    }

    public static void setIntroSeen(Connection c, UUID player) throws SQLException {
        update(c, "UPDATE players SET intro_seen = 1 WHERE uuid = ?", player.toString());
    }

    public static List<RunRow> aliveRuns(Connection c) throws SQLException {
        List<RunRow> runs = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("SELECT run_id, uuid, started_sys_ms, kills FROM runs WHERE state = 'ALIVE'");
             ResultSet r = s.executeQuery()) {
            while (r.next()) runs.add(new RunRow(r.getString(1), UUID.fromString(r.getString(2)), "ALIVE", r.getLong(3), null, null, r.getInt(4)));
        }
        return runs;
    }

    /** One line of the board: the living lives first, by points, then the best careers. */
    public record Ranking(String name, int points, int kills, long startedAt, boolean alive, long careerPoints, long bestSurvivalMs) {
    }

    public static List<Ranking> leaderboard(Connection c, int limit) throws SQLException {
        List<Ranking> rows = new ArrayList<>();
        try (PreparedStatement s = c.prepareStatement("SELECT p.name, COALESCE(r.points, 0), COALESCE(r.kills, 0), COALESCE(r.started_sys_ms, 0),"
                + " r.state = 'ALIVE', p.career_points, p.best_survival_ms FROM players p LEFT JOIN runs r ON r.run_id = p.current_run"
                + " ORDER BY (r.state = 'ALIVE') DESC, COALESCE(r.points, 0) DESC, p.career_points DESC LIMIT ?")) {
            s.setInt(1, limit);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) rows.add(new Ranking(r.getString(1), r.getInt(2), r.getInt(3), r.getLong(4), r.getBoolean(5), r.getLong(6), r.getLong(7)));
            }
        }
        return rows;
    }

    private static int update(Connection c, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            return statement.executeUpdate();
        }
    }
}
