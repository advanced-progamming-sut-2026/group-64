package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.controller.GameMenuController;
import ir.sharif.pvz.controller.MenuType;
import ir.sharif.pvz.model.game.GameCatalog;
import ir.sharif.pvz.model.game.Burst;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.MinigameProp;
import ir.sharif.pvz.model.game.Sun;
import ir.sharif.pvz.view.fx.Assets;
import ir.sharif.pvz.view.fx.GameUi;
import ir.sharif.pvz.view.fx.Music;
import ir.sharif.pvz.view.fx.Sfx;
import ir.sharif.pvz.view.fx.widget.ActorCard;
import ir.sharif.pvz.view.fx.widget.GameGeometry;
import ir.sharif.pvz.view.fx.widget.BossBar;
import ir.sharif.pvz.view.fx.widget.LawnView;
import ir.sharif.pvz.view.fx.widget.WaveBar;
import java.util.List;
import java.util.Map;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.image.Image;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Slider;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.layout.VBox;

/**
 * A level in progress.
 *
 * <p>The engine is untouched: this screen simply calls the phase-1
 * {@code advance time} command on a timer, at a rate the settings menu
 * controls, and repaints the lawn from whatever state that leaves behind.
 */
public final class BattleScreen extends Screen {

    /** The engine's own tick rate, so one game second is ten ticks. */
    private static final double SECONDS_PER_TICK = 1.0 / GameSession.TICKS_PER_SECOND;

    private final MenuType menu;
    private final LawnView lawn;
    private final VBox seedBar = new VBox(6);
    private final Label sunLabel = new Label("0");
    private final Label plantFoodLabel = new Label("0");
    private final Label waveLabel = new Label();
    private final Label objectiveLabel = new Label();
    private final WaveBar waveBar = new WaveBar();
    private final BossBar bossBar = new BossBar();

    private AnimationTimer loop;
    private long lastFrameNanos;
    private double tickDebt;
    private double elapsedSeconds;
    private boolean paused;
    private int lastShotCount;
    private int lastWave;
    private boolean lastGameWon;
    private final java.util.Set<Burst> playedBursts =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private boolean finished;
    /** What the engine reported during the last ticks, kept for the report. */
    private List<String> closingReport = List.of();

    /** null when planting, otherwise the plant the cursor is carrying. */
    private String armedPlant;
    private boolean shovelArmed;
    private boolean plantFoodArmed;
    /** null unless an I, Zombie card is picked up. */
    private String armedZombie;

    /** The tile Beghouled is holding for the second half of a swap, or null. */
    private int[] swapFrom;

    public BattleScreen(GameUi ui, MenuType menu) {
        super(ui);
        this.menu = menu;
        this.lawn = new LawnView(chapterId());
    }

    private GameMenuController controller() {
        return (GameMenuController) ui.app().currentController();
    }

    private GameSession session() {
        return controller().getSession();
    }

    /**
     * The chapter this level belongs to, for the closing line.
     */
    private ir.sharif.pvz.model.game.Chapter chapterOfLevel() {
        GameSession session = session();
        return session == null
                ? ir.sharif.pvz.model.game.Chapter.ANCIENT_EGYPT
                : session.getLevel().getChapter();
    }

    private String chapterId() {
        GameSession session = ((GameMenuController) ui.app().currentController()).getSession();
        return session == null ? "ancient-egypt"
                : AdventureScreen.chapterId(session.getLevel().getChapter());
    }

    @Override
    public Parent build() {
        lawn.setShowGrid(ui.user().isShowGrid());
        lawn.setOnMouseClicked(event -> onLawnClick(event.getX(), event.getY()));
        lawn.setOnMouseMoved(event -> onLawnHover(event.getX(), event.getY()));
        lawn.setOnMouseExited(event -> lawn.setHoveredTile(-1, -1, false));

        StackPane stage = new StackPane(lawn);
        stage.getStyleClass().add("lawn-stage");
        bindLawnScale(stage);

        BorderPane layout = new BorderPane(stage);
        layout.setTop(hud());
        layout.setLeft(sideTray());
        layout.getStyleClass().addAll("screen", "battle-screen");

        layoutKeys(layout);
        Music.play(session() != null && session().getZomboss() != null
                ? "zomboss" : chapterId());
        startLoop();
        return layout;
    }

