package com.songranker.ui.screens;

import com.songranker.domain.Track;
import com.songranker.ui.AppContext;
import com.songranker.ui.session.RatingSession;
import com.songranker.ui.util.AlbumArt;
import com.songranker.ui.util.Background;
import com.songranker.ui.util.Playback;
import com.songranker.ui.util.RatingFormat;
import com.songranker.ui.util.ReorderUtil;
import com.songranker.ui.util.Shortcuts;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * "Rate each song 0-10" gameplay screen: no head-to-head comparisons. The user scores each
 * track directly, with a live side panel showing the current order (highest first) that can be
 * freely dragged to fine-tune, in case the plain numeric order doesn't match what they'd actually pick.
 */
public final class RateScreen extends VBox {

    private final AppContext ctx;
    private final RatingSession session;
    private final ListView<Track> orderList = new ListView<>();
    private final VBox mainCard = new VBox(10);
    private final Label progressLabel = new Label();
    private final Button finishButton = new Button("Finish ranking");
    private Track currentTrack;
    private final java.util.Set<String> listened = new java.util.HashSet<>();

    private final Runnable onExit;

    public RateScreen(AppContext ctx, RatingSession session, String artistName, String scopeDescription) {
        this(ctx, session, artistName, scopeDescription, () -> ctx.navigate(new HomeScreen(ctx)));
    }

    public RateScreen(AppContext ctx, RatingSession session, String artistName, String scopeDescription,
                       Runnable onExit) {
        this.ctx = ctx;
        this.session = session;
        this.onExit = onExit;
        setSpacing(16);
        setPadding(new Insets(24));
        setAlignment(Pos.TOP_CENTER);

        Label title = new Label(artistName + " · " + scopeDescription);
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label instructions = new Label("Rate each song 0-10. Drag songs in the list on the right to "
                + "fine-tune the order if you disagree with the scores (higher is better).");
        instructions.getStyleClass().add("subtle-text");
        instructions.setWrapText(true);
        instructions.setMaxWidth(560);

        progressLabel.getStyleClass().add("subtle-text");

        mainCard.setAlignment(Pos.CENTER);
        mainCard.setPadding(new Insets(20));
        mainCard.getStyleClass().add("match-card");
        mainCard.setPrefWidth(320);

        orderList.setPrefSize(280, 380);
        orderList.setCellFactory(lv -> orderCell());

        Label sideHeading = new Label("Current order");
        sideHeading.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        VBox sidePanel = new VBox(8, sideHeading, orderList);
        sidePanel.setAlignment(Pos.TOP_CENTER);

        HBox content = new HBox(24, mainCard, sidePanel);
        content.setAlignment(Pos.CENTER);

        Button exitButton = new Button("Save & exit");
        exitButton.getStyleClass().add("accent-button");
        exitButton.setOnAction(e -> onExit.run());
        finishButton.getStyleClass().add("accent-button");
        finishButton.setOnAction(e -> {
            session.finish();
            ctx.navigate(new ResultsScreen(ctx, session.getCurrentOrder(), onExit, session.getRunId(),
                    artistName, scopeDescription));
        });
        HBox navRow = new HBox(12, exitButton, finishButton, progressLabel);
        navRow.setAlignment(Pos.CENTER);

        getChildren().addAll(title, instructions, content, navRow);
        Shortcuts.forScreen(this).escapeFires(exitButton).install();

        refreshOrderList();
        selectNextUnrated();
    }

    private void selectNextUnrated() {
        // The first song without a rating. Once everything is rated, show the top of the order.
        currentTrack = session.getTracks().stream()
                .filter(t -> session.getRating(t) == null)
                .findFirst()
                .orElse(session.getCurrentOrder().isEmpty() ? null : session.getCurrentOrder().get(0));
        renderMainCard();
    }

