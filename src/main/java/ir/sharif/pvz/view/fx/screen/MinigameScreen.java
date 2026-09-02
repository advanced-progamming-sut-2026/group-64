package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.game.Minigames;
import ir.sharif.pvz.view.fx.Assets;
import ir.sharif.pvz.view.fx.GameUi;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/**
 * The minigame list, with each game's stage progress and a way into the next
 * stage the player has not finished.
 */
public final class MinigameScreen extends Screen {

    private static final double ART = 70;

    public MinigameScreen(GameUi ui) {
        super(ui);
    }

    @Override
    public Parent build() {
        User user = ui.user();
        FlowPane games = new FlowPane(18, 18);
        games.setAlignment(Pos.TOP_CENTER);
        for (String name : Minigames.NAMES) {
            games.getChildren().add(card(name, user.getMinigameProgress().getOrDefault(name, 0)));
        }
        games.setPadding(new Insets(24));

        BorderPane layout = new BorderPane(games);
        layout.setTop(Chrome.bar(ui, "Minigames"));
        layout.getStyleClass().addAll("screen", "minigame-screen");
        return layout;
    }

    private VBox card(String name, int done) {
        boolean finished = done >= Minigames.STAGES;
        int next = Math.min(done + 1, Minigames.STAGES);

        Label title = new Label(name);
        title.getStyleClass().add("shop-title");
        title.setWrapText(true);

        ProgressBar bar = new ProgressBar(done / (double) Minigames.STAGES);
        bar.setPrefWidth(200);
        Label progress = new Label(done + " / " + Minigames.STAGES + " stages");
        progress.getStyleClass().add("shop-body");

        Button play = new Button(finished ? "Replay stage " + Minigames.STAGES : "Play stage " + next);
        play.getStyleClass().add("primary-button");
        play.setOnAction(event -> ui.submit("play -m " + name + " -s " + next));

        VBox card = new VBox(10, title, Assets.view(Assets.zombie("normal"), ART),
                bar, progress, play);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(250);
        card.getStyleClass().add("shop-card");
        return card;
    }
}
