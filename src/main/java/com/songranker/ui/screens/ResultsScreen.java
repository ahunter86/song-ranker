package com.songranker.ui.screens;

import com.songranker.domain.Track;
import com.songranker.ui.AppContext;
import com.songranker.ui.util.AlbumArt;
import com.songranker.ui.util.AlbumSummaries;
import com.songranker.ui.util.Background;
import com.songranker.ui.util.Playback;
import com.songranker.ui.util.RatingFormat;
import com.songranker.ui.util.ReorderUtil;
import com.songranker.ui.util.ShareCard;
import com.songranker.ui.util.Shortcuts;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The final ranking of a finished run. Songs can be played from here, dragged into a different
 * order afterwards (saved immediately, so a changed mind sticks), and the whole ranking can be
 * exported as a shareable image.
 */
public final class ResultsScreen extends VBox {

    private final Set<String> listened = new HashSet<>();

    /**
     * @param runId the saved run this ranking belongs to, or -1 if there isn't one (order is then
     *              not editable because there's nowhere to save it)
     * @param artistName artist (or playlist) name for the heading and share card; may be null
     * @param scopeDescription what was ranked ("Entire discography", an album name...); may be null
     */
    public ResultsScreen(AppContext ctx, List<Track> ranking, Runnable onBack, long runId,
                          String artistName, String scopeDescription) {
        setSpacing(12);
        setPadding(new Insets(24));
        setAlignment(Pos.TOP_CENTER);

        Label title = new Label("Final ranking");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        getChildren().add(title);

        if (artistName != null) {
            Label which = new Label(scopeDescription != null ? artistName + " · " + scopeDescription : artistName);
            which.getStyleClass().add("subtle-text");
            getChildren().add(which);
        }

        List<String> ids = ranking.stream().map(Track::getId).toList();
        Map<String, Double> ratings = ctx.trackRatingRepository.getRatings(ids);
        listened.addAll(ctx.listenedRepository.getListened(ids));

        Double average = AlbumSummaries.averageOf(ratings.values());
        String averageText = average == null ? null
                : RatingFormat.format(Math.round(average * 10) / 10.0) + "/10";
        if (average != null) {
            Label score = new Label("Overall score: " + averageText + "  (average of " + ratings.size()
                    + " rated song" + (ratings.size() == 1 ? "" : "s") + ")");
            score.getStyleClass().add("accent-text");
            score.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            getChildren().add(score);
        }
        if (runId >= 0) {
            Label hint = new Label("Changed your mind? Drag songs up or down. The new order is saved automatically.");
            hint.getStyleClass().add("subtle-text");
            getChildren().add(hint);
        }

        ListView<Track> list = new ListView<>();
        list.getItems().setAll(ranking);
        list.setCellFactory(lv -> buildCell(ctx, list, ratings, runId));
        list.setPrefHeight(430);
        VBox.setVgrow(list, Priority.ALWAYS);

        Button backButton = new Button("Back");
        backButton.getStyleClass().add("accent-button");
        backButton.setOnAction(e -> onBack.run());

        Button shareButton = new Button("Share as image");
        shareButton.getStyleClass().add("accent-button");
        shareButton.setOnAction(e -> {
            // Copied here on the UI thread, because the work below runs on another thread.
            List<Track> current = new ArrayList<>(list.getItems());
            shareButton.setDisable(true);
            String[] titles = ShareCard.titles(artistName, scopeDescription);
            String footer = current.size() + " song" + (current.size() == 1 ? "" : "s") + " ranked"
                    + (averageText != null ? "  •  avg " + averageText : "");
            Background.run(
                    () -> loadShareEntries(current, ratings),
                    entries -> {
                        shareButton.setDisable(false);
                        new ShareCardWindow(ctx.primaryStage, titles[0], titles[1], entries, footer).show();
                    },
                    error -> {
                        shareButton.setDisable(false);
                        Background.showError(error);
                    });
        });

        HBox navRow = new HBox(10, backButton, shareButton);
        navRow.setAlignment(Pos.CENTER);
        getChildren().addAll(list, navRow);

        Shortcuts.forScreen(this).escapeFires(backButton).install();
    }

