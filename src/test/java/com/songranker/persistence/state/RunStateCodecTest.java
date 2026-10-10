package com.songranker.persistence.state;

import com.songranker.domain.BracketEngine;
import com.songranker.domain.Matchup;
import com.songranker.domain.SeedingMode;
import com.songranker.domain.SwissEngine;
import com.songranker.domain.Track;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * These exercise the actual resume path: capture a run's state mid-play, rebuild an engine from
 * it, and confirm the rebuild reproduces the exact same progress/outcome as the original. This is
 * the logic every "resume from History" flow in the app depends on.
 */
class RunStateCodecTest {

    private static Track track(int i) {
        return new Track("id" + i, "Track " + i, "Album", "Artist", 200_000, "spotify:track:id" + i, null);
    }

    private static List<Track> tracks(int n) {
        List<Track> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) list.add(track(i));
        return list;
    }

    @Test
    void rebuiltBracketResumesAtTheSamePointAsOriginal() {
        List<Track> input = tracks(6);
        long seed = 123L;
        BracketEngine original = BracketEngine.create(input, SeedingMode.ORIGINAL_ORDER, new Random(seed));

        for (Matchup m : original.getCurrentRoundPendingMatchups()) {
            original.choose(m, m.getTrackA());
        }
        original.advanceRound();

        RankingRunState state = RunStateCodec.captureBracket(original, input, SeedingMode.ORIGINAL_ORDER, seed);
        BracketEngine rebuilt = RunStateCodec.rebuildBracket(state);

        assertEquals(original.getCurrentRoundNumber(), rebuilt.getCurrentRoundNumber());
        assertEquals(original.getCompletedComparisonCount(), rebuilt.getCompletedComparisonCount());
        assertEquals(
                original.getCurrentRoundPendingMatchups().stream().map(m -> m.getTrackA().getId()).toList(),
                rebuilt.getCurrentRoundPendingMatchups().stream().map(m -> m.getTrackA().getId()).toList());
    }

    @Test
    void rebuiltBracketReproducesTheExactSameFinalRankingWhenPlayedToCompletion() {
        List<Track> input = tracks(5);
        long seed = 42L;
        BracketEngine original = BracketEngine.create(input, SeedingMode.RANDOM, new Random(seed));
        while (!original.isComplete()) {
            for (Matchup m : original.getCurrentRoundPendingMatchups()) {
                original.choose(m, m.getTrackA());
            }
            if (!original.isComplete()) original.advanceRound();
        }

        RankingRunState state = RunStateCodec.captureBracket(original, input, SeedingMode.RANDOM, seed);
        BracketEngine rebuilt = RunStateCodec.rebuildBracket(state);

        assertTrue(rebuilt.isComplete());
        assertEquals(
                original.getFinalRanking().stream().map(Track::getId).toList(),
                rebuilt.getFinalRanking().stream().map(Track::getId).toList());
    }

    @Test
    void rebuiltSwissResumesAtTheSamePointAsOriginal() {
        List<Track> input = tracks(7);
        long seed = 99L;
        SwissEngine original = SwissEngine.create(input, -1, new Random(seed));

        for (Matchup m : original.getCurrentRoundPendingMatchups()) {
            original.choose(m, m.getTrackA());
        }
        original.advanceRound();

        RankingRunState state = RunStateCodec.captureSwiss(original, input, seed, original.getTotalRounds());
        SwissEngine rebuilt = RunStateCodec.rebuildSwiss(state);

        assertEquals(original.getCurrentRoundNumber(), rebuilt.getCurrentRoundNumber());
        assertEquals(original.getCompletedComparisonCount(), rebuilt.getCompletedComparisonCount());
    }

    @Test
    void capturedStatePreservesRatingsAndNotesAcrossAJsonRoundTrip() {
        List<Track> input = tracks(4);
        long seed = 1L;
        BracketEngine engine = BracketEngine.create(input, SeedingMode.ORIGINAL_ORDER, new Random(seed));

        RankingRunState state = RunStateCodec.captureBracket(engine, input, SeedingMode.ORIGINAL_ORDER, seed);
        state.trackRatings.put("id1", 8.5);
        state.trackNotes.put("id1", "great hook");

        // Mirrors how RankingRunRepository actually persists and reloads state, via Jackson JSON.
        ObjectMapper mapper = new ObjectMapper();
        try {
            String json = mapper.writeValueAsString(state);
            RankingRunState reloaded = mapper.readValue(json, RankingRunState.class);
            assertEquals(8.5, reloaded.trackRatings.get("id1"));
            assertEquals("great hook", reloaded.trackNotes.get("id1"));
        } catch (Exception e) {
            fail(e);
        }
    }
}