    /**
     * Scales the lawn to cover its pane rather than fit inside it, so the
     * background reaches every edge without ever being stretched out of shape —
     * the document's FillViewport suggestion. Whichever side overflows is
     * clipped away instead of showing an empty band beside the artwork.
     *
     * <p>The canvas is resized rather than scaled: its geometry is expressed in
     * fractions, so it redraws crisply at any size.
     */
    private void bindLawnScale(StackPane stage) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(stage.widthProperty());
        clip.heightProperty().bind(stage.heightProperty());
        stage.setClip(clip);

        Runnable cover = () -> {
            double width = Math.max(stage.getWidth(),
                    stage.getHeight() * GameGeometry.WIDTH / GameGeometry.HEIGHT);
            if (width <= 0) {
                return;
            }
            lawn.setWidth(width);
            lawn.setHeight(width * GameGeometry.HEIGHT / GameGeometry.WIDTH);
        };
        stage.widthProperty().addListener((observable, was, now) -> cover.run());
        stage.heightProperty().addListener((observable, was, now) -> cover.run());
    }

    // ===== chrome =====

    private HBox hud() {
        sunLabel.getStyleClass().add("hud-value");
        plantFoodLabel.getStyleClass().add("hud-value");
        waveLabel.getStyleClass().add("hud-wave");
        waveBar.getStyleClass().add("wave-bar");

        HBox sun = new HBox(6, Assets.view(Assets.ui("sun"), 30), sunLabel);
        sun.setAlignment(Pos.CENTER);
        sun.getStyleClass().add("hud-chip");

        HBox food = new HBox(6, new Label("🌿"), plantFoodLabel);
        food.setAlignment(Pos.CENTER);
        food.getStyleClass().add("hud-chip");

        objectiveLabel.getStyleClass().add("hud-objective");
        VBox waves = new VBox(2, waveLabel, waveBar, bossBar, objectiveLabel);
        waves.setAlignment(Pos.CENTER);

        Button pause = new Button("⏸");
        pause.getStyleClass().add("hud-button");
        pause.setOnAction(event -> openPauseMenu());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(14, sun, food, spacer, waves, spacer(),
                Chrome.wallet(ui.user()), pause);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 18, 10, 18));
        bar.getStyleClass().add("hud-bar");
        return bar;
    }

    private Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    /**
     * The seed packets run down the left edge, which leaves the lawn the full
     * height of the window and matches how the game itself is laid out.
     */
    private VBox sideTray() {
        seedBar.setAlignment(Pos.TOP_CENTER);
        seedBar.setPadding(new Insets(4));

        Button shovel = new Button("🪏 Shovel");
        shovel.getStyleClass().add("hud-button");
        shovel.setOnAction(event -> {
            shovelArmed = !shovelArmed;
            armedPlant = null;
            armedPlant = null;
            plantFoodArmed = false;
        });

        Button food = new Button("🌿 Plant food");
        food.getStyleClass().add("hud-button");
        food.setOnAction(event -> {
            plantFoodArmed = !plantFoodArmed;
            armedPlant = null;
            shovelArmed = false;
        });

        VBox tools = new VBox(6, shovel, food);
        tools.setAlignment(Pos.CENTER);
        if (ui.user().isDebugMode()) {
            tools.getChildren().add(debugTools());
        }

        ScrollPane scroller = new ScrollPane(seedBar);
        scroller.setFitToWidth(true);
        scroller.getStyleClass().add("seed-scroll");
        VBox.setVgrow(scroller, Priority.ALWAYS);

        VBox tray = new VBox(8, scroller, tools);
        tray.setPadding(new Insets(10, 8, 10, 10));
        tray.setPrefWidth(132);
        tray.setMinWidth(132);
        tray.getStyleClass().add("seed-tray");
        return tray;
    }

    /**
     * The cheats from phase 1, offered as buttons when debug mode is on.
     */
    private VBox debugTools() {
        Button sun = new Button("+500 sun");
        sun.setOnAction(event -> ui.submitQuietly("cheat add -n 500 suns"));
        Button food = new Button("+plant food");
        food.setOnAction(event -> ui.submitQuietly("cheat add-plant-food"));
        Button cooldown = new Button("No cooldown");
        cooldown.setOnAction(event -> ui.submitQuietly("cheat remove-cooldown"));
        Button coins = new Button("+500 coins");
        coins.setOnAction(event -> ui.submit("cheat add -n 500 coins"));
        Button gems = new Button("+10 diamonds");
        gems.setOnAction(event -> ui.submit("cheat add -n 10 diamonds"));
        Button waves = new Button("Start waves");
        waves.setOnAction(event -> ui.submitQuietly("start zombie waves"));

        VBox box = new VBox(6, sun, food, coins, gems, cooldown, waves);
        box.getStyleClass().add("debug-tools");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ===== the loop =====

    private void startLoop() {
        loop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                step(now);
            }
        };
        loop.start();
    }

    /**
     * Turns real elapsed time into engine ticks, scaled by the speed setting,
     * then redraws. Pausing simply stops feeding the engine, so nothing on the
     * lawn moves while the menu is open.
     */
    private void step(long now) {
        if (lastFrameNanos == 0) {
            lastFrameNanos = now;
            return;
        }
        double delta = (now - lastFrameNanos) / 1_000_000_000.0;
        lastFrameNanos = now;

        GameSession session = session();
        if (session == null) {
            finish();
            return;
        }
        if (!paused) {
            elapsedSeconds += delta;
            tickDebt += delta * ui.user().getGameSpeed() / SECONDS_PER_TICK;
            int ticks = (int) tickDebt;
            if (ticks > 0) {
                tickDebt -= ticks;
                advance(ticks);
            }
        }
        GameSession after = session();
        if (after == null) {
            finish();
            return;
        }
        playSoundsFor(after);
        lawn.render(after, elapsedSeconds);
        refreshHud(after);
    }

    /**
     * Advances the engine, collecting what it narrates rather than showing it.
     *
     * <p>A running level produces a line every time a sunflower makes sun, which
     * would bury the screen in notifications; the lawn already shows all of it.
     * The last batch is kept because it carries the win or lose message.
     */
    private void advance(int ticks) {
        GameSession before = session();
        List<String> messages = ui.view().capture(
                () -> ui.app().submit("advance time -t " + ticks + " ticks"));
        if (!messages.isEmpty()) {
            closingReport = messages;
        }
        // the controller clears the session the moment a level ends, so the
        // result has to be read off it before that happens
        if (before != null && before.isOver()) {
            lastGameWon = before.isWon();
        }
    }

    /**
     * The controller cleared the session, which means it already applied the
     * rewards and reported the outcome; all that is left is to say so.
     */
    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        loop.stop();
        showOutcome();
    }

    private void refreshHud(GameSession session) {
        sunLabel.setText(String.valueOf(session.getSunAmount()));
        plantFoodLabel.setText(session.getPlantFood() + " / " + GameSession.MAX_PLANT_FOOD);
        var boss = session.getZomboss();
        boolean bossFight = boss != null;
        // a boss level has no waves to track, so its health takes that place
        waveBar.setVisible(!bossFight);
        waveBar.setManaged(!bossFight);
        bossBar.setVisible(bossFight);
        bossBar.setManaged(bossFight);
        if (bossFight) {
            waveLabel.setText(boss.isStunned() ? "Zomboss is reeling!" : "Zomboss");
            bossBar.setBoss(boss);
        } else {
            int total = Math.max(1, session.getLevel().getTotalWaves());
            waveLabel.setText("Wave " + session.getCurrentWave() + " of " + total);
            waveBar.setTotalWaves(total);
            waveBar.setProgress(session.getWaveProgress());
        }
        objectiveLabel.setText(objectiveStatus(session));
        objectiveLabel.setVisible(!objectiveLabel.getText().isEmpty());
        rebuildSeedBar(session);
    }

    /**
     * The live objective line for a special level: how many zombies are left to
     * kill and how long there is to do it, how many plants may still be lost,
     * or the reminder that the waves are waiting on the player.
     */
    private String objectiveStatus(GameSession session) {
        String minigame = session.minigameObjective();
        if (minigame != null) {
            return swapFrom == null
                    ? minigame + "   ·   click two neighbouring plants to swap them"
                    : minigame + "   ·   now click the plant to swap it with";
        }
        var special = session.getSpecial();
        var rules = special.getRules();
        if (rules == null) {
            return "";
        }
        return switch (rules.getType()) {
            case TIMED_WAR -> "Kills " + special.getKills() + " / " + rules.getTargetKills()
                    + "   ·   " + secondsLeft(session, rules.getTimerSeconds()) + "s left";
            case LOVE_YOUR_PLANTS -> "Plants lost " + special.getPlantLosses()
                    + " / " + rules.getMaxPlantLosses();
            case DEAD_LINE -> "Hold the line at column " + rules.getDeadlineColumn();
            case SAVE_OUR_SEEDS -> "Keep every marked plant alive";
            case PLANT_WHAT_YOU_GET -> special.isWavesReleased()
                    ? "" : "Plant freely, then start the waves";
            default -> "";
        };
    }

    private static long secondsLeft(GameSession session, int limitSeconds) {
        return Math.max(0, limitSeconds - Math.round(session.getElapsedSeconds()));
    }

    /**
     * One card per chosen plant, or the belt's contents on a conveyor level.
     */
    private void rebuildSeedBar(GameSession session) {
        if (!zombieCards(session).isEmpty()) {
            rebuildZombieBar(session);
            return;
        }
        List<String> available = cardsToShow(session);
        if (seedBar.getChildren().size() == available.size()) {
            updateSeedBar(session, available);
            return;
        }
        seedBar.getChildren().clear();
        for (String type : available) {
            seedBar.getChildren().add(seedCard(session, type));
        }
    }

    /**
     * What belongs in the card bar right now: the belt on a conveyor level, the
     * plants shaken out of vases in Vasebreaker, otherwise the chosen line-up.
     */
    private static List<String> cardsToShow(GameSession session) {
        if (session.isConveyorLevel()) {
            return session.conveyorBelt();
        }
        if (session.getMinigame() != null && session.getMinigame().freePlantMode()) {
            return session.getMinigame().handContents();
        }
        return session.getSelectedPlants();
    }

    /**
     * I, Zombie deals the player zombies rather than seed packets.
     */
    private static Map<String, Integer> zombieCards(GameSession session) {
        return session.getMinigame() == null
                ? Map.of()
                : session.getMinigame().cardsInsteadOfPlants();
    }

    private void rebuildZombieBar(GameSession session) {
        Map<String, Integer> cards = zombieCards(session);
        List<String> types = new java.util.ArrayList<>(cards.keySet());
        java.util.Collections.sort(types);
        if (seedBar.getChildren().size() != types.size()) {
            seedBar.getChildren().clear();
            for (String type : types) {
                seedBar.getChildren().add(zombieCard(session, type, cards.get(type)));
            }
            return;
        }
        for (int i = 0; i < types.size(); i++) {
            if (seedBar.getChildren().get(i) instanceof ActorCard card) {
                card.affordable(session.getSunAmount() >= cards.get(types.get(i)));
                card.selected(types.get(i).equals(armedZombie));
            }
        }
    }

    private ActorCard zombieCard(GameSession session, String type, int price) {
        return new ActorCard(Assets.zombie(type), type)
                .cost(price)
                .affordable(session.getSunAmount() >= price)
                .selected(type.equals(armedZombie))
                .onClick(() -> {
                    armedZombie = type.equals(armedZombie) ? null : type;
                    armedPlant = null;
                    shovelArmed = false;
                    plantFoodArmed = false;
                });
    }

    private void updateSeedBar(GameSession session, List<String> available) {
        for (int i = 0; i < available.size(); i++) {
            String type = available.get(i);
            if (!(seedBar.getChildren().get(i) instanceof ActorCard card)) {
                continue;
            }
            boolean free = session.getMinigame() != null && session.getMinigame().freePlantMode();
            int cost = GameCatalog.get().plant(type).getSunCost();
            double recharge = Math.max(0.001, GameCatalog.get().plant(type).getRechargeSeconds());
            card.cooldown(free ? 0 : session.cooldownRemaining(type) / recharge);
            card.affordable(free || session.getSunAmount() >= cost);
            card.selected(type.equals(armedPlant));
        }
    }

    private ActorCard seedCard(GameSession session, String type) {
        boolean free = session.getMinigame() != null && session.getMinigame().freePlantMode();
        int cost = free ? 0 : GameCatalog.get().plant(type).getSunCost();
        ActorCard card = new ActorCard(Assets.plant(type), type);
        if (!free) {
            card.cost(cost);
        }
        return card
                .affordable(free || session.getSunAmount() >= cost)
                .selected(type.equals(armedPlant))
                .onClick(() -> {
                    armedPlant = type.equals(armedPlant) ? null : type;
                    shovelArmed = false;
                    plantFoodArmed = false;
                });
    }

    /**
     * Gives every new burst and every new shot its sound. Both lists are the
     * engine's own, so counting what appeared since the last frame is enough to
     * fire each effect exactly once.
     */
    private void playSoundsFor(GameSession session) {
        for (Burst burst : session.getBursts()) {
            if (burst.progress() > 0 || playedBursts.contains(burst)) {
                continue;
            }
            playedBursts.add(burst);
            switch (burst.getKind()) {
                case EXPLOSION -> Sfx.play(Sfx.Sound.EXPLODE);
                case ZOMBIE_DOWN -> Sfx.play(Sfx.Sound.ZOMBIE);
                case MOWER -> Sfx.play(Sfx.Sound.MOWER);
                case PLANT_FOOD -> Sfx.play(Sfx.Sound.SUN);
                default -> { }
            }
        }
        playedBursts.retainAll(session.getBursts());

        int waveNow = session.getCurrentWave();
        if (waveNow > lastWave) {
            Sfx.play(Sfx.Sound.WAVE);
        }
        lastWave = waveNow;

        int shotsNow = session.getShots().size();
        if (shotsNow > lastShotCount) {
            Sfx.play(Sfx.Sound.SHOOT);
        }
        lastShotCount = shotsNow;
    }

    // ===== interaction =====

    /**
     * True while the cursor is carrying something that has to land on a tile.
     */
    private boolean isPlacing() {
        return armedPlant != null || shovelArmed || plantFoodArmed || armedZombie != null;
    }

    /**
     * Follows the cursor across the lawn so the target tile can be marked and
     * the cursor itself can show what is about to be placed.
     */
    private void onLawnHover(double x, double y) {
        GameSession session = session();
        // the document asks for suns to be picked up by passing over them,
        // so hovering collects and clicking is only a fallback
        if (session != null && !paused) {
            collectSunAt(session, x, y);
        }
        int[] tile = lawn.tileAt(x, y);
        if (tile == null || !isPlacing()) {
            lawn.setHoveredTile(-1, -1, false);
        } else {
            lawn.setHoveredTile(tile[0], tile[1], true);
        }
        applyCursor();
    }

    /**
     * The document asks the cursor to say what it is holding: the plant's own
     * art while planting, a shovel while digging, a leaf while feeding.
     */
    private void applyCursor() {
        if (shovelArmed) {
            lawn.setCursor(cursorFrom(Assets.ui("shovel"), Cursor.HAND));
        } else if (plantFoodArmed) {
            lawn.setCursor(cursorFrom(Assets.ui("plant-food"), Cursor.HAND));
        } else if (armedZombie != null) {
            lawn.setCursor(cursorFrom(Assets.zombie(armedZombie), Cursor.HAND));
        } else if (armedPlant != null) {
            lawn.setCursor(cursorFrom(Assets.plant(armedPlant), Cursor.HAND));
        } else {
            lawn.setCursor(Cursor.DEFAULT);
        }
    }

    private static Cursor cursorFrom(Image art, Cursor fallback) {
        if (art == null) {
            return fallback;
        }
        return new ImageCursor(art, art.getWidth() / 2, art.getHeight() / 2);
    }

    /**
     * A click on the lawn means whatever the cursor is currently carrying:
     * collect a sun, plant, dig or feed.
     */
    private void onLawnClick(double x, double y) {
        GameSession session = session();
        if (session == null || paused) {
            return;
        }
        int[] tile = lawn.tileAt(x, y);
        if (collectSunAt(session, x, y)) {
            return;
        }
        if (tile == null) {
            return;
        }
        if (collectPlantFoodAt(session, tile)) {
            return;
        }
        String location = " -l (" + tile[0] + ", " + tile[1] + ")";
        if (session.minigameObjective() != null) {
            swapAt(tile);
            return;
        }
        if (breakVaseAt(session, tile)) {
            return;
        }
        if (armedZombie != null) {
            ui.submitQuietly("place zombie -t " + armedZombie + location);
            armedZombie = null;
            lawn.setHoveredTile(-1, -1, false);
            applyCursor();
            return;
        }
        if (shovelArmed) {
            ui.submitQuietly("pluck plant" + location);
            shovelArmed = false;
        } else if (plantFoodArmed) {
            ui.submitQuietly("feed plant" + location);
            plantFoodArmed = false;
        } else if (armedPlant != null) {
            ui.submitQuietly("plant plant -t " + armedPlant + location);
            Sfx.play(Sfx.Sound.PLANT);
            armedPlant = null;
        }
        // whatever the cursor was holding has been used up, so stop marking
        // the tile even if the mouse never moves again
        lawn.setHoveredTile(-1, -1, false);
        applyCursor();
    }

    /**
     * The Beghouled interaction: the first click picks a plant up, the second
     * one names the neighbour to trade it with. Clicking the same tile twice
     * puts it back down.
     */
    private void swapAt(int[] tile) {
        if (swapFrom == null) {
            swapFrom = tile;
            lawn.setHoveredTile(tile[0], tile[1], true);
            return;
        }
        int[] from = swapFrom;
        swapFrom = null;
        lawn.setHoveredTile(-1, -1, false);
        if (from[0] == tile[0] && from[1] == tile[1]) {
            return;
        }
        ui.submitQuietly("swap plant -l (" + from[0] + ", " + from[1] + ")"
                + " -l (" + tile[0] + ", " + tile[1] + ")");
    }

    /**
     * Smashes a vase when the click lands on one, which is the whole of the
     * Vasebreaker interaction.
     */
    private boolean breakVaseAt(GameSession session, int[] tile) {
        String here = LawnView.props(session).stream()
                .filter(prop -> (int) prop.col() == tile[0] && (int) prop.row() == tile[1])
                .map(MinigameProp::kind)
                .filter(kind -> !"nut".equals(kind))
                .findFirst().orElse(null);
        if (here == null) {
            return false;
        }
        String location = " -l (" + tile[0] + ", " + tile[1] + ")";
        // a packet lying where a vase used to be is picked up, not smashed again
        ui.submitQuietly(("packet".equals(here) ? "take packet" : "break vase") + location);
        return true;
    }

    /**
     * Picks up the plant food a glowing zombie dropped, when the click is on
     * the tile it fell on.
     */
    private boolean collectPlantFoodAt(GameSession session, int[] tile) {
        for (int[] dropped : session.getDroppedPlantFood()) {
            if (dropped[0] == tile[0] && dropped[1] == tile[1]) {
                ui.submitQuietly("collect plant food -l (" + tile[0] + ", " + tile[1] + ")");
                Sfx.play(Sfx.Sound.SUN);
                return true;
            }
        }
        return false;
    }

    /**
     * Picks up a sun when the click lands close enough to one of them.
     */
    private boolean collectSunAt(GameSession session, double x, double y) {
        double reach = lawn.tileHeight() * 0.4;
        for (Sun sun : session.groundSuns()) {
            var centre = lawn.sunCentre(sun);
            if (centre.distance(x, y) <= reach) {
                ui.submitQuietly("collect sun -l ("
                        + (sun.getCol() + 1) + ", " + (sun.getRow() + 1) + ")");
                Sfx.play(Sfx.Sound.SUN);
                return true;
            }
        }
        return false;
    }

    /**
     * Arms a seed packet and marks a tile, without a mouse. Only the snapshot
     * tool uses this, to capture what placement looks like.
     */
    public void showPlacementPreview(String plantType, int col, int row) {
        armedPlant = plantType;
        shovelArmed = false;
        plantFoodArmed = false;
        lawn.setHoveredTile(col, row, true);
        applyCursor();
    }

    // ===== menus =====

    /**
     * Escape pauses, as well as the button in the corner.
     */
    private void layoutKeys(BorderPane layout) {
        layout.setFocusTraversable(true);
        layout.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && !paused) {
                openPauseMenu();
            }
        });
        layout.sceneProperty().addListener((observable, was, now) -> {
            if (now != null) {
                layout.requestFocus();
            }
        });
    }

    /**
     * Halts the clock without opening the pause menu, so a short-lived effect
     * can be captured mid-flight. Only the snapshot tool uses this.
     */
    public void freezeForSnapshot() {
        paused = true;
    }

    /**
     * Opens the pause menu without a click. Only the snapshot tool uses this.
     */
    public void openPauseMenuForSnapshot() {
        openPauseMenu();
    }

    @Override
    public void dispose() {
        if (loop != null) {
            loop.stop();
        }
    }

    private void openPauseMenu() {
        paused = true;
        Button resume = new Button("Resume");
        resume.getStyleClass().add("primary-button");
        resume.setOnAction(event -> {
            ui.closeModal();
            paused = false;
        });
        Button quit = new Button("Save and exit");
        quit.getStyleClass().add("ghost-button");
        quit.setOnAction(event -> {
            ui.closeModal();
            loop.stop();
            // the level itself is put away, not just the account
            ui.submitQuietly("save game");
            ui.app().save();
            finish();
        });

        Button giveUp = new Button("Give up");
        giveUp.getStyleClass().add("ghost-button");
        giveUp.setOnAction(event -> {
            ui.closeModal();
            loop.stop();
            ui.submitQuietly("forfeit level");
            finish();
        });

        Button restart = new Button("Restart");
        restart.getStyleClass().add("ghost-button");
        restart.setOnAction(event -> {
            ui.closeModal();
            loop.stop();
            ui.submitQuietly("forfeit level");
            ui.refresh();
        });

        HBox buttons = new HBox(12, resume, restart, quit, giveUp);
        buttons.setAlignment(Pos.CENTER);
        Label note = new Label("Nothing moves while this is open.");
        note.getStyleClass().add("modal-body");
        VBox body = new VBox(14, note, soundControls());
        body.setAlignment(Pos.CENTER);
        ui.showModal(Dialogs.panel("Game paused", body, buttons));
    }

    /**
     * The music and sound sliders the document shows on the pause menu; there
     * is no soundtrack, so the one slider drives every effect.
     */
    private VBox soundControls() {
        CheckBox musicOn = new CheckBox("Music");
        musicOn.setSelected(Music.isEnabled());
        Slider musicLevel = new Slider(0, 1, Music.getVolume());
        musicLevel.setPrefWidth(240);
        musicLevel.disableProperty().bind(musicOn.selectedProperty().not());
        musicOn.selectedProperty().addListener((observable, was, now) -> Music.setEnabled(now));
        musicLevel.valueProperty().addListener(
                (observable, was, now) -> Music.setVolume(now.doubleValue()));

        CheckBox soundOn = new CheckBox("Sound FX");
        soundOn.setSelected(Sfx.isEnabled());
        Slider soundLevel = new Slider(0, 1, Sfx.getVolume());
        soundLevel.setPrefWidth(240);
        soundLevel.disableProperty().bind(soundOn.selectedProperty().not());
        soundOn.selectedProperty().addListener((observable, was, now) -> Sfx.setEnabled(now));
        soundLevel.valueProperty().addListener((observable, was, now) -> {
            Sfx.setVolume(now.doubleValue());
            Sfx.play(Sfx.Sound.SUN);
        });

        VBox box = new VBox(6, musicOn, musicLevel, soundOn, soundLevel);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /**
     * The win/lose report, with a retry when the level was lost.
     */
    private void showOutcome() {
        Music.stop();
        Sfx.play(lastGameWon ? Sfx.Sound.WIN : Sfx.Sound.LOSE);
        Button back = new Button(menu == MenuType.GAME ? "Back to the map" : "Back to the menu");
        back.getStyleClass().add("primary-button");
        back.setOnAction(event -> {
            ui.closeModal();
            // a finished adventure level goes back to the chapter map, where
            // the next one is picked and the progress just made shows
            if (menu == MenuType.GAME) {
                ui.exitToAdventure();
            } else {
                ui.refresh();
            }
        });
        Button retry = new Button("Try again");
        retry.getStyleClass().add("ghost-button");
        retry.setOnAction(event -> {
            ui.closeModal();
            // actually play it again rather than dropping back at the picker
            ui.submit("replay level");
        });

        HBox buttons = new HBox(12, back, retry);
        buttons.setAlignment(Pos.CENTER);

        VBox lines = new VBox(4);
        for (String message : closingReport) {
            Label label = new Label(message);
            label.getStyleClass().add("modal-body");
            label.setWrapText(true);
            lines.getChildren().add(label);
        }
        if (lines.getChildren().isEmpty()) {
            Label label = new Label("Your rewards have been added to your account.");
            label.getStyleClass().add("modal-body");
            lines.getChildren().add(label);
        }
        Label spoken = new Label(Dialogue.closing(lastGameWon, chapterOfLevel()));
        spoken.getStyleClass().add("dialogue-line");
        spoken.setWrapText(true);
        spoken.setMaxWidth(420);
        lines.getChildren().add(spoken);
        lines.setAlignment(Pos.CENTER);

        ui.showModal(Dialogs.panel(menu == MenuType.SCORE_GAME ? "Run complete" : "Level over",
                lines, buttons));
    }
}
