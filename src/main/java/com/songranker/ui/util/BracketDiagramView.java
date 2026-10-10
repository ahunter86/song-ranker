package com.songranker.ui.util;

import com.songranker.domain.BracketEngine;
import com.songranker.domain.Matchup;
import com.songranker.domain.Track;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;

import java.util.List;

/**
 * Renders a {@link BracketEngine} as an actual bracket tree: rounds as columns, matchups as boxes,
 * connected by rectilinear lines the way a real tournament bracket looks. Rounds not reached yet are
 * drawn as "TBD vs TBD" placeholders so the whole shape of the bracket is visible from round one.
 */
public final class BracketDiagramView {

    private static final double BOX_WIDTH = 190;
    private static final double BOX_HEIGHT = 46;
    private static final double ROUND_WIDTH = 260;
    private static final double BASE_GAP = 62;
    private static final double TOP_PADDING = 30;

    private BracketDiagramView() {
    }

    /** @param currentMatchup the matchup currently being played, highlighted if found (may be null) */
    public static Node build(BracketEngine engine, Matchup currentMatchup) {
        List<List<Matchup>> playedRounds = engine.getAllRounds();
        int round0Count = playedRounds.get(0).size();
        int totalRounds = engine.getTotalRounds();

        Pane canvas = new Pane();
        double[][] centerY = new double[totalRounds][];

        for (int r = 0; r < totalRounds; r++) {
            // Each round has half as many matchups as the one before: round0Count / 2^r.
            int count = round0Count >> r;
            centerY[r] = new double[count];
            for (int i = 0; i < count; i++) {
                // Round 1 boxes are stacked evenly. Every later box sits halfway between the
                // two boxes that feed it.
                double y = (r == 0)
                        ? TOP_PADDING + i * BASE_GAP + BASE_GAP / 2.0
                        : (centerY[r - 1][2 * i] + centerY[r - 1][2 * i + 1]) / 2.0;
                centerY[r][i] = y;

                // The engine only creates a round once the previous one is finished. Later rounds
                // have no matchup yet and are drawn as "TBD" boxes.
                Matchup matchup = (r < playedRounds.size()) ? playedRounds.get(r).get(i) : null;
                Node box = buildBox(matchup, matchup != null && matchup == currentMatchup);
                box.setLayoutX(r * ROUND_WIDTH);
                box.setLayoutY(y - BOX_HEIGHT / 2.0);
                canvas.getChildren().add(box);
            }
        }

        for (int r = 0; r < totalRounds - 1; r++) {
            // Join each pair of boxes in round r to the box they feed in round r + 1: a stub
            // from each box to a shared vertical line at midX, then one stub from that line
            // into the next box.
            int childCount = centerY[r + 1].length;
            double rightX = r * ROUND_WIDTH + BOX_WIDTH;
            double childLeftX = (r + 1) * ROUND_WIDTH;
            double midX = (rightX + childLeftX) / 2.0;
            for (int i = 0; i < childCount; i++) {
                double yA = centerY[r][2 * i];
                double yB = centerY[r][2 * i + 1];
                double yChild = centerY[r + 1][i];
                canvas.getChildren().addAll(
                        connector(rightX, yA, midX, yA),
                        connector(rightX, yB, midX, yB),
                        connector(midX, yA, midX, yB),
                        connector(midX, yChild, childLeftX, yChild));
            }
        }

        canvas.setPrefSize(totalRounds * ROUND_WIDTH, TOP_PADDING * 2 + round0Count * BASE_GAP);

        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.setPannable(true);
        return scrollPane;
    }

    private static Line connector(double x1, double y1, double x2, double y2) {
        Line line = new Line(x1, y1, x2, y2);
        line.getStyleClass().add("bracket-connector");
        return line;
    }

    private static Node buildBox(Matchup matchup, boolean isCurrent) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPrefSize(BOX_WIDTH, BOX_HEIGHT);
        box.setMinSize(BOX_WIDTH, BOX_HEIGHT);
        box.setMaxSize(BOX_WIDTH, BOX_HEIGHT);
        box.getStyleClass().add("bracket-box");
        if (isCurrent) box.getStyleClass().add("bracket-box-current");

        if (matchup == null) {
            box.getStyleClass().add("bracket-box-placeholder");
            box.getChildren().addAll(trackLabel("TBD", false, false), trackLabel("TBD", false, false));
            return box;
        }
        if (matchup.isBye()) {
            box.getChildren().add(trackLabel(matchup.getTrackA().getName() + "  (bye)", true, false));
            return box;
        }

        Track winner = matchup.getWinner();
        Track a = matchup.getTrackA();
        Track b = matchup.getTrackB();
        box.getChildren().add(trackLabel(a.getName(), a.equals(winner), winner != null && !a.equals(winner)));
        box.getChildren().add(trackLabel(b.getName(), b.equals(winner), winner != null && !b.equals(winner)));
        return box;
    }

    private static Label trackLabel(String text, boolean isWinner, boolean isLoser) {
        Label label = new Label(text);
        label.setMaxWidth(BOX_WIDTH - 16);
        label.setMinWidth(BOX_WIDTH - 16);
        if (isWinner) {
            label.getStyleClass().add("accent-text");
        } else if (isLoser) {
            label.getStyleClass().add("subtle-text");
            label.setStyle("-fx-strikethrough: true;");
        }
        return label;
    }
}
