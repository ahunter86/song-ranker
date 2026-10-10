package com.songranker.ui.screens;

import com.songranker.persistence.RankingRunRepository.SavedRunSummary;
import com.songranker.ui.AppContext;
import com.songranker.ui.session.RankingSession;
import com.songranker.ui.session.RatingSession;
import com.songranker.ui.util.HistoryFilter;
import com.songranker.ui.util.HistoryFilter.ModeFilter;
import com.songranker.ui.util.Shortcuts;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.Predicate;

public final class HistoryScreen extends VBox {

    private final AppContext ctx;
    private final ObservableList<SavedRunSummary> inProgress;
    private final ObservableList<SavedRunSummary> completed;
    private final ListView<SavedRunSummary> inProgressList = new ListView<>();
    private final ListView<SavedRunSummary> completedList = new ListView<>();
    private final Tab inProgressTab = new Tab();
    private final Tab completedTab = new Tab();
    private final TabPane tabs = new TabPane(inProgressTab, completedTab);

    public HistoryScreen(AppContext ctx) {
        this.ctx = ctx;
        setSpacing(12);
        setPadding(new Insets(24));

        Label title = new Label("Resume or review");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        inProgress = FXCollections.observableArrayList(ctx.runRepository.findInProgress());
        completed = FXCollections.observableArrayList(ctx.runRepository.findHistory());
        // A FilteredList is a live view. Changing its predicate, or adding to or removing from
        // the source list, updates the ListView on its own.
        FilteredList<SavedRunSummary> shownInProgress = new FilteredList<>(inProgress);
        FilteredList<SavedRunSummary> shownCompleted = new FilteredList<>(completed);
        inProgressList.setItems(shownInProgress);
        completedList.setItems(shownCompleted);
        inProgressList.setCellFactory(lv -> summaryCell());
        completedList.setCellFactory(lv -> summaryCell());

        TextField search = new TextField();
        search.setPromptText("Search by artist, album, or mode...");
        search.setPrefWidth(320);
        ComboBox<ModeFilter> modeBox = new ComboBox<>();
        modeBox.getItems().setAll(ModeFilter.values());
        modeBox.setValue(ModeFilter.ALL);

        Label emptyInProgress = new Label();
        Label emptyCompleted = new Label();
        inProgressList.setPlaceholder(emptyInProgress);
        completedList.setPlaceholder(emptyCompleted);

        Runnable applyFilter = () -> {
            Predicate<SavedRunSummary> p = run -> HistoryFilter.matches(run, search.getText(), modeBox.getValue());
            shownInProgress.setPredicate(p);
            shownCompleted.setPredicate(p);
            boolean filtering = !search.getText().isBlank() || modeBox.getValue() != ModeFilter.ALL;
            String empty = filtering ? "No rankings match your search." : "Nothing here yet.";
            emptyInProgress.setText(empty);
            emptyCompleted.setText(empty);
            updateTabTitles(shownInProgress.size(), shownCompleted.size());
        };
        search.textProperty().addListener((obs, o, n) -> applyFilter.run());
        modeBox.valueProperty().addListener((obs, o, n) -> applyFilter.run());
        inProgress.addListener((javafx.collections.ListChangeListener<SavedRunSummary>) c -> applyFilter.run());
        completed.addListener((javafx.collections.ListChangeListener<SavedRunSummary>) c -> applyFilter.run());

        inProgressTab.setContent(inProgressList);
        inProgressTab.setClosable(false);
        completedTab.setContent(completedList);
        completedTab.setClosable(false);
        tabs.setPrefHeight(420);
        applyFilter.run();

        Button openButton = new Button("Open");
        openButton.getStyleClass().add("accent-button");
        openButton.setOnAction(e -> openSelected());
        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("accent-button");
        deleteButton.setOnAction(e -> deleteSelected());
        Button backButton = new Button("Back to home");
        backButton.getStyleClass().add("accent-button");
        backButton.setOnAction(e -> ctx.navigate(new HomeScreen(ctx)));

        for (ListView<SavedRunSummary> list : java.util.List.of(inProgressList, completedList)) {
            list.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) openSelected();
            });
            // ListView does not open an item on Enter by itself.
            list.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    openSelected();
                    e.consume();
                }
            });
        }

        Label shortcutHint = new Label("Enter opens  •  Delete removes  •  Ctrl+F searches  •  Esc goes back");
        shortcutHint.getStyleClass().add("subtle-text");
        shortcutHint.setStyle("-fx-font-size: 12px;");

        HBox filterRow = new HBox(10, search, modeBox);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        HBox navRow = new HBox(10, backButton, openButton, deleteButton);
        navRow.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(title, filterRow, tabs, navRow, shortcutHint);

        Shortcuts.forScreen(this)
                .escapeFires(backButton)
                .on(KeyCode.DELETE, this::deleteSelected)
                .onShortcut(KeyCode.F, search::requestFocus)
                .install();
    }

    private void updateTabTitles(int inProgressCount, int completedCount) {
        inProgressTab.setText("In progress (" + inProgressCount + ")");
        completedTab.setText("Completed (" + completedCount + ")");
    }

    private boolean onInProgressTab() {
        return tabs.getSelectionModel().getSelectedItem() == inProgressTab;
    }

    private SavedRunSummary selected() {
        return (onInProgressTab() ? inProgressList : completedList).getSelectionModel().getSelectedItem();
    }

    private void openSelected() {
        SavedRunSummary summary = selected();
        if (summary == null) return;
        Runnable backToHistory = () -> ctx.navigate(new HistoryScreen(ctx));
        if (onInProgressTab()) {
            if ("RATING".equals(summary.mode())) {
                RatingSession session = RatingSession.resume(ctx.runRepository, ctx.trackRatingRepository, summary.id());
                ctx.navigate(new RateScreen(ctx, session, summary.artistName(), summary.scopeDescription(),
                        backToHistory));
            } else {
                RankingSession session = RankingSession.resume(ctx.runRepository, ctx.trackRatingRepository,
                        summary.id());
                ctx.navigate(new PlayScreen(ctx, session, summary.artistName(), summary.scopeDescription(),
                        backToHistory));
            }
        } else {
            var ranking = ctx.runRepository.loadResultTracks(summary.id());
            ctx.navigate(new ResultsScreen(ctx, ranking, backToHistory, summary.id(), summary.artistName(),
                    summary.scopeDescription()));
        }
    }

    private void deleteSelected() {
        SavedRunSummary summary = selected();
        if (summary == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + summary.artistName() + " · " + summary.scopeDescription() + "\"?\n\n"
                        + "This can't be undone. Its notes and saved order go with it; song ratings you gave "
                        + "are kept.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Delete ranking");
        confirm.setHeaderText(null);
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        ctx.runRepository.delete(summary.id());
        // Remove from the source list. The filtered views and the tab counts follow.
        (onInProgressTab() ? inProgress : completed).remove(summary);
    }

    private static ListCell<SavedRunSummary> summaryCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(SavedRunSummary s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setText(null);
                } else {
                    setText(s.artistName() + " · " + s.scopeDescription()
                            + "  [" + HistoryFilter.modeLabel(s.mode()) + "]"
                            + "  (updated " + s.updatedAt().substring(0, 10) + ")");
                }
            }
        };
    }
}
