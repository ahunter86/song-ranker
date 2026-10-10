package com.songranker.ui.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.util.List;

/**
 * Builds the shareable "Wrapped"-style ranking card: a tall poster with bold colors, the #1 song
 * as a hero, and the next few songs listed below. Everything is styled inline (no theme CSS), so
 * it looks the same in light and dark mode and can be rendered off-screen to a PNG.
 */
public final class ShareCard {

    public static final double WIDTH = 1080;
    public static final double HEIGHT = 1350;
    /** #1 plus this many more rows fit on the card; the rest are summarized as "+ N more". */
    public static final int MAX_LISTED = 7;

    private ShareCard() {
    }

    public record Palette(String name, String backgroundFrom, String backgroundTo, String accent, String text) {
    }

    public static final List<Palette> PALETTES = List.of(
            new Palette("Green", "#1DB954", "#06210f", "#B9FFD1", "#FFFFFF"),
            new Palette("Purple", "#7B2FF7", "#1b0638", "#F7C6FF", "#FFFFFF"),
            new Palette("Sunset", "#FF5E3A", "#3a0a1a", "#FFE0A3", "#FFFFFF"),
            new Palette("Ocean", "#00B4DB", "#041b3d", "#B8F3FF", "#FFFFFF"));

    /** One song on the card. {@code art} may be null (a plain colored square is drawn instead). */
    public record Entry(String name, String albumName, Double rating, Image art) {
    }

    public static Region build(String title, String subtitle, List<Entry> ranking, String footerStat,
                               Palette palette) {
        Pane background = new Pane();
        background.setPrefSize(WIDTH, HEIGHT);
        background.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, "
                + palette.backgroundFrom() + ", " + palette.backgroundTo() + ");");
        background.getChildren().addAll(
                circle(WIDTH - 60, 40, 330, palette.accent(), 0.16),
                circle(40, HEIGHT - 120, 420, palette.accent(), 0.10),
                circle(WIDTH * 0.55, HEIGHT * 0.52, 140, palette.accent(), 0.07));

        VBox content = new VBox(24);
        content.setPadding(new Insets(64));

        Text brand = text("SONG RANKER", 26, FontWeight.BOLD, palette.accent());
        Text titleText = wrapped(title, 64, FontWeight.EXTRA_BOLD, palette.text(), WIDTH - 128);
        Text subtitleText = wrapped(subtitle, 34, FontWeight.NORMAL, palette.accent(), WIDTH - 128);
        content.getChildren().addAll(brand, titleText, subtitleText);

        if (!ranking.isEmpty()) {
            content.getChildren().add(hero(ranking.get(0), palette));
            int shown = Math.min(ranking.size(), MAX_LISTED);
            VBox rows = new VBox(14);
            for (int i = 1; i < shown; i++) {
                rows.getChildren().add(row(i + 1, ranking.get(i), palette));
            }
            content.getChildren().add(rows);
            if (ranking.size() > shown) {
                content.getChildren().add(text("+ " + (ranking.size() - shown) + " more ranked", 28,
                        FontWeight.NORMAL, palette.accent()));
            }
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        content.getChildren().add(spacer);
        if (footerStat != null && !footerStat.isBlank()) {
            content.getChildren().add(text(footerStat, 30, FontWeight.BOLD, palette.text()));
        }

        // Shrink everything uniformly if a long title or many rows make it taller than the poster,
        // rather than letting it spill off the top and bottom.
        double natural = content.prefHeight(WIDTH);
        double contentHeight = Math.max(natural, HEIGHT);
        content.setPrefSize(WIDTH, contentHeight);
        content.setMinSize(WIDTH, contentHeight);
        content.setMaxSize(WIDTH, contentHeight);
        double scale = HEIGHT / contentHeight;
        content.setScaleX(scale);
        content.setScaleY(scale);

        StackPane root = new StackPane(background, content);
        root.setPrefSize(WIDTH, HEIGHT);
        root.setMinSize(WIDTH, HEIGHT);
        root.setMaxSize(WIDTH, HEIGHT);
        root.setClip(new Rectangle(WIDTH, HEIGHT));
        return root;
    }

