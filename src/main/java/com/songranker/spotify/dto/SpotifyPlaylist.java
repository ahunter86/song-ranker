package com.songranker.spotify.dto;

public final class SpotifyPlaylist {
    public final String id;
    public final String name;
    public final String imageUrl;
    public final int trackCount;

    public SpotifyPlaylist(String id, String name, String imageUrl, int trackCount) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.trackCount = trackCount;
    }

    @Override
    public String toString() {
        return name + "  (" + trackCount + " tracks)";
    }
}
