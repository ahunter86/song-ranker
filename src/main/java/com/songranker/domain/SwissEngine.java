package com.songranker.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A Swiss-style pairwise comparison ranking: every track is compared across
 * several rounds against opponents with a similar record so far, without a
 * single loss causing elimination. Produces a full ranked list instead of only
 * a winner, which suits smaller lists where the user wants their #7 favorite
 * as well as their #1.
 */
public final class SwissEngine implements RankingEngine {

    private final List<Track> tracks;
    private final Map<Track, Integer> wins = new HashMap<>();
    private final Map<Track, Integer> byesTaken = new HashMap<>();
    private final Set<String> playedPairs = new HashSet<>();
    private final Random random;

    private final int totalRounds;
    private int currentRoundIndex = 0; // 0-based
    private List<Matchup> currentRoundMatchups;
    private final List<List<Matchup>> history = new ArrayList<>();

    private SwissEngine(List<Track> tracks, int totalRounds, Random random) {
        this.tracks = tracks;
        this.totalRounds = totalRounds;
        this.random = random;
        for (Track t : tracks) {
            wins.put(t, 0);
            byesTaken.put(t, 0);
        }
        this.currentRoundMatchups = pairNextRound();
        history.add(currentRoundMatchups);
    }

    /**
     * @param tracks tracks to rank; must have at least 2
     * @param rounds number of rounds to play; if <= 0, a sensible default
     *               (ceil(log2(n)) + 2, capped at n-1) is used
     */
    public static SwissEngine create(List<Track> tracks, int rounds, Random random) {
        if (tracks == null || tracks.size() < 2) {
            throw new IllegalArgumentException("Swiss ranking needs at least 2 tracks");
        }
        List<Track> shuffled = new ArrayList<>(tracks);
        Collections.shuffle(shuffled, random);
        return new SwissEngine(shuffled, resolveRounds(shuffled.size(), rounds), random);
    }

    /**
     * Works out how many rounds a Swiss run will actually play, given a requested count
     * (or &lt;= 0 for "pick automatically"). Exposed statically so the UI can preview the
     * round count, and so an estimated comparison count, before a run starts.
     */
    public static int resolveRounds(int trackCount, int requestedRounds) {
        // The default is ceil(log2(n)) + 2 rounds. The cap is n - 1 because a track
        // can face at most n - 1 different opponents.
        int defaultRounds = Math.min(trackCount - 1, (int) Math.ceil(Math.log(trackCount) / Math.log(2)) + 2);
        return requestedRounds > 0 ? Math.min(requestedRounds, trackCount - 1) : Math.max(1, defaultRounds);
    }

    private String pairKey(Track a, Track b) {
        String x = a.getId(), y = b.getId();
        // Sorted so the key is the same for (a, b) and (b, a).
        return x.compareTo(y) < 0 ? x + "|" + y : y + "|" + x;
    }

    private List<Matchup> pairNextRound() {
        List<Track> byScore = new ArrayList<>(tracks);
        // Sort by current score desc; shuffle first so ties break randomly, not by input order.
        Collections.shuffle(byScore, random);
        byScore.sort(Comparator.comparingInt(wins::get).reversed());

        List<Matchup> round = new ArrayList<>();
        List<Track> unpaired = new ArrayList<>(byScore);

        // Odd number of players: give the bye to the lowest-scored player who hasn't had one.
        if (unpaired.size() % 2 == 1) {
            Track byeTrack = null;
            for (int i = unpaired.size() - 1; i >= 0; i--) {
                if (byesTaken.get(unpaired.get(i)) == 0) {
                    byeTrack = unpaired.remove(i);
                    break;
                }
            }
            if (byeTrack == null) {
                // Everyone has already had a bye: give it to the lowest-ranked track again.
                byeTrack = unpaired.remove(unpaired.size() - 1);
            }
            byesTaken.merge(byeTrack, 1, Integer::sum);
            // A bye adds no win. The matchup is decided when it is created, so choose()
            // is never called for it.
            round.add(new Matchup(byeTrack, null));
        }

        while (!unpaired.isEmpty()) {
            Track a = unpaired.remove(0);
            // Take the highest-ranked track left and pair it with the best-ranked one
            // it has not met yet.
            Track b = findBestOpponent(a, unpaired);
            unpaired.remove(b);
            round.add(new Matchup(a, b));
            playedPairs.add(pairKey(a, b));
        }
        return round;
    }

