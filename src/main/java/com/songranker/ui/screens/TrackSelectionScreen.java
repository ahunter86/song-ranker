package com.songranker.ui.screens;

import com.songranker.domain.Track;
import com.songranker.spotify.dto.SpotifyArtist;
import com.songranker.ui.AppContext;
import com.songranker.ui.util.Shortcuts;
import com.songranker.ui.util.AlbumArt;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TrackSelectionScreen extends VBox {

    public TrackSelectionScreen(AppContext ctx, SpotifyArtist artist, String scopeDescription, List<Track> tracks,
                                 Runnable onBack) {
        setSpacing(10);
        setPadding(new Insets(24));

        Label title = new Label("Which tracks do you want to rank?");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label subtitle = new Label(scopeDescription + ": " + tracks.size() + " tracks found. "
                + "Uncheck anything you don't want ranked, such as skits, interludes or live cuts.");
        subtitle.setWrapText(true);

        Map<Track, CheckBox> checkboxes = new LinkedHashMap<>();
        VBox listBox = new VBox(6);
        for (Track t : tracks) {
            CheckBox cb = new CheckBox(t.getName() + "  ·  " + t.getAlbumName());
            cb.setSelected(true);
            checkboxes.put(t, cb);
            HBox row = new HBox(8, AlbumArt.thumbnail(t.getAlbumArtUrl(), 32), cb);
            row.setAlignment(Pos.CENTER_LEFT);
            listBox.getChildren().add(row);
        }
        ScrollPane scrollPane = new ScrollPane(listBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(380);

        Button selectAll = new Button("Select all");
        selectAll.setOnAction(e -> checkboxes.values().forEach(cb -> cb.setSelected(true)));
        Button selectNone = new Button("Select none");
        selectNone.setOnAction(e -> checkboxes.values().forEach(cb -> cb.setSelected(false)));

        Button backButton = new Button("Back");
        backButton.getStyleClass().add("accent-button");
        backButton.setOnAction(e -> onBack.run());
        Shortcuts.escapeFires(this, backButton);

        Button continueButton = new Button("Continue");
        continueButton.getStyleClass().add("accent-button");
        continueButton.setOnAction(e -> {
            List<Track> selected = new ArrayList<>();
            checkboxes.forEach((track, cb) -> {
                if (cb.isSelected()) selected.add(track);
            });
            if (selected.isEmpty()) {
                new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING,
                        "Pick at least 1 track to rank.").showAndWait();
                return;
            }
            ctx.navigate(new ModeScreen(ctx, artist, scopeDescription, selected,
                    () -> ctx.navigate(new TrackSelectionScreen(ctx, artist, scopeDescription, tracks, onBack))));
        });

        HBox selectRow = new HBox(8, selectAll, selectNone);
        HBox navRow = new HBox(8, backButton, continueButton);
        getChildren().addAll(title, subtitle, selectRow, scrollPane, navRow);
    }
}
