package org.duckcatmine.apocalypse.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One SQLite connection owned by one thread. Every unit of work is a transaction.
 * A blocking call that times out is either abandoned before commit or awaited until its
 * commit finishes, so callers always know the real outcome.
 */
public final class Database implements AutoCloseable {
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private static final int WAITING = 0, COMMITTING = 1, ABANDONED = 2;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "DuckCatApocalypse-db");
        thread.setDaemon(true);
        return thread;
    });
    private final Logger logger;
    private final long timeoutMs;
    private Connection connection;
    private volatile boolean healthy = true;

    private Database(Logger logger, long timeoutMs) {
        this.logger = logger;
        this.timeoutMs = timeoutMs;
    }

    public static Database open(Path file, Logger logger) {
        return open(file, logger, 5000);
    }

    public static Database open(Path file, Logger logger, long timeoutMs) {
        Database database = new Database(logger, timeoutMs);
        try {
            database.executor.submit(() -> {
                database.connect(file);
                return null;
            }).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            database.executor.shutdownNow();
            throw new PersistenceException("Ouverture de la base interrompue", e);
        } catch (ExecutionException | TimeoutException e) {
            database.executor.shutdownNow();
            throw new PersistenceException("Ouverture de la base impossible", e.getCause() == null ? e : e.getCause());
        }
        database.call(Schema::migrate);
        return database;
    }

    private void connect(Path file) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Pilote SQLite absent du serveur", e);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        connection.setAutoCommit(false);
    }

    public boolean healthy() {
        return healthy;
    }

    /** Blocks the caller until the transaction is committed or definitely not committed. */
    public <T> T call(Work<T> work) {
        AtomicInteger phase = new AtomicInteger(WAITING);
        Future<T> future = executor.submit(() -> {
            if (phase.get() == ABANDONED) throw new CancellationException("abandoned");
            try {
                T result = work.run(connection);
                if (!phase.compareAndSet(WAITING, COMMITTING)) {
                    connection.rollback();
                    throw new CancellationException("abandoned");
                }
                connection.commit();
                healthy = true;
                return result;
            } catch (Throwable error) {
                rollbackQuietly();
                throw error;
            }
        });
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            if (phase.compareAndSet(WAITING, ABANDONED)) throw failure("Base indisponible (délai dépassé)", e);
            return awaitCommit(future);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (phase.compareAndSet(WAITING, ABANDONED)) throw failure("Attente de la base interrompue", e);
            return awaitCommit(future);
        } catch (ExecutionException e) {
            throw failure("Erreur de base de données", e.getCause());
        }
    }

    private <T> T awaitCommit(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure("Attente de la base interrompue pendant une validation", e);
        } catch (ExecutionException e) {
            throw failure("Erreur de base de données", e.getCause());
        }
    }

    public <T> CompletableFuture<T> submit(Work<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                T result = work.run(connection);
                connection.commit();
                healthy = true;
                return result;
            } catch (SQLException | RuntimeException error) {
                rollbackQuietly();
                throw failure("Écriture différée refusée", error);
            }
        }, executor).whenComplete((value, error) -> {
            if (error != null) logger.log(Level.SEVERE, "Écriture du scénario impossible", error);
        });
    }

    private PersistenceException failure(String message, Throwable cause) {
        healthy = false;
        return cause instanceof PersistenceException known ? known : new PersistenceException(message, cause);
    }

    private void rollbackQuietly() {
        try {
            if (connection != null) connection.rollback();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Annulation de transaction impossible", e);
        }
    }

    @Override
    public void close() {
        Future<?> closing = executor.submit(() -> {
            if (connection != null) connection.close();
            return null;
        });
        executor.shutdown();
        try {
            closing.get(10, TimeUnit.SECONDS);
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            logger.log(Level.WARNING, "Fermeture de la base incomplète", e);
        }
    }
}
