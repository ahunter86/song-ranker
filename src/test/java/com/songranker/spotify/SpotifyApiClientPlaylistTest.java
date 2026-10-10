package com.songranker.spotify;

import com.songranker.domain.Track;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpotifyApiClientPlaylistTest {

    private static Track parse(String json) throws Exception {
        return SpotifyApiClient.parsePlaylistEntry(new ObjectMapper().readTree(json));
    }

    @Test
    void parsesATrackFromTheItemField() throws Exception {
        Track t = parse("""
                {"is_local":false,"item":{"type":"track","id":"abc","name":"Song","uri":"spotify:track:abc",
                 "duration_ms":180000,"artists":[{"name":"A"},{"name":"B"}],
                 "album":{"name":"Alb","images":[{"url":"http://img"}]}}}""");
        assertEquals("abc", t.getId());
        assertEquals("A, B", t.getArtistName());
        assertEquals("Alb", t.getAlbumName());
        assertEquals("http://img", t.getAlbumArtUrl());
        assertEquals(180000, t.getDurationMs());
    }

    @Test
    void fallsBackToTheOlderTrackField() throws Exception {
        Track t = parse("""
                {"track":{"type":"track","id":"x","name":"Old","artists":[],"album":{"name":"Al"}}}""");
        assertEquals("x", t.getId());
    }

    @Test
    void skipsEpisodesLocalFilesAndRemovedItems() throws Exception {
        assertNull(parse("{\"item\":{\"type\":\"episode\",\"id\":\"e\",\"name\":\"Ep\"}}"));
        assertNull(parse("{\"is_local\":true,\"item\":{\"type\":\"track\",\"id\":null,\"name\":\"L\"}}"));
        assertNull(parse("{\"item\":null}"));
        assertNull(parse("{\"item\":{\"type\":\"track\",\"id\":null,\"name\":\"Gone\"}}"));
    }
}