    /**
     * Card heading and subheading for a ranking. A whole-artist or playlist ranking is titled by
     * the artist/playlist; a single album is titled by the album with the artist underneath.
     */
    public static String[] titles(String artistName, String scopeDescription) {
        if (artistName == null || artistName.isBlank()) {
            return new String[]{scopeDescription != null ? scopeDescription : "My Ranking", "Ranked with Song Ranker"};
        }
        if (scopeDescription == null || scopeDescription.isBlank()) {
            return new String[]{artistName, "My ranking"};
        }
        boolean wholeCatalog = scopeDescription.equals("Entire discography") || scopeDescription.equals("Playlist");
        if (wholeCatalog) {
            return new String[]{artistName,
                    scopeDescription.equals("Playlist") ? "Playlist ranking" : "Discography ranking"};
        }
        return new String[]{scopeDescription, "by " + artistName};
    }

    /** Renders the card to a bitmap (must be called on the JavaFX thread). */
    public static WritableImage render(Region card) {
        // A node needs a Scene before CSS and layout run. This one only holds the card
        // for the snapshot and is never shown.
        new Scene(card, WIDTH, HEIGHT);
        card.applyCss();
        card.layout();
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        // Cut to exactly the poster size, so decorations that extend past an edge do not
        // make the image bigger.
        params.setViewport(new javafx.geometry.Rectangle2D(0, 0, WIDTH, HEIGHT));
        return card.snapshot(params, null);
    }

    private static HBox hero(Entry entry, Palette palette) {
        Text rank = text("#1", 150, FontWeight.EXTRA_BOLD, palette.accent());
        Text name = wrapped(entry.name(), 52, FontWeight.EXTRA_BOLD, palette.text(), 560);
        VBox info = new VBox(8, rank, name);
        if (entry.albumName() != null) {
            info.getChildren().add(wrapped(entry.albumName(), 28, FontWeight.NORMAL, palette.accent(), 560));
        }
        if (entry.rating() != null) {
            info.getChildren().add(text(oneDecimal(entry.rating()) + " / 10", 40, FontWeight.BOLD,
                    palette.text()));
        }
        info.setAlignment(Pos.CENTER_LEFT);
        HBox box = new HBox(36, art(entry.art(), 340, 28, palette), info);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static HBox row(int rank, Entry entry, Palette palette) {
        Text number = text(String.valueOf(rank), 40, FontWeight.EXTRA_BOLD, palette.accent());
        number.setTextAlignment(TextAlignment.RIGHT);
        StackPane numberBox = new StackPane(number);
        numberBox.setMinWidth(64);
        numberBox.setAlignment(Pos.CENTER_RIGHT);

        Text name = text(entry.name(), 34, FontWeight.BOLD, palette.text());
        // Leave room for the rating on the right when there is one.
        name.setWrappingWidth(entry.rating() != null ? 600 : 760);
        VBox nameBox = new VBox(name);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        HBox row = new HBox(20, numberBox, art(entry.art(), 84, 14, palette), nameBox);
        if (entry.rating() != null) {
            row.getChildren().add(text(oneDecimal(entry.rating()), 36, FontWeight.BOLD, palette.accent()));
        }
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static StackPane art(Image image, double size, double arc, Palette palette) {
        StackPane frame = new StackPane();
        frame.setMinSize(size, size);
        frame.setMaxSize(size, size);
        frame.setPrefSize(size, size);
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(arc * 2);
        clip.setArcHeight(arc * 2);
        frame.setClip(clip);
        if (image != null && !image.isError()) {
            ImageView view = new ImageView(image);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(false);
            frame.getChildren().add(view);
        } else {
            // The trailing 55 is a hex alpha value, about 33% opacity.
            frame.setStyle("-fx-background-color: " + palette.accent() + "55;");
        }
        return frame;
    }

    /** Always one decimal on the card (6.0, 8.8) so the column of scores lines up visually. */
    static String oneDecimal(double rating) {
        return String.format(java.util.Locale.ROOT, "%.1f", rating);
    }

    private static Circle circle(double x, double y, double radius, String color, double opacity) {
        Circle c = new Circle(x, y, radius);
        c.setFill(Color.web(color));
        c.setOpacity(opacity);
        return c;
    }

    private static Text text(String value, double size, FontWeight weight, String color) {
        Text t = new Text(value);
        t.setFont(Font.font("System", weight, size));
        t.setFill(Color.web(color));
        return t;
    }

    private static Text wrapped(String value, double size, FontWeight weight, String color, double width) {
        Text t = text(value, size, weight, color);
        t.setWrappingWidth(width);
        return t;
    }
}
