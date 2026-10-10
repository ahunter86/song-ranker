package com.songranker.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Which songs the user has played from inside the app, so a "Listened" marker can show up
 * everywhere a song appears. Global rather than per-run: hearing a song once means you know it.
 */
public final class ListenedRepository {

    public void markListened(String trackId) {
        String sql = "INSERT INTO track_listened (track_id, listened_at) VALUES (?, ?) "
                + "ON CONFLICT(track_id) DO NOTHING";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, trackId);
            ps.setString(2, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark track listened: " + trackId, e);
        }
    }

    /** The subset of the given ids that have been played. */
    public Set<String> getListened(Collection<String> trackIds) {
        Set<String> result = new HashSet<>();
        if (trackIds.isEmpty()) return result;
        String placeholders = String.join(",", trackIds.stream().map(id -> "?").toList());
        String sql = "SELECT track_id FROM track_listened WHERE track_id IN (" + placeholders + ")";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            int i = 1;
            for (String id : trackIds) ps.setString(i++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load listened tracks", e);
        }
        return result;
    }
}
