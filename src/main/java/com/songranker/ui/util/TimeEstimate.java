package com.songranker.ui.util;

import com.songranker.domain.Track;

import java.util.List;

/**
 * Rough "how long will this take" estimates for the user, not precise playback
 * accounting. Just enough to set expectations before or during a ranking run.
 */
public final class TimeEstimate {

    private TimeEstimate() {
    }

    /** Average track length across a scope, used as a stand-in for "the next song's length". */
    public static double averageDurationMs(List<Track> tracks) {
        return tracks.stream().mapToInt(Track::getDurationMs).average().orElse(0);
    }

    /** Worst case: both songs in every remaining comparison play in full. */
    public static long estimateMillis(int comparisonsRemaining, double averageTrackDurationMs) {
        return Math.round(comparisonsRemaining * 2 * averageTrackDurationMs);
    }

    public static String formatDuration(long millis) {
        // Rounded to the nearest minute, so anything under 30 seconds reads as "under a minute".
        long totalMinutes = Math.round(millis / 60000.0);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0 && minutes > 0) return hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h";
        if (minutes > 0) return minutes + "m";
        return "under a minute";
    }
}
