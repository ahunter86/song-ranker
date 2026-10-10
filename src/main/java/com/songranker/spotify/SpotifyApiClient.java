package com.songranker.spotify;

import com.songranker.domain.Track;
import com.songranker.spotify.dto.NewRelease;
import com.songranker.spotify.dto.SpotifyAlbum;
import com.songranker.spotify.dto.SpotifyArtist;
import com.songranker.spotify.dto.SpotifyPlaylist;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin wrapper over the parts of the Spotify Web API this app needs: search,
 * artist discography, album tracks, and playback control. All calls require a
 * valid access token from {@link SpotifyAuthService}.
 */
public final class SpotifyApiClient {

    private static final String BASE = "https://api.spotify.com/v1";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final SpotifyAuthService authService;

    public SpotifyApiClient(SpotifyAuthService authService) {
        this.authService = authService;
    }

    /** The logged-in user's display name (falls back to their Spotify id if they haven't set one). */
    public String getCurrentUserDisplayName() throws IOException, InterruptedException {
        JsonNode json = get(BASE + "/me");
        String name = json.path("display_name").asText(null);
        return (name != null && !name.isBlank()) ? name : json.path("id").asText(null);
    }

    public List<SpotifyArtist> searchArtists(String query) throws IOException, InterruptedException {
        String url = BASE + "/search?type=artist&limit=10&q=" + enc(query);
        JsonNode json = get(url);
        List<SpotifyArtist> results = new ArrayList<>();
        for (JsonNode item : json.path("artists").path("items")) {
            String image = item.path("images").isArray() && item.path("images").size() > 0
                    ? item.path("images").get(0).path("url").asText(null) : null;
            results.add(new SpotifyArtist(item.get("id").asText(), item.get("name").asText(), image));
        }
        return results;
    }

    /**
     * All albums (and, optionally, singles) for an artist, deduplicated by
     * normalized title so deluxe editions / regional re-releases collapse into
     * one entry. A general listener wants "the album", not five copies of it.
     */
    public List<SpotifyAlbum> getArtistAlbums(String artistId, boolean includeSingles)
            throws IOException, InterruptedException {
        String includeGroups = includeSingles ? "album,single" : "album";
        Map<String, SpotifyAlbum> byNormalizedName = new LinkedHashMap<>();
        // Note: this endpoint's max `limit` is 10 (unlike most other Spotify list
        // endpoints, which allow 50). The pagination loop below still fetches every
        // page via the `next` link, it just takes more round trips now.
        // market=US only returns albums available in the US, so a listener in another
        // country can see a different set.
        String url = BASE + "/artists/" + artistId + "/albums?include_groups=" + includeGroups
                + "&limit=10&market=US";
        while (url != null) {
            JsonNode json = get(url);
            for (JsonNode item : json.path("items")) {
                String image = item.path("images").isArray() && item.path("images").size() > 0
                        ? item.path("images").get(0).path("url").asText(null) : null;
                SpotifyAlbum album = new SpotifyAlbum(
                        item.get("id").asText(),
                        item.get("name").asText(),
                        item.get("album_type").asText(),
                        item.path("release_date").asText(null),
                        image);
                String key = normalize(album.name);
                // Keep the earliest release (usually the original, not a deluxe/anniversary reissue).
                SpotifyAlbum existing = byNormalizedName.get(key);
                if (existing == null || compareReleaseDate(album, existing) < 0) {
                    byNormalizedName.put(key, album);
                }
            }
            // next is the full URL of the following page, or null on the last page.
            JsonNode next = json.path("next");
            url = next.isMissingNode() || next.isNull() ? null : next.asText();
        }
        return new ArrayList<>(byNormalizedName.values());
    }

    public List<Track> getAlbumTracks(String albumId, String artistName, String albumName, String albumArtUrl)
            throws IOException, InterruptedException {
        List<Track> tracks = new ArrayList<>();
        String url = BASE + "/albums/" + albumId + "/tracks?limit=50&market=US";
        while (url != null) {
            JsonNode json = get(url);
            for (JsonNode item : json.path("items")) {
                tracks.add(toTrack(item, artistName, albumName, albumArtUrl));
            }
            JsonNode next = json.path("next");
            url = next.isMissingNode() || next.isNull() ? null : next.asText();
        }
        return tracks;
    }

