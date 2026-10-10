package com.songranker.ui.screens;

import com.songranker.persistence.BookmarkRepository;
import com.songranker.spotify.dto.SpotifyAlbum;
import com.songranker.spotify.dto.SpotifyArtist;
import com.songranker.ui.AppContext;
import com.songranker.ui.util.Shortcuts;
import com.songranker.ui.util.AlbumArt;
import com.songranker.ui.util.Background;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

public final class AlbumListScreen extends VBox {

    public AlbumListScreen(AppContext ctx, SpotifyArtist artist, List<SpotifyAlbum> albums) {
        setSpacing(12);
        setPadding(new Insets(24));

        Label title = new Label("Pick an album by " + artist.name);
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        ListView<SpotifyAlbum> albumList = new ListView<>();
        albumList.getItems().setAll(albums);
        albumList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(SpotifyAlbum album, boolean empty) {
                super.updateItem(album, empty);
                if (empty || album == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String year = album.releaseDate != null && album.releaseDate.length() >= 4
                            ? album.releaseDate.substring(0, 4) : "?";
                    Label text = new Label(album.name + "  (" + year + ", " + album.albumType + ")");
                    HBox row = new HBox(10, AlbumArt.thumbnail(album.imageUrl, 40), text);
                    row.setAlignment(Pos.CENTER_LEFT);
                    setText(null);
                    setGraphic(row);
                }
            }
        });
        albumList.setPrefHeight(420);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setMaxSize(24, 24);

        Button backButton = new Button("Back");
        backButton.getStyleClass().add("accent-button");
        backButton.setOnAction(e -> ctx.navigate(new ScopeScreen(ctx, artist)));
        Shortcuts.escapeFires(this, backButton);

        Button chooseButton = new Button("Choose album");
        chooseButton.getStyleClass().add("accent-button");
        Runnable pick = () -> {
            SpotifyAlbum selected = albumList.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            spinner.setVisible(true);
            chooseButton.setDisable(true);
            Background.run(
                    () -> ctx.apiClient.getAlbumTracks(selected.id, artist.name, selected.name, selected.imageUrl),
                    tracks -> {
                        spinner.setVisible(false);
                        chooseButton.setDisable(false);
                        ScopeScreen.proceedOrWarn(ctx, artist, selected.name, tracks,
                                () -> ctx.navigate(new AlbumListScreen(ctx, artist, albums)));
                    },
                    error -> {
                        spinner.setVisible(false);
                        chooseButton.setDisable(false);
                        Background.showError(error);
                    });
        };
        chooseButton.setOnAction(e -> pick.run());
        albumList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) pick.run();
        });

        Button bookmarkButton = new Button("Bookmark for later");
        bookmarkButton.getStyleClass().add("accent-button");
        Runnable refreshBookmarkLabel = () -> {
            SpotifyAlbum sel = albumList.getSelectionModel().getSelectedItem();
            bookmarkButton.setDisable(sel == null);
            bookmarkButton.setText(sel != null && ctx.bookmarkRepository.isBookmarked(sel.id)
                    ? "Remove bookmark" : "Bookmark for later");
        };
        albumList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> refreshBookmarkLabel.run());
        refreshBookmarkLabel.run();
        bookmarkButton.setOnAction(e -> {
            SpotifyAlbum sel = albumList.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (ctx.bookmarkRepository.isBookmarked(sel.id)) {
                ctx.bookmarkRepository.remove(sel.id);
            } else {
                ctx.bookmarkRepository.add(new BookmarkRepository.Bookmark(sel.id, sel.name, artist.id,
                        artist.name, sel.imageUrl, sel.releaseDate));
            }
            refreshBookmarkLabel.run();
        });

        HBox navRow = new HBox(8, backButton, chooseButton, bookmarkButton, spinner);
        getChildren().addAll(title, albumList, navRow);
    }
}
