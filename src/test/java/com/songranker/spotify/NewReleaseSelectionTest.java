package com.songranker.spotify;

import com.songranker.spotify.dto.NewRelease;
import com.songranker.spotify.dto.SpotifyArtist;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class NewReleaseSelectionTest {

    private static List<SpotifyArtist> artists(int n) {
        List<SpotifyArtist> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) list.add(new SpotifyArtist("a" + i, "Artist " + i, null));
        return list;
    }

    private static NewRelease release(String albumId, String date) {
        return new NewRelease(albumId, "Album " + albumId, null, date, new SpotifyArtist("x", "X", null));
    }

    @Test
    void neverCheckedArtistsAreChosenBeforeRecentlyCheckedOnes() {
        List<SpotifyArtist> picked = NewReleaseSelection.pickCandidates(
                artists(5), List.of("a1", "a2"), 3, new Random(1));
        assertEquals(3, picked.size());
        assertTrue(picked.stream().noneMatch(a -> a.id.equals("a1") || a.id.equals("a2")));
    }

    @Test
    void fillsRemainingSlotsWithLeastRecentlyCheckedFirst() {
        // a1 was checked longest ago, a2 more recently; only 2 slots and 1 unchecked artist (a3).
        List<SpotifyArtist> picked = NewReleaseSelection.pickCandidates(
                artists(3), List.of("a1", "a2"), 2, new Random(1));
        assertEquals(List.of("a3", "a1"), picked.stream().map(a -> a.id).toList());
    }

    @Test
    void neverReturnsMoreThanThePool() {
        assertEquals(2, NewReleaseSelection.pickCandidates(artists(2), List.of(), 12, new Random(1)).size());
    }

    @Test
    void mergeKeepsRecentOldFindsDropsExpiredOnesAndDedupes() {
        LocalDate cutoff = LocalDate.parse("2026-07-01");
        List<NewRelease> merged = NewReleaseSelection.merge(
                List.of(release("new", "2026-09-10"), release("dup", "2026-08-01")),
                List.of(release("dup", "2026-08-01"), release("old-ok", "2026-07-15"), release("expired", "2026-05-01")),
                cutoff, 10);
        assertEquals(List.of("new", "dup", "old-ok"), merged.stream().map(r -> r.albumId).toList());
    }

    @Test
    void mergeRespectsTheMaximum() {
        List<NewRelease> merged = NewReleaseSelection.merge(
                List.of(release("1", "2026-09-01"), release("2", "2026-09-02"), release("3", "2026-09-03")),
                null, LocalDate.parse("2026-01-01"), 2);
        assertEquals(List.of("3", "2"), merged.stream().map(r -> r.albumId).toList());
    }
}
