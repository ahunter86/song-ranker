package com.songranker.ui.util;

import com.songranker.persistence.RankingRunRepository.AlbumChampion;
import com.songranker.persistence.TrackRatingRepository.TrackRating;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AlbumSummariesTest {

    private static TrackRating rating(String id, String album, double r) {
        return new TrackRating(id, "Song " + id, album, "Artist", null, r, "2026-01-01");
    }

    @Test
    void averagesRatedSongsPerAlbumAndSortsHighestFirst() {
        List<AlbumSummaries.Entry> result = AlbumSummaries.build(List.of(
                rating("1", "Low", 4), rating("2", "High", 9), rating("3", "High", 7)), List.of());

        assertEquals(List.of("High", "Low"), result.stream().map(AlbumSummaries.Entry::albumName).toList());
        assertEquals(8.0, result.get(0).average());
        assertEquals(2, result.get(0).ratedCount());
    }

    @Test
    void unratedChampionAlbumsSortAfterRatedOnes() {
        List<AlbumSummaries.Entry> result = AlbumSummaries.build(
                List.of(rating("1", "Rated", 2)),
                List.of(new AlbumChampion("Unrated", "x", "Winner", "Artist")));

        assertEquals("Rated", result.get(0).albumName());
        assertNull(result.get(1).average());
        assertEquals("Winner", result.get(1).favoriteTrackName());
    }

    @Test
    void championIsAttachedToItsRatedAlbum() {
        List<AlbumSummaries.Entry> result = AlbumSummaries.build(
                List.of(rating("1", "Album", 6)),
                List.of(new AlbumChampion("Album", "1", "Song 1", "Artist")));

        assertEquals(1, result.size());
        assertEquals("Song 1", result.get(0).favoriteTrackName());
    }
}
