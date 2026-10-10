package com.songranker.ui.screens;

import com.songranker.persistence.BookmarkRepository;
import com.songranker.spotify.NewReleaseSelection;
import com.songranker.spotify.NewReleasesCache;
import com.songranker.spotify.SpotifyApiClient;
import com.songranker.spotify.dto.NewRelease;
import com.songranker.ui.AppContext;
import com.songranker.ui.util.AlbumArt;
import com.songranker.ui.util.Background;
import com.songranker.ui.util.Icons;
import com.songranker.ui.util.Theme;
import com.songranker.ui.util.ThemeManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

public final class HomeScreen extends BorderPane {

    public HomeScreen(AppContext ctx) {
        boolean connected = ctx.authService.isLoggedIn();

        setLeft(buildSidebar(ctx, connected));

        VBox content = new VBox(16);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));

        Label title = new Label("Song Ranker");
        title.setStyle("-fx-font-size: 32px; -fx-font-weight: bold;");
        Label subtitle = new Label("Rank your favorite artist's songs, one head-to-head at a time.");
        subtitle.setStyle("-fx-font-size: 14px;");
        subtitle.getStyleClass().add("subtle-text");

        Label statusLabel = new Label(connected ? "✓ Connected to Spotify" : "Not connected to Spotify");
        statusLabel.setStyle("-fx-font-size: 13px;");
        statusLabel.getStyleClass().add(connected ? "accent-text" : "subtle-text");

        Button startButton = new Button("Start a new ranking");
        startButton.setPrefWidth(260);
        startButton.setDefaultButton(true);
        startButton.setOnAction(e -> ctx.navigate(new ArtistSearchScreen(ctx)));

        Button playlistButton = new Button("Rank a playlist");
        playlistButton.setPrefWidth(260);
        playlistButton.getStyleClass().add("accent-button");
        playlistButton.setOnAction(e -> ctx.navigate(new PlaylistScreen(ctx)));

        VBox newReleasesSection = buildNewReleasesSection(ctx, connected);

        content.getChildren().addAll(title, subtitle, statusLabel, startButton, playlistButton, newReleasesSection);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setCenter(scrollPane);

        if (connected) {
            // Best-effort: upgrade the generic "Connected" label with the account name once it loads.
            Background.run(
                    () -> ctx.apiClient.getCurrentUserDisplayName(),
                    name -> {
                        if (name != null && !name.isBlank()) {
                            statusLabel.setText("✓ Connected to Spotify as " + name);
                        }
                    },
                    error -> { /* leave the generic "Connected" label as-is */ });
        }
    }

    /** A slim column of icon-only buttons for secondary/utility actions. Hover for the full name. */
    private VBox buildSidebar(AppContext ctx, boolean connected) {
        VBox sidebar = new VBox(12);
        sidebar.setAlignment(Pos.TOP_CENTER);
        sidebar.setPadding(new Insets(20, 10, 20, 10));
        sidebar.setPrefWidth(64);
        sidebar.getStyleClass().add("sidebar");

        Button historyButton = iconButton(Icons.clock(), "Resume or view history");
        historyButton.setOnAction(e -> ctx.navigate(new HistoryScreen(ctx)));

        Button bookmarksButton = iconButton(Icons.bookmark(), "Saved albums");
        bookmarksButton.setOnAction(e -> ctx.navigate(new BookmarksScreen(ctx)));

        Button statsButton = iconButton(Icons.chartBar(), "View your stats");
        statsButton.setOnAction(e -> ctx.navigate(new StatsScreen(ctx)));

        Button accountButton = iconButton(connected ? Icons.linkSlash() : Icons.link(),
                connected ? "Disconnect Spotify account" : "Connect Spotify account");
        accountButton.setOnAction(e -> {
            if (connected) {
                ctx.authService.logout();
                ctx.navigate(new HomeScreen(ctx));
            } else {
                accountButton.setDisable(true);
                Background.run(
                        () -> { ctx.authService.getValidAccessToken(); return null; },
                        v -> ctx.navigate(new HomeScreen(ctx)),
                        error -> {
                            accountButton.setDisable(false);
                            Background.showError(error);
                        });
            }
        });

        Button themeButton = iconButton(ThemeManager.current() == Theme.DARK ? Icons.sun() : Icons.moon(),
                ThemeManager.current() == Theme.DARK ? "Switch to light theme" : "Switch to dark theme");
        themeButton.setOnAction(e -> {
            ThemeManager.toggle(ctx.primaryStage.getScene());
            ctx.navigate(new HomeScreen(ctx));
        });

        sidebar.getChildren().addAll(historyButton, bookmarksButton, statsButton, accountButton, themeButton);
        return sidebar;
    }

    private static Button iconButton(Node icon, String tooltipText) {
        Button button = new Button();
        button.setGraphic(icon);
        button.setPrefSize(44, 44);
        button.setStyle("-fx-padding: 6;");
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setShowDelay(javafx.util.Duration.millis(150));
        button.setTooltip(tooltip);
        return button;
    }

    private VBox buildNewReleasesSection(AppContext ctx, boolean connected) {
        Label heading = new Label("New releases");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label statusLabel = new Label(connected
                ? "Loading new releases..."
                : "Connect Spotify to see new releases from artists you follow or listen to a lot.");
        statusLabel.getStyleClass().add("subtle-text");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(560);
        statusLabel.setAlignment(Pos.CENTER);
        statusLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        HBox cardsRow = new HBox(14);
        cardsRow.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scrollPane = new ScrollPane(cardsRow);
        scrollPane.setFitToHeight(true);
        scrollPane.setPrefViewportHeight(260);
        scrollPane.setPrefWidth(600);
        scrollPane.setMaxWidth(600);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        // Hidden, and out of the layout, until there are cards to show.
        scrollPane.setVisible(false);
        scrollPane.setManaged(false);

        VBox section = new VBox(8, heading, statusLabel, scrollPane);
        section.setAlignment(Pos.CENTER);

        if (connected) {
            NewReleasesCache cache = new NewReleasesCache();
            // A cache younger than 24 hours is shown as is, with no Spotify calls. Otherwise the
            // feed is refreshed, and the older cache is the fallback if that fails.
            List<NewRelease> fresh = cache.loadIfFresh();
            if (fresh != null) {
                showReleases(ctx, fresh, statusLabel, cardsRow, scrollPane, false);
            } else {
                Background.run(
                        () -> ctx.apiClient.getNewReleases(20),
                        releases -> {
                            // Each refresh checks a different set of artists, so keep still-recent finds from earlier rounds too.
                            List<NewRelease> merged = NewReleaseSelection.merge(releases, cache.loadStale(),
                                    java.time.LocalDate.now().minusDays(SpotifyApiClient.NEW_RELEASE_WINDOW_DAYS), 20);
                            cache.save(merged);
                            showReleases(ctx, merged, statusLabel, cardsRow, scrollPane, false);
                        },
                        error -> {
                            List<NewRelease> stale = cache.loadStale();
                            if (stale != null && !stale.isEmpty()) {
                                showReleases(ctx, stale, statusLabel, cardsRow, scrollPane, true);
                            } else {
                                String friendly = Background.rateLimitMessage(error);
                                statusLabel.setText(friendly != null ? friendly
                                        : "Couldn't load new releases: " + error.getMessage());
                            }
                        });
            }
        }

        return section;
    }

    private void showReleases(AppContext ctx, List<NewRelease> releases, Label statusLabel, HBox cardsRow,
                               ScrollPane scrollPane, boolean stale) {
        if (releases.isEmpty()) {
            statusLabel.setText("No new releases in the last " + SpotifyApiClient.NEW_RELEASE_WINDOW_DAYS + " days from artists "
                    + "you follow or listen to a lot.");
            return;
        }
        if (stale) {
            statusLabel.setText("Showing the list saved earlier "
                    + "because refreshing failed.");
        } else {
            statusLabel.setVisible(false);
            statusLabel.setManaged(false);
        }
        for (NewRelease release : releases) {
            cardsRow.getChildren().add(buildReleaseCard(ctx, release));
        }
        scrollPane.setVisible(true);
        scrollPane.setManaged(true);
    }

    private static VBox buildReleaseCard(AppContext ctx, NewRelease release) {
        VBox card = new VBox(6, AlbumArt.thumbnail(release.imageUrl, 140));
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(150);
        card.setMaxWidth(150);
        card.setStyle("-fx-cursor: hand;");

        Label nameLabel = new Label(release.albumName);
        nameLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-alignment: center;");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(150);
        nameLabel.setAlignment(Pos.CENTER);

        Label artistLabel = new Label(release.artist.name);
        artistLabel.setStyle("-fx-font-size: 12px; -fx-text-alignment: center;");
        artistLabel.setWrapText(true);
        artistLabel.setMaxWidth(150);
        artistLabel.setAlignment(Pos.CENTER);

        Button bookmarkButton = new Button();
        bookmarkButton.setStyle("-fx-padding: 4;");
        Runnable refresh = () -> {
            boolean saved = ctx.bookmarkRepository.isBookmarked(release.albumId);
            bookmarkButton.setGraphic(Icons.bookmark());
            bookmarkButton.setOpacity(saved ? 1.0 : 0.45);
            Tooltip tip = new Tooltip(saved ? "Remove bookmark" : "Bookmark for later");
            tip.setShowDelay(javafx.util.Duration.millis(150));
            bookmarkButton.setTooltip(tip);
        };
        refresh.run();
        bookmarkButton.setOnAction(e -> {
            if (ctx.bookmarkRepository.isBookmarked(release.albumId)) {
                ctx.bookmarkRepository.remove(release.albumId);
            } else {
                ctx.bookmarkRepository.add(new BookmarkRepository.Bookmark(release.albumId, release.albumName,
                        release.artist.id, release.artist.name, release.imageUrl, release.releaseDate));
            }
            refresh.run();
        });
        // The card starts a ranking on any mouse click. Clicking this button also sends a
        // MOUSE_CLICKED event up to the card, so it is consumed here. (Consuming the
        // ActionEvent above would not stop it.)
        bookmarkButton.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                javafx.scene.input.MouseEvent::consume);

        card.getChildren().addAll(nameLabel, artistLabel, bookmarkButton);
        card.setOnMouseClicked(e -> Background.run(
                () -> ctx.apiClient.getAlbumTracks(release.albumId, release.artist.name,
                        release.albumName, release.imageUrl),
                tracks -> ScopeScreen.proceedOrWarn(ctx, release.artist, release.albumName, tracks,
                        () -> ctx.navigate(new HomeScreen(ctx))),
                Background::showError));
        return card;
    }
}
