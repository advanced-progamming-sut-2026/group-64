package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.model.game.GameCatalog;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.Minigames;
import ir.sharif.pvz.model.game.VersusGame;
import ir.sharif.pvz.view.fx.Assets;
import ir.sharif.pvz.view.fx.GameUi;
import ir.sharif.pvz.view.fx.Sfx;
import ir.sharif.pvz.view.fx.widget.ActorCard;
import ir.sharif.pvz.view.fx.widget.GameGeometry;
import ir.sharif.pvz.view.fx.widget.LawnView;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * Two players on one keyboard and mouse.
 *
 * <p>The whole match runs here — there is no server involved — with the plant
 * player clicking their seed packets and the zombie player driving a cursor
 * with the arrow keys. Both sides act on the same {@link GameSession}, so the
 * one board on screen is the only board there is.
 */
public final class CouchPlayScreen extends Screen {

    private static final double SECONDS_PER_FRAME = 1.0 / 60;
    private static final double SECONDS_PER_TICK = 1.0 / GameSession.TICKS_PER_SECOND;

    private final LawnView lawn = new LawnView("ancient-egypt");
    private final Label clock = new Label();
    private final Label plantSun = new Label("0");
    private final Label zombieSun = new Label("0");
    private final Label zombieHint = new Label();
    private final VBox plantCards = new VBox(8);

    private final VersusGame rules = new VersusGame();
    private final GameSession session;
    private final List<String> zombieTypes;

    private AnimationTimer loop;
    private double elapsed;
    private double tickDebt;
    private String armedPlant;
    private int zombieChoice;
    private int cursorCol = GameSession.COLS;
    private int cursorRow = 1;
    private boolean finished;

    public CouchPlayScreen(GameUi ui) {
        super(ui);
        this.session = Minigames.versus(rules, new java.util.Random());
        this.zombieTypes = VersusGame.zombiePrices().keySet().stream().sorted().toList();
    }

    @Override
    public Parent build() {
        lawn.setOnMouseClicked(event -> plantAt(event.getX(), event.getY()));

        StackPane stage = new StackPane(lawn);
        stage.getStyleClass().add("lawn-stage");
        // the canvas would otherwise claim its full size as a minimum and
        // squeeze the bars above and below it out of the window
        stage.setMinSize(0, 0);
        bindLawnScale(stage);

        BorderPane layout = new BorderPane(stage);
        layout.setTop(hud());
        layout.setLeft(plantTray());
        layout.setBottom(zombieTray());
        layout.getStyleClass().addAll("screen", "battle-screen");
        layout.setFocusTraversable(true);
        layout.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        layout.sceneProperty().addListener((observable, was, now) -> {
            if (now != null) {
                layout.requestFocus();
            }
        });

        rebuildPlantCards();
        startLoop();
        return layout;
    }

