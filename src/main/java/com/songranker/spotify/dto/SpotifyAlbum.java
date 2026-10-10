package com.songranker.spotify.dto;

public final class SpotifyAlbum {
    public String id;
    public String name;
    public String albumType; // "album", "single", "compilation"
    public String releaseDate;
    public String imageUrl;

    public SpotifyAlbum(String id, String name, String albumType, String releaseDate, String imageUrl) {
        this.id = id;
        this.name = name;
        this.albumType = albumType;
        this.releaseDate = releaseDate;
        this.imageUrl = imageUrl;
    }

    @Override
    public String toString() {
        return name;
    }
}
