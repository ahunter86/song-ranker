package com.songranker;

/**
 * Entry point for the packaged jar ({@code java -jar song-ranker.jar}).
 *
 * <p>JavaFX refuses to start from the classpath when the main class itself extends
 * {@link javafx.application.Application} ("JavaFX runtime components are missing"). This class
 * does not, so the jar's manifest points here and it hands over to {@link App}.</p>
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        App.main(args);
    }
}
