package com.songranker.spotify;

import com.songranker.spotify.dto.NewRelease;
import com.songranker.spotify.dto.SpotifyArtist;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Pure selection logic for the New Releases feed, kept apart from the API calls so it's testable:
 * which artists to check on this refresh, and how to combine fresh results with earlier ones.
 */
public final class NewReleaseSelection {

    private NewReleaseSelection() {
    }

    /**
     * Picks up to {@code limit} artists to check, favoring ones not checked recently so each refresh
     * surfaces different artists. {@code checkedHistory} lists artist ids oldest-checked first.
     * Never-checked artists come first (shuffled), then previously-checked ones, least recently first.
     */
    public static List<SpotifyArtist> pickCandidates(List<SpotifyArtist> pool, List<String> checkedHistory,
                                                     int limit, Random random) {
        Map<String, Integer> lastChecked = new LinkedHashMap<>();
        for (int i = 0; i < checkedHistory.size(); i++) lastChecked.put(checkedHistory.get(i), i);

        List<SpotifyArtist> unchecked = new ArrayList<>();
        List<SpotifyArtist> checked = new ArrayList<>();
        for (SpotifyArtist a : pool) {
            (lastChecked.containsKey(a.id) ? checked : unchecked).add(a);
        }
        java.util.Collections.shuffle(unchecked, random);
        // A lower position in the history means checked longer ago.
        checked.sort(Comparator.comparingInt(a -> lastChecked.get(a.id)));

        List<SpotifyArtist> picked = new ArrayList<>(unchecked);
        picked.addAll(checked);
        return picked.size() > limit ? new ArrayList<>(picked.subList(0, limit)) : picked;
    }

    /**
     * Fresh results plus still-recent releases from earlier refreshes (so finds from previous rounds
     * don't vanish when a different set of artists is checked), deduped by album, newest first.
     */
    public static List<NewRelease> merge(List<NewRelease> fresh, List<NewRelease> previous, LocalDate cutoff,
                                         int max) {
        Map<String, NewRelease> byAlbum = new LinkedHashMap<>();
        // Fresh results go in first, so they win over an older cached copy of the same album.
        for (NewRelease r : fresh) byAlbum.putIfAbsent(r.albumId, r);
        if (previous != null) {
            for (NewRelease r : previous) {
                if (SpotifyApiClient.isOnOrAfter(r.releaseDate, cutoff)) byAlbum.putIfAbsent(r.albumId, r);
            }
        }
        return byAlbum.values().stream()
                .sorted(Comparator.comparing((NewRelease r) -> r.releaseDate, Comparator.reverseOrder()))
                .limit(max)
                .toList();
    }
}
