package com.songranker.ui.util;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

/** A fixed-size album art thumbnail with a themed placeholder frame for when there's no image (or it's still loading). */
public final class AlbumArt {

    private AlbumArt() {
    }

    public static StackPane thumbnail(String imageUrl, double size) {
        ImageView imageView = new ImageView();
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(true);
        if (imageUrl != null && !imageUrl.isBlank()) {
            imageView.setImage(new Image(imageUrl, size, size, true, true, true));
        }
        StackPane frame = new StackPane(imageView);
        frame.setPrefSize(size, size);
        frame.setMinSize(size, size);
        frame.setMaxSize(size, size);
        frame.getStyleClass().add("art-frame");
        return frame;
    }
}
