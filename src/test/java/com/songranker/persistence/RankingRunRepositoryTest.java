package com.songranker.persistence;

import com.songranker.domain.Track;
import com.songranker.persistence.RankingRunRepository.AlbumChampion;
import com.songranker.persistence.RankingRunRepository.RunStats;
import com.songranker.persistence.RankingRunRepository.SavedRunSummary;
import com.songranker.persistence.state.RankingRunState;
import com.songranker.persistence.state.SavedTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against a throwaway SQLite file for every test method (never the user's real
 * ~/.song-ranker/song-ranker.db), via {@link Database#useDatabaseFileForTesting}.
 */
class RankingRunRepositoryTest {

    private final RankingRunRepository repo = new RankingRunRepository();

    @BeforeEach
    void useTempDatabase() throws IOException {
        Path dbFile = Files.createTempFile("song-ranker-test-", ".db");
        Files.deleteIfExists(dbFile); // let SQLite create it fresh on first connection
        // SQLite holds this file open (and locked, on Windows) for as long as the connection is
        // live, which outlives this test method. Clean up on JVM exit instead of in @AfterEach.
        dbFile.toFile().deleteOnExit();
        Database.useDatabaseFileForTesting(dbFile);
    }

    private static Track track(int i) {
        return new Track("id" + i, "Track " + i, "Album " + i, "Artist", 200_000, "spotify:track:id" + i, null);
    }

    private static RankingRunState state(String mode, List<Track> tracks) {
        RankingRunState state = new RankingRunState();
        state.mode = mode;
        state.seedingMode = "ORIGINAL_ORDER";
        state.tracks = tracks.stream().map(SavedTrack::fromDomain).toList();
        return state;
    }

    @Test
    void savedRunCanBeLoadedBackById() {
        List<Track> tracks = List.of(track(1), track(2));
        long runId = repo.saveNew("Artist", "Entire discography", state("BRACKET", tracks));

        RankingRunState loaded = repo.loadState(runId);
        assertEquals("BRACKET", loaded.mode);
        assertEquals(2, loaded.tracks.size());
    }

    @Test
    void newRunShowsUpInProgressNotHistory() {
        long runId = repo.saveNew("Artist", "Scope", state("BRACKET", List.of(track(1), track(2))));

        List<SavedRunSummary> inProgress = repo.findInProgress();
        assertEquals(1, inProgress.size());
        assertEquals(runId, inProgress.get(0).id());
        assertTrue(repo.findHistory().isEmpty());
    }

    @Test
    void completingARunMovesItToHistoryAndStoresTheResultInOrder() {
        List<Track> tracks = List.of(track(1), track(2));
        long runId = repo.saveNew("Artist", "Scope", state("BRACKET", tracks));

        repo.markComplete(runId, state("BRACKET", tracks), List.of(tracks.get(1), tracks.get(0)));

        assertTrue(repo.findInProgress().isEmpty());
        assertEquals(1, repo.findHistory().size());

        List<Track> result = repo.loadResultTracks(runId);
        assertEquals(List.of("id2", "id1"), result.stream().map(Track::getId).toList());
    }

    @Test
    void deletedRunNoLongerAppearsAnywhere() {
        long runId = repo.saveNew("Artist", "Scope", state("BRACKET", List.of(track(1), track(2))));
        repo.delete(runId);
        assertTrue(repo.findInProgress().isEmpty());
        assertThrows(RuntimeException.class, () -> repo.loadState(runId));
    }

    @Test
    void statsAggregateAcrossRunsOfDifferentModesAndStatuses() {
        RankingRunState bracket = state("BRACKET", List.of(track(1), track(2), track(3)));
        bracket.winnerChoicesInOrder = List.of("id1", "id2");
        long bracketRunId = repo.saveNew("Artist A", "Album X", bracket);
        repo.markComplete(bracketRunId, bracket, List.of(track(1), track(2), track(3)));

        repo.saveNew("Artist A", "Album Y", state("SWISS", List.of(track(4), track(5)))); // stays in progress
        repo.saveNew("Artist B", "Single Z", state("RATING", List.of(track(6)))); // stays in progress

        RunStats stats = repo.getStats();
        assertEquals(3, stats.totalRuns());
        assertEquals(1, stats.completedRuns());
        assertEquals(2, stats.inProgressRuns());
        assertEquals(1, stats.bracketRuns());
        assertEquals(1, stats.swissRuns());
        assertEquals(1, stats.ratingRuns());
        assertEquals(2, stats.distinctArtistCount());
        assertEquals("Artist A", stats.mostRankedArtist());
        assertEquals(2, stats.mostRankedArtistCount());
        assertEquals(2, stats.totalComparisonsMade()); // only the bracket run recorded choices
        assertEquals(6, stats.totalTracksRanked()); // 3 + 2 + 1
    }

    @Test
    void albumChampionsPicksTheMostRecentlyUpdatedCompletedRunPerAlbum() throws InterruptedException {
        Track older = new Track("old", "Old Winner", "Same Album", "Artist", 200_000, "uri", null);
        Track newer = new Track("new", "New Winner", "Same Album", "Artist", 200_000, "uri", null);

        RankingRunState first = state("BRACKET", List.of(older, newer));
        long firstRunId = repo.saveNew("Artist", "Same Album", first);
        repo.markComplete(firstRunId, first, List.of(older, newer));

        Thread.sleep(5); // ensure a strictly later updated_at timestamp for the second run

        RankingRunState second = state("BRACKET", List.of(older, newer));
        long secondRunId = repo.saveNew("Artist", "Same Album", second);
        repo.markComplete(secondRunId, second, List.of(newer, older));

        List<AlbumChampion> champions = repo.getAlbumChampions();
        assertEquals(1, champions.size());
        assertEquals("new", champions.get(0).trackId());
    }
}
