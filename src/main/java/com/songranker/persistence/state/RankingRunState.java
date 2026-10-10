package com.songranker.persistence.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The full serializable state of one ranking session (bracket, Swiss, or Rating).
 *
 * <p>Rather than serializing engine internals directly, this captures enough
 * to deterministically rebuild the engine and replay every choice the user
 * already made: the original track list, the seeding inputs, and an ordered
 * log of winner picks. See {@code RunStateCodec} for the rebuild logic.</p>
 */
public final class RankingRunState {

    public String mode; // "BRACKET", "SWISS", or "RATING"
    public String seedingMode; // RANDOM / ORIGINAL_ORDER; unused for RATING
    public long randomSeed;
    public int swissRounds = -1; // only meaningful when mode == SWISS; -1 = default
    public List<SavedTrack> tracks = new ArrayList<>();
    /** Track ids, in the exact order {@code choose()} was called during play. Unused for RATING. */
    public List<String> winnerChoicesInOrder = new ArrayList<>();

    /** 0-10 rating (fractional allowed) per track id, given so far. Available in any mode. */
    public Map<String, Double> trackRatings = new LinkedHashMap<>();
    /** Free-text note per track id, written while ranking. Available in any mode. */
    public Map<String, String> trackNotes = new LinkedHashMap<>();
    /** Track ids in the user's current manual order (rating-sorted, then drag-adjusted). Only meaningful when mode == "RATING". */
    public List<String> currentOrder = new ArrayList<>();

    public RankingRunState() {
    }
}
