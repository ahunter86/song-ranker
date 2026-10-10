package com.songranker.ui.screens;

import com.songranker.domain.BracketEngine;
import com.songranker.domain.Matchup;
import com.songranker.domain.Track;
import com.songranker.ui.AppContext;
import com.songranker.ui.session.RankingSession;
import com.songranker.ui.util.AlbumArt;
import com.songranker.ui.util.BracketDiagramView;
import com.songranker.ui.util.Playback;
import com.songranker.ui.util.RatingFormat;
import com.songranker.ui.util.Shortcuts;
import com.songranker.ui.util.ThemeManager;
import com.songranker.ui.util.TimeEstimate;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/** The main gameplay screen: one head-to-head comparison at a time. */
public final class PlayScreen extends VBox {

    private final AppContext ctx;
    private final RankingSession session;
    private final Runnable onExit;
    private final String artistName;
    private final String scopeDescription;
    private final double averageTrackDurationMs;
    private final Label roundLabel = new Label();
    private final Label timeRemainingLabel = new Label();
    private final VBox matchupArea = new VBox(20);
    private final Button undoButton = new Button("↩ Undo last pick");
    private Matchup currentMatchup;
    private Stage bracketWindow;

    public PlayScreen(AppContext ctx, RankingSession session, String artistName, String scopeDescription) {
        this(ctx, session, artistName, scopeDescription, () -> ctx.navigate(new HomeScreen(ctx)));
    }

    public PlayScreen(AppContext ctx, RankingSession session, String artistName, String scopeDescription,
                       Runnable onExit) {
        this.ctx = ctx;
        this.session = session;
        this.onExit = onExit;
        this.artistName = artistName;
        this.scopeDescription = scopeDescription;
        this.averageTrackDurationMs = TimeEstimate.averageDurationMs(session.getOriginalOrder());
        setSpacing(16);
        setPadding(new Insets(24));
        setAlignment(Pos.TOP_CENTER);

        Label title = new Label(artistName + " · " + scopeDescription);
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        roundLabel.setStyle("-fx-font-size: 13px;");
        roundLabel.getStyleClass().add("subtle-text");
        timeRemainingLabel.setStyle("-fx-font-size: 13px;");
        timeRemainingLabel.getStyleClass().add("subtle-text");

        Button exitButton = new Button("Save & exit");
        exitButton.getStyleClass().add("accent-button");
        exitButton.setOnAction(e -> onExit.run());

        undoButton.setOnAction(e -> undoLastPick());
        Tooltip undoTip = new Tooltip("Take back your last pick (Ctrl+Z)");
        undoTip.setShowDelay(javafx.util.Duration.millis(150));
        undoButton.setTooltip(undoTip);

        HBox navRow = new HBox(8, exitButton, undoButton);
        navRow.setAlignment(Pos.CENTER);
        if (session.getEngine() instanceof BracketEngine) {
            Button viewBracketButton = new Button("View full bracket");
            viewBracketButton.setOnAction(e -> showBracketWindow());
            navRow.getChildren().add(viewBracketButton);
        }

        Label shortcutHint = new Label("← / → pick a winner  •  1 / 2 play a song  •  Ctrl+Z undo  •  Esc save & exit");
        shortcutHint.getStyleClass().add("subtle-text");
        shortcutHint.setStyle("-fx-font-size: 12px;");

        getChildren().addAll(title, roundLabel, timeRemainingLabel, matchupArea, navRow, shortcutHint);
        refresh();

        Shortcuts.forScreen(this)
                .escapeFires(exitButton)
                .on(KeyCode.LEFT, () -> pickSide(true))
                .on(KeyCode.RIGHT, () -> pickSide(false))
                .on(KeyCode.DIGIT1, () -> playSide(true))
                .on(KeyCode.DIGIT2, () -> playSide(false))
                .on(KeyCode.NUMPAD1, () -> playSide(true))
                .on(KeyCode.NUMPAD2, () -> playSide(false))
                .onShortcut(KeyCode.Z, this::undoLastPick)
                .install();
    }

    private void pickSide(boolean left) {
        if (currentMatchup == null) return;
        pick(currentMatchup, left ? currentMatchup.getTrackA() : currentMatchup.getTrackB());
    }

    private void playSide(boolean left) {
        if (currentMatchup == null) return;
        Playback.play(ctx, left ? currentMatchup.getTrackA() : currentMatchup.getTrackB(), this::refreshMatchupBadges);
    }

    private void pick(Matchup matchup, Track winner) {
        session.choose(matchup, winner);
        refresh();
    }

    private void undoLastPick() {
        if (!session.canUndo()) return;
        session.undoLastChoice();
        refresh();
    }

    /** Opens (or refreshes, if already open) a separate window with the full bracket tree. */
    private void showBracketWindow() {
        var diagram = BracketDiagramView.build((BracketEngine) session.getEngine(), currentMatchup);
        if (bracketWindow == null) {
            bracketWindow = new Stage();
            bracketWindow.setTitle("Bracket");
        }
        StackPane root = new StackPane(diagram);
        Scene scene = new Scene(root, 800, 600);
        String css = getClass().getResource("/style.css") != null
                ? getClass().getResource("/style.css").toExternalForm() : null;
        if (css != null) scene.getStylesheets().add(css);
        ThemeManager.apply(scene);
        bracketWindow.setScene(scene);
        bracketWindow.show();
        bracketWindow.toFront();
    }

