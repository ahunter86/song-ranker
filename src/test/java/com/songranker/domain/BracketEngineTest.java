package com.songranker.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BracketEngineTest {

    private static Track track(int i) {
        return new Track("id" + i, "Track " + i, "Album", "Artist", 200_000, "spotify:track:id" + i, null);
    }

    private static List<Track> tracks(int n) {
        List<Track> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) list.add(track(i));
        return list;
    }

    @Test
    void powerOfTwoSizesGetNoByes() {
        BracketEngine engine = BracketEngine.create(tracks(8), SeedingMode.ORIGINAL_ORDER, new Random(1));
        assertEquals(4, engine.getCurrentRoundMatchups().size());
        assertTrue(engine.getCurrentRoundMatchups().stream().noneMatch(Matchup::isBye));
    }

    @Test
    void nonPowerOfTwoGetsByesUpToNextPowerOfTwo() {
        // 5 tracks -> bracket size 8 -> 3 byes in round 1
        BracketEngine engine = BracketEngine.create(tracks(5), SeedingMode.ORIGINAL_ORDER, new Random(1));
        assertEquals(4, engine.getCurrentRoundMatchups().size());
        long byeCount = engine.getCurrentRoundMatchups().stream().filter(Matchup::isBye).count();
        assertEquals(3, byeCount);
    }

    @Test
    void singleTrackIsAlreadyChampion() {
        BracketEngine engine = BracketEngine.create(tracks(1), SeedingMode.ORIGINAL_ORDER, new Random(1));
        assertTrue(engine.isComplete());
        assertEquals(track(1), engine.getChampion());
    }

    @Test
    void playThroughFullBracketProducesCompleteRanking() {
        List<Track> input = tracks(6); // bracket size 8, 2 byes
        BracketEngine engine = BracketEngine.create(input, SeedingMode.ORIGINAL_ORDER, new Random(1));

        while (!engine.isComplete()) {
            for (Matchup m : engine.getCurrentRoundPendingMatchups()) {
                engine.choose(m, m.getTrackA()); // always pick "A" for determinism
            }
            if (!engine.isComplete()) {
                engine.advanceRound();
            }
        }

        List<Track> ranking = engine.getFinalRanking();
        assertEquals(6, ranking.size());
        assertEquals(engine.getChampion(), ranking.get(0));
        // no duplicates, every input track appears exactly once
        assertEquals(input.size(), ranking.stream().distinct().count());
        assertTrue(ranking.containsAll(input));
    }

    @Test
    void standardSeedOrderKeepsTopSeedsApart() {
        List<Integer> order = BracketEngine.standardSeedOrder(8);
        assertEquals(List.of(1, 8, 4, 5, 2, 7, 3, 6), order);
    }

    @Test
    void nextPowerOfTwoRoundsUpCorrectly() {
        assertEquals(1, BracketEngine.nextPowerOfTwo(1));
        assertEquals(8, BracketEngine.nextPowerOfTwo(5));
        assertEquals(8, BracketEngine.nextPowerOfTwo(8));
        assertEquals(16, BracketEngine.nextPowerOfTwo(9));
    }

    @Test
    void totalComparisonCountIsAlwaysOneLessThanRealTrackCountRegardlessOfByes() {
        // A single-elimination bracket among N real tracks always needs exactly N-1 real
        // comparisons, no matter how many byes pad it out to the next power of two.
        assertEquals(5, BracketEngine.create(tracks(6), SeedingMode.ORIGINAL_ORDER, new Random(1))
                .getTotalComparisonCount());
        assertEquals(4, BracketEngine.create(tracks(5), SeedingMode.ORIGINAL_ORDER, new Random(1))
                .getTotalComparisonCount());
        assertEquals(7, BracketEngine.create(tracks(8), SeedingMode.ORIGINAL_ORDER, new Random(1))
                .getTotalComparisonCount());
    }

    @Test
    void completedComparisonCountTracksRealChoicesOnlyNotByes() {
        BracketEngine engine = BracketEngine.create(tracks(6), SeedingMode.ORIGINAL_ORDER, new Random(1));
        assertEquals(0, engine.getCompletedComparisonCount());

        List<Matchup> pending = engine.getCurrentRoundPendingMatchups();
        engine.choose(pending.get(0), pending.get(0).getTrackA());
        assertEquals(1, engine.getCompletedComparisonCount());

        for (Matchup m : engine.getCurrentRoundPendingMatchups()) {
            engine.choose(m, m.getTrackA());
        }
        // Round 1 fully decided (including byes, which don't count). The completed count should
        // equal however many *real* (non-bye) matchups round 1 actually had.
        long realRound1Matchups = engine.getCurrentRoundMatchups().stream().filter(m -> !m.isBye()).count();
        assertEquals(realRound1Matchups, engine.getCompletedComparisonCount());
    }
}