    private void startLoop() {
        loop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                elapsed += SECONDS_PER_FRAME;
                tickDebt += SECONDS_PER_FRAME / SECONDS_PER_TICK;
                int ticks = (int) tickDebt;
                if (ticks > 0) {
                    tickDebt -= ticks;
                    session.advance(ticks);
                }
                lawn.setHoveredTile(cursorCol, cursorRow, true);
                lawn.render(session, elapsed);
                refreshHud();
                if (session.isOver()) {
                    finish();
                }
            }
        };
        loop.start();
    }

    private void refreshHud() {
        double left = Math.max(0, VersusGame.ROUND_SECONDS - session.getElapsedSeconds());
        int seconds = (int) Math.ceil(left);
        clock.setText(String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
        plantSun.setText(String.valueOf(session.getSunAmount()));
        zombieSun.setText(String.valueOf(rules.getZombieSun()));
        zombieHint.setText("Player 2 — "
                + zombieTypes.get(zombieChoice) + "  ·  tile (" + cursorCol + ", " + cursorRow + ")");
    }

    // ===== player one: the mouse =====

    private void rebuildPlantCards() {
        plantCards.getChildren().clear();
        plantCards.setAlignment(Pos.TOP_CENTER);
        for (String type : VersusGame.PLANTS) {
            int cost = GameCatalog.get().plant(type).getSunCost();
            plantCards.getChildren().add(new ActorCard(Assets.plant(type), type)
                    .cost(cost)
                    .affordable(session.getSunAmount() >= cost)
                    .selected(type.equals(armedPlant))
                    .onClick(() -> {
                        armedPlant = type.equals(armedPlant) ? null : type;
                        rebuildPlantCards();
                    }));
        }
    }

    private void plantAt(double x, double y) {
        int[] tile = lawn.tileAt(x, y);
        if (tile == null || armedPlant == null) {
            return;
        }
        ui.view().info(session.plant(armedPlant, tile[0], tile[1]));
        Sfx.play(Sfx.Sound.PLANT);
        armedPlant = null;
        rebuildPlantCards();
    }

    // ===== player two: the keyboard =====

    /**
     * Arrow keys move the placement cursor, 1 to 5 pick a zombie, and space
     * drops it. Escape leaves the game to both of them.
     */
    private void onKey(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code == KeyCode.LEFT) {
            cursorCol = Math.max(VersusGame.RED_LINE_COLUMN + 1, cursorCol - 1);
        } else if (code == KeyCode.RIGHT) {
            cursorCol = Math.min(GameSession.COLS, cursorCol + 1);
        } else if (code == KeyCode.UP) {
            cursorRow = Math.max(1, cursorRow - 1);
        } else if (code == KeyCode.DOWN) {
            cursorRow = Math.min(GameSession.ROWS, cursorRow + 1);
        } else if (code == KeyCode.SPACE || code == KeyCode.ENTER) {
            placeZombie();
        } else if (code == KeyCode.ESCAPE) {
            leave();
        } else {
            pickZombie(code);
            return;
        }
        event.consume();
    }

    private void pickZombie(KeyCode code) {
        if (!code.isDigitKey()) {
            return;
        }
        int index = Integer.parseInt(code.getChar()) - 1;
        if (index >= 0 && index < zombieTypes.size()) {
            zombieChoice = index;
        }
    }

    private void placeZombie() {
        ui.view().info(session.placeZombie(zombieTypes.get(zombieChoice), cursorCol, cursorRow));
    }

    // ===== chrome =====

    private HBox hud() {
        plantSun.getStyleClass().add("hud-value");
        zombieSun.getStyleClass().add("hud-value");
        clock.getStyleClass().add("hud-wave");

        HBox one = new HBox(6, Assets.view(Assets.ui("sun"), 26), plantSun);
        one.setAlignment(Pos.CENTER);
        one.getStyleClass().add("hud-chip");

        HBox two = new HBox(6, new Label("🧟"), zombieSun);
        two.setAlignment(Pos.CENTER);
        two.getStyleClass().add("hud-chip");

        Button quit = new Button("Leave");
        quit.getStyleClass().add("hud-button");
        quit.setOnAction(event -> leave());

        Region spacerOne = new Region();
        HBox.setHgrow(spacerOne, Priority.ALWAYS);
        Region spacerTwo = new Region();
        HBox.setHgrow(spacerTwo, Priority.ALWAYS);

        HBox bar = new HBox(14, one, spacerOne, clock, spacerTwo, two, quit);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 18, 10, 18));
        bar.getStyleClass().add("hud-bar");
        return bar;
    }

    private VBox plantTray() {
        Label who = new Label("Player 1 — mouse");
        who.getStyleClass().add("dialogue-speaker");
        // scrolled, so a tall stack of cards cannot squeeze the bars above and
        // below out of the window
        javafx.scene.control.ScrollPane scroller =
                new javafx.scene.control.ScrollPane(plantCards);
        scroller.setFitToWidth(true);
        scroller.setMinHeight(0);
        scroller.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroller.getStyleClass().add("seed-scroller");
        VBox.setVgrow(scroller, Priority.ALWAYS);

        VBox tray = new VBox(10, who, scroller);
        tray.setMinHeight(0);
        tray.setPadding(new Insets(12, 10, 12, 10));
        tray.setPrefWidth(132);
        tray.setMinWidth(132);
        tray.getStyleClass().add("seed-tray");
        return tray;
    }

    /**
     * The second player's controls, spelled out because they are keys rather
     * than something they can see and click.
     */
    private VBox zombieTray() {
        HBox picks = new HBox(8);
        picks.setAlignment(Pos.CENTER);
        Map<String, Integer> prices = VersusGame.zombiePrices();
        for (int i = 0; i < zombieTypes.size(); i++) {
            String type = zombieTypes.get(i);
            Label label = new Label((i + 1) + ". " + type + " (" + prices.get(type) + ")");
            label.getStyleClass().add("hint");
            picks.getChildren().add(label);
        }

        zombieHint.getStyleClass().add("hud-objective");
        Label keys = new Label("Arrow keys move · number keys choose · space places · Esc leaves");
        keys.getStyleClass().add("hint");

        VBox box = new VBox(4, zombieHint, picks, keys);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(8));
        box.getStyleClass().add("reaction-bar");
        return box;
    }

    private void leave() {
        stop();
        ui.refresh();
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        stop();
        boolean plantsWon = session.isWon();
        Sfx.play(Sfx.Sound.WIN);

        Button back = new Button("Back to the menu");
        back.getStyleClass().add("primary-button");
        back.setOnAction(event -> {
            ui.closeModal();
            ui.refresh();
        });
        HBox buttons = new HBox(back);
        buttons.setAlignment(Pos.CENTER);
        ui.showModal(Dialogs.panel(plantsWon ? "Player 1 wins" : "Player 2 wins",
                plantsWon ? "The plants held the line." : "The zombies ate every brain.",
                buttons));
    }

    @Override
    public void dispose() {
        stop();
    }

    private void stop() {
        if (loop != null) {
            loop.stop();
        }
    }

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
}
