package com.songranker.spotify;

import java.io.IOException;

/** A 429 from Spotify, with a plain-language explanation of what it means and how long to wait. */
public final class SpotifyRateLimitException extends IOException {

    private final boolean quotaExceeded;
    private final long retryAfterSeconds; // -1 when Spotify didn't say

    public SpotifyRateLimitException(String responseBody, String retryAfterHeader) {
        this(responseBody != null && responseBody.contains("QUOTA_EXCEEDED"), parseSeconds(retryAfterHeader));
    }

    SpotifyRateLimitException(boolean quotaExceeded, long retryAfterSeconds) {
        super(buildMessage(quotaExceeded, retryAfterSeconds));
        this.quotaExceeded = quotaExceeded;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public boolean isQuotaExceeded() {
        return quotaExceeded;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    static long parseSeconds(String header) {
        if (header == null) return -1;
        try {
            long seconds = Long.parseLong(header.trim());
            return seconds >= 0 ? seconds : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** "about 30 seconds", "about 5 minutes", "about 2 hours". */
    public static String describeWait(long seconds) {
        if (seconds < 90) return "about " + Math.max(seconds, 1) + " second" + (seconds == 1 ? "" : "s");
        long minutes = Math.round(seconds / 60.0);
        if (minutes < 60) return "about " + minutes + " minute" + (minutes == 1 ? "" : "s");
        long hours = Math.round(minutes / 60.0);
        return "about " + hours + " hour" + (hours == 1 ? "" : "s");
    }

    static String buildMessage(boolean quotaExceeded, long retryAfterSeconds) {
        String wait = retryAfterSeconds >= 0
                ? "Spotify says to try again in " + describeWait(retryAfterSeconds) + "."
                : "Spotify did not say how long to wait.";
        if (quotaExceeded) {
            return "This app has used up its Spotify allowance. The allowance is shared by every app on your "
                    + "developer account, and Spotify decides when it resets. " + wait
                    + " History, ratings and stats saved on this computer still work.";
        }
        return "Spotify is limiting how fast this app can ask for data. " + wait;
    }
}
