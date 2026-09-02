package ir.sharif.pvz.view.fx.screen;

import com.google.gson.Gson;
import ir.sharif.pvz.model.game.GameCatalog;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.VersusGame;
import ir.sharif.pvz.net.Message;
import ir.sharif.pvz.net.Protocol;
import ir.sharif.pvz.net.Snapshot;
import ir.sharif.pvz.view.fx.Assets;
import ir.sharif.pvz.view.fx.GameUi;
import ir.sharif.pvz.view.fx.Sfx;
import ir.sharif.pvz.view.fx.widget.ActorCard;
import ir.sharif.pvz.view.fx.widget.GameGeometry;
import ir.sharif.pvz.view.fx.widget.Sticker;
import ir.sharif.pvz.view.fx.widget.VersusLawnView;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * A two-player round of "I, Zombie".
 *
 * <p>Nothing here simulates anything. The screen draws the last board the
 * server sent, and every click becomes a request for the server to apply, which
 * is what keeps the two players looking at the same game.
 */
public final class VersusScreen extends Screen {

    private static final Gson GSON = new Gson();
    private static final double SECONDS_PER_FRAME = 1.0 / 60;

    /** The canned lines the document asks for. */
    private static final List<String> PHRASES =
            List.of("Good luck!", "Nice one.", "You will not get through.");
    private static final List<String> EMOJI = List.of("😄", "😱", "🧠");

    private final VersusLawnView lawn = new VersusLawnView("ancient-egypt");
    private final Label clock = new Label();
    private final Label sunLabel = new Label("0");
    private final Label roleLabel = new Label();
    private final Label reactionLabel = new Label();
    private final VBox cardBar = new VBox(8);
    private final StackPane stickerLayer = new StackPane();

    private final String role;
    private final String opponent;

    private volatile Snapshot board;
    private AnimationTimer loop;
    private double elapsed;
    private String armed;
    private boolean finished;

    public VersusScreen(GameUi ui, String role, String opponent) {
        super(ui);
        this.role = role;
        this.opponent = opponent;
    }

    private boolean plantsSide() {
        return "plants".equals(role);
    }

    @Override
    public Parent build() {
        lawn.setOnMouseClicked(event -> onLawnClick(event.getX(), event.getY()));

        StackPane stage = new StackPane(lawn, stickerLayer);
        stickerLayer.setMouseTransparent(true);
        stickerLayer.setAlignment(Pos.TOP_RIGHT);
        stickerLayer.setPadding(new Insets(24));
        stage.getStyleClass().add("lawn-stage");
        // the canvas would otherwise claim its full size as a minimum and
        // squeeze the bars above and below it out of the window
        stage.setMinSize(0, 0);
        bindLawnScale(stage);

        BorderPane layout = new BorderPane(stage);
        layout.setTop(hud());
        layout.setLeft(sideTray());
        layout.setBottom(reactionBar());
        layout.getStyleClass().addAll("screen", "battle-screen");

        listen();
        startLoop();
        return layout;
    }

    /**
     * Takes the boards and the reactions the server pushes. Both arrive on the
     * reader thread, so they are handed to the toolkit before touching the UI.
     */
    private void listen() {
        var link = ui.app().connection();
        if (link == null) {
            return;
        }
        link.on(Protocol.MATCH_STATE, message ->
                board = GSON.fromJson(message.getData().get("state"), Snapshot.class));
        link.on(Protocol.REACTION, message ->
                Platform.runLater(() -> showReaction(message)));
        link.on(Protocol.MATCH_OVER, message ->
                Platform.runLater(() -> finish(message)));
    }

