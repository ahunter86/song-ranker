package com.songranker.ui.util;

import javafx.concurrent.Task;

import java.util.function.Consumer;

/** Small helper for running a blocking call (Spotify API, DB) off the JavaFX thread. */
public final class Background {

    private Background() {
    }

    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    public static <T> void run(ThrowingSupplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.get();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> onError.accept(task.getException()));
        Thread thread = new Thread(task, "song-ranker-background");
        thread.setDaemon(true);
        thread.start();
    }

    /** The plain-language rate-limit explanation if anything in the cause chain is a Spotify 429, else null. */
    public static String rateLimitMessage(Throwable t) {
        java.util.Set<Throwable> seen = new java.util.HashSet<>();
        for (Throwable c = t; c != null && seen.add(c); c = c.getCause()) {
            if (c instanceof com.songranker.spotify.SpotifyRateLimitException r) return r.getMessage();
        }
        return null;
    }

    public static void showError(Throwable t) {
        String friendly = rateLimitMessage(t);
        if (friendly != null) {
            javafx.scene.control.Alert busy = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.WARNING);
            busy.setTitle("Spotify needs a moment");
            busy.setHeaderText("Spotify is rate limiting this app");
            javafx.scene.control.Label body = new javafx.scene.control.Label(friendly);
            body.setWrapText(true);
            body.setPrefWidth(460);
            busy.getDialogPane().setContent(body);
            busy.showAndWait();
            return;
        }
        String message = fullCauseChain(t);

        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Something went wrong");
        alert.setHeaderText(null);

        // Plain Alert content text truncates/clips long messages (like full HTTP error
        // bodies) and can't be scrolled or selected, so use a read-only, wrapping TextArea
        // in the alert's content instead so the full message is always visible and copyable.
        javafx.scene.control.TextArea textArea = new javafx.scene.control.TextArea(message);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefWidth(520);
        textArea.setPrefHeight(220);

        alert.getDialogPane().setContent(textArea);
        alert.setResizable(true);
        alert.showAndWait();
    }

    /**
     * Wrapper exceptions (e.g. "Spotify login did not complete") often hide the actual
     * underlying reason in their cause. Walk the whole chain and show every layer instead
     * of only the outermost message.
     */
    private static String fullCauseChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        java.util.Set<Throwable> seen = new java.util.HashSet<>();
        Throwable current = t;
        while (current != null && seen.add(current)) {
            if (sb.length() > 0) sb.append("\n\nCaused by: ");
            sb.append(current.getClass().getSimpleName());
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                sb.append(": ").append(current.getMessage());
            }
            current = current.getCause();
        }
        return sb.toString();
    }
}
