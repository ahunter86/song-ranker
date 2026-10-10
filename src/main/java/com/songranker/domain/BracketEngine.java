package com.songranker.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Builds and drives a single-elimination bracket over a list of tracks.
 *
 * <p>Track counts that aren't a power of two are rounded up to the next one;
 * the extra slots become byes, which are distributed using the standard
 * tournament seeding order (1 vs N, 2 vs N-1, ...) so real tracks, not byes,
 * are what plays in later rounds whenever possible.</p>
 *
 * <p>This class has no dependency on JavaFX, Spotify, or persistence, so it
 * can be unit tested and reused from any UI or headless context.</p>
 */
public final class BracketEngine implements RankingEngine {

    private final List<List<Matchup>> rounds = new ArrayList<>();
    private int currentRound = 0;
    private int totalRealTracks;

    private BracketEngine() {
    }

    /**
     * Creates a new bracket from the given tracks.
     *
     * @param tracks     tracks to rank; must not be empty
     * @param seedingMode how to order tracks into seed slots before byes are assigned
     * @param random      source of randomness, used only for {@link SeedingMode#RANDOM}
     */
    public static BracketEngine create(List<Track> tracks, SeedingMode seedingMode, Random random) {
        if (tracks == null || tracks.isEmpty()) {
            throw new IllegalArgumentException("Need at least one track to build a bracket");
        }
        List<Track> seeded = new ArrayList<>(tracks);
        switch (seedingMode) {
            case RANDOM -> Collections.shuffle(seeded, random);
            case ORIGINAL_ORDER -> { /* keep as given */ }
        }

        // Example: 5 tracks give a bracket of 8, so seeds 6, 7 and 8 do not exist.
        // Their slots stay null and the opponents they would have faced get byes.
        int bracketSize = nextPowerOfTwo(seeded.size());
        List<Integer> seedOrder = standardSeedOrder(bracketSize);

        // seedOrder holds seed numbers (1-based) in bracket-slot order.
        // Seed numbers beyond the real track count are byes (null track).
        Track[] slots = new Track[bracketSize];
        for (int slotIndex = 0; slotIndex < bracketSize; slotIndex++) {
            int seedNumber = seedOrder.get(slotIndex);
            slots[slotIndex] = (seedNumber <= seeded.size()) ? seeded.get(seedNumber - 1) : null;
        }

        BracketEngine engine = new BracketEngine();
        engine.totalRealTracks = seeded.size();
        List<Matchup> round1 = new ArrayList<>();
        // Slots are paired as (0,1), (2,3), ... so adjacent slots meet in round 1.
        for (int i = 0; i < bracketSize; i += 2) {
            Track a = slots[i];
            Track b = (i + 1 < bracketSize) ? slots[i + 1] : null;
            // The first slot of each pair holds the lower seed, and more than half the seeds are
            // real tracks, so "a" is never null in practice. This branch is only a guard.
            if (a == null) {
                round1.add(new Matchup(b, null)); // b gets the bye
            } else {
                round1.add(new Matchup(a, b)); // if b is also null, a gets the bye
            }
        }
        engine.rounds.add(round1);
        return engine;
    }

