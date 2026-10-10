package com.songranker.ui.screens;

import com.songranker.domain.SeedingMode;
import com.songranker.domain.SwissEngine;
import com.songranker.domain.Track;
import com.songranker.spotify.dto.SpotifyArtist;
import com.songranker.ui.AppContext;
import com.songranker.ui.util.Shortcuts;
import com.songranker.ui.session.RankingSession;
import com.songranker.ui.session.RatingSession;
import com.songranker.ui.util.TimeEstimate;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

public final class ModeScreen extends VBox {

    public ModeScreen(AppContext ctx, SpotifyArtist artist, String scopeDescription, List<Track> tracks,
                       Runnable onBack) {
        setSpacing(14);
        setPadding(new Insets(24));

        Label title = new Label("How do you want to rank " + tracks.size() + " tracks?");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label modeHint = new Label("A bracket is quicker. It only asks about the songs still in the running. "
                + "Pairwise ranking asks more questions and gives you a full ranked list instead of only a winner. "
                + "Rating skips comparisons: you score each song 0-10 yourself, then drag to fine-tune.");
        modeHint.setStyle("-fx-font-size: 12px;");
        modeHint.getStyleClass().add("subtle-text");
        modeHint.setWrapText(true);

        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton bracketOption = new RadioButton("Bracket (fastest, finds a winner)");
        bracketOption.setToggleGroup(modeGroup);
        bracketOption.setSelected(true);
        RadioButton swissOption = new RadioButton("Pairwise ranking (slower, ranks every song)");
        swissOption.setToggleGroup(modeGroup);
        RadioButton rateOption = new RadioButton("Rate 0-10 (no comparisons, you score each song)");
        rateOption.setToggleGroup(modeGroup);

        // Bracket-only options
        Label seedingLabel = new Label("Matchup order:");
        ComboBox<SeedingMode> seedingCombo = new ComboBox<>();
        seedingCombo.getItems().addAll(SeedingMode.RANDOM, SeedingMode.ORIGINAL_ORDER);
        seedingCombo.setValue(SeedingMode.RANDOM);
        seedingCombo.setCellFactory(lv -> seedingCell());
        seedingCombo.setButtonCell(seedingCell());
        HBox seedingRow = new HBox(8, seedingLabel, seedingCombo);

        // Swiss-only options
        CheckBox autoRounds = new CheckBox("Choose number of rounds automatically (recommended)");
        autoRounds.setSelected(true);
        Spinner<Integer> roundsSpinner = new Spinner<>(1, Math.max(1, tracks.size() - 1), 5);
        roundsSpinner.setDisable(true);
        autoRounds.selectedProperty().addListener((obs, was, isNow) -> roundsSpinner.setDisable(isNow));
        HBox roundsRow = new HBox(8, autoRounds, roundsSpinner);

        // managed follows visible, so a hidden row takes up no space.
        seedingRow.visibleProperty().bind(bracketOption.selectedProperty());
        seedingRow.managedProperty().bind(bracketOption.selectedProperty());
        roundsRow.visibleProperty().bind(swissOption.selectedProperty());
        roundsRow.managedProperty().bind(swissOption.selectedProperty());

        Label estimateLabel = new Label();
        estimateLabel.setStyle("-fx-font-size: 12px;");
        estimateLabel.getStyleClass().add("subtle-text");
        estimateLabel.setWrapText(true);
        Runnable updateEstimate = () -> {
            int n = tracks.size();
            double avgMs = TimeEstimate.averageDurationMs(tracks);
            if (rateOption.isSelected()) {
                // estimateMillis counts two songs per comparison. Halving the average length
                // makes n songs play once each.
                long ms = TimeEstimate.estimateMillis(n, avgMs / 2);
                estimateLabel.setText("Estimated time if you listen to each song once while rating: ≈ "
                        + TimeEstimate.formatDuration(ms) + "  (" + n + " song" + (n == 1 ? "" : "s") + " to rate)");
                return;
            }
            // The same formulas the engines use for getTotalComparisonCount(), so the
            // estimate matches what the run will ask.
            int comparisons = bracketOption.isSelected()
                    ? Math.max(0, n - 1)
                    : SwissEngine.resolveRounds(n, autoRounds.isSelected() ? -1 : roundsSpinner.getValue()) * (n / 2);
            long ms = TimeEstimate.estimateMillis(comparisons, avgMs);
            estimateLabel.setText("Estimated time if you play every song in full: ≈ " + TimeEstimate.formatDuration(ms)
                    + "  (" + comparisons + " comparison" + (comparisons == 1 ? "" : "s") + ")");
        };
        modeGroup.selectedToggleProperty().addListener((obs, was, isNow) -> updateEstimate.run());
        autoRounds.selectedProperty().addListener((obs, was, isNow) -> updateEstimate.run());
        roundsSpinner.valueProperty().addListener((obs, was, isNow) -> updateEstimate.run());
        updateEstimate.run();

        Button backButton = new Button("Back");
        backButton.getStyleClass().add("accent-button");
        backButton.setOnAction(e -> onBack.run());
        Shortcuts.escapeFires(this, backButton);

        Button startButton = new Button("Start ranking");
        startButton.getStyleClass().add("accent-button");
        startButton.setOnAction(e -> {
            if ((bracketOption.isSelected() || swissOption.isSelected()) && tracks.size() < 2) {
                new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING,
                        "Bracket and pairwise ranking need at least 2 songs. Choose Rate 0-10 instead "
                                + "for a single song.").showAndWait();
                return;
            }
            if (rateOption.isSelected()) {
                RatingSession session = RatingSession.start(ctx.runRepository, ctx.trackRatingRepository,
                        tracks, artist.name, scopeDescription);
                ctx.navigate(new RateScreen(ctx, session, artist.name, scopeDescription));
                return;
            }
            RankingSession session;
            if (bracketOption.isSelected()) {
                session = RankingSession.startBracket(ctx.runRepository, ctx.trackRatingRepository, tracks,
                        seedingCombo.getValue(), artist.name, scopeDescription);
            } else {
                int rounds = autoRounds.isSelected() ? -1 : roundsSpinner.getValue();
                session = RankingSession.startSwiss(ctx.runRepository, ctx.trackRatingRepository, tracks, rounds,
                        artist.name, scopeDescription);
            }
            ctx.navigate(new PlayScreen(ctx, session, artist.name, scopeDescription));
        });

        HBox navRow = new HBox(8, backButton, startButton);
        getChildren().addAll(title, modeHint, bracketOption, seedingRow, swissOption, roundsRow, rateOption,
                estimateLabel, navRow);
    }

    private static ListCell<SeedingMode> seedingCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(SeedingMode mode, boolean empty) {
                super.updateItem(mode, empty);
                if (empty || mode == null) {
                    setText(null);
                    return;
                }
                setText(switch (mode) {
                    case RANDOM -> "Random order";
                    case ORIGINAL_ORDER -> "Original order (album track order)";
                });
            }
        };
    }
}
