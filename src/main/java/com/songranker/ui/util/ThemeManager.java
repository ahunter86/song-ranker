package com.songranker.ui.util;

import javafx.scene.Scene;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Remembers the user's dark/light theme choice across launches and (re)applies it to the scene. */
public final class ThemeManager {

    private static final Path PREF_FILE = Paths.get(System.getProperty("user.home"), ".song-ranker", "theme.txt");

    private static Theme current = load();

    private ThemeManager() {
    }

    public static Theme current() {
        return current;
    }

    /** Swaps in the current theme's stylesheet, replacing whichever theme stylesheet was there before. */
    public static void apply(Scene scene) {
        // Only the two theme sheets are removed. style.css stays in place.
        scene.getStylesheets().removeIf(s -> s.endsWith("theme-dark.css") || s.endsWith("theme-light.css"));
        scene.getStylesheets().add(ThemeManager.class.getResource(current.stylesheetPath).toExternalForm());
    }

    public static void toggle(Scene scene) {
        current = current.other();
        save(current);
        apply(scene);
    }

    private static Theme load() {
        try {
            if (Files.exists(PREF_FILE)) {
                return Theme.valueOf(Files.readString(PREF_FILE, StandardCharsets.UTF_8).trim());
            }
        } catch (Exception ignored) {
            // fall through to default
        }
        return Theme.DARK;
    }

    private static void save(Theme theme) {
        try {
            Files.createDirectories(PREF_FILE.getParent());
            Files.writeString(PREF_FILE, theme.name(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // best-effort; worst case the choice doesn't survive a restart
        }
    }
}
