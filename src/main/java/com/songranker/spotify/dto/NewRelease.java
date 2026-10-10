package com.songranker.spotify.dto;

/** The latest album/EP/single from one artist the user follows, for the Home screen's "New Releases" feed. */
public final class NewRelease {
    public String albumId;
    public String albumName;
    public String imageUrl;
    public String releaseDate;
    public SpotifyArtist artist;

    public NewRelease(String albumId, String albumName, String imageUrl, String releaseDate, SpotifyArtist artist) {
        this.albumId = albumId;
        this.albumName = albumName;
        this.imageUrl = imageUrl;
        this.releaseDate = releaseDate;
        this.artist = artist;
    }
}
