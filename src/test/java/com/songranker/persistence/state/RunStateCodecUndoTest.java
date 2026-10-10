package com.songranker.persistence.state;

import com.songranker.domain.BracketEngine;
import com.songranker.domain.Matchup;
import com.songranker.domain.RankingEngine;
import com.songranker.domain.SeedingMode;
import com.songranker.domain.SwissEngine;
import com.songranker.domain.Track;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "Undo last pick" works by replaying every pick but the last, so it must restore that exact matchup. */
class RunStateCodecUndoTest {

    private static List<Track> tracks(int n) {
        List<Track> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add(new Track("id" + i, "Track " + i, "Album", "Artist", 200_000, "uri:" + i, null));
        }
        return list;
    }

    /** Plays {@code picks} real comparisons (always choosing side A); returns the ids of the last matchup decided. */
    private static List<String> play(RankingEngine engine, int picks) {
        List<String> last = List.of();
        int done = 0;
        while (done < picks && !engine.isComplete()) {
            List<Matchup> pending = engine.getCurrentRoundPendingMatchups();
            if (pending.isEmpty()) {
                engine.advanceRound();
                continue;
            }
            Matchup m = pending.get(0);
            last = List.of(m.getTrackA().getId(), m.getTrackB().getId());
            engine.choose(m, m.getTrackA());
            done++;
        }
        return last;
    }

    private static List<String> nextUndecided(RankingEngine engine) {
        List<Matchup> pending = engine.getCurrentRoundPendingMatchups();
        assertTrue(!pending.isEmpty(), "expected a pending matchup");
        return List.of(pending.get(0).getTrackA().getId(), pending.get(0).getTrackB().getId());
    }

    @Test
    void undoingABracketPickBringsBackThatMatchup() {
        List<Track> input = tracks(6);
        BracketEngine engine = BracketEngine.create(input, SeedingMode.ORIGINAL_ORDER, new Random(3));
        List<String> thirdMatchup = play(engine, 3);

        RankingRunState state = RunStateCodec.captureBracket(engine, input, SeedingMode.ORIGINAL_ORDER, 3);
        BracketEngine undone = RunStateCodec.rebuildBracket(RunStateCodec.withoutLastChoice(state));

        assertEquals(2, undone.getCompletedComparisonCount());
        assertEquals(thirdMatchup, nextUndecided(undone));
    }

    @Test
    void undoingASwissPickBringsBackThatMatchup() {
        List<Track> input = tracks(8);
        SwissEngine engine = SwissEngine.create(input, 3, new Random(9));
        List<String> secondMatchup = play(engine, 2);

        RankingRunState state = RunStateCodec.captureSwiss(engine, input, 9, 3);
        SwissEngine undone = RunStateCodec.rebuildSwiss(RunStateCodec.withoutLastChoice(state));

        assertEquals(1, undone.getCompletedComparisonCount());
        assertEquals(secondMatchup, nextUndecided(undone));
    }

    @Test
    void undoingAcrossARoundBoundaryRestoresTheLastMatchupOfThePreviousRound() {
        List<Track> input = tracks(8); // bracket: 4 matchups in round 1
        BracketEngine engine = BracketEngine.create(input, SeedingMode.ORIGINAL_ORDER, new Random(1));
        List<String> lastOfRoundOne = play(engine, 4);

        RankingRunState state = RunStateCodec.captureBracket(engine, input, SeedingMode.ORIGINAL_ORDER, 1);
        BracketEngine undone = RunStateCodec.rebuildBracket(RunStateCodec.withoutLastChoice(state));

        assertEquals(3, undone.getCompletedComparisonCount());
        assertEquals(1, undone.getCurrentRoundNumber());
        assertEquals(lastOfRoundOne, nextUndecided(undone));
    }

    @Test
    void undoKeepsRatingsAndNotesAndLeavesTheOriginalStateAlone() {
        List<Track> input = tracks(4);
        BracketEngine engine = BracketEngine.create(input, SeedingMode.ORIGINAL_ORDER, new Random(1));
        play(engine, 2);
        RankingRunState state = RunStateCodec.captureBracket(engine, input, SeedingMode.ORIGINAL_ORDER, 1);
        state.trackRatings.put("id1", 8.5);
        state.trackNotes.put("id1", "great bridge");
        int choicesBefore = state.winnerChoicesInOrder.size();

        RankingRunState trimmed = RunStateCodec.withoutLastChoice(state);

        assertEquals(choicesBefore - 1, trimmed.winnerChoicesInOrder.size());
        assertEquals(choicesBefore, state.winnerChoicesInOrder.size());
        assertEquals(8.5, trimmed.trackRatings.get("id1"));
        assertEquals("great bridge", trimmed.trackNotes.get("id1"));
    }
}