    private void renderMainCard() {
        mainCard.getChildren().clear();
        if (currentTrack == null) {
            mainCard.getChildren().add(new Label("No songs to rate."));
            return;
        }
        Track track = currentTrack;

        Label name = new Label(track.getName());
        name.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        name.setWrapText(true);
        Label album = new Label(track.getAlbumName());
        album.getStyleClass().add("subtle-text");
        album.setWrapText(true);

        Button playButton = new Button("▶ Play on Spotify");
        playButton.getStyleClass().add("accent-button");
        Label listenedBadge = Playback.listenedBadge(listened.contains(track.getId()));
        playButton.setOnAction(e -> Playback.play(ctx, track, () -> {
            Playback.showBadge(listenedBadge);
            listened.add(track.getId());
            orderList.refresh();
        }));

        Double existingRating = session.getRating(track);
        Label ratingPrompt = new Label(existingRating == null
                ? "Your rating:" : "Your rating: " + RatingFormat.format(existingRating) + "/10 (change below)");

        TextField scoreField = new TextField(existingRating != null ? RatingFormat.format(existingRating) : "");
        scoreField.setPromptText("0-10, e.g. 7.5");
        scoreField.setPrefWidth(90);
        Button submitButton = new Button(existingRating == null ? "Submit rating" : "Update rating");
        submitButton.getStyleClass().add("accent-button");
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("danger-text");

        Runnable submit = () -> {
            double value;
            try {
                value = Double.parseDouble(scoreField.getText().trim());
            } catch (NumberFormatException ex) {
                errorLabel.setText("Enter a number between 0 and 10.");
                return;
            }
            if (value < 0 || value > 10) {
                errorLabel.setText("Rating must be between 0 and 10.");
                return;
            }
            session.rate(track, value);
            refreshOrderList();
            selectNextUnrated();
        };
        scoreField.setOnAction(e -> submit.run());
        submitButton.setOnAction(e -> submit.run());
        HBox scoreRow = new HBox(8, scoreField, submitButton);
        scoreRow.setAlignment(Pos.CENTER);

        TextField noteField = new TextField(session.getNote(track) != null ? session.getNote(track) : "");
        noteField.setPromptText("Note (optional)");
        noteField.setPrefWidth(200);
        noteField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) session.setNote(track, noteField.getText());
        });

        mainCard.getChildren().addAll(AlbumArt.thumbnail(track.getAlbumArtUrl(), 160),
                name, album, playButton, listenedBadge, ratingPrompt, scoreRow, errorLabel, noteField);
    }

    private void refreshOrderList() {
        orderList.getItems().setAll(session.getCurrentOrder());
        listened.addAll(ctx.listenedRepository.getListened(
                session.getTracks().stream().map(Track::getId).toList()));
        long rated = session.getTracks().stream().filter(t -> session.getRating(t) != null).count();
        progressLabel.setText(rated + " of " + session.getTracks().size() + " rated");
        // Finish stays off until every song has a rating.
        finishButton.setDisable(!session.allRated());
    }

    private ListCell<Track> orderCell() {
        ListCell<Track> cell = new ListCell<>() {
            @Override
            protected void updateItem(Track track, boolean empty) {
                super.updateItem(track, empty);
                if (empty || track == null) {
                    setText(null);
                    return;
                }
                Double rating = session.getRating(track);
                setText((getIndex() + 1) + ".  " + track.getName()
                        + (rating != null ? "   (" + RatingFormat.format(rating) + "/10)" : "")
                        + (listened.contains(track.getId()) ? "   ✓" : ""));
            }
        };
        // Clicking a row loads that song into the card on the left so its rating can be changed.
        cell.setOnMouseClicked(e -> {
            if (cell.getItem() != null) {
                currentTrack = cell.getItem();
                renderMainCard();
            }
        });
        cell.setOnDragDetected(e -> {
            if (cell.getItem() == null) return;
            Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            // The dragboard carries only the row index. The drop handler uses it to find the track.
            content.putString(String.valueOf(cell.getIndex()));
            db.setContent(content);
            e.consume();
        });
        cell.setOnDragOver(e -> {
            if (e.getGestureSource() != cell && e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        cell.setOnDragEntered(e -> {
            if (e.getGestureSource() != cell && e.getDragboard().hasString() && !cell.isEmpty()) {
                cell.getStyleClass().add("drag-insert-before");
            }
        });
        cell.setOnDragExited(e -> cell.getStyleClass().remove("drag-insert-before"));
        cell.setOnDragDropped(e -> {
            cell.getStyleClass().remove("drag-insert-before");
            Dragboard db = e.getDragboard();
            if (db.hasString()) {
                int draggedIndex = Integer.parseInt(db.getString());
                Track dragged = orderList.getItems().get(draggedIndex);
                int size = orderList.getItems().size();
                int beforeIndex = cell.isEmpty() ? size : cell.getIndex();
                session.reorder(dragged, ReorderUtil.finalIndexWhenDroppedBefore(draggedIndex, beforeIndex, size));
                refreshOrderList();
                e.setDropCompleted(true);
            }
            e.consume();
        });
        return cell;
    }
}