    /** All tracks across every (deduplicated) album for an artist, used by "entire discography" mode. */
    public List<Track> getArtistAllTracks(String artistId, String artistName, boolean includeSingles)
            throws IOException, InterruptedException {
        List<Track> all = new ArrayList<>();
        for (SpotifyAlbum album : getArtistAlbums(artistId, includeSingles)) {
            all.addAll(getAlbumTracks(album.id, artistName, album.name, album.imageUrl));
        }
        return all;
    }

    /**
     * Playlists the user owns or collaborates on. Spotify only allows reading the items of those
     * (a 403 for anyone else's playlist, including ones you merely follow), so the rest are hidden
     * rather than listed and then failing. Requires {@code playlist-read-private}.
     */
    public List<SpotifyPlaylist> getRankablePlaylists() throws IOException, InterruptedException {
        String myId = get(BASE + "/me").path("id").asText(null);
        List<SpotifyPlaylist> playlists = new ArrayList<>();
        String url = BASE + "/me/playlists?limit=50";
        while (url != null) {
            JsonNode json = get(url);
            for (JsonNode item : json.path("items")) {
                if (item.isNull() || item.path("id").isMissingNode()) continue;
                boolean mine = myId != null && myId.equals(item.path("owner").path("id").asText(null));
                if (!mine && !item.path("collaborative").asBoolean(false)) continue;
                String image = item.path("images").isArray() && item.path("images").size() > 0
                        ? item.path("images").get(0).path("url").asText(null) : null;
                // Playlist size comes from items.total, with tracks.total as the fallback.
                int count = item.path("items").path("total").asInt(item.path("tracks").path("total").asInt(0));
                playlists.add(new SpotifyPlaylist(item.get("id").asText(), item.get("name").asText(), image, count));
            }
            JsonNode next = json.path("next");
            url = next.isMissingNode() || next.isNull() ? null : next.asText();
        }
        return playlists;
    }

    /** Every song in a playlist (episodes and local files are skipped because they can't be played or ranked here). */
    public List<Track> getPlaylistTracks(String playlistId) throws IOException, InterruptedException {
        List<Track> tracks = new ArrayList<>();
        String url = BASE + "/playlists/" + playlistId + "/items?limit=50&market=US";
        while (url != null) {
            JsonNode json = get(url);
            for (JsonNode entry : json.path("items")) {
                Track t = parsePlaylistEntry(entry);
                if (t != null) tracks.add(t);
            }
            JsonNode next = json.path("next");
            url = next.isMissingNode() || next.isNull() ? null : next.asText();
        }
        return tracks;
    }

    /** One playlist entry -> Track, or null for episodes, local files, and removed/unavailable items. */
    static Track parsePlaylistEntry(JsonNode entry) {
        JsonNode item = entry.path("item");
        if (item.isMissingNode() || item.isNull()) item = entry.path("track"); // older field name
        if (item.isMissingNode() || item.isNull()) return null;
        if (entry.path("is_local").asBoolean(false)) return null;
        if (!"track".equals(item.path("type").asText("track"))) return null;
        if (item.path("id").isMissingNode() || item.path("id").isNull()) return null;

        JsonNode album = item.path("album");
        String image = album.path("images").isArray() && album.path("images").size() > 0
                ? album.path("images").get(0).path("url").asText(null) : null;
        List<String> artistNames = new ArrayList<>();
        for (JsonNode a : item.path("artists")) artistNames.add(a.path("name").asText());
        String id = item.get("id").asText();
        return new Track(id, item.path("name").asText("Unknown"), album.path("name").asText(null),
                String.join(", ", artistNames), item.path("duration_ms").asInt(0),
                item.path("uri").asText("spotify:track:" + id), image);
    }

    /** Artists the user follows. Requires the {@code user-follow-read} scope. */
    public List<SpotifyArtist> getFollowedArtists() throws IOException, InterruptedException {
        List<SpotifyArtist> artists = new ArrayList<>();
        String url = BASE + "/me/following?type=artist&limit=50";
        while (url != null) {
            JsonNode artistsPage = get(url).path("artists");
            for (JsonNode item : artistsPage.path("items")) {
                artists.add(toArtist(item));
            }
            JsonNode next = artistsPage.path("next");
            url = next.isMissingNode() || next.isNull() ? null : next.asText();
        }
        return artists;
    }

    private static final List<String> TOP_ARTIST_TIME_RANGES = List.of("short_term", "medium_term", "long_term");

