package com.songranker.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the single SQLite connection for the app. The database file lives
 * under the user's home directory so it survives app updates/reinstalls.
 */
public final class Database {

    private static final Path DB_DIR = Paths.get(System.getProperty("user.home"), ".song-ranker");
    private static final Path DB_FILE = DB_DIR.resolve("song-ranker.db");

    private static Path activeDbFile = DB_FILE;
    private static Connection connection;

    private Database() {
    }

    public static synchronized Connection get() {
        if (connection == null) {
            // Opened on first use and kept for the life of the process.
            connection = open();
            migrate(connection);
        }
        return connection;
    }

    private static Connection open() {
        try {
            if (activeDbFile.getParent() != null) {
                Files.createDirectories(activeDbFile.getParent());
            }
            return DriverManager.getConnection("jdbc:sqlite:" + activeDbFile);
        } catch (IOException | SQLException e) {
            throw new RuntimeException("Failed to open database at " + activeDbFile, e);
        }
    }

    /**
     * Test-only hook: points subsequent {@link #get()} calls at a different SQLite file and closes
     * any already-open connection, so each test can run against its own throwaway database instead
     * of the user's real one. Package-private, and never called from application code.
     */
    static synchronized void useDatabaseFileForTesting(Path dbFile) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // best-effort
            }
            connection = null;
        }
        activeDbFile = dbFile;
    }

    // There is no schema version table. Every statement is CREATE ... IF NOT EXISTS,
    // so this can run each time the app starts. Changing an existing table needs an
    // explicit ALTER TABLE added here.
    private static void migrate(Connection conn) {
        String createRuns = """
                CREATE TABLE IF NOT EXISTS ranking_run (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    artist_name TEXT NOT NULL,
                    scope_description TEXT NOT NULL,
                    mode TEXT NOT NULL,
                    seeding_mode TEXT,
                    status TEXT NOT NULL,
                    state_json TEXT NOT NULL,
                    result_json TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """;
        // Long-term per-song ratings (0-10), independent of any single ranking run. A song's
        // rating persists and carries over no matter how many different runs it shows up in.
        String createRatings = """
                CREATE TABLE IF NOT EXISTS track_rating (
                    track_id TEXT PRIMARY KEY,
                    track_name TEXT NOT NULL,
                    album_name TEXT,
                    artist_name TEXT,
                    album_art_url TEXT,
                    rating REAL NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """;
        String createBookmarks = """
                CREATE TABLE IF NOT EXISTS bookmark (
                    album_id TEXT PRIMARY KEY,
                    album_name TEXT NOT NULL,
                    artist_id TEXT NOT NULL,
                    artist_name TEXT NOT NULL,
                    image_url TEXT,
                    release_date TEXT,
                    created_at TEXT NOT NULL
                )
                """;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createBookmarks);
            stmt.execute("CREATE TABLE IF NOT EXISTS track_listened ("
                    + "track_id TEXT PRIMARY KEY, listened_at TEXT NOT NULL)");
            stmt.execute(createRuns);
            // findInProgress() and findHistory() both filter on status.
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ranking_run_status ON ranking_run(status)");
            stmt.execute(createRatings);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to migrate database schema", e);
        }
    }
}
