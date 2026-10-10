package com.songranker.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SwissEngineTest {

    private static Track track(int i) {
        return new Track("id" + i, "Track " + i, "Album", "Artist", 200_000, "spotify:track:id" + i, null);
    }

    private static List<Track> tracks(int n) {
        List<Track> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) list.add(track(i));
        return list;
    }

    @Test
    void playThroughProducesFullRankingWithNoDuplicates() {
        List<Track> input = tracks(7); // odd count -> exercises byes
        SwissEngine engine = SwissEngine.create(input, -1, new Random(42));

        while (!engine.isComplete()) {
            for (Matchup m : engine.getCurrentRoundPendingMatchups()) {
                engine.choose(m, m.getTrackA());
            }
            if (!engine.isComplete()) {
                engine.advanceRound();
            }
        }

        List<Track> ranking = engine.getFinalRanking();
        assertEquals(7, ranking.size());
        assertEquals(input.size(), ranking.stream().distinct().count());
        assertTrue(ranking.containsAll(input));
    }

    @Test
    void avoidsRematchesWhenPossible() {
        List<Track> input = tracks(8);
        SwissEngine engine = SwissEngine.create(input, 3, new Random(7));

        while (!engine.isComplete()) {
            for (Matchup m : engine.getCurrentRoundPendingMatchups()) {
                engine.choose(m, m.getTrackA());
            }
            if (!engine.isComplete()) {
                engine.advanceRound();
            }
        }
        assertEquals(3, engine.getTotalRounds());
    }

    @Test
    void resolveRoundsUsesTheRequestedValueWhenPositive() {
        assertEquals(3, SwissEngine.resolveRounds(8, 3));
    }

    @Test
    void resolveRoundsCapsTheRequestedValueAtTrackCountMinusOne() {
        assertEquals(7, SwissEngine.resolveRounds(8, 100));
    }

    @Test
    void resolveRoundsPicksASensibleDefaultWhenNotGivenAPositiveValue() {
        assertEquals(5, SwissEngine.resolveRounds(8, -1));
        assertEquals(5, SwissEngine.resolveRounds(8, 0));
    }

    @Test
    void totalComparisonCountIsRoundsTimesHalfTheTrackCount() {
        SwissEngine engine = SwissEngine.create(tracks(8), 3, new Random(1));
        assertEquals(12, engine.getTotalComparisonCount()); // 3 rounds * 4 pairs/round
    }

    @Test
    void completedComparisonCountTracksDecidedNonByeMatchupsOnly() {
        SwissEngine engine = SwissEngine.create(tracks(7), 2, new Random(1));
        assertEquals(0, engine.getCompletedComparisonCount());

        for (Matchup m : engine.getCurrentRoundPendingMatchups()) {
            engine.choose(m, m.getTrackA());
        }
        long round1RealMatchups = engine.getCurrentRoundMatchups().stream().filter(m -> !m.isBye()).count();
        assertEquals(round1RealMatchups, engine.getCompletedComparisonCount());
    }
}
