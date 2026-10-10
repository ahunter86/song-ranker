package com.songranker.ui.util;

/** Formats a 0-10 rating for display. Whole numbers show without a trailing ".0". */
public final class RatingFormat {

    private RatingFormat() {
    }

    public static String format(double rating) {
        // Math.rint rounds to the nearest whole number, so equality means there is no fraction.
        return rating == Math.rint(rating) ? String.valueOf((long) rating) : String.valueOf(rating);
    }
}
