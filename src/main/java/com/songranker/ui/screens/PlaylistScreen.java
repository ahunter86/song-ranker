package com.songranker.ui.screens;

import com.songranker.spotify.dto.SpotifyArtist;
import com.songranker.spotify.dto.SpotifyPlaylist;
import com.songranker.ui.AppContext;
import com.songranker.ui.util.Shortcuts;
import com.songranker.ui.util.AlbumArt;
import com.songranker.ui.util.Background;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class PlaylistScreen extends VBox {

    public PlaylistScreen(AppContext ctx) {
        setSpacing(12);
        setPadding(new Insets(24));

        Label title = new Label("Pick a playlist");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        Label note = new Label("Only playlists you created or collaborate on are listed. Spotify doesn't let "
                + "apps read the songs of playlists you only follow.");
        note.setWrapText(true);
        note.getStyleClass().add("subtle-text");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(24, 24);

        ListView<SpotifyPlaylist> list = new ListView<>();
        list.setPrefHeight(400);
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SpotifyPlaylist p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(p.toString());
                    setGraphic(AlbumArt.thumbnail(p.imageUrl, 40));
                }
            }
        });

        Button backButton = new Button("Back");
        backButton.getStyleClass().add("accent-button");
        backButton.setOnAction(e -> ctx.navigate(new HomeScreen(ctx)));
        Shortcuts.escapeFires(this, backButton);
        Button chooseButton = new Button("Choose playlist");
        chooseButton.getStyleClass().add("accent-button");

        Runnable choose = () -> {
            SpotifyPlaylist selected = list.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            spinner.setVisible(true);
            chooseButton.setDisable(true);
            Background.run(
                    () -> ctx.apiClient.getPlaylistTracks(selected.id),
                    tracks -> {
                        spinner.setVisible(false);
                        chooseButton.setDisable(false);
                        if (tracks.isEmpty()) {
                            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING,
                                    "That playlist has no rankable songs.").showAndWait();
                            return;
                        }
                        // A playlist stands in for the "artist" everywhere downstream (History, Results, sessions).
                        SpotifyArtist standIn = new SpotifyArtist(selected.id, selected.name, selected.imageUrl);
                        ctx.navigate(new TrackSelectionScreen(ctx, standIn, "Playlist", tracks,
                                () -> ctx.navigate(new PlaylistScreen(ctx))));
                    },
                    error -> {
                        spinner.setVisible(false);
                        chooseButton.setDisable(false);
                        Background.showError(error);
                    });
        };
        chooseButton.setOnAction(e -> choose.run());
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) choose.run();
        });

        HBox navRow = new HBox(8, backButton, chooseButton, spinner);
        navRow.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(title, note, list, navRow);

        Background.run(
                () -> ctx.apiClient.getRankablePlaylists(),
                playlists -> {
                    spinner.setVisible(false);
                    list.getItems().setAll(playlists);
                    if (playlists.isEmpty()) {
                        list.setPlaceholder(new Label("No playlists of yours found."));
                    }
                },
                error -> {
                    spinner.setVisible(false);
                    Background.showError(error);
                });
    }
}