    private void startLoop() {
        loop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                elapsed += SECONDS_PER_FRAME;
                Snapshot latest = board;
                if (latest != null) {
                    lawn.render(latest, elapsed);
                    refreshHud(latest);
                }
            }
        };
        loop.start();
    }

    private void refreshHud(Snapshot latest) {
        int seconds = (int) Math.max(0, Math.ceil(latest.secondsLeft()));
        clock.setText(String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
        sunLabel.setText(String.valueOf(plantsSide() ? latest.plantSun() : latest.zombieSun()));
    }

    // ===== chrome =====

    private HBox hud() {
        sunLabel.getStyleClass().add("hud-value");
        clock.getStyleClass().add("hud-wave");
        roleLabel.setText(plantsSide()
                ? "You grow the plants — hold out for two minutes"
                : "You send the zombies — eat all five brains");
        roleLabel.getStyleClass().add("hud-objective");

        HBox sun = new HBox(6, Assets.view(Assets.ui("sun"), 30), sunLabel);
        sun.setAlignment(Pos.CENTER);
        sun.getStyleClass().add("hud-chip");

        VBox middle = new VBox(2, clock, roleLabel);
        middle.setAlignment(Pos.CENTER);

        Button quit = new Button("Leave");
        quit.getStyleClass().add("hud-button");
        quit.setOnAction(event -> leave());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        HBox bar = new HBox(14, sun, spacer, middle, spacer2, versusLabel(), quit);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 18, 10, 18));
        bar.getStyleClass().add("hud-bar");
        return bar;
    }

    private Label versusLabel() {
        Label label = new Label("vs " + opponent);
        label.getStyleClass().add("hud-value");
        return label;
    }

    /**
     * The cards down the left: seed packets for the plant side, zombies for the
     * other one, each with what it costs.
     */
    private VBox sideTray() {
        rebuildCards();
        cardBar.setAlignment(Pos.TOP_CENTER);
        ScrollPane scroller = new ScrollPane(cardBar);
        scroller.setFitToWidth(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.getStyleClass().add("seed-scroller");
        VBox.setVgrow(scroller, Priority.ALWAYS);

        VBox tray = new VBox(10, scroller);
        tray.setPadding(new Insets(12, 10, 12, 10));
        tray.setPrefWidth(132);
        tray.setMinWidth(132);
        tray.getStyleClass().add("seed-tray");
        return tray;
    }

    private void rebuildCards() {
        cardBar.getChildren().clear();
        if (plantsSide()) {
            for (String type : VersusGame.PLANTS) {
                int cost = GameCatalog.get().plant(type).getSunCost();
                cardBar.getChildren().add(card(Assets.plant(type), type, cost));
            }
        } else {
            Map<String, Integer> prices = VersusGame.zombiePrices();
            prices.keySet().stream().sorted().forEach(type ->
                    cardBar.getChildren().add(card(Assets.zombie(type), type, prices.get(type))));
        }
    }

    private ActorCard card(javafx.scene.image.Image art, String type, int cost) {
        return new ActorCard(art, type)
                .cost(cost)
                .selected(type.equals(armed))
                .onClick(() -> {
                    armed = type.equals(armed) ? null : type;
                    rebuildCards();
                });
    }

    /**
     * The three phrases and three emoji the document asks for.
     */
    private VBox reactionBar() {
        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER);
        for (String phrase : PHRASES) {
            buttons.getChildren().add(reactionButton("text", phrase, phrase));
        }
        for (String face : EMOJI) {
            buttons.getChildren().add(reactionButton("emoji", face, face));
        }
        for (Sticker.Kind sticker : Sticker.Kind.values()) {
            Button button = reactionButton("sticker", sticker.glyph(), sticker.name());
            button.setTooltip(new javafx.scene.control.Tooltip(sticker.title()));
            buttons.getChildren().add(button);
        }

        reactionLabel.getStyleClass().add("reaction-banner");
        reactionLabel.setVisible(false);

        VBox box = new VBox(6, reactionLabel, buttons);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(8));
        box.getStyleClass().add("reaction-bar");
        return box;
    }

    private Button reactionButton(String kind, String label, String value) {
        Button button = new Button(label);
        button.getStyleClass().add("ghost-button");
        button.setOnAction(event -> send(kind, value));
        return button;
    }

    private void send(String kind, String value) {
        var link = ui.app().connection();
        if (link == null) {
            return;
        }
        link.tell(link.request(Protocol.REACTION).with("kind", kind).with("value", value));
    }

    /**
     * Shows what the opponent sent, in the corner, for a few seconds.
     */
    private void showReaction(Message message) {
        if ("sticker".equals(message.text("kind"))) {
            playSticker(message.text("value"));
            return;
        }
        reactionLabel.setText(opponent + ": " + message.text("value"));
        reactionLabel.setVisible(true);
        javafx.animation.PauseTransition hide =
                new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
        hide.setOnFinished(event -> reactionLabel.setVisible(false));
        hide.play();
    }

    /**
     * Drops an animated sticker in the corner of the lawn, where the document
     * asks for the opponent's reaction to appear.
     */
    private void playSticker(String name) {
        Sticker.Kind kind = Sticker.Kind.byName(name);
        if (kind == null) {
            return;
        }
        Label sticker = Sticker.play(kind);
        stickerLayer.getChildren().setAll(sticker);
        javafx.animation.PauseTransition clear =
                new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3.2));
        clear.setOnFinished(event -> stickerLayer.getChildren().remove(sticker));
        clear.play();
    }

    // ===== interaction =====

    private void onLawnClick(double x, double y) {
        int[] tile = lawn.tileAt(x, y);
        if (tile == null || armed == null) {
            return;
        }
        var link = ui.app().connection();
        if (link == null) {
            return;
        }
        link.tell(link.request(Protocol.MATCH_ACTION)
                .with("plant", armed)
                .with("col", tile[0])
                .with("row", tile[1]));
        Sfx.play(Sfx.Sound.PLANT);
        armed = null;
        rebuildCards();
    }

    private void leave() {
        var link = ui.app().connection();
        if (link != null) {
            link.tell(link.request(Protocol.MATCH_LEAVE));
        }
        stop();
        ui.refresh();
    }

    private void finish(Message message) {
        if (finished) {
            return;
        }
        finished = true;
        stop();
        boolean won = ui.user() != null
                && ui.user().getUsername().equals(message.text("winner"));
        Sfx.play(won ? Sfx.Sound.WIN : Sfx.Sound.LOSE);

        Button back = new Button("Back to the menu");
        back.getStyleClass().add("primary-button");
        back.setOnAction(event -> {
            ui.closeModal();
            ui.refresh();
        });
        HBox buttons = new HBox(back);
        buttons.setAlignment(Pos.CENTER);
        ui.showModal(Dialogs.panel(won ? "You win!" : "You lose",
                message.text("reason"), buttons));
    }

    /**
     * Puts one board on screen without a server behind it. Only the snapshot
     * tool uses this.
     */
    public void showBoardForSnapshot(Snapshot sample) {
        board = sample;
        lawn.render(sample, 0);
        refreshHud(sample);
    }

    @Override
    public void dispose() {
        stop();
        var link = ui.app().connection();
        if (link != null) {
            // stop taking boards for a match this screen is no longer showing
            link.off(Protocol.MATCH_STATE);
            link.off(Protocol.REACTION);
            link.off(Protocol.MATCH_OVER);
        }
    }

    private void stop() {
        if (loop != null) {
            loop.stop();
        }
    }

    /**
     * Scales the lawn to cover its pane, as the single-player screen does.
     */
    private void bindLawnScale(StackPane stage) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(stage.widthProperty());
        clip.heightProperty().bind(stage.heightProperty());
        stage.setClip(clip);

        Runnable cover = () -> {
            double width = Math.max(stage.getWidth(),
                    stage.getHeight() * GameGeometry.WIDTH / GameGeometry.HEIGHT);
            if (width > 0) {
                lawn.setWidth(width);
                lawn.setHeight(width * GameGeometry.HEIGHT / GameGeometry.WIDTH);
            }
        };
        stage.widthProperty().addListener((observable, was, now) -> cover.run());
        stage.heightProperty().addListener((observable, was, now) -> cover.run());
    }

    /**
     * Kept so the compiler sees the engine constant this screen is sized around.
     */
    static int rows() {
        return GameSession.ROWS;
    }
}
