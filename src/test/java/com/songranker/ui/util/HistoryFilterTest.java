package com.songranker.ui.util;

import com.songranker.persistence.RankingRunRepository.SavedRunSummary;
import com.songranker.ui.util.HistoryFilter.ModeFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryFilterTest {

    private static SavedRunSummary run(String artist, String scope, String mode) {
        return new SavedRunSummary(1, artist, scope, mode, "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z");
    }

    @Test
    void blankQueryAndAllModesAcceptsEverything() {
        assertTrue(HistoryFilter.matches(run("Phoebe Bridgers", "Punisher", "BRACKET"), "  ", ModeFilter.ALL));
    }

    @Test
    void searchIsCaseInsensitiveAndChecksArtistAndScope() {
        SavedRunSummary r = run("Phoebe Bridgers", "Punisher", "BRACKET");
        assertTrue(HistoryFilter.matches(r, "phoebe", ModeFilter.ALL));
        assertTrue(HistoryFilter.matches(r, "PUNISH", ModeFilter.ALL));
        assertFalse(HistoryFilter.matches(r, "gracie", ModeFilter.ALL));
    }

    @Test
    void everyTermMustMatchSomewhere() {
        SavedRunSummary r = run("Phoebe Bridgers", "Punisher", "BRACKET");
        assertTrue(HistoryFilter.matches(r, "bridgers punisher", ModeFilter.ALL));
        assertFalse(HistoryFilter.matches(r, "bridgers reputation", ModeFilter.ALL));
    }

    @Test
    void modeFilterUsesTheStoredModeNames() {
        assertTrue(HistoryFilter.matches(run("A", "B", "SWISS"), "", ModeFilter.PAIRWISE));
        assertFalse(HistoryFilter.matches(run("A", "B", "SWISS"), "", ModeFilter.BRACKET));
        assertTrue(HistoryFilter.matches(run("A", "B", "RATING"), "", ModeFilter.RATE));
    }

    @Test
    void searchCanUseTheFriendlyModeName() {
        assertTrue(HistoryFilter.matches(run("A", "B", "SWISS"), "pairwise", ModeFilter.ALL));
    }

    @Test
    void modeLabelsAreUserFacing() {
        assertEquals("Pairwise", HistoryFilter.modeLabel("SWISS"));
        assertEquals("Bracket", HistoryFilter.modeLabel("BRACKET"));
        assertEquals("Rate 0-10", HistoryFilter.modeLabel("RATING"));
    }
}
