package com.songranker.ui.util;

import com.songranker.persistence.RankingRunRepository.AlbumChampion;
import com.songranker.persistence.TrackRatingRepository.TrackRating;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Combines per-song ratings and per-album ranking champions into one list of albums for the Stats screen. */
public final class AlbumSummaries {

    private AlbumSummaries() {
    }

    /**
     * @param average    mean of every rated song on the album, or null if none are rated
     * @param ratedCount how many songs on the album have a rating
     * @param favoriteTrackName the #1 track from the latest completed ranking of the album, or null
     */
    public record Entry(String albumName, String artistName, Double average, int ratedCount,
                        String favoriteTrackName) {
    }

    /** Mean of the given ratings, or null when there are none. */
    public static Double averageOf(java.util.Collection<Double> values) {
        if (values.isEmpty()) return null;
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.size();
    }

    /** Albums with an average first (highest to lowest), then albums with only a ranking champion. */
    public static List<Entry> build(List<TrackRating> ratings, List<AlbumChampion> champions) {
        Map<String, double[]> sums = new LinkedHashMap<>(); // album -> {sum, count}
        Map<String, String> artists = new LinkedHashMap<>();
        for (TrackRating r : ratings) {
            String album = r.albumName() != null ? r.albumName() : r.trackName();
            double[] acc = sums.computeIfAbsent(album, k -> new double[2]);
            acc[0] += r.rating();
            acc[1]++;
            artists.putIfAbsent(album, r.artistName());
        }

        Map<String, AlbumChampion> championByAlbum = new LinkedHashMap<>();
        for (AlbumChampion c : champions) championByAlbum.put(c.albumName(), c);

        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, double[]> e : sums.entrySet()) {
            AlbumChampion c = championByAlbum.remove(e.getKey());
            entries.add(new Entry(e.getKey(), artists.get(e.getKey()), e.getValue()[0] / e.getValue()[1],
                    (int) e.getValue()[1], c != null ? c.trackName() : null));
        }
        for (AlbumChampion c : championByAlbum.values()) {
            entries.add(new Entry(c.albumName(), c.artistName(), null, 0, c.trackName()));
        }
        entries.sort(Comparator.comparing(Entry::average, Comparator.nullsLast(Comparator.reverseOrder())));
        return entries;
    }
}
