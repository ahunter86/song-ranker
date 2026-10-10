package com.songranker.ui.util;

import com.songranker.persistence.RankingRunRepository.SavedRunSummary;

import java.util.Locale;

/** Search + mode filtering for the History screen, kept free of UI so it's easy to test. */
public final class HistoryFilter {

    private HistoryFilter() {
    }

    public enum ModeFilter {
        ALL("All modes", null),
        BRACKET("Bracket", "BRACKET"),
        PAIRWISE("Pairwise", "SWISS"),
        RATE("Rate 0-10", "RATING");

        private final String label;
        private final String storedMode;

        ModeFilter(String label, String storedMode) {
            this.label = label;
            this.storedMode = storedMode;
        }

        public boolean accepts(String mode) {
            return storedMode == null || storedMode.equals(mode);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** User-facing name of a stored mode value ("SWISS" is shown as "Pairwise" everywhere in the app). */
    public static String modeLabel(String mode) {
        if (mode == null) return "";
        return switch (mode) {
            case "BRACKET" -> "Bracket";
            case "SWISS" -> "Pairwise";
            case "RATING" -> "Rate 0-10";
            default -> mode;
        };
    }

    /** Every whitespace-separated search term must appear in the artist, scope, or mode name. */
    public static boolean matches(SavedRunSummary run, String query, ModeFilter mode) {
        if (!mode.accepts(run.mode())) return false;
        if (query == null || query.isBlank()) return true;
        String haystack = (run.artistName() + " " + run.scopeDescription() + " " + modeLabel(run.mode()))
                .toLowerCase(Locale.ROOT);
        for (String term : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!haystack.contains(term)) return false;
        }
        return true;
    }
}
