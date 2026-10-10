package com.songranker.persistence;

import com.songranker.persistence.BookmarkRepository.Bookmark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against a throwaway SQLite file (never the user's real ~/.song-ranker/song-ranker.db). */
class BookmarkRepositoryTest {

    private final BookmarkRepository repo = new BookmarkRepository();

    @BeforeEach
    void useTempDatabase() throws IOException {
        Path dbFile = Files.createTempFile("song-ranker-test-", ".db");
        Files.deleteIfExists(dbFile);
        dbFile.toFile().deleteOnExit();
        Database.useDatabaseFileForTesting(dbFile);
    }

    private static Bookmark bookmark(String id) {
        return new Bookmark(id, "Album " + id, "artist", "Artist", null, "2026-01-01");
    }

    @Test
    void addedBookmarkIsFoundAndListed() {
        repo.add(bookmark("a1"));
        assertTrue(repo.isBookmarked("a1"));
        assertEquals("Album a1", repo.findAll().get(0).albumName());
    }

    @Test
    void addingTheSameAlbumTwiceKeepsOneEntry() {
        repo.add(bookmark("a1"));
        repo.add(bookmark("a1"));
        assertEquals(1, repo.findAll().size());
    }

    @Test
    void removedBookmarkIsGone() {
        repo.add(bookmark("a1"));
        repo.remove("a1");
        assertFalse(repo.isBookmarked("a1"));
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void newestBookmarkComesFirst() {
        repo.add(bookmark("first"));
        repo.add(bookmark("second"));
        List<Bookmark> all = repo.findAll();
        assertEquals("second", all.get(0).albumId());
    }
}
