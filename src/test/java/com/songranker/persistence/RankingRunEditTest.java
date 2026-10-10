package com.songranker.persistence;

import com.songranker.domain.Track;
import com.songranker.persistence.RankingRunRepository.AlbumChampion;
import com.songranker.persistence.state.RankingRunState;
import com.songranker.persistence.state.SavedTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Editing a finished ranking and deleting runs, against a throwaway SQLite file. */
class RankingRunEditTest {

    private final RankingRunRepository repo = new RankingRunRepository();

    @BeforeEach
    void useTempDatabase() throws IOException {
        Path dbFile = Files.createTempFile("song-ranker-test-", ".db");
        Files.deleteIfExists(dbFile);
        dbFile.toFile().deleteOnExit();
        Database.useDatabaseFileForTesting(dbFile);
    }

    private static Track track(int i) {
        return new Track("id" + i, "Track " + i, "Album", "Artist", 200_000, "spotify:track:id" + i, null);
    }

    private static RankingRunState state(List<Track> tracks) {
        RankingRunState state = new RankingRunState();
        state.mode = "BRACKET";
        state.seedingMode = "ORIGINAL_ORDER";
        state.tracks = tracks.stream().map(SavedTrack::fromDomain).toList();
        return state;
    }

    private long completedRun(List<Track> tracks) {
        long id = repo.saveNew("Artist", "Album", state(tracks));
        repo.markComplete(id, state(tracks), tracks);
        return id;
    }

    @Test
    void reorderedResultIsWhatGetsLoadedNextTime() {
        List<Track> tracks = List.of(track(1), track(2), track(3));
        long id = completedRun(tracks);

        repo.updateResultOrder(id, List.of(track(3), track(1), track(2)));

        assertEquals(List.of("id3", "id1", "id2"), repo.loadResultTracks(id).stream().map(Track::getId).toList());
    }

    @Test
    void newNumberOneBecomesTheAlbumFavorite() {
        List<Track> tracks = List.of(track(1), track(2));
        long id = completedRun(tracks);
        assertEquals("id1", repo.getAlbumChampions().get(0).trackId());

        repo.updateResultOrder(id, List.of(track(2), track(1)));

        AlbumChampion champion = repo.getAlbumChampions().get(0);
        assertEquals("id2", champion.trackId());
    }

    @Test
    void reorderingDoesNotTouchAnInProgressRun() {
        List<Track> tracks = List.of(track(1), track(2));
        long id = repo.saveNew("Artist", "Album", state(tracks));

        repo.updateResultOrder(id, List.of(track(2), track(1)));

        assertEquals(1, repo.findInProgress().size());
        assertTrue(repo.findHistory().isEmpty());
    }

    @Test
    void deletingACompletedRunRemovesItFromHistoryAndStats() {
        long id = completedRun(List.of(track(1), track(2)));
        repo.delete(id);
        assertTrue(repo.findHistory().isEmpty());
        assertEquals(0, repo.getStats().totalRuns());
        assertTrue(repo.getAlbumChampions().isEmpty());
    }
}