    private Track findBestOpponent(Track a, List<Track> candidates) {
        for (Track candidate : candidates) {
            if (!playedPairs.contains(pairKey(a, candidate))) {
                return candidate;
            }
        }
        // Everyone remaining has already played `a`. Fall back to the closest-scoring one.
        // That is a rematch. Greedy pairing can leave two tracks that already met,
        // mostly when the round count approaches n - 1.
        return candidates.get(0);
    }

    public List<Matchup> getCurrentRoundMatchups() {
        return Collections.unmodifiableList(currentRoundMatchups);
    }

    public List<Matchup> getCurrentRoundPendingMatchups() {
        List<Matchup> pending = new ArrayList<>();
        for (Matchup m : currentRoundMatchups) {
            if (!m.isDecided()) pending.add(m);
        }
        return pending;
    }

    public int getCurrentRoundNumber() {
        return currentRoundIndex + 1;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public void choose(Matchup matchup, Track winner) {
        if (!currentRoundMatchups.contains(matchup)) {
            throw new IllegalArgumentException("That matchup isn't part of the current round");
        }
        matchup.choose(winner);
        wins.merge(winner, 1, Integer::sum);
    }

    public boolean isCurrentRoundComplete() {
        return currentRoundMatchups.stream().allMatch(Matchup::isDecided);
    }

    /** All rounds played so far, including the in-progress one. Useful for persistence/rendering. */
    public List<List<Matchup>> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public boolean isComplete() {
        return isCurrentRoundComplete() && currentRoundIndex + 1 >= totalRounds;
    }

    public void advanceRound() {
        if (!isCurrentRoundComplete()) {
            throw new IllegalStateException("Current round still has undecided matchups");
        }
        if (isComplete()) {
            throw new IllegalStateException("Swiss ranking is already complete");
        }
        currentRoundIndex++;
        currentRoundMatchups = pairNextRound();
        history.add(currentRoundMatchups);
    }

    /**
     * Final ranking, most wins first. Ties are broken by Buchholz score (the
     * summed win-count of everyone that track played), a standard Swiss
     * tiebreaker that rewards beating tougher opponents.
     */
    public List<Track> getFinalRanking() {
        if (!isComplete()) {
            throw new IllegalStateException("Swiss ranking isn't complete yet");
        }
        Map<Track, Integer> buchholz = new HashMap<>();
        for (Track t : tracks) buchholz.put(t, 0);
        // Buchholz: each track collects the final win total of every opponent it faced.
        // These are totals at the end of the run, not at the time of the matchup.
        for (List<Matchup> round : history) {
            for (Matchup m : round) {
                if (m.isBye()) continue;
                buchholz.merge(m.getTrackA(), wins.get(m.getTrackB()), Integer::sum);
                buchholz.merge(m.getTrackB(), wins.get(m.getTrackA()), Integer::sum);
            }
        }
        List<Track> ranked = new ArrayList<>(tracks);
        ranked.sort(Comparator
                .comparingInt((Track t) -> wins.get(t)).reversed()
                .thenComparing(Comparator.comparingInt((Track t) -> buchholz.get(t)).reversed()));
        return ranked;
    }

    public int getWins(Track track) {
        return wins.getOrDefault(track, 0);
    }

    /** Every round pairs up all but at most one (odd-track-out bye) track. */
    public int getTotalComparisonCount() {
        return totalRounds * (tracks.size() / 2);
    }

    public int getCompletedComparisonCount() {
        int count = 0;
        for (List<Matchup> round : history) {
            for (Matchup m : round) {
                if (!m.isBye() && m.isDecided()) count++;
            }
        }
        return count;
    }
}
