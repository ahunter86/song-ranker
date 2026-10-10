package com.songranker.ui.session;

import com.songranker.domain.Track;
import com.songranker.persistence.RankingRunRepository;
import com.songranker.persistence.TrackRatingRepository;
import com.songranker.persistence.state.RankingRunState;
import com.songranker.persistence.state.SavedTrack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Drives one "rate each song 0-10" run: unlike bracket/Swiss, there are no head-to-head
 * matchups. The user assigns each track a score directly, then can freely drag tracks up/down
 * to fine-tune the final order. Every rating is also saved long-term via {@link TrackRatingRepository}
 * so the score also appears in other runs and on the Stats screen.
 */
public final class RatingSession {

    private final RankingRunRepository repository;
    private final TrackRatingRepository ratingRepository;
    private final List<Track> tracks;
    private final Map<String, Double> ratings = new LinkedHashMap<>();
    private final Map<String, String> notes = new LinkedHashMap<>();
    private List<Track> currentOrder;
    private long runId = -1;

    private RatingSession(RankingRunRepository repository, TrackRatingRepository ratingRepository, List<Track> tracks) {
        this.repository = repository;
        this.ratingRepository = ratingRepository;
        this.tracks = tracks;
        this.currentOrder = new ArrayList<>(tracks);
    }

    public static RatingSession start(RankingRunRepository repo, TrackRatingRepository ratingRepo,
                                       List<Track> tracks, String artistName, String scopeDescription) {
        RatingSession session = new RatingSession(repo, ratingRepo, tracks);
        session.runId = repo.saveNew(artistName, scopeDescription, session.captureState());
        return session;
    }

    public static RatingSession resume(RankingRunRepository repo, TrackRatingRepository ratingRepo, long runId) {
        RankingRunState state = repo.loadState(runId);
        List<Track> tracks = state.tracks.stream().map(SavedTrack::toDomain).toList();
        RatingSession session = new RatingSession(repo, ratingRepo, tracks);
        session.runId = runId;
        session.ratings.putAll(state.trackRatings);
        session.notes.putAll(state.trackNotes);

        Map<String, Track> byId = tracks.stream().collect(Collectors.toMap(Track::getId, t -> t));
        if (!state.currentOrder.isEmpty()) {
            // Ids in the saved order that no longer match a track are dropped. A run saved
            // before the order was tracked keeps the order the tracks came in.
            session.currentOrder = state.currentOrder.stream()
                    .map(byId::get).filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
        }
        return session;
    }

    private RankingRunState captureState() {
        RankingRunState state = new RankingRunState();
        state.mode = "RATING";
        state.tracks = tracks.stream().map(SavedTrack::fromDomain).toList();
        state.trackRatings = new LinkedHashMap<>(ratings);
        state.trackNotes = new LinkedHashMap<>(notes);
        state.currentOrder = currentOrder.stream().map(Track::getId).toList();
        return state;
    }

    public long getRunId() {
        return runId;
    }

    public List<Track> getTracks() {
        return tracks;
    }

    public List<Track> getCurrentOrder() {
        return currentOrder;
    }

    public Double getRating(Track track) {
        return ratings.get(track.getId());
    }

    public boolean allRated() {
        // Ratings are keyed by track id and only rated tracks have an entry.
        return ratings.size() >= tracks.size();
    }

    /** Records a 0-10 rating, saves it long-term, re-sorts the current order by score, and persists progress. */
    public void rate(Track track, double rating) {
        ratings.put(track.getId(), rating);
        ratingRepository.saveRating(track, rating);
        resortByRating();
        repository.updateProgress(runId, captureState());
    }

    /** Moves a track to a new position in the current order (drag-and-drop reorder), then persists. */
    public void reorder(Track track, int newIndex) {
        currentOrder.remove(track);
        // Clamp to the list size after the removal above.
        currentOrder.add(Math.min(newIndex, currentOrder.size()), track);
        repository.updateProgress(runId, captureState());
    }

    private void resortByRating() {
        // Highest rating first, unrated tracks last. The sort is stable, so equal ratings
        // keep their current order. Each new rating re-sorts the whole list, which also
        // undoes earlier drag moves between tracks that have different ratings.
        currentOrder.sort((a, b) -> {
            Double ra = ratings.get(a.getId());
            Double rb = ratings.get(b.getId());
            if (ra == null && rb == null) return 0;
            if (ra == null) return 1;
            if (rb == null) return -1;
            return Double.compare(rb, ra);
        });
    }

    public String getNote(Track track) {
        return notes.get(track.getId());
    }

    public void setNote(Track track, String note) {
        if (note == null || note.isBlank()) {
            notes.remove(track.getId());
        } else {
            notes.put(track.getId(), note);
        }
        repository.updateProgress(runId, captureState());
    }

    /** Finalizes the run using the current (rating-sorted, possibly drag-adjusted) order. */
    public void finish() {
        repository.markComplete(runId, captureState(), currentOrder);
    }
}