    /**
     * The artists the user listens to most for a given Spotify time range ({@code short_term} ~4
     * weeks, {@code medium_term} ~6 months, {@code long_term} ~years). Requires {@code user-top-read}.
     * A single time range's "top 50" is a relative ranking, so an artist you genuinely like but
     * don't play as obsessively as your true favorites can fall out of it entirely. See
     * {@link #getTopArtistsAcrossAllTimeRanges()}, which checks all three ranges for that reason.
     */
    public List<SpotifyArtist> getTopArtists(String timeRange) throws IOException, InterruptedException {
        List<SpotifyArtist> artists = new ArrayList<>();
        String url = BASE + "/me/top/artists?limit=50&time_range=" + enc(timeRange);
        while (url != null) {
            JsonNode json = get(url);
            for (JsonNode item : json.path("items")) {
                artists.add(toArtist(item));
            }
            JsonNode next = json.path("next");
            url = next.isMissingNode() || next.isNull() ? null : next.asText();
        }
        return artists;
    }

    /** Union of top artists across short/medium/long term, deduped by id. This is a wider net than one range alone. */
    public List<SpotifyArtist> getTopArtistsAcrossAllTimeRanges() throws IOException, InterruptedException {
        Map<String, SpotifyArtist> byId = new LinkedHashMap<>();
        for (String range : TOP_ARTIST_TIME_RANGES) {
            for (SpotifyArtist a : getTopArtists(range)) byId.putIfAbsent(a.id, a);
        }
        return new ArrayList<>(byId.values());
    }

    private static SpotifyArtist toArtist(JsonNode item) {
        String image = item.path("images").isArray() && item.path("images").size() > 0
                ? item.path("images").get(0).path("url").asText(null) : null;
        return new SpotifyArtist(item.get("id").asText(), item.get("name").asText(), image);
    }

    /** How recent a release has to be to count as "new" for the Home-screen feed. */
    public static final int NEW_RELEASE_WINDOW_DAYS = 60;

    /**
     * How many artists one refresh looks up. Each artist costs at least one call, plus one more
     * for every 10 albums, and Spotify's Development Mode limits are low and shared across the
     * whole developer account. Which artists are checked rotates between refreshes
     * (see {@link NewReleaseSelection}).
     */
    private static final int MAX_CANDIDATE_ARTISTS = 12;

