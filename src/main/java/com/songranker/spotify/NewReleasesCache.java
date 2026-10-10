package com.songranker.spotify;

import com.songranker.spotify.dto.NewRelease;
import com.songranker.spotify.dto.SpotifyArtist;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Caches the Home screen's "New Releases" feed to disk, since building it means checking dozens of
 * artists' discographies, which is expensive against Spotify's shared, tight Development Mode quota. Without
 * this, every app launch (or every Home screen visit) would re-run that whole sweep from scratch.
 */
public final class NewReleasesCache {

    private static final Path CACHE_FILE = Paths.get(System.getProperty("user.home"),
            ".song-ranker", "new-releases-cache.json");
    // Long enough that repeated app launches/dev testing in the same day don't keep re-running the
    // expensive multi-artist sweep and re-consuming Spotify's tight, slow-to-recover dev-mode quota.
    private static final long MAX_AGE_MS = 24L * 60 * 60 * 1000;

    private final ObjectMapper mapper = new ObjectMapper();

    /** The cached feed, or null if there's no cache or it's older than {@link #MAX_AGE_MS}. */
    public List<NewRelease> loadIfFresh() {
        CacheFile cache = loadRaw();
        if (cache == null) return null;
        if (System.currentTimeMillis() - cache.fetchedAtEpochMs > MAX_AGE_MS) return null;
        return toReleases(cache);
    }

    /** The cached feed regardless of age. A stale result beats an empty one when a live refresh fails. */
    public List<NewRelease> loadStale() {
        CacheFile cache = loadRaw();
        return cache == null ? null : toReleases(cache);
    }

    public void save(List<NewRelease> releases) {
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            CacheFile cache = new CacheFile();
            cache.fetchedAtEpochMs = System.currentTimeMillis();
            for (NewRelease r : releases) {
                Entry e = new Entry();
                e.albumId = r.albumId;
                e.albumName = r.albumName;
                e.imageUrl = r.imageUrl;
                e.releaseDate = r.releaseDate;
                e.artistId = r.artist.id;
                e.artistName = r.artist.name;
                e.artistImageUrl = r.artist.imageUrl;
                cache.releases.add(e);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(CACHE_FILE.toFile(), cache);
        } catch (IOException e) {
            // best-effort; worst case the next load re-fetches
        }
    }

    private CacheFile loadRaw() {
        try {
            if (!Files.exists(CACHE_FILE)) return null;
            return mapper.readValue(CACHE_FILE.toFile(), CacheFile.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<NewRelease> toReleases(CacheFile cache) {
        List<NewRelease> result = new ArrayList<>();
        for (Entry e : cache.releases) {
            result.add(new NewRelease(e.albumId, e.albumName, e.imageUrl, e.releaseDate,
                    new SpotifyArtist(e.artistId, e.artistName, e.artistImageUrl)));
        }
        return result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class CacheFile {
        public long fetchedAtEpochMs;
        public List<Entry> releases = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Entry {
        public String albumId;
        public String albumName;
        public String imageUrl;
        public String releaseDate;
        public String artistId;
        public String artistName;
        public String artistImageUrl;
    }
}
