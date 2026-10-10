package com.songranker.persistence;

import com.songranker.domain.Track;
import com.songranker.persistence.state.RankingRunState;
import com.songranker.persistence.state.SavedTrack;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores ranking runs (in-progress and completed) as rows with a JSON state
 * blob, so a bracket can be resumed later and completed ones show up in history.
 */
public final class RankingRunRepository {

    private final ObjectMapper mapper = new ObjectMapper();

    public long saveNew(String artistName, String scopeDescription, RankingRunState state) {
        String sql = """
                INSERT INTO ranking_run (artist_name, scope_description, mode, seeding_mode, status,
                    state_json, result_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'IN_PROGRESS', ?, NULL, ?, ?)
                """;
        String now = Instant.now().toString();
        try (PreparedStatement ps = Database.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, artistName);
            ps.setString(2, scopeDescription);
            ps.setString(3, state.mode);
            ps.setString(4, state.seedingMode);
            ps.setString(5, mapper.writeValueAsString(state));
            ps.setString(6, now);
            ps.setString(7, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save ranking run", e);
        }
    }

    public void updateProgress(long runId, RankingRunState state) {
        updateInternal(runId, state, "IN_PROGRESS", null);
    }

    public void markComplete(long runId, RankingRunState state, List<Track> finalRanking) {
        try {
            // result_json holds only the track ids in final order. loadResultTracks() looks the
            // full tracks up in state_json.
            String resultJson = mapper.writeValueAsString(
                    finalRanking.stream().map(Track::getId).toList());
            updateInternal(runId, state, "COMPLETED", resultJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save completed ranking result", e);
        }
    }

    /**
     * Replaces the stored final order of a completed run (the user dragged songs around after
     * finishing). Leaves the replayable state alone; only the displayed/used result changes.
     */
    public void updateResultOrder(long runId, List<Track> newOrder) {
        String sql = "UPDATE ranking_run SET result_json = ?, updated_at = ? WHERE id = ? AND status = 'COMPLETED'";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, mapper.writeValueAsString(newOrder.stream().map(Track::getId).toList()));
            ps.setString(2, Instant.now().toString());
            ps.setLong(3, runId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update ranking order for run " + runId, e);
        }
    }

    private void updateInternal(long runId, RankingRunState state, String status, String resultJson) {
        String sql = "UPDATE ranking_run SET status = ?, state_json = ?, result_json = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, mapper.writeValueAsString(state));
            ps.setString(3, resultJson);
            ps.setString(4, Instant.now().toString());
            ps.setLong(5, runId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update ranking run " + runId, e);
        }
    }

    public List<SavedRunSummary> findInProgress() {
        return query("SELECT id, artist_name, scope_description, mode, created_at, updated_at " +
                "FROM ranking_run WHERE status = 'IN_PROGRESS' ORDER BY updated_at DESC");
    }

    public List<SavedRunSummary> findHistory() {
        return query("SELECT id, artist_name, scope_description, mode, created_at, updated_at " +
                "FROM ranking_run WHERE status = 'COMPLETED' ORDER BY updated_at DESC");
    }

