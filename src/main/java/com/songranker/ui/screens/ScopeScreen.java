package com.songranker.ui.screens;

import com.songranker.domain.Track;
import com.songranker.spotify.dto.SpotifyArtist;
import com.songranker.ui.AppContext;
import com.songranker.ui.util.Shortcuts;
import com.songranker.ui.util.Background;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

public final class ScopeScreen extends VBox {

    public ScopeScreen(AppContext ctx, SpotifyArtist artist) {
        setSpacing(14);
        setPadding(new Insets(24));

        Label title = new Label(artist.name);
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        Label subtitle = new Label("What do you want to rank?");

        ToggleGroup scopeGroup = new ToggleGroup();
        RadioButton discographyOption = new RadioButton("Entire discography");
        discographyOption.setToggleGroup(scopeGroup);
        discographyOption.setSelected(true);
        RadioButton albumOption = new RadioButton("A specific album");
        albumOption.setToggleGroup(scopeGroup);

        // On by default: Spotify files EPs under "single" internally (there's no separate EP
        // type), so leaving this off would silently drop EPs along with true one-off singles.
        CheckBox includeSingles = new CheckBox("Also include singles & EPs (uncheck for full albums only)");
        includeSingles.setSelected(true);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setMaxSize(24, 24);

        Button continueButton = new Button("Continue");
        continueButton.getStyleClass().add("accent-button");
        Button backButton = new Button("Back");
        backButton.getStyleClass().add("accent-button");
        backButton.setOnAction(e -> ctx.navigate(new ArtistSearchScreen(ctx)));
        Shortcuts.escapeFires(this, backButton);

        continueButton.setOnAction(e -> {
            spinner.setVisible(true);
            continueButton.setDisable(true);
            if (discographyOption.isSelected()) {
                Background.run(
                        () -> ctx.apiClient.getArtistAllTracks(artist.id, artist.name, includeSingles.isSelected()),
                        tracks -> {
                            spinner.setVisible(false);
                            continueButton.setDisable(false);
                            proceedOrWarn(ctx, artist, "Entire discography", tracks, () -> ctx.navigate(new ScopeScreen(ctx, artist)));
                        },
                        error -> {
                            spinner.setVisible(false);
                            continueButton.setDisable(false);
                            Background.showError(error);
                        });
            } else {
                Background.run(
                        () -> ctx.apiClient.getArtistAlbums(artist.id, includeSingles.isSelected()),
                        albums -> {
                            spinner.setVisible(false);
                            continueButton.setDisable(false);
                            ctx.navigate(new AlbumListScreen(ctx, artist, albums));
                        },
                        error -> {
                            spinner.setVisible(false);
                            continueButton.setDisable(false);
                            Background.showError(error);
                        });
            }
        });

        HBox navRow = new HBox(8, backButton, continueButton, spinner);
        getChildren().addAll(title, subtitle, discographyOption, albumOption, includeSingles, navRow);
    }

    // Used by every screen that has just loaded a list of tracks (this one, the album list,
    // Home's New Releases cards, and Saved Albums). An empty list gets a warning; otherwise
    // the next stop is track selection. onBack is where that screen's Back button returns to.
    static void proceedOrWarn(AppContext ctx, SpotifyArtist artist, String scopeDescription, List<Track> tracks,
                               Runnable onBack) {
        if (tracks.isEmpty()) {
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING,
                    "No tracks found for that selection.").showAndWait();
            return;
        }
        ctx.navigate(new TrackSelectionScreen(ctx, artist, scopeDescription, tracks, onBack));
    }
}