    private void refresh() {
        if (session.getEngine().isComplete()) {
            ctx.navigate(new ResultsScreen(ctx, session.getEngine().getFinalRanking(), onExit,
                    session.getRunId(), artistName, scopeDescription));
            return;
        }
        List<Matchup> pending = session.getEngine().getCurrentRoundPendingMatchups();
        if (pending.isEmpty()) {
            // Current round fully decided (or was all byes). Advance and try again.
            session.advanceRound();
            refresh();
            return;
        }
        roundLabel.setText("Round " + session.getEngine().getCurrentRoundNumber()
                + " of " + session.getEngine().getTotalRounds()
                + "  •  " + pending.size() + " comparison" + (pending.size() == 1 ? "" : "s") + " left this round");

        int remainingComparisons = session.getEngine().getTotalComparisonCount()
                - session.getEngine().getCompletedComparisonCount();
        long remainingMs = TimeEstimate.estimateMillis(remainingComparisons, averageTrackDurationMs);
        timeRemainingLabel.setText("≈ " + TimeEstimate.formatDuration(remainingMs) + " left"
                + " (" + remainingComparisons + " comparison" + (remainingComparisons == 1 ? "" : "s") + " to go,"
                + " if you listen to full songs)");

        undoButton.setDisable(!session.canUndo());
        // Always the first undecided matchup. Resume and undo rely on picks being made in
        // this order (see RunStateCodec.collectChoices).
        currentMatchup = pending.get(0);
        renderMatchup(currentMatchup);
        if (bracketWindow != null && bracketWindow.isShowing()) {
            showBracketWindow();
        }
    }

    private void renderMatchup(Matchup matchup) {
        matchupArea.getChildren().clear();
        HBox row = new HBox(30, buildTrackCard(matchup.getTrackA(), matchup), buildTrackCard(matchup.getTrackB(), matchup));
        row.setAlignment(Pos.CENTER);
        matchupArea.getChildren().add(row);
    }

    /** Badges are looked up by user data so playing via the keyboard updates the right card. */
    private void refreshMatchupBadges() {
        matchupArea.lookupAll(".listened-badge").forEach(node -> {
            if (node instanceof Label badge && badge.getUserData() instanceof String id
                    && ctx.listenedRepository.getListened(List.of(id)).contains(id)) {
                Playback.showBadge(badge);
            }
        });
    }

    private VBox buildTrackCard(Track track, Matchup matchup) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(20));
        card.getStyleClass().add("match-card");
        card.setPrefWidth(320);
        HBox.setHgrow(card, Priority.ALWAYS);

        Label name = new Label(track.getName());
        name.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        name.setWrapText(true);
        Label album = new Label(track.getAlbumName());
        album.getStyleClass().add("subtle-text");
        album.setWrapText(true);

        Label listenedBadge = Playback.listenedBadge(
                ctx.listenedRepository.getListened(List.of(track.getId())).contains(track.getId()));
        listenedBadge.getStyleClass().add("listened-badge");
        listenedBadge.setUserData(track.getId());

        Button playButton = new Button("▶ Play on Spotify");
        playButton.getStyleClass().add("accent-button");
        playButton.setOnAction(e -> Playback.play(ctx, track, () -> Playback.showBadge(listenedBadge)));

        Button pickButton = new Button("This one wins");
        pickButton.setDefaultButton(false);
        pickButton.getStyleClass().add("accent-button");
        pickButton.setOnAction(e -> pick(matchup, track));

        Double existingRating = session.getRating(track);
        TextField ratingField = new TextField(existingRating != null ? RatingFormat.format(existingRating) : "");
        ratingField.setPromptText("Rate 0-10 (optional)");
        ratingField.setPrefWidth(140);
        Label ratingError = new Label();
        ratingError.getStyleClass().add("danger-text");
        ratingField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused || ratingField.getText().isBlank()) return;
            try {
                double value = Double.parseDouble(ratingField.getText().trim());
                if (value < 0 || value > 10) {
                    ratingError.setText("Must be 0-10.");
                    return;
                }
                ratingError.setText(null);
                session.setRating(track, value);
            } catch (NumberFormatException ex) {
                ratingError.setText("Not a number.");
            }
        });

        TextArea noteArea = new TextArea(session.getNote(track) != null ? session.getNote(track) : "");
        noteArea.setPromptText("Notes...");
        noteArea.setWrapText(true);
        noteArea.setPrefSize(100, 160);
        noteArea.setMaxSize(100, 160);
        noteArea.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) session.setNote(track, noteArea.getText());
        });

        HBox artAndNotes = new HBox(10, AlbumArt.thumbnail(track.getAlbumArtUrl(), 160), noteArea);
        artAndNotes.setAlignment(Pos.CENTER);

        card.getChildren().addAll(artAndNotes, name, album, playButton, listenedBadge, ratingField, ratingError,
                pickButton);
        return card;
    }
}
