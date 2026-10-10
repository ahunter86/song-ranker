package com.songranker.spotify;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Persists Spotify tokens to a small JSON file under the user's home directory. */
public final class TokenStore {

    // Plain JSON in the home directory. Anyone who can read this file can use the
    // tokens until they expire or the app is disconnected in Spotify's account settings.
    private static final Path TOKEN_FILE = Paths.get(System.getProperty("user.home"),
            ".song-ranker", "spotify-tokens.json");

    private final ObjectMapper mapper = new ObjectMapper();

    public SpotifyTokens load() {
        try {
            if (!Files.exists(TOKEN_FILE)) return null;
            return mapper.readValue(TOKEN_FILE.toFile(), SpotifyTokens.class);
        } catch (IOException e) {
            return null;
        }
    }

    public void save(SpotifyTokens tokens) {
        try {
            Files.createDirectories(TOKEN_FILE.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(TOKEN_FILE.toFile(), tokens);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save Spotify tokens to " + TOKEN_FILE, e);
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(TOKEN_FILE);
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
