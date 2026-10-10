package com.songranker.domain;

/**
 * One head-to-head comparison in a bracket round.
 * {@code trackB} is null when {@code trackA} has a bye and auto-advances.
 */
public final class Matchup {

    private final Track trackA;
    private final Track trackB;
    private Track winner;

    public Matchup(Track trackA, Track trackB) {
        this.trackA = trackA;
        this.trackB = trackB;
        if (trackB == null && trackA != null) {
            this.winner = trackA; // bye auto-advances
        }
    }

    public Track getTrackA() { return trackA; }
    public Track getTrackB() { return trackB; }
    public boolean isBye() { return trackB == null; }
    public boolean isDecided() { return winner != null; }
    public Track getWinner() { return winner; }

    /** Records the user's pick. Must be trackA or trackB. */
    public void choose(Track winner) {
        if (isBye()) {
            throw new IllegalStateException("Bye matchups auto-advance and can't be chosen");
        }
        // Track.equals compares Spotify ids, so a Track rebuilt from saved state still matches.
        if (!winner.equals(trackA) && !winner.equals(trackB)) {
            throw new IllegalArgumentException("Winner must be one of the two tracks in this matchup");
        }
        this.winner = winner;
    }

    public Track getLoser() {
        if (winner == null || isBye()) return null;
        return winner.equals(trackA) ? trackB : trackA;
    }

    @Override
    public String toString() {
        return isBye()
                ? trackA + " (bye)"
                : trackA + " vs " + trackB + (isDecided() ? " -> " + winner : "");
    }
}
