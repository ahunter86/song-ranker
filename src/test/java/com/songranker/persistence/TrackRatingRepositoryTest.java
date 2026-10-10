package com.songranker.persistence;

import com.songranker.domain.Track;
import com.songranker.persistence.TrackRatingRepository.TrackRating;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Runs against a throwaway SQLite file (never the user's real ~/.song-ranker/song-ranker.db). */
class TrackRatingRepositoryTest {

    private final TrackRatingRepository repo = new TrackRatingRepository();

    @BeforeEach
    void useTempDatabase() throws IOException {
        Path dbFile = Files.createTempFile("song-ranker-test-", ".db");
        Files.deleteIfExists(dbFile);
        // SQLite holds this file open (and locked, on Windows) for as long as the connection is
        // live, which outlives this test method. Clean up on JVM exit instead of in @AfterEach.
        dbFile.toFile().deleteOnExit();
        Database.useDatabaseFileForTesting(dbFile);
    }

    private static Track track(String id, String name) {
        return new Track(id, name, "Album", "Artist", 200_000, "uri:" + id, null);
    }

    @Test
    void savedRatingCanBeReadBack() {
        repo.saveRating(track("t1", "Song One"), 7.5);
        assertEquals(7.5, repo.getRating("t1"));
    }

    @Test
    void unratedTrackHasNoRating() {
        assertNull(repo.getRating("nonexistent"));
    }

    @Test
    void ratingAgainOverwritesThePreviousValue() {
        Track t = track("t1", "Song One");
        repo.saveRating(t, 5.0);
        repo.saveRating(t, 9.0);
        assertEquals(9.0, repo.getRating("t1"));
    }

    @Test
    void getRatingsReturnsOnlyTheIdsThatHaveRatings() {
        repo.saveRating(track("t1", "Song One"), 6.0);
        repo.saveRating(track("t2", "Song Two"), 8.0);

        Map<String, Double> ratings = repo.getRatings(List.of("t1", "t2", "t3"));
        assertEquals(6.0, ratings.get("t1"));
        assertEquals(8.0, ratings.get("t2"));
        assertFalse(ratings.containsKey("t3"));
    }

    @Test
    void getAllRatingsIsSortedHighestFirst() {
        repo.saveRating(track("low", "Low Song"), 3.0);
        repo.saveRating(track("high", "High Song"), 9.5);
        repo.saveRating(track("mid", "Mid Song"), 6.0);

        List<TrackRating> all = repo.getAllRatings();
        assertEquals(List.of("high", "mid", "low"), all.stream().map(TrackRating::trackId).toList());
    }
}