    /**
     * Releases from the last {@link #NEW_RELEASE_WINDOW_DAYS} days by artists the user follows or
     * listens to a lot, newest first. Both sources are used because many listeners never follow
     * anyone. At most {@link #MAX_CANDIDATE_ARTISTS} artists are looked up per call; the rest
     * wait for a later refresh.
     */
    public List<NewRelease> getNewReleases(int maxResults) throws IOException, InterruptedException {
        List<SpotifyArtist> followed = getFollowedArtists();
        List<SpotifyArtist> topArtists = getTopArtistsAcrossAllTimeRanges();
        Map<String, SpotifyArtist> candidates = new LinkedHashMap<>();
        for (SpotifyArtist a : followed) candidates.putIfAbsent(a.id, a);
        for (SpotifyArtist a : topArtists) candidates.putIfAbsent(a.id, a);
        CheckedArtistsHistory history = new CheckedArtistsHistory();
        List<SpotifyArtist> toCheck = NewReleaseSelection.pickCandidates(
                new ArrayList<>(candidates.values()), history.load(), MAX_CANDIDATE_ARTISTS, new java.util.Random());
        System.out.println("[new-releases] followed artists: " + followed.size() + ", top artists (all time ranges): "
                + topArtists.size() + ", unique candidates: " + candidates.size() + ", checking: " + toCheck.size());
        if (toCheck.isEmpty()) return List.of();

        // Three threads overlap network waits. Requests are still spaced out by the
        // shared throttle in sendWithRetry().
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(3, toCheck.size()));
        try {
            java.time.LocalDate cutoff = java.time.LocalDate.now().minusDays(NEW_RELEASE_WINDOW_DAYS);
            java.util.Map<String, Exception> failedIds = new java.util.concurrent.ConcurrentHashMap<>();
            List<CompletableFuture<NewRelease>> futures = toCheck.stream()
                    .map(artist -> CompletableFuture.supplyAsync(() -> latestReleaseFor(artist, failedIds), pool))
                    .toList();
            List<NewRelease> allLatest = futures.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList();
            // Only mark artists as "checked" if the lookup actually worked, so failures get retried next time.
            history.record(toCheck.stream().map(a -> a.id).filter(id -> !failedIds.containsKey(id)).toList());
            if (!toCheck.isEmpty() && failedIds.size() == toCheck.size()) {
                // Every lookup failed. That is an outage (usually rate limiting), not "no new
                // releases", so report it instead of caching an empty feed for a day.
                Exception cause = failedIds.values().iterator().next();
                if (cause instanceof IOException io) throw io;
                throw new IOException("Couldn't check any artists: " + cause.getMessage(), cause);
            }
            System.out.println("[new-releases] got a latest release for " + allLatest.size() + "/" + toCheck.size()
                    + " checked candidates; cutoff date is " + cutoff);
            for (NewRelease r : allLatest) {
                System.out.println("[new-releases]   " + r.artist.name + " -> \"" + r.albumName + "\" ("
                        + r.releaseDate + ")" + (isOnOrAfter(r.releaseDate, cutoff) ? "  [WITHIN WINDOW]" : ""));
            }
            return allLatest.stream()
                    .filter(r -> isOnOrAfter(r.releaseDate, cutoff))
                    .sorted(Comparator.comparing((NewRelease r) -> r.releaseDate, Comparator.reverseOrder()))
                    .limit(maxResults)
                    .toList();
        } finally {
            pool.shutdown();
        }
    }

    /** Best-effort: one artist's discography failing to load shouldn't sink the whole feed. */
    private NewRelease latestReleaseFor(SpotifyArtist artist, java.util.Map<String, Exception> failedIds) {
        try {
            SpotifyAlbum latest = null;
            for (SpotifyAlbum album : getArtistAlbums(artist.id, true)) {
                if (album.releaseDate == null) continue;
                // Release dates are ISO strings, so text order is date order.
                if (latest == null || album.releaseDate.compareTo(latest.releaseDate) > 0) latest = album;
            }
            return latest == null ? null
                    : new NewRelease(latest.id, latest.name, latest.imageUrl, latest.releaseDate, artist);
        } catch (Exception e) {
            failedIds.put(artist.id, e);
            System.out.println("[new-releases]   FAILED for " + artist.name + ": " + e);
            return null;
        }
    }

    /** Release dates without day precision (just "YYYY" or "YYYY-MM") are old catalog entries, never truly new. */
    static boolean isOnOrAfter(String releaseDate, java.time.LocalDate cutoff) {
        if (releaseDate == null || releaseDate.length() < 10) return false;
        try {
            return !java.time.LocalDate.parse(releaseDate).isBefore(cutoff);
        } catch (Exception e) {
            return false;
        }
    }

    // ---- Playback control ----

    /** Returns the device id of the user's currently active Spotify device, or null if none. */
    public String getActiveDeviceId() throws IOException, InterruptedException {
        JsonNode json = get(BASE + "/me/player/devices");
        for (JsonNode device : json.path("devices")) {
            if (device.path("is_active").asBoolean(false)) {
                return device.get("id").asText();
            }
        }
        // No active device, but return the first available one so playback can still be started on it.
        JsonNode devices = json.path("devices");
        return devices.size() > 0 ? devices.get(0).get("id").asText() : null;
    }

    /** Starts playback of a single track URI on the given device (or the current active device if null). */
    public void playTrack(String deviceId, String trackUri) throws IOException, InterruptedException {
        String url = BASE + "/me/player/play" + (deviceId != null ? "?device_id=" + enc(deviceId) : "");
        // Built by hand. Spotify track URIs hold only letters, digits and colons, so
        // nothing needs escaping.
        String body = "{\"uris\":[\"" + trackUri + "\"]}";
        putJson(url, body);
    }

    // Not called from anywhere at the moment.
    public void pausePlayback(String deviceId) throws IOException, InterruptedException {
        String url = BASE + "/me/player/pause" + (deviceId != null ? "?device_id=" + enc(deviceId) : "");
        putJson(url, "");
    }

    // ---- internals ----

    private Track toTrack(JsonNode item, String artistName, String albumName, String albumArtUrl) {
        String id = item.get("id").asText();
        String name = item.get("name").asText();
        int durationMs = item.path("duration_ms").asInt(0);
        String uri = item.path("uri").asText("spotify:track:" + id);
        return new Track(id, name, albumName, artistName, durationMs, uri, albumArtUrl);
    }

    // Key used to collapse reissues into one album: lower-case, drop a bracketed part
    // that mentions an edition word, then keep only letters and digits.
    // Example: "Punisher (Deluxe Edition)" and "Punisher" both become "punisher".
    // Known limit: the bracket patterns are loose, so "A (live) B (deluxe)" loses the "B" too.
    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("\\(.*?(deluxe|remaster|anniversary|edition|version|expanded).*?\\)", "")
                .replaceAll("\\[.*?(deluxe|remaster|anniversary|edition|version|expanded).*?\\]", "")
                .replaceAll("[^a-z0-9]+", "")
                .trim();
    }

    private static int compareReleaseDate(SpotifyAlbum a, SpotifyAlbum b) {
        // Dates are "YYYY", "YYYY-MM" or "YYYY-MM-DD", which sort correctly as text.
        // A missing date gets "9999" so a dated copy of the album always wins.
        String da = a.releaseDate == null ? "9999" : a.releaseDate;
        String db = b.releaseDate == null ? "9999" : b.releaseDate;
        return da.compareTo(db);
    }

    private JsonNode get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + authService.getValidAccessToken())
                .GET()
                .build();
        HttpResponse<String> response = sendWithRetry(request);
        throwIfRateLimited(response);
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Spotify API GET " + url + " failed (" + response.statusCode() + "): " + response.body());
        }
        return mapper.readTree(response.body());
    }

    private void putJson(String url, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + authService.getValidAccessToken())
                .header("Content-Type", "application/json")
                .method("PUT", HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = sendWithRetry(request);
        throwIfRateLimited(response);
        // 204 = success with no body. Anything outside 2xx is a real failure (e.g. no active device, no Premium).
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Spotify API PUT " + url + " failed (" + response.statusCode() + "): " + response.body());
        }
    }

    private static final int MAX_RATE_LIMIT_RETRIES = 3;
    private static final long MAX_RETRY_WAIT_MS = 30_000;

    // Global pacing across every request this client makes, regardless of how many threads are
    // calling concurrently. Per Spotify's own docs (developer.spotify.com/documentation/web-api/
    // concepts/rate-limits), the rate limit is "calculated based on the number of calls that your
    // app makes to Spotify in a rolling 30 second window", a separate, per-app, short-lived
    // mechanism from the account-wide quota (which has no published reset window). Since the exact
    // per-30-second cap for dev-mode apps isn't published, staying well under any plausible value
    // matters more than speed. 400ms keeps a full sweep to at most ~75 requests per 30s window.
    // Static/shared: the constraint is on Spotify's side per-credential, not per Java object.
    private static final Object THROTTLE_LOCK = new Object();
    private static long nextAllowedRequestAtMs = 0;
    private static final long MIN_REQUEST_INTERVAL_MS = 400;

    private static void throttle() throws InterruptedException {
        long waitMs;
        synchronized (THROTTLE_LOCK) {
            long now = System.currentTimeMillis();
            long scheduled = Math.max(now, nextAllowedRequestAtMs);
            waitMs = scheduled - now;
            nextAllowedRequestAtMs = scheduled + MIN_REQUEST_INTERVAL_MS;
        }
        if (waitMs > 0) Thread.sleep(waitMs);
    }

    private static void throwIfRateLimited(HttpResponse<String> response) throws SpotifyRateLimitException {
        if (response.statusCode() == 429) {
            throw new SpotifyRateLimitException(response.body(),
                    response.headers().firstValue("Retry-After").orElse(null));
        }
    }

    /** Retries a request on 429, honoring Spotify's {@code Retry-After} header instead of just failing outright. */
    private HttpResponse<String> sendWithRetry(HttpRequest request) throws IOException, InterruptedException {
        throttle();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int attempt = 0;
        // A wait longer than we're willing to sit through (quota exhaustion can say "hours") is
        // reported to the caller right away instead of burning all the retries on it.
        while (response.statusCode() == 429 && attempt < MAX_RATE_LIMIT_RETRIES
                && retryAfterMillis(response) <= MAX_RETRY_WAIT_MS) {
            long waitMs = Math.min(retryAfterMillis(response), MAX_RETRY_WAIT_MS);
            // The response body says which of Spotify's two separate 429 mechanisms this is:
            // a body containing "QUOTA_EXCEEDED" is the account-wide quota (no published reset
            // window); anything else here is the per-app rolling-30-second rate limit instead
            // (see developer.spotify.com/documentation/web-api/concepts/rate-limits vs quota-modes).
            System.out.println("[spotify] 429 (" + response.body() + "), waiting " + waitMs + "ms (retry "
                    + (attempt + 1) + "/" + MAX_RATE_LIMIT_RETRIES + ") for " + request.uri());
            Thread.sleep(waitMs);
            throttle();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            attempt++;
        }
        return response;
    }

    private static long retryAfterMillis(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After")
                .map(s -> {
                    try {
                        return Long.parseLong(s.trim()) * 1000L;
                    } catch (NumberFormatException e) {
                        return 2000L;
                    }
                })
                .orElse(2000L);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
