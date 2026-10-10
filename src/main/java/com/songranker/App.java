package com.songranker;

import com.songranker.ui.AppContext;
import com.songranker.ui.screens.HomeScreen;
import com.songranker.ui.util.ThemeManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public final class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        StackPane screenHost = new StackPane();
        AppContext context = new AppContext(primaryStage, screenHost);

        Scene scene = new Scene(screenHost, 900, 650);
        String css = getClass().getResource("/style.css") != null
                ? getClass().getResource("/style.css").toExternalForm() : null;
        if (css != null) scene.getStylesheets().add(css);
        // Added after style.css so the theme sheet's rules come last.
        ThemeManager.apply(scene);

        primaryStage.setTitle("Song Ranker");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(700);
        primaryStage.setMinHeight(500);
        primaryStage.show();

        context.navigate(new HomeScreen(context));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
