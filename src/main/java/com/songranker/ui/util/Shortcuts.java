package com.songranker.ui.util;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keyboard shortcuts for one screen. Handlers live on the {@link javafx.scene.Scene} only while the
 * screen is showing, so a screen's shortcuts never leak into the next one. They run in the
 * capture phase (so arrow keys aren't swallowed by focus traversal) but step aside whenever a
 * text box has focus, so typing a rating or note (including Ctrl+Z inside the box) is
 * untouched. Escape is the one key that still works there.
 */
public final class Shortcuts {

    private Shortcuts() {
    }

    public static final KeyCombination ESCAPE = new KeyCodeCombination(KeyCode.ESCAPE);

    /** Builder-style registration for one screen. */
    public static final class Builder {
        private final Parent screen;
        private final Map<KeyCombination, Runnable> bindings = new LinkedHashMap<>();

        private Builder(Parent screen) {
            this.screen = screen;
        }

        public Builder on(KeyCombination combo, Runnable action) {
            bindings.put(combo, action);
            return this;
        }

        public Builder on(KeyCode key, Runnable action) {
            return on(new KeyCodeCombination(key), action);
        }

        /** Ctrl+key (Cmd on macOS). */
        public Builder onShortcut(KeyCode key, Runnable action) {
            return on(new KeyCodeCombination(key, KeyCombination.SHORTCUT_DOWN), action);
        }

        /** Escape presses the given button, if it's currently enabled, the same as clicking it. */
        public Builder escapeFires(Button button) {
            return on(ESCAPE, () -> {
                if (!button.isDisabled()) button.fire();
            });
        }

        public void install() {
            EventHandler<KeyEvent> handler = event -> {
                Node focus = event.getTarget() instanceof Node n && n.getScene() != null
                        ? n.getScene().getFocusOwner() : null;
                boolean typing = focus instanceof TextInputControl;
                for (Map.Entry<KeyCombination, Runnable> entry : bindings.entrySet()) {
                    if (!entry.getKey().match(event)) continue;
                    if (typing && !allowedWhileTyping(entry.getKey())) continue;
                    entry.getValue().run();
                    event.consume();
                    return;
                }
            };
            screen.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (oldScene != null) oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, handler);
                if (newScene != null) newScene.addEventFilter(KeyEvent.KEY_PRESSED, handler);
            });
            if (screen.getScene() != null) {
                screen.getScene().addEventFilter(KeyEvent.KEY_PRESSED, handler);
            }
        }
    }

    public static Builder forScreen(Parent screen) {
        return new Builder(screen);
    }

    /** Escape-to-go-back, the most common case: any screen with a Back button. */
    public static void escapeFires(Parent screen, Button backButton) {
        forScreen(screen).escapeFires(backButton).install();
    }

    /** While a text box has focus only Escape is ours. Everything else is the user typing or editing. */
    static boolean allowedWhileTyping(KeyCombination combo) {
        return combo.equals(ESCAPE);
    }
}
