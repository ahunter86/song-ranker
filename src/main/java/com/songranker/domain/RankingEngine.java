package com.songranker.domain;

import java.util.List;

/**
 * Common surface shared by {@link BracketEngine} (single elimination) and
 * {@link SwissEngine} (pairwise comparison), so UI and persistence code can
 * drive either one without caring which ranking mode the user picked.
 */
public interface RankingEngine {

    List<Matchup> getCurrentRoundMatchups();

    List<Matchup> getCurrentRoundPendingMatchups();

    int getCurrentRoundNumber();

    int getTotalRounds();

    void choose(Matchup matchup, Track winner);

    boolean isCurrentRoundComplete();

    boolean isComplete();

    void advanceRound();

    /** Final ranking, most-preferred first. Only valid once {@link #isComplete()} is true. */
    List<Track> getFinalRanking();

    /** Total number of real (non-bye) head-to-head comparisons this run will require overall. */
    int getTotalComparisonCount();

    /** How many real (non-bye) comparisons have been decided so far. */
    int getCompletedComparisonCount();
}