    private List<SavedRunSummary> query(String sql) {
        List<SavedRunSummary> results = new ArrayList<>();
        try (Statement stmt = Database.get().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                results.add(new SavedRunSummary(
                        rs.getLong("id"), rs.getString("artist_name"), rs.getString("scope_description"),
                        rs.getString("mode"), rs.getString("created_at"), rs.getString("updated_at")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query ranking runs", e);
        }
        return results;
    }

    public RankingRunState loadState(long runId) {
        String sql = "SELECT state_json FROM ranking_run WHERE id = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new RuntimeException("No ranking run with id " + runId);
                return mapper.readValue(rs.getString(1), RankingRunState.class);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load ranking run " + runId, e);
        }
    }

    /** Loads the final ranking (winner first) for a completed run, resolving ids back to full Track info. */
    public List<Track> loadResultTracks(long runId) {
        String sql = "SELECT state_json, result_json FROM ranking_run WHERE id = ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new RuntimeException("No ranking run with id " + runId);
                RankingRunState state = mapper.readValue(rs.getString("state_json"), RankingRunState.class);
                String resultJson = rs.getString("result_json");
                if (resultJson == null) throw new RuntimeException("Run " + runId + " has no result yet");
                List<String> orderedIds = mapper.readValue(resultJson, mapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class));
                var byId = state.tracks.stream()
                        .collect(java.util.stream.Collectors.toMap(t -> t.id, t -> t.toDomain()));
                return orderedIds.stream().map(byId::get).toList();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load result for ranking run " + runId, e);
        }
    }

    /** Aggregate stats across every saved run (in-progress and completed) for a Home-screen stats panel. */
    public RunStats getStats() {
        int totalRuns = 0, completedRuns = 0, inProgressRuns = 0, bracketRuns = 0, swissRuns = 0, ratingRuns = 0;
        int totalTracksRanked = 0, totalComparisonsMade = 0;
        Map<String, Integer> artistCounts = new HashMap<>();
        Set<String> distinctArtists = new HashSet<>();
        String biggestArtist = null, biggestScope = null;
        int biggestTrackCount = -1;

        String sql = "SELECT artist_name, scope_description, mode, status, state_json FROM ranking_run";
        try (Statement stmt = Database.get().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                totalRuns++;
                String artistName = rs.getString("artist_name");
                String scopeDescription = rs.getString("scope_description");
                String mode = rs.getString("mode");
                String status = rs.getString("status");

                distinctArtists.add(artistName);
                artistCounts.merge(artistName, 1, Integer::sum);
                if ("COMPLETED".equals(status)) completedRuns++; else inProgressRuns++;
                switch (mode) {
                    case "BRACKET" -> bracketRuns++;
                    case "SWISS" -> swissRuns++;
                    case "RATING" -> ratingRuns++;
                    default -> { /* unknown mode - just don't miscount it as something else */ }
                }

                RankingRunState state;
                try {
                    state = mapper.readValue(rs.getString("state_json"), RankingRunState.class);
                // The run was already counted in the totals above. Only its track and
                // comparison numbers are missing.
                } catch (Exception malformed) {
                    continue; // skip a corrupt row rather than failing the whole stats panel
                }
                int trackCount = state.tracks.size();
                totalTracksRanked += trackCount;
                // Rate 0-10 runs have no head-to-head picks, so they add nothing to this total.
                totalComparisonsMade += state.winnerChoicesInOrder.size();
                // Strictly greater: with several equally large runs, the first one read is reported.
                if (trackCount > biggestTrackCount) {
                    biggestTrackCount = trackCount;
                    biggestArtist = artistName;
                    biggestScope = scopeDescription;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute ranking stats", e);
        }

        String mostRankedArtist = null;
        int mostRankedArtistCount = 0;
        // On a tie the artist the map returns first wins, which is arbitrary.
        for (Map.Entry<String, Integer> entry : artistCounts.entrySet()) {
            if (entry.getValue() > mostRankedArtistCount) {
                mostRankedArtistCount = entry.getValue();
                mostRankedArtist = entry.getKey();
            }
        }

        return new RunStats(totalRuns, completedRuns, inProgressRuns, bracketRuns, swissRuns, ratingRuns,
                totalTracksRanked, totalComparisonsMade, distinctArtists.size(),
                mostRankedArtist, mostRankedArtistCount,
                biggestArtist, biggestScope, biggestTrackCount);
    }

    /**
     * For each album that has a completed run, the #1 track from the most recently updated such
     * run: a "favorite track per album" list. Ratings (if any) are attached separately by the
     * caller via {@link TrackRatingRepository}, since this repository doesn't know about ratings.
     */
    public List<AlbumChampion> getAlbumChampions() {
        Map<String, AlbumChampion> byAlbum = new LinkedHashMap<>();
        Map<String, String> updatedAtByAlbum = new HashMap<>();
        String sql = "SELECT state_json, result_json, updated_at FROM ranking_run WHERE status = 'COMPLETED'";
        try (Statement stmt = Database.get().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String updatedAt = rs.getString("updated_at");
                try {
                    RankingRunState state = mapper.readValue(rs.getString("state_json"), RankingRunState.class);
                    List<String> orderedIds = mapper.readValue(rs.getString("result_json"), mapper.getTypeFactory()
                            .constructCollectionType(List.class, String.class));
                    if (orderedIds.isEmpty()) continue;
                    String championId = orderedIds.get(0);
                    for (SavedTrack t : state.tracks) {
                        if (!t.id.equals(championId)) continue;
                        // Albums are matched by name because saved tracks keep the album name, not its id.
                        // Two albums with the same title by different artists share one entry.
                        String albumName = t.albumName != null ? t.albumName : t.name;
                        String previousUpdatedAt = updatedAtByAlbum.get(albumName);
                        // updated_at is an ISO-8601 UTC string, so comparing it as text orders by time.
                        if (previousUpdatedAt == null || updatedAt.compareTo(previousUpdatedAt) > 0) {
                            byAlbum.put(albumName, new AlbumChampion(albumName, t.id, t.name, t.artistName));
                            updatedAtByAlbum.put(albumName, updatedAt);
                        }
                        break;
                    }
                } catch (Exception malformed) {
                    // skip a corrupt/incomplete row rather than failing the whole list
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute album favorites", e);
        }
        return new ArrayList<>(byAlbum.values());
    }

    public void delete(long runId) {
        try (PreparedStatement ps = Database.get().prepareStatement("DELETE FROM ranking_run WHERE id = ?")) {
            ps.setLong(1, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete ranking run " + runId, e);
        }
    }

    public record SavedRunSummary(long id, String artistName, String scopeDescription, String mode,
                                   String createdAt, String updatedAt) {
    }

    public record RunStats(int totalRuns, int completedRuns, int inProgressRuns, int bracketRuns, int swissRuns,
                            int ratingRuns, int totalTracksRanked, int totalComparisonsMade, int distinctArtistCount,
                            String mostRankedArtist, int mostRankedArtistCount,
                            String biggestRunArtist, String biggestRunScope, int biggestRunTrackCount) {
    }

    /** The #1 track from the most recently completed run for one album. */
    public record AlbumChampion(String albumName, String trackId, String trackName, String artistName) {
    }
}
