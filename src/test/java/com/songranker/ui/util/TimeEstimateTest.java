package com.songranker.ui.util;

import com.songranker.domain.Track;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeEstimateTest {

    private static Track track(int durationMs) {
        return new Track("id", "name", "album", "artist", durationMs, "uri", null);
    }

    @Test
    void averageDurationMsAveragesAcrossTracks() {
        List<Track> tracks = List.of(track(100_000), track(200_000), track(300_000));
        assertEquals(200_000.0, TimeEstimate.averageDurationMs(tracks));
    }

    @Test
    void averageDurationMsOfEmptyListIsZero() {
        assertEquals(0.0, TimeEstimate.averageDurationMs(List.of()));
    }

    @Test
    void estimateMillisDoublesForBothSongsInEachComparison() {
        // 3 comparisons, 100_000ms average track -> each comparison assumes both songs play in full
        assertEquals(600_000L, TimeEstimate.estimateMillis(3, 100_000));
    }

    @Test
    void estimateMillisOfZeroComparisonsIsZero() {
        assertEquals(0L, TimeEstimate.estimateMillis(0, 200_000));
    }

    @Test
    void formatDurationUnderAMinute() {
        assertEquals("under a minute", TimeEstimate.formatDuration(20_000));
    }

    @Test
    void formatDurationMinutesOnly() {
        assertEquals("45m", TimeEstimate.formatDuration(45 * 60_000L));
    }

    @Test
    void formatDurationHoursOnly() {
        assertEquals("2h", TimeEstimate.formatDuration(2 * 60 * 60_000L));
    }

    @Test
    void formatDurationHoursAndMinutes() {
        assertEquals("1h 30m", TimeEstimate.formatDuration(90 * 60_000L));
    }

    @Test
    void formatDurationRoundsToNearestMinute() {
        // 89.6 minutes rounds to 90 minutes -> "1h 30m", not "1h 29m"
        assertEquals("1h 30m", TimeEstimate.formatDuration((long) (89.6 * 60_000)));
    }
}
