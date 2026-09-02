package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.model.User;
import ir.sharif.pvz.view.fx.GameUi;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * Difficulty from phase 1 plus the three settings phase 2 introduces: how fast
 * the game advances, whether the lawn grid is drawn, and whether the debug
 * cheats are offered during a level.
 */
public final class SettingsScreen extends Screen {

    private static final double PANEL_WIDTH = 560;

    public SettingsScreen(GameUi ui) {
        super(ui);
    }

    @Override
    public Parent build() {
        User user = ui.user();

        VBox panel = Forms.panel(20,
                difficulty(user),
                speed(user),
                toggle("Show the lawn grid",
                        "Draws red lines between the tiles while you play.",
                        user.isShowGrid(), "menu settings toggle-grid"),
                toggle("Debug mode",
                        "Adds buttons for free sun, coins, diamonds and plant food during a level.",
                        user.isDebugMode(), "menu settings toggle-debug"));
        panel.setMaxWidth(PANEL_WIDTH);

        VBox column = new VBox(panel);
        column.setAlignment(Pos.TOP_CENTER);
        column.setPadding(new Insets(28));

        BorderPane layout = new BorderPane(column);
        layout.setTop(Chrome.bar(ui, "Settings"));
        layout.getStyleClass().addAll("screen", "settings-screen");
        return layout;
    }

    private VBox difficulty(User user) {
        Label value = new Label("Level " + user.getDifficulty());
        value.getStyleClass().add("setting-value");

        Slider slider = new Slider(1, 5, user.getDifficulty());
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.valueProperty().addListener((observable, was, now) ->
                value.setText("Level " + now.intValue()));
        slider.setOnMouseReleased(event ->
                ui.submit("menu settings change-difficulty -l " + (int) slider.getValue()));

        return group("Difficulty",
                "Higher levels send tougher waves and slow your sun down.", slider, value);
    }

    private VBox speed(User user) {
        Label value = new Label(user.getGameSpeed() + "x");
        value.getStyleClass().add("setting-value");

        Slider slider = new Slider(1, 3, user.getGameSpeed());
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.valueProperty().addListener((observable, was, now) ->
                value.setText(now.intValue() + "x"));
        slider.setOnMouseReleased(event ->
                ui.submit("menu settings change-speed -l " + (int) slider.getValue()));

        return group("Game speed", "How fast a level plays out.", slider, value);
    }

    private VBox toggle(String caption, String hint, boolean on, String command) {
        CheckBox box = new CheckBox(caption);
        box.setSelected(on);
        box.getStyleClass().add("setting-toggle");
        box.setOnAction(event -> ui.submit(command));
        return new VBox(4, box, Forms.hint(hint));
    }

    private VBox group(String caption, String hint, Slider slider, Label value) {
        Label label = new Label(caption);
        label.getStyleClass().add("section-heading");
        VBox box = new VBox(6, label, Forms.hint(hint), slider, value);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }
}
