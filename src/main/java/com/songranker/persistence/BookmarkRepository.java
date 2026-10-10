package com.songranker.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Albums saved for later ("I want to rank this, just not right now"), newest bookmark first. */
public final class BookmarkRepository {

    public void add(Bookmark bookmark) {
        String sql = """
                INSERT INTO bookmark (album_id, album_name, artist_id, artist_name, image_url, release_date, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(album_id) DO NOTHING
                """;
        // Bookmarking the same album again keeps the first row and its original timestamp.
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, bookmark.albumId());
            ps.setString(2, bookmark.albumName());
            ps.setString(3, bookmark.artistId());
            ps.setString(4, bookmark.artistName());
            ps.setString(5, bookmark.imageUrl());
            ps.setString(6, bookmark.releaseDate());
            ps.setString(7, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to bookmark album " + bookmark.albumId(), e);
        }
    }

    public void remove(String albumId) {
        try (PreparedStatement ps = Database.get().prepareStatement("DELETE FROM bookmark WHERE album_id = ?")) {
            ps.setString(1, albumId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove bookmark " + albumId, e);
        }
    }

    public boolean isBookmarked(String albumId) {
        try (PreparedStatement ps = Database.get().prepareStatement("SELECT 1 FROM bookmark WHERE album_id = ?")) {
            ps.setString(1, albumId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check bookmark " + albumId, e);
        }
    }

    public List<Bookmark> findAll() {
        // Newest first. rowid breaks ties when two bookmarks share a timestamp.
        List<Bookmark> result = new ArrayList<>();
        try (Statement stmt = Database.get().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM bookmark ORDER BY created_at DESC, rowid DESC")) {
            while (rs.next()) {
                result.add(new Bookmark(rs.getString("album_id"), rs.getString("album_name"),
                        rs.getString("artist_id"), rs.getString("artist_name"),
                        rs.getString("image_url"), rs.getString("release_date")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load bookmarks", e);
        }
        return result;
    }

    public record Bookmark(String albumId, String albumName, String artistId, String artistName,
                            String imageUrl, String releaseDate) {
    }
}
