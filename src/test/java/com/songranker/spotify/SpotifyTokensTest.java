package com.songranker.spotify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpotifyTokensTest {

    @Test
    void tokensWithoutRecordedScopesDoNotCoverAnything() {
        assertFalse(new SpotifyTokens().grants("a"));
    }

    @Test
    void grantsOnlyWhenEveryRequiredScopeIsPresent() {
        SpotifyTokens t = new SpotifyTokens();
        t.scope = "a b  c";
        assertTrue(t.grants("a c"));
        assertFalse(t.grants("a d"));
    }

    @Test
    void currentAppScopesAreCoveredByATokenGrantedExactlyThose() {
        SpotifyTokens t = new SpotifyTokens();
        t.scope = SpotifyConfig.SCOPES;
        assertTrue(t.grants(SpotifyConfig.SCOPES));
    }
}
