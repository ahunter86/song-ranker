package com.songranker.ui.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RatingFormatTest {

    @Test
    void wholeNumberHasNoTrailingDecimal() {
        assertEquals("7", RatingFormat.format(7.0));
    }

    @Test
    void fractionalKeepsDecimal() {
        assertEquals("7.5", RatingFormat.format(7.5));
    }

    @Test
    void zeroIsFormattedAsWholeNumber() {
        assertEquals("0", RatingFormat.format(0.0));
    }

    @Test
    void tenIsFormattedAsWholeNumber() {
        assertEquals("10", RatingFormat.format(10.0));
    }
}
