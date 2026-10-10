package com.songranker.ui.screens;

import com.songranker.persistence.RankingRunRepository.RunStats;
import com.songranker.persistence.TrackRatingRepository.TrackRating;
import com.songranker.ui.AppContext;
import com.songranker.ui.util.Shortcuts;
import com.songranker.ui.util.AlbumSummaries;
import com.songranker.ui.util.RatingFormat;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

public final class StatsScreen extends VBox {

    public StatsScreen(AppContext ctx) {
        setSpacing(18);
        setPadding(new Insets(24));
        setAlignment(Pos.TOP_CENTER);

        Label title = new Label("Your stats");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        RunStats stats = ctx.runRepository.getStats();

        VBox content;
        if (stats.totalRuns() == 0) {
            Label empty = new Label("No rankings yet. Finish or start one to see your stats here.");
            empty.getStyleClass().add("subtle-text");
            content = new VBox(empty);
            content.setAlignment(Pos.CENTER);
        } else {
            content = buildContent(ctx, stats);
        }

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefSize(640, 480);

        Button backButton = new Button("Back to home");
        backButton.getStyleClass().add("accent-button");
        backButton.setOnAction(e -> ctx.navigate(new HomeScreen(ctx)));
        Shortcuts.escapeFires(this, backButton);

        getChildren().addAll(title, scrollPane, backButton);
    }

    private VBox buildContent(AppContext ctx, RunStats stats) {
        FlowPane cards = new FlowPane(14, 14);
        cards.setAlignment(Pos.CENTER);
        cards.setPrefWrapLength(600);

        cards.getChildren().addAll(
                card(String.valueOf(stats.completedRuns()), "Rankings completed"),
                card(String.valueOf(stats.inProgressRuns()), "In progress"),
                card(String.valueOf(stats.totalTracksRanked()), "Total songs ranked"),
                card(String.valueOf(stats.totalComparisonsMade()), "Head-to-heads played"),
                card(String.valueOf(stats.distinctArtistCount()), "Different artists"));

        VBox favoriteSongs = buildFavoriteSongs(ctx);
        VBox favoriteAlbums = buildFavoriteAlbums(ctx);
        HBox favorites = new HBox(24, favoriteSongs, favoriteAlbums);
        favorites.setAlignment(Pos.TOP_CENTER);

        VBox content = new VBox(24, cards, favorites);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(8));
        return content;
    }

    /** Every rated song, highest score first. There is no per-album cap. */
    private VBox buildFavoriteSongs(AppContext ctx) {
        VBox section = new VBox(10);
        section.setAlignment(Pos.TOP_LEFT);
        section.setMaxWidth(280);
        section.setPrefWidth(280);
        Label sectionTitle = new Label("Favorite songs");
        sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        section.getChildren().add(sectionTitle);

        List<TrackRating> ratings = ctx.trackRatingRepository.getAllRatings();
        if (ratings.isEmpty()) {
            Label none = new Label("Rate some songs to see your favorites here.");
            none.getStyleClass().add("subtle-text");
            section.getChildren().add(none);
        } else {
            for (TrackRating r : ratings) {
                section.getChildren().add(bulletLine(r.trackName() + "  ·  " + r.artistName()
                        + "  (" + RatingFormat.format(r.rating()) + "/10)"));
            }
        }
        return section;
    }

    /** Every album you've rated songs on or ranked, ordered by the average rating of its rated songs. */
    private VBox buildFavoriteAlbums(AppContext ctx) {
        VBox section = new VBox(10);
        section.setAlignment(Pos.TOP_LEFT);
        section.setMaxWidth(280);
        section.setPrefWidth(280);
        Label sectionTitle = new Label("Favorite albums");
        sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        section.getChildren().add(sectionTitle);

        List<AlbumSummaries.Entry> albums = AlbumSummaries.build(
                ctx.trackRatingRepository.getAllRatings(), ctx.runRepository.getAlbumChampions());
        if (albums.isEmpty()) {
            Label none = new Label("Rate songs or finish a ranking to see your favorite albums here.");
            none.getStyleClass().add("subtle-text");
            section.getChildren().add(none);
        } else {
            for (AlbumSummaries.Entry album : albums) {
                StringBuilder text = new StringBuilder(album.albumName() + "  ·  " + album.artistName());
                if (album.average() != null) {
                    text.append("  (avg ").append(RatingFormat.format(Math.round(album.average() * 10) / 10.0))
                            .append("/10, ").append(album.ratedCount())
                            .append(album.ratedCount() == 1 ? " song rated)" : " songs rated)");
                }
                if (album.favoriteTrackName() != null) {
                    text.append("  · #1: ").append(album.favoriteTrackName());
                }
                section.getChildren().add(bulletLine(text.toString()));
            }
        }
        return section;
    }

    private static Label bulletLine(String text) {
        Label label = new Label("•  " + text);
        label.setWrapText(true);
        return label;
    }

    private static VBox card(String value, String caption) {
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");
        valueLabel.getStyleClass().add("accent-text");
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("subtle-text");
        captionLabel.setWrapText(true);
        captionLabel.setStyle("-fx-font-size: 11px; -fx-text-alignment: center;");
        captionLabel.setAlignment(Pos.CENTER);

        VBox box = new VBox(4, valueLabel, captionLabel);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(14));
        box.setPrefSize(140, 100);
        box.setMaxWidth(140);
        box.getStyleClass().add("match-card");
        return box;
    }
}
