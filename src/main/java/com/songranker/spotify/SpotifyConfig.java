package com.songranker.spotify;

/**
 * App-level Spotify configuration. Set the SPOTIFY_CLIENT_ID environment variable to the
 * id of your own app from https://developer.spotify.com/dashboard. The login uses PKCE,
 * so there is no client secret to configure.
 *
 * <p>When registering the app on the Spotify dashboard, add this exact
 * redirect URI: {@code http://127.0.0.1:8888/callback} (Spotify requires a
 * loopback IP literal, not "localhost", and an exact string match).</p>
 */
public final class SpotifyConfig {

    /** Read from the SPOTIFY_CLIENT_ID environment variable. Empty when it is not set. */
    public static final String CLIENT_ID = System.getenv().getOrDefault("SPOTIFY_CLIENT_ID", "");

    public static final String REDIRECT_URI = "http://127.0.0.1:8888/callback";
    public static final int CALLBACK_PORT = 8888;

    /**
     * Permissions requested at login, each with the call that needs it.
     * See https://developer.spotify.com/documentation/web-api/concepts/scopes
     */
    public static final String SCOPES = String.join(" ",
            // GET /me/player/devices, to find a device to play on.
            "user-read-playback-state",
            // PUT /me/player/play, to start a song.
            "user-modify-playback-state",
            // No call uses this one at the moment.
            "user-read-currently-playing",
            // GET /me/following, one source of artists for New Releases.
            "user-follow-read",
            // GET /me/top/artists, the other source for New Releases.
            "user-top-read",
            // GET /me/playlists and /playlists/{id}/items, for ranking a playlist.
            "playlist-read-private"
    );

    private SpotifyConfig() {
    }
}
