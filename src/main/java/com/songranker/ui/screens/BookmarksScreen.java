package com.songranker.ui.screens;

import com.songranker.persistence.BookmarkRepository.Bookmark;
import com.songranker.spotify.dto.SpotifyArtist;
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

public final class BookmarksScreen extends VBox {

    public BookmarksScreen(AppContext ctx) {
        setSpacing(12);
        setPadding(new Insets(24));

        Label title = new Label("Saved albums");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        Label hint = new Label("Bookmark albums from the album list or the new releases row on Home, "
                + "then rank them here whenever you're ready.");
        hint.setWrapText(true);
        hint.getStyleClass().add("subtle-text");

        ListView<Bookmark> list = new ListView<>();
        list.setPrefHeight(400);
        list.setPlaceholder(new Label("No bookmarks yet."));
        list.getItems().setAll(ctx.bookmarkRepository.findAll());
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Bookmark b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String year = b.releaseDate() != null && b.releaseDate().length() >= 4
                            ? "  (" + b.releaseDate().substring(0, 4) + ")" : "";
                    HBox row = new HBox(10, AlbumArt.thumbnail(b.imageUrl(), 40),
                            new Label(b.albumName() + "  ·  " + b.artistName() + year));
                    row.setAlignment(Pos.CENTER_LEFT);
                    setText(null);
                    setGraphic(row);
                }
            }
        });

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setMaxSize(24, 24);

        Button backButton = new Button("Back to home");
        backButton.getStyleClass().add("accent-button");
        backButton.setOnAction(e -> ctx.navigate(new HomeScreen(ctx)));
        Shortcuts.escapeFires(this, backButton);

        Button rankButton = new Button("Rank this album");
        rankButton.getStyleClass().add("accent-button");
        Button removeButton = new Button("Remove");
        removeButton.getStyleClass().add("accent-button");

        Runnable rank = () -> {
            Bookmark b = list.getSelectionModel().getSelectedItem();
            if (b == null) return;
            spinner.setVisible(true);
            rankButton.setDisable(true);
            Background.run(
                    () -> ctx.apiClient.getAlbumTracks(b.albumId(), b.artistName(), b.albumName(), b.imageUrl()),
                    tracks -> {
                        spinner.setVisible(false);
                        rankButton.setDisable(false);
                        ScopeScreen.proceedOrWarn(ctx, new SpotifyArtist(b.artistId(), b.artistName(), null),
                                b.albumName(), tracks, () -> ctx.navigate(new BookmarksScreen(ctx)));
                    },
                    error -> {
                        spinner.setVisible(false);
                        rankButton.setDisable(false);
                        Background.showError(error);
                    });
        };
        rankButton.setOnAction(e -> rank.run());
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) rank.run();
        });
        removeButton.setOnAction(e -> {
            Bookmark b = list.getSelectionModel().getSelectedItem();
            if (b == null) return;
            ctx.bookmarkRepository.remove(b.albumId());
            list.getItems().remove(b);
        });

        HBox navRow = new HBox(8, backButton, rankButton, removeButton, spinner);
        navRow.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(title, hint, list, navRow);
    }
}