    /** Smallest power of two >= n. */
    static int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }

    /**
     * Standard bracket seeding order: for size N, returns seed numbers 1..N in the
     * order they should be placed into bracket slots so that seed 1 and 2 can only
     * meet in the final, seeds 1-4 can't meet before the semifinal, etc.
     */
    static List<Integer> standardSeedOrder(int size) {
        List<Integer> order = new ArrayList<>();
        order.add(1);
        while (order.size() < size) {
            // Each pass doubles the bracket. Every seed s is followed by its complement
            // (sum - s), and the two always add up to the new size + 1:
            //   [1]  ->  [1, 2]  ->  [1, 4, 2, 3]  ->  [1, 8, 4, 5, 2, 7, 3, 6]
            // Seed 1 and seed 2 end up in opposite halves, so they can only meet in the final.
            int sum = order.size() * 2 + 1;
            List<Integer> next = new ArrayList<>(order.size() * 2);
            for (int seed : order) {
                next.add(seed);
                next.add(sum - seed);
            }
            order = next;
        }
        return order;
    }

    /** Matchups in the current round that still need a winner picked (byes are pre-decided). */
    public List<Matchup> getCurrentRoundPendingMatchups() {
        List<Matchup> pending = new ArrayList<>();
        for (Matchup m : rounds.get(currentRound)) {
            if (!m.isDecided()) pending.add(m);
        }
        return pending;
    }

    public List<Matchup> getCurrentRoundMatchups() {
        return Collections.unmodifiableList(rounds.get(currentRound));
    }

    public int getCurrentRoundNumber() {
        return currentRound + 1;
    }

    public int getTotalRounds() {
        // Round 1 has bracketSize / 2 matchups, so size * 2 is a power of two. Its number of
        // trailing zero bits is log2(bracketSize), which is the number of rounds.
        // (A single track reports 1 round.)
        return Integer.numberOfTrailingZeros(rounds.get(0).size() * 2);
    }

    /** Records a winner for one matchup in the current round. */
    public void choose(Matchup matchup, Track winner) {
        if (!rounds.get(currentRound).contains(matchup)) {
            throw new IllegalArgumentException("That matchup isn't part of the current round");
        }
        matchup.choose(winner);
    }

    /** True once every matchup in the current round has a decided winner. */
    public boolean isCurrentRoundComplete() {
        return rounds.get(currentRound).stream().allMatch(Matchup::isDecided);
    }

    /** True once the bracket has produced a single champion. */
    public boolean isComplete() {
        return isCurrentRoundComplete() && rounds.get(currentRound).size() == 1;
    }

    /**
     * Advances to the next round, pairing up winners in order. Call only when
     * {@link #isCurrentRoundComplete()} is true and {@link #isComplete()} is false.
     */
    public void advanceRound() {
        if (!isCurrentRoundComplete()) {
            throw new IllegalStateException("Current round still has undecided matchups");
        }
        if (isComplete()) {
            throw new IllegalStateException("Bracket is already complete");
        }
        List<Matchup> current = rounds.get(currentRound);
        List<Matchup> next = new ArrayList<>();
        for (int i = 0; i < current.size(); i += 2) {
            // Winners of matchups 0 and 1 meet, then 2 and 3, and so on. This follows the
            // slot order laid out in create().
            Track a = current.get(i).getWinner();
            Track b = current.get(i + 1).getWinner();
            next.add(new Matchup(a, b));
        }
        rounds.add(next);
        currentRound++;
    }

    public Track getChampion() {
        if (!isComplete()) {
            throw new IllegalStateException("Bracket isn't complete yet");
        }
        return rounds.get(currentRound).get(0).getWinner();
    }

    /**
     * Full finishing order, champion first, ties broken by elimination round
     * (tracks eliminated in the same round are listed in bracket order).
     */
    public List<Track> getFinalRanking() {
        if (!isComplete()) {
            throw new IllegalStateException("Bracket isn't complete yet");
        }
        List<Track> ranking = new ArrayList<>();
        // Walk from the final back to round 1. Whoever lost in a later round ranks
        // higher: the runner-up comes first, then the two semifinal losers, and so on.
        // Losers from the same round keep their position in the bracket.
        ranking.add(getChampion());
        for (int r = rounds.size() - 1; r >= 0; r--) {
            for (Matchup m : rounds.get(r)) {
                if (!m.isBye() && m.getLoser() != null) {
                    ranking.add(m.getLoser());
                }
            }
        }
        return ranking;
    }

    /** All rounds played so far, including in-progress ones. Useful for persistence/rendering. */
    public List<List<Matchup>> getAllRounds() {
        return Collections.unmodifiableList(rounds);
    }

    /**
     * A single-elimination bracket among N real tracks always needs exactly N-1 real
     * comparisons to produce a champion, no matter how the byes padded it out to a power
     * of two. Each real comparison eliminates exactly one real track.
     */
    public int getTotalComparisonCount() {
        return Math.max(0, totalRealTracks - 1);
    }

    public int getCompletedComparisonCount() {
        int count = 0;
        for (List<Matchup> round : rounds) {
            for (Matchup m : round) {
                if (!m.isBye() && m.isDecided()) count++;
            }
        }
        return count;
    }
}
