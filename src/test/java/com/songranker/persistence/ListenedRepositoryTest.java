package com.songranker.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against a throwaway SQLite file (never the user's real ~/.song-ranker/song-ranker.db). */
class ListenedRepositoryTest {

    private final ListenedRepository repo = new ListenedRepository();

    @BeforeEach
    void useTempDatabase() throws IOException {
        Path dbFile = Files.createTempFile("song-ranker-test-", ".db");
        Files.deleteIfExists(dbFile);
        dbFile.toFile().deleteOnExit();
        Database.useDatabaseFileForTesting(dbFile);
    }

    @Test
    void onlyPlayedTracksAreReportedAsListened() {
        repo.markListened("t1");
        repo.markListened("t3");
        assertEquals(Set.of("t1", "t3"), repo.getListened(List.of("t1", "t2", "t3")));
    }

    @Test
    void markingTwiceIsHarmless() {
        repo.markListened("t1");
        repo.markListened("t1");
        assertEquals(Set.of("t1"), repo.getListened(List.of("t1")));
    }

    @Test
    void emptyQueryReturnsNothing() {
        assertTrue(repo.getListened(List.of()).isEmpty());
    }
}
