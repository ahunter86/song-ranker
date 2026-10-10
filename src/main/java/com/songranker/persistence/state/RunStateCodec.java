package com.songranker.persistence.state;

import com.songranker.domain.BracketEngine;
import com.songranker.domain.Matchup;
import com.songranker.domain.SeedingMode;
import com.songranker.domain.SwissEngine;
import com.songranker.domain.Track;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Rebuilds a {@link BracketEngine} or {@link SwissEngine} from a
 * {@link RankingRunState}, replaying every saved choice. Both engines are
 * seeded from the same {@link Random} instance and draw from it in the same
 * call order on every run, so replaying the exact same sequence of
 * choose()/advanceRound() calls reproduces the exact same pairings.
 */
public final class RunStateCodec {

    private RunStateCodec() {
    }

    public static RankingRunState captureBracket(BracketEngine engine, List<Track> originalInputOrder,
                                                   SeedingMode seedingMode, long randomSeed) {
        RankingRunState state = new RankingRunState();
        state.mode = "BRACKET";
        state.seedingMode = seedingMode.name();
        state.randomSeed = randomSeed;
        state.tracks = originalInputOrder.stream().map(SavedTrack::fromDomain).collect(Collectors.toList());
        state.winnerChoicesInOrder = collectChoices(engine.getAllRounds());
        return state;
    }

    public static RankingRunState captureSwiss(SwissEngine engine, List<Track> originalInputOrder,
                                                 long randomSeed, int swissRounds) {
        RankingRunState state = new RankingRunState();
        state.mode = "SWISS";
        state.seedingMode = SeedingMode.RANDOM.name(); // Swiss always shuffles its initial order
        state.randomSeed = randomSeed;
        state.swissRounds = swissRounds;
        state.tracks = originalInputOrder.stream().map(SavedTrack::fromDomain).collect(Collectors.toList());
        state.winnerChoicesInOrder = collectChoices(engine.getHistory());
        return state;
    }

    /** A copy of the state with the most recent pick removed. Replaying it undoes that pick. */
    public static RankingRunState withoutLastChoice(RankingRunState state) {
        RankingRunState copy = new RankingRunState();
        copy.mode = state.mode;
        copy.seedingMode = state.seedingMode;
        copy.randomSeed = state.randomSeed;
        copy.swissRounds = state.swissRounds;
        copy.tracks = new ArrayList<>(state.tracks);
        copy.winnerChoicesInOrder = new ArrayList<>(state.winnerChoicesInOrder);
        if (!copy.winnerChoicesInOrder.isEmpty()) {
            copy.winnerChoicesInOrder.remove(copy.winnerChoicesInOrder.size() - 1);
        }
        copy.trackRatings = new java.util.LinkedHashMap<>(state.trackRatings);
        copy.trackNotes = new java.util.LinkedHashMap<>(state.trackNotes);
        copy.currentOrder = new ArrayList<>(state.currentOrder);
        return copy;
    }

    // Choices are listed round by round, in matchup order. The play screen always asks
    // for the first undecided matchup, so this is also the order the user answered them.
    // replay() depends on that.
    private static List<String> collectChoices(List<List<Matchup>> rounds) {
        List<String> choices = new ArrayList<>();
        for (List<Matchup> round : rounds) {
            for (Matchup m : round) {
                if (!m.isBye() && m.isDecided()) {
                    choices.add(m.getWinner().getId());
                }
            }
        }
        return choices;
    }

    public static BracketEngine rebuildBracket(RankingRunState state) {
        List<Track> tracks = state.tracks.stream().map(SavedTrack::toDomain).collect(Collectors.toList());
        Map<String, Track> byId = tracks.stream().collect(Collectors.toMap(Track::getId, t -> t));
        BracketEngine engine = BracketEngine.create(tracks, SeedingMode.valueOf(state.seedingMode), new Random(state.randomSeed));
        replay(engine::getCurrentRoundPendingMatchups, engine::choose, engine::isCurrentRoundComplete,
                engine::isComplete, engine::advanceRound, state.winnerChoicesInOrder, byId);
        return engine;
    }

    public static SwissEngine rebuildSwiss(RankingRunState state) {
        List<Track> tracks = state.tracks.stream().map(SavedTrack::toDomain).collect(Collectors.toList());
        Map<String, Track> byId = tracks.stream().collect(Collectors.toMap(Track::getId, t -> t));
        SwissEngine engine = SwissEngine.create(tracks, state.swissRounds, new Random(state.randomSeed));
        replay(engine::getCurrentRoundPendingMatchups, engine::choose, engine::isCurrentRoundComplete,
                engine::isComplete, engine::advanceRound, state.winnerChoicesInOrder, byId);
        return engine;
    }

    private interface PendingSupplier { List<Matchup> get(); }
    private interface Chooser { void choose(Matchup m, Track winner); }
    private interface BoolSupplier { boolean get(); }
    private interface Advancer { void advance(); }

    private static void replay(PendingSupplier pending, Chooser chooser, BoolSupplier roundComplete,
                                BoolSupplier complete, Advancer advancer,
                                List<String> winnerIds, Map<String, Track> byId) {
        // Saved picks, oldest first. Each one is applied to the next undecided matchup.
        Deque<String> queue = new ArrayDeque<>(winnerIds);
        while (!queue.isEmpty() && !complete.get()) {
            List<Matchup> currentPending = pending.get();
            // The engines never advance a round on their own. The play screen calls
            // advanceRound() when a round is done, so replay has to do the same, at the
            // same points, or Swiss would pair the next round with different random draws.
            if (currentPending.isEmpty()) {
                if (roundComplete.get() && !complete.get()) {
                    advancer.advance();
                    continue;
                }
                // Not reachable with the current engines (no pending matchups means the round is
                // complete). Stops the loop if an engine ever reports otherwise.
                break;
            }
            // Pending matchups come back in the order they were first shown, so the nth saved
            // pick belongs to the nth matchup.
            for (Matchup m : currentPending) {
                if (queue.isEmpty()) break;
                Track winner = byId.get(queue.poll());
                chooser.choose(m, winner);
            }
            // That pick finished the round. If the saved picks ran out mid-round this is
            // false and the engine is left waiting on the next pick.
            if (roundComplete.get() && !complete.get()) {
                advancer.advance();
            }
        }
    }
}
