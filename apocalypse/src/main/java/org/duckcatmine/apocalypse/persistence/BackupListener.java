package org.duckcatmine.apocalypse.persistence;

import java.nio.file.Path;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * DuckCatMine backups run save-off, save-all, archive, save-on over RCON. The snapshot is taken during
 * save-all, so the archived world and the archived scenario database describe the same instant.
 */
public final class BackupListener implements Listener {
    private final Database database;
    private final Path snapshot;
    private final Logger logger;

    public BackupListener(Database database, Path snapshot, Logger logger) {
        this.database = database;
        this.snapshot = snapshot;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRemote(RemoteServerCommandEvent event) {
        handle(event.getCommand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsole(ServerCommandEvent event) {
        handle(event.getCommand());
    }

    private void handle(String command) {
        String normalized = command.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (!normalized.equals("save-all") && !normalized.startsWith("save-all ")) return;
        try {
            database.snapshotTo(snapshot);
            logger.info("Instantané de la base du scénario écrit pour la sauvegarde.");
        } catch (PersistenceException e) {
            logger.log(Level.SEVERE, "Instantané de la base du scénario impossible : cette sauvegarde ne contiendra pas l'état Apocalypse", e);
        }
    }
}
