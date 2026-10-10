package com.songranker.persistence;

import com.songranker.domain.Track;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Long-term ratings (0-10, fractional allowed) for individual songs, keyed by Spotify track id.
 * They are independent of any single ranking run, so a song's rating persists and carries over everywhere
 * it shows up.
 */
public final class TrackRatingRepository {

    public void saveRating(Track track, double rating) {
        String sql = """
                INSERT INTO track_rating (track_id, track_name, album_name, artist_name, album_art_url, rating, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(track_id) DO UPDATE SET
                    track_name = excluded.track_name,
                    album_name = excluded.album_name,
                    artist_name = excluded.artist_name,
                    album_art_url = excluded.album_art_url,
                    rating = excluded.rating,
                    updated_at = excluded.updated_at
                """;
        // Upsert by track id. The name, album, artist and art URL are stored next to the
        // rating so the Stats screen can list songs without calling Spotify.
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, track.getId());
            ps.setString(2, track.getName());
            ps.setString(3, track.getAlbumName());
            ps.setString(4, track.getArtistName());
            ps.setString(5, track.getAlbumArtUrl());
            ps.setDouble(6, rating);
            ps.setString(7, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save rating for track " + track.getId(), e);
        }
    }

    /** Ratings for the given track ids, keyed by id. Ids with no saved rating are absent. */
    public Map<String, Double> getRatings(Collection<String> trackIds) {
        Map<String, Double> result = new HashMap<>();
        if (trackIds.isEmpty()) return result;
        String placeholders = String.join(",", trackIds.stream().map(id -> "?").toList());
        String sql = "SELECT track_id, rating FROM track_rating WHERE track_id IN (" + placeholders + ")";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            int i = 1;
            for (String id : trackIds) ps.setString(i++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("track_id"), rs.getDouble("rating"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load track ratings", e);
        }
        return result;
    }

    public Double getRating(String trackId) {
        return getRatings(List.of(trackId)).get(trackId);
    }

    public List<TrackRating> getAllRatings() {
        List<TrackRating> ratings = new ArrayList<>();
        String sql = "SELECT * FROM track_rating ORDER BY rating DESC";
        try (Statement stmt = Database.get().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ratings.add(new TrackRating(rs.getString("track_id"), rs.getString("track_name"),
                        rs.getString("album_name"), rs.getString("artist_name"),
                        rs.getString("album_art_url"), rs.getDouble("rating"), rs.getString("updated_at")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load track ratings", e);
        }
        return ratings;
    }

    public record TrackRating(String trackId, String trackName, String albumName, String artistName,
                               String albumArtUrl, double rating, String updatedAt) {
    }
}
