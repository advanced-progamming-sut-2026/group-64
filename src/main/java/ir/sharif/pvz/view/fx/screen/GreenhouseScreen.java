package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.model.GreenhousePot;
import ir.sharif.pvz.model.GreenhouseService;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.view.fx.Assets;
import ir.sharif.pvz.view.fx.GameUi;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The greenhouse bench. Each pot is either locked, empty, growing (with the
 * time left and a diamond shortcut) or ready to harvest.
 */
public final class GreenhouseScreen extends Screen {

    private static final double POT_ART = 56;

    public GreenhouseScreen(GameUi ui) {
        super(ui);
    }

    @Override
    public Parent build() {
        User user = ui.user();
        List<GreenhousePot> pots = user.getGreenhousePots();

        GridPane bench = new GridPane();
        bench.setHgap(14);
        bench.setVgap(14);
        bench.setAlignment(Pos.CENTER);
        for (int index = 0; index < pots.size(); index++) {
            int column = index % GreenhouseService.COLUMNS;
            int row = index / GreenhouseService.COLUMNS;
            bench.add(pot(pots.get(index), column + 1, row + 1), column, row);
        }

        Button shop = new Button("Open the shop");
        shop.getStyleClass().add("primary-button");
        shop.setOnAction(event -> ui.submit("enter shop"));

        VBox column = new VBox(20, Forms.hint("Grown plants start your next level already boosted."),
                bench, shop);
        column.setAlignment(Pos.CENTER);
        column.setPadding(new Insets(24));

        BorderPane layout = new BorderPane(column);
        layout.setTop(Chrome.bar(ui, "Greenhouse"));
        layout.getStyleClass().addAll("screen", "greenhouse-screen");
        // the bench sits on its own backdrop, as the document asks. Set inline,
        // because the stylesheet's own background colour would otherwise paint
        // over it; the colour underneath fills whatever the image does not
        // reach rather than leaving a black edge.
        var backdrop = GreenhouseScreen.class.getResource("/assets/backgrounds/greenhouse.png");
        if (backdrop != null) {
            layout.setStyle("-fx-background-color: #1d2a14;"
                    + "-fx-background-image: url('" + backdrop.toExternalForm() + "');"
                    + "-fx-background-size: cover;"
                    + "-fx-background-position: center center;");
        }
        return layout;
    }

    private StackPane pot(GreenhousePot pot, int x, int y) {
        VBox body = new VBox(6);
        body.setAlignment(Pos.CENTER);
        body.setPrefSize(150, 150);
        body.getStyleClass().add("pot");

        if (!pot.isUnlocked()) {
            body.getStyleClass().add("pot-locked");
            body.getChildren().addAll(new Label("🔒"), Forms.hint("Locked"));
            return new StackPane(body);
        }
        if (pot.isEmpty()) {
            Button plant = new Button("Plant");
            plant.getStyleClass().add("primary-button");
            plant.setOnAction(event -> ui.submit("plant pot at (" + x + ", " + y + ")"));
            body.getChildren().addAll(new Label("🪴"), plant);
            return new StackPane(body);
        }

        body.getChildren().add(Assets.view(Assets.plant(pot.getPlantType()), POT_ART));
        Label name = new Label(pot.getPlantType());
        name.getStyleClass().add("pot-name");
        body.getChildren().add(name);

        long now = System.currentTimeMillis();
        if (pot.isReady(now)) {
            Button collect = new Button("Harvest");
            collect.getStyleClass().add("primary-button");
            collect.setOnAction(event -> ui.submit("collect (" + x + ", " + y + ")"));
            body.getChildren().add(collect);
            body.getStyleClass().add("pot-ready");
        } else {
            Label left = new Label(remaining(pot.getReadyAtMillis() - now));
            left.getStyleClass().add("pot-timer");
            Button grow = new Button("💎 Grow now");
            grow.getStyleClass().add("link-button");
            grow.setOnAction(event -> ui.submit("grow (" + x + ", " + y + ")"));
            body.getChildren().addAll(left, grow);
        }
        return new StackPane(body);
    }

    /**
     * Formats a duration the way the game does: "3h 26m", or "4m" near the end.
     */
    private String remaining(long millis) {
        long totalMinutes = Math.max(0, millis) / 60_000;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%dh %02dm", hours, minutes)
                : String.format(Locale.ROOT, "%dm", minutes);
    }
}
