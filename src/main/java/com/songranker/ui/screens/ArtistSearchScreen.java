package com.songranker.ui.screens;

import com.songranker.spotify.dto.SpotifyArtist;
import com.songranker.ui.AppContext;
import com.songranker.ui.util.Shortcuts;
import com.songranker.ui.util.Background;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class ArtistSearchScreen extends VBox {

    public ArtistSearchScreen(AppContext ctx) {
        setSpacing(12);
        setPadding(new Insets(24));

        Label title = new Label("Find an artist");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        TextField searchField = new TextField();
        searchField.setPromptText("Artist name...");
        searchField.setPrefWidth(400);
        Button searchButton = new Button("Search");
        searchButton.getStyleClass().add("accent-button");
        Button backButton = new Button("Back");
        backButton.getStyleClass().add("accent-button");
        backButton.setOnAction(e -> ctx.navigate(new HomeScreen(ctx)));
        Shortcuts.escapeFires(this, backButton);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setMaxSize(24, 24);

        ListView<SpotifyArtist> results = new ListView<>();
        results.setPrefHeight(400);

        Runnable doSearch = () -> {
            String query = searchField.getText();
            if (query == null || query.isBlank()) return;
            spinner.setVisible(true);
            results.getItems().clear();
            Background.run(
                    () -> ctx.apiClient.searchArtists(query),
                    artists -> {
                        spinner.setVisible(false);
                        results.getItems().setAll(artists);
                        if (artists.isEmpty()) {
                            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                                    "No artists found for \"" + query + "\".").showAndWait();
                        }
                    },
                    error -> {
                        spinner.setVisible(false);
                        Background.showError(error);
                    });
        };
        searchButton.setOnAction(e -> doSearch.run());
        searchField.setOnAction(e -> doSearch.run());

        results.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && results.getSelectionModel().getSelectedItem() != null) {
                ctx.navigate(new ScopeScreen(ctx, results.getSelectionModel().getSelectedItem()));
            }
        });
        Button chooseButton = new Button("Choose artist");
        chooseButton.getStyleClass().add("accent-button");
        chooseButton.setOnAction(e -> {
            SpotifyArtist selected = results.getSelectionModel().getSelectedItem();
            if (selected != null) ctx.navigate(new ScopeScreen(ctx, selected));
        });

        HBox searchRow = new HBox(8, searchField, searchButton, spinner);
        HBox navRow = new HBox(8, backButton, chooseButton);

        getChildren().addAll(title, searchRow, results, navRow);
    }
}
