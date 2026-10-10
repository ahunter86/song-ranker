package com.songranker.persistence.state;

import com.songranker.domain.Track;

/** JSON-friendly mirror of {@link Track} for persistence. */
public final class SavedTrack {
    public String id;
    public String name;
    public String albumName;
    public String artistName;
    public int durationMs;
    public String spotifyUri;
    public String albumArtUrl;

    // Public fields and a no-argument constructor are what Jackson needs to read this
    // class back from JSON.
    public SavedTrack() {
    }

    public static SavedTrack fromDomain(Track t) {
        SavedTrack s = new SavedTrack();
        s.id = t.getId();
        s.name = t.getName();
        s.albumName = t.getAlbumName();
        s.artistName = t.getArtistName();
        s.durationMs = t.getDurationMs();
        s.spotifyUri = t.getSpotifyUri();
        s.albumArtUrl = t.getAlbumArtUrl();
        return s;
    }

    public Track toDomain() {
        return new Track(id, name, albumName, artistName, durationMs, spotifyUri, albumArtUrl);
    }
}
