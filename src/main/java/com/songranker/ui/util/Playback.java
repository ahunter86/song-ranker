package com.songranker.ui.util;

import com.songranker.domain.Track;
import com.songranker.ui.AppContext;
import javafx.scene.control.Label;

/** Start playback of a song on the user's Spotify, and remember that they've now heard it. */
public final class Playback {

    private Playback() {
    }

    /**
     * Plays the track on the user's active Spotify device. On success the track is recorded as
     * listened and {@code onStarted} (may be null) runs on the UI thread.
     */
    public static void play(AppContext ctx, Track track, Runnable onStarted) {
        Background.run(
                () -> {
                    String deviceId = ctx.apiClient.getActiveDeviceId();
                    ctx.apiClient.playTrack(deviceId, track.getSpotifyUri());
                    return null;
                },
                v -> {
                    ctx.listenedRepository.markListened(track.getId());
                    if (onStarted != null) onStarted.run();
                },
                error -> {
                    if (Background.rateLimitMessage(error) != null) {
                        Background.showError(error);
                    } else {
                        Background.showError(new RuntimeException(
                                "Couldn't start playback (is Spotify open on a device, and is your account "
                                        + "Premium?): " + error.getMessage(), error));
                    }
                });
    }

    /** The small "Listened" marker shown under a song's Play button. */
    public static Label listenedBadge(boolean listened) {
        Label badge = new Label("✓ Listened");
        badge.getStyleClass().add("accent-text");
        badge.setStyle("-fx-font-size: 12px;");
        badge.setVisible(listened);
        badge.setManaged(listened);
        return badge;
    }

    public static void showBadge(Label badge) {
        badge.setVisible(true);
        badge.setManaged(true);
    }
}
