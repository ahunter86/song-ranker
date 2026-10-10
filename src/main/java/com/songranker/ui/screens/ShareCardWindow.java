package com.songranker.ui.screens;

import com.songranker.ui.util.Background;
import com.songranker.ui.util.ImageExport;
import com.songranker.ui.util.ShareCard;
import com.songranker.ui.util.ThemeManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

/** Preview of the shareable ranking card, with color options, Save as PNG, and Copy. */
final class ShareCardWindow {

    private final Stage owner;
    private final String title;
    private final String subtitle;
    private final List<ShareCard.Entry> entries;
    private final String footerStat;
    private int paletteIndex = 0;
    private Image rendered;
    private final ImageView preview = new ImageView();

    ShareCardWindow(Stage owner, String title, String subtitle, List<ShareCard.Entry> entries, String footerStat) {
        this.owner = owner;
        this.title = title;
        this.subtitle = subtitle;
        this.entries = entries;
        this.footerStat = footerStat;
    }

    void show() {
        preview.setPreserveRatio(true);
        preview.setFitHeight(620);
        render();

        Label status = new Label();
        status.getStyleClass().add("subtle-text");

        Button colors = new Button("Change colors");
        colors.setOnAction(e -> {
            paletteIndex = (paletteIndex + 1) % ShareCard.PALETTES.size();
            render();
        });
        Button save = new Button("Save as PNG...");
        save.getStyleClass().add("accent-button");
        save.setOnAction(e -> savePng(status));
        Button copy = new Button("Copy image");
        copy.getStyleClass().add("accent-button");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putImage(rendered);
            Clipboard.getSystemClipboard().setContent(content);
            status.setText("Copied. Paste it into a message or post.");
        });
        Button close = new Button("Close");

        HBox buttons = new HBox(10, colors, save, copy, close);
        buttons.setAlignment(Pos.CENTER);
        VBox root = new VBox(14, preview, buttons, status);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(18));

        Stage stage = new Stage();
        stage.setTitle("Share your ranking");
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        Scene scene = new Scene(root);
        String css = getClass().getResource("/style.css") != null
                ? getClass().getResource("/style.css").toExternalForm() : null;
        if (css != null) scene.getStylesheets().add(css);
        ThemeManager.apply(scene);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });
        close.setOnAction(e -> stage.close());
        stage.setScene(scene);
        stage.show();
    }

    private void render() {
        var card = ShareCard.build(title, subtitle, entries, footerStat, ShareCard.PALETTES.get(paletteIndex));
        rendered = ShareCard.render(card);
        preview.setImage(rendered);
    }

    private void savePng(Label status) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save ranking image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG image", "*.png"));
        // Keep letters, digits, spaces, underscores and hyphens so the name is valid on
        // any OS. Accented letters are dropped too.
        chooser.setInitialFileName(title.replaceAll("[^A-Za-z0-9 _-]", "").trim().replace(' ', '-') + "-ranking.png");
        File file = chooser.showSaveDialog(owner);
        if (file == null) return;
        // Capture the current image: Change Colors can be pressed while the file is written.
        Image toSave = rendered;
        Background.run(
                () -> {
                    ImageExport.writePng(toSave, file);
                    return file;
                },
                saved -> status.setText("Saved to " + saved.getAbsolutePath()),
                Background::showError);
    }
}
