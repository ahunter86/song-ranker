package com.songranker.spotify.dto;

public final class SpotifyArtist {
    public String id;
    public String name;
    public String imageUrl;

    public SpotifyArtist(String id, String name, String imageUrl) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
    }

    @Override
    public String toString() {
        return name;
    }
}
