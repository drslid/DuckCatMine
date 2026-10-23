package org.duckcatmine.apocalypse.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class Schema {
    static final int VERSION = 1;

    private static final String[] V1 = {
            "CREATE TABLE players (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, current_run TEXT,"
                    + " op_seq INTEGER NOT NULL DEFAULT 0, lives INTEGER NOT NULL DEFAULT 0,"
                    + " best_survival_ms INTEGER NOT NULL DEFAULT 0, last_safe TEXT)",
            "CREATE TABLE runs (run_id TEXT PRIMARY KEY, uuid TEXT NOT NULL, scenario TEXT NOT NULL,"
                    + " state TEXT NOT NULL CHECK (state IN ('ALIVE','DEAD')), started_sys_ms INTEGER NOT NULL,"
                    + " ended_sys_ms INTEGER, death_id TEXT, kills INTEGER NOT NULL DEFAULT 0)",
            "CREATE INDEX runs_player ON runs(uuid)",
            "CREATE TABLE ops (uuid TEXT NOT NULL, seq INTEGER NOT NULL, type TEXT NOT NULL,"
                    + " state TEXT NOT NULL CHECK (state IN ('PENDING','DONE','ROLLED_BACK')), payload TEXT,"
                    + " created_sys_ms INTEGER NOT NULL, PRIMARY KEY (uuid, seq))",
            "CREATE INDEX ops_pending ON ops(uuid) WHERE state = 'PENDING'",
            "CREATE TABLE profiles (uuid TEXT NOT NULL, kind TEXT NOT NULL CHECK (kind IN ('CLASSIC','APOCALYPSE')),"
                    + " run_id TEXT, data BLOB NOT NULL, saved_sys_ms INTEGER NOT NULL, op_seq INTEGER NOT NULL,"
                    + " PRIMARY KEY (uuid, kind))",
            "CREATE TABLE infections (infection_id INTEGER PRIMARY KEY AUTOINCREMENT, run_id TEXT NOT NULL,"
                    + " uuid TEXT NOT NULL, started_sys_ms INTEGER NOT NULL, deadline_sys_ms INTEGER NOT NULL,"
                    + " state TEXT NOT NULL CHECK (state IN ('ACTIVE','CURED','FATAL','ENDED')), resolved_sys_ms INTEGER)",
            "CREATE UNIQUE INDEX infections_one_active ON infections(run_id) WHERE state = 'ACTIVE'",
            "CREATE TABLE deaths (death_id TEXT PRIMARY KEY, uuid TEXT NOT NULL, run_id TEXT NOT NULL UNIQUE,"
                    + " cause TEXT NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,"
                    + " at_sys_ms INTEGER NOT NULL, bag_id TEXT)",
            "CREATE TABLE bags (bag_id TEXT PRIMARY KEY, death_id TEXT NOT NULL UNIQUE REFERENCES deaths(death_id),"
                    + " owner TEXT NOT NULL, owner_name TEXT NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL,"
                    + " z REAL NOT NULL, created_sys_ms INTEGER NOT NULL, expires_sys_ms INTEGER NOT NULL,"
                    + " state TEXT NOT NULL CHECK (state IN ('ACTIVE','EMPTY','EXPIRED')))",
            "CREATE TABLE bag_items (bag_id TEXT NOT NULL REFERENCES bags(bag_id), slot INTEGER NOT NULL, item BLOB NOT NULL,"
                    + " state TEXT NOT NULL CHECK (state IN ('PRESENT','MOVING','TAKEN','PURGED')), op_seq INTEGER,"
                    + " PRIMARY KEY (bag_id, slot))",
            "CREATE TABLE audit (id INTEGER PRIMARY KEY AUTOINCREMENT, at_sys_ms INTEGER NOT NULL, at_wall_ms INTEGER NOT NULL,"
                    + " actor TEXT NOT NULL, action TEXT NOT NULL, detail TEXT)",
    };

    private Schema() {
    }

    static Void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
        String stored = Store.meta(connection, "schema_version");
        int current = stored == null ? 0 : Integer.parseInt(stored);
        if (current > VERSION) throw new SQLException("Base créée par une version plus récente du plugin (schéma " + current + ").");
        if (current < 1) {
            try (Statement statement = connection.createStatement()) {
                for (String sql : V1) statement.execute(sql);
            }
        }
        Store.putMeta(connection, "schema_version", Integer.toString(VERSION));
        return null;
    }
}
