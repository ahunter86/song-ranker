package com.songranker.spotify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Remembers which artists the New Releases sweep has checked (oldest first), so refreshes rotate through them. */
public final class CheckedArtistsHistory {

    private static final Path FILE = Paths.get(System.getProperty("user.home"),
            ".song-ranker", "new-releases-checked.json");
    private static final int MAX_REMEMBERED = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    public List<String> load() {
        try {
            if (!Files.exists(FILE)) return new ArrayList<>();
            return mapper.readValue(FILE.toFile(), new TypeReference<List<String>>() { });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Marks these ids as just checked (moving any earlier entry to the end) and persists, best-effort. */
    public void record(List<String> checkedNow) {
        List<String> history = load();
        // Remove, then append: an artist checked again moves to the end, so the front of
        // the list is always the one checked longest ago.
        history.removeAll(checkedNow);
        history.addAll(checkedNow);
        if (history.size() > MAX_REMEMBERED) history = new ArrayList<>(history.subList(history.size() - MAX_REMEMBERED, history.size()));
        try {
            Files.createDirectories(FILE.getParent());
            mapper.writeValue(FILE.toFile(), history);
        } catch (IOException ignored) {
            // best-effort; worst case, the rotation restarts
        }
    }
}
