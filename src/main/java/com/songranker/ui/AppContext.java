package com.songranker.ui;

import com.songranker.persistence.BookmarkRepository;
import com.songranker.persistence.ListenedRepository;
import com.songranker.persistence.RankingRunRepository;
import com.songranker.persistence.TrackRatingRepository;
import com.songranker.spotify.SpotifyApiClient;
import com.songranker.spotify.SpotifyAuthService;
import javafx.scene.Parent;
import javafx.stage.Stage;

/** Shared services and navigation, passed down to every screen. */
public final class AppContext {

    public final Stage primaryStage;
    public final SpotifyAuthService authService = new SpotifyAuthService();
    public final SpotifyApiClient apiClient = new SpotifyApiClient(authService);
    public final RankingRunRepository runRepository = new RankingRunRepository();
    public final TrackRatingRepository trackRatingRepository = new TrackRatingRepository();
    public final BookmarkRepository bookmarkRepository = new BookmarkRepository();
    public final ListenedRepository listenedRepository = new ListenedRepository();

    private final javafx.scene.layout.StackPane screenHost;

    public AppContext(Stage primaryStage, javafx.scene.layout.StackPane screenHost) {
        this.primaryStage = primaryStage;
        this.screenHost = screenHost;
    }

    public void navigate(Parent screen) {
        screenHost.getChildren().setAll(screen);
    }
}
