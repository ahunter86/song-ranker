package com.songranker.ui.util;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/** Saves a JavaFX image as a PNG without needing the javafx-swing module. */
public final class ImageExport {

    private ImageExport() {
    }

    public static void writePng(Image image, File file) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader reader = image.getPixelReader();
        if (reader == null || width <= 0 || height <= 0) {
            throw new IOException("Nothing to save. The image isn't ready.");
        }
        int[] pixels = new int[width * height];
        reader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, width, height, pixels, 0, width);
        if (!ImageIO.write(out, "png", file)) {
            throw new IOException("This system can't write PNG files.");
        }
    }
}
