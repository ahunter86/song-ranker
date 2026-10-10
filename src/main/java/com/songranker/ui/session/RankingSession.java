package com.songranker.ui.session;

import com.songranker.domain.BracketEngine;
import com.songranker.domain.Matchup;
import com.songranker.domain.RankingEngine;
import com.songranker.domain.SeedingMode;
import com.songranker.domain.SwissEngine;
import com.songranker.domain.Track;
import com.songranker.persistence.RankingRunRepository;
import com.songranker.persistence.TrackRatingRepository;
import com.songranker.persistence.state.RankingRunState;
import com.songranker.persistence.state.RunStateCodec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Drives one ranking run (bracket or Swiss) from the UI's point of view:
 * wraps whichever {@link RankingEngine} the user picked, and persists
 * progress after every choice so the run can be resumed later. Also carries
 * optional per-track ratings and notes, given inline during play (on the
 * head-to-head "vs" screen) rather than in a separate step after the fact.
 */
public final class RankingSession {

    private final RankingRunRepository repository;
    private final TrackRatingRepository trackRatingRepository;
    private RankingEngine engine;
    private final List<Track> originalOrder;
    private final SeedingMode seedingMode;
    private final long randomSeed;
    private final int swissRounds;
    private final boolean isSwiss;
    private final Map<String, Double> ratings = new LinkedHashMap<>();
    private final Map<String, String> notes = new LinkedHashMap<>();
    private long runId = -1;

    private RankingSession(RankingRunRepository repository, TrackRatingRepository trackRatingRepository,
                            RankingEngine engine, List<Track> originalOrder, SeedingMode seedingMode,
                            long randomSeed, int swissRounds, boolean isSwiss) {
        this.repository = repository;
        this.trackRatingRepository = trackRatingRepository;
        this.engine = engine;
        this.originalOrder = originalOrder;
        this.seedingMode = seedingMode;
        this.randomSeed = randomSeed;
        this.swissRounds = swissRounds;
        this.isSwiss = isSwiss;
    }

    public static RankingSession startBracket(RankingRunRepository repo, TrackRatingRepository trackRatingRepo,
                                                List<Track> tracks, SeedingMode seedingMode,
                                                String artistName, String scopeDescription) {
        long seed = new Random().nextLong();
        BracketEngine engine = BracketEngine.create(tracks, seedingMode, new Random(seed));
        RankingSession session = new RankingSession(repo, trackRatingRepo, engine, tracks, seedingMode, seed, -1, false);
        session.persistNew(artistName, scopeDescription);
        return session;
    }

    public static RankingSession startSwiss(RankingRunRepository repo, TrackRatingRepository trackRatingRepo,
                                              List<Track> tracks, int rounds, String artistName, String scopeDescription) {
        long seed = new Random().nextLong();
        SwissEngine engine = SwissEngine.create(tracks, rounds, new Random(seed));
        RankingSession session = new RankingSession(repo, trackRatingRepo, engine, tracks, SeedingMode.RANDOM, seed,
                engine.getTotalRounds(), true);
        session.persistNew(artistName, scopeDescription);
        return session;
    }

    public static RankingSession resume(RankingRunRepository repo, TrackRatingRepository trackRatingRepo, long runId) {
        RankingRunState state = repo.loadState(runId);
        boolean isSwiss = "SWISS".equals(state.mode);
        RankingEngine engine = isSwiss ? RunStateCodec.rebuildSwiss(state) : RunStateCodec.rebuildBracket(state);
        List<Track> tracks = state.tracks.stream().map(t -> t.toDomain()).toList();
        RankingSession session = new RankingSession(repo, trackRatingRepo, engine, tracks,
                SeedingMode.valueOf(state.seedingMode), state.randomSeed, state.swissRounds, isSwiss);
        session.runId = runId;
        session.ratings.putAll(state.trackRatings);
        session.notes.putAll(state.trackNotes);
        return session;
    }

    private void persistNew(String artistName, String scopeDescription) {
        RankingRunState state = captureState();
        this.runId = repository.saveNew(artistName, scopeDescription, state);
    }

    private RankingRunState captureState() {
        RankingRunState state = isSwiss
                ? RunStateCodec.captureSwiss((SwissEngine) engine, originalOrder, randomSeed, swissRounds)
                : RunStateCodec.captureBracket((BracketEngine) engine, originalOrder, seedingMode, randomSeed);
        state.trackRatings = new LinkedHashMap<>(ratings);
        state.trackNotes = new LinkedHashMap<>(notes);
        return state;
    }

    public RankingEngine getEngine() {
        return engine;
    }

    /** The tracks this run was built from, for estimating playback time etc. */
    public List<Track> getOriginalOrder() {
        return originalOrder;
    }

    public long getRunId() {
        return runId;
    }

    /** Records a pick and persists progress immediately, so a crash/close never loses more than one pick. */
    public void choose(Matchup matchup, Track winner) {
        engine.choose(matchup, winner);
        // The last pick also stores the final order, which moves the run from
        // in-progress to history in the same write.
        if (engine.isComplete()) {
            repository.markComplete(runId, captureState(), engine.getFinalRanking());
        } else {
            repository.updateProgress(runId, captureState());
        }
    }

    /** Whether there's a pick to take back. */
    public boolean canUndo() {
        return engine.getCompletedComparisonCount() > 0;
    }

    /**
     * Takes back the most recent pick by rebuilding the engine from every earlier pick, which
     * restores the exact matchup that was just decided. Ratings and notes are kept.
     */
    public void undoLastChoice() {
        RankingRunState state = captureState();
        if (state.winnerChoicesInOrder.isEmpty()) return;
        RankingRunState trimmed = RunStateCodec.withoutLastChoice(state);
        engine = isSwiss ? RunStateCodec.rebuildSwiss(trimmed) : RunStateCodec.rebuildBracket(trimmed);
        repository.updateProgress(runId, captureState());
    }

    public void advanceRound() {
        engine.advanceRound();
        repository.updateProgress(runId, captureState());
    }

    public Double getRating(Track track) {
        return ratings.get(track.getId());
    }

    /** Rates a track inline during play. Also saved long-term, so it shows up in stats regardless of mode. */
    public void setRating(Track track, double rating) {
        ratings.put(track.getId(), rating);
        // Stored twice on purpose: in the run state, so resuming restores it, and in the
        // long-term table that the Stats screen reads.
        trackRatingRepository.saveRating(track, rating);
        repository.updateProgress(runId, captureState());
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
}
