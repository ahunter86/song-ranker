package com.songranker.domain;

import java.util.Objects;

/**
 * A single song, as pulled from Spotify (or any future source).
 * Immutable value object. Identity is the Spotify track id.
 */
public final class Track {

    private final String id;
    private final String name;
    private final String albumName;
    private final String artistName;
    private final int durationMs;
    private final String spotifyUri;
    private final String albumArtUrl;

    public Track(String id, String name, String albumName, String artistName,
                 int durationMs, String spotifyUri, String albumArtUrl) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.albumName = albumName;
        this.artistName = artistName;
        this.durationMs = durationMs;
        this.spotifyUri = spotifyUri;
        this.albumArtUrl = albumArtUrl;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getAlbumName() { return albumName; }
    public String getArtistName() { return artistName; }
    public int getDurationMs() { return durationMs; }
    public String getSpotifyUri() { return spotifyUri; }
    public String getAlbumArtUrl() { return albumArtUrl; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Track)) return false;
        return id.equals(((Track) o).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return name + " (" + albumName + ")";
    }
}
