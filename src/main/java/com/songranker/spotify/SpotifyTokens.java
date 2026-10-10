package com.songranker.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Access/refresh token pair persisted between runs so the user doesn't have to log in every time. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SpotifyTokens {

    @JsonProperty("access_token")
    public String accessToken;

    @JsonProperty("refresh_token")
    public String refreshToken;

    @JsonProperty("expires_at_epoch_seconds")
    public long expiresAtEpochSeconds;

    /** Space-separated scopes Spotify actually granted; null for tokens saved before this was tracked. */
    @JsonProperty("scope")
    public String scope;

    public SpotifyTokens() {
    }

    public SpotifyTokens(String accessToken, String refreshToken, int expiresInSeconds) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        // Refresh a little early to avoid a request failing right at the boundary.
        this.expiresAtEpochSeconds = Instant.now().getEpochSecond() + expiresInSeconds - 60;
    }

    /** True when every scope in {@code required} (space-separated) was granted. False if unknown. */
    public boolean grants(String required) {
        if (scope == null) return false;
        java.util.Set<String> granted = new java.util.HashSet<>(java.util.Arrays.asList(scope.trim().split("\s+")));
        return granted.containsAll(java.util.Arrays.asList(required.trim().split("\s+")));
    }

    public boolean isExpired() {
        return Instant.now().getEpochSecond() >= expiresAtEpochSeconds;
    }
}
