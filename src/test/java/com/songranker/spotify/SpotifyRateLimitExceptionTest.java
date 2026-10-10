package com.songranker.spotify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotifyRateLimitExceptionTest {

    @Test
    void describesWaitsInTheMostNaturalUnit() {
        assertEquals("about 1 second", SpotifyRateLimitException.describeWait(1));
        assertEquals("about 30 seconds", SpotifyRateLimitException.describeWait(30));
        assertEquals("about 5 minutes", SpotifyRateLimitException.describeWait(300));
        assertEquals("about 1 hour", SpotifyRateLimitException.describeWait(3600));
        assertEquals("about 3 hours", SpotifyRateLimitException.describeWait(3 * 3600));
    }

    @Test
    void quotaBodiesAreRecognizedAndExplainedDifferently() {
        var quota = new SpotifyRateLimitException("{\"reason\":\"QUOTA_EXCEEDED\"}", "7200");
        var burst = new SpotifyRateLimitException("{\"error\":\"API rate limit exceeded\"}", "12");
        assertTrue(quota.isQuotaExceeded());
        assertFalse(burst.isQuotaExceeded());
        assertTrue(quota.getMessage().contains("about 2 hours"));
        assertTrue(burst.getMessage().contains("about 12 seconds"));
        assertTrue(quota.getMessage().contains("allowance"));
        assertTrue(burst.getMessage().contains("limiting how fast"));
    }

    @Test
    void missingOrGarbageRetryAfterIsHandled() {
        assertEquals(-1, SpotifyRateLimitException.parseSeconds(null));
        assertEquals(-1, SpotifyRateLimitException.parseSeconds("soon"));
        assertEquals(-1, SpotifyRateLimitException.parseSeconds("-5"));
        assertEquals(45, SpotifyRateLimitException.parseSeconds(" 45 "));
        assertTrue(new SpotifyRateLimitException("", null).getMessage().contains("did not say how long"));
    }
}