    /** Runs off the UI thread: downloads art for the songs that appear on the card (blocking). */
    private static List<ShareCard.Entry> loadShareEntries(List<Track> tracks, Map<String, Double> ratings) {
        List<ShareCard.Entry> entries = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            Track t = tracks.get(i);
            Image art = null;
            if (i < ShareCard.MAX_LISTED && t.getAlbumArtUrl() != null && !t.getAlbumArtUrl().isBlank()) {
                try {
                    // The last argument turns off background loading, so this call returns once the
                    // picture has downloaded. The snapshot needs it fully loaded.
                    art = new Image(t.getAlbumArtUrl(), 400, 400, false, true, false);
                } catch (RuntimeException ignored) {
                    // a missing picture just becomes a colored square on the card
                }
            }
            entries.add(new ShareCard.Entry(t.getName(), t.getAlbumName(), ratings.get(t.getId()), art));
        }
        return entries;
    }

    private ListCell<Track> buildCell(AppContext ctx, ListView<Track> list, Map<String, Double> ratings, long runId) {
        ListCell<Track> cell = new ListCell<>() {
            @Override
            protected void updateItem(Track track, boolean empty) {
                super.updateItem(track, empty);
                if (empty || track == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label rankLabel = new Label((getIndex() + 1) + ".");
                rankLabel.setStyle("-fx-font-weight: bold; -fx-min-width: 32;");

                Label nameLabel = new Label(track.getName());
                nameLabel.setStyle("-fx-font-weight: bold;");
                Label albumLabel = new Label(track.getAlbumName());
                albumLabel.getStyleClass().add("subtle-text");
                VBox names = new VBox(2, nameLabel, albumLabel);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                HBox row = new HBox(10, rankLabel, AlbumArt.thumbnail(track.getAlbumArtUrl(), 36), names, spacer);
                Double rating = ratings.get(track.getId());
                if (rating != null) {
                    Label ratingLabel = new Label(RatingFormat.format(rating) + "/10");
                    ratingLabel.getStyleClass().add("accent-text");
                    row.getChildren().add(ratingLabel);
                }
                if (listened.contains(track.getId())) {
                    Label heard = new Label("✓");
                    heard.getStyleClass().add("accent-text");
                    Tooltip.install(heard, new Tooltip("You've listened to this one"));
                    row.getChildren().add(heard);
                }
                Button play = new Button("▶");
                play.setStyle("-fx-padding: 4 10 4 10;");
                Tooltip playTip = new Tooltip("Play on Spotify");
                playTip.setShowDelay(javafx.util.Duration.millis(150));
                play.setTooltip(playTip);
                play.setOnAction(e -> Playback.play(ctx, track, () -> {
                    listened.add(track.getId());
                    list.refresh();
                }));
                row.getChildren().add(play);
                row.setAlignment(Pos.CENTER_LEFT);
                setText(null);
                setGraphic(row);
            }
        };

        // Without a saved run there is nowhere to store a new order, so dragging stays off.
        if (runId < 0) return cell;

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
        // The CSS class draws a green bar above this row, which is where the song will land.
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
                int from = Integer.parseInt(db.getString());
                int size = list.getItems().size();
                // Dropping on the empty space under the last row means "move to the end".
                int before = cell.isEmpty() ? size : cell.getIndex();
                ReorderUtil.moveBefore(list.getItems(), from, before);
                list.refresh();
                // Saved on every drop. There is no separate Save button.
                ctx.runRepository.updateResultOrder(runId, new ArrayList<>(list.getItems()));
                e.setDropCompleted(true);
            }
            e.consume();
        });
        return cell;
    }
}
