package ir.sharif.pvz.devtools;

import ir.sharif.pvz.controller.GameApp;
import ir.sharif.pvz.controller.MenuType;
import ir.sharif.pvz.model.game.Chapter;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.Levels;
import ir.sharif.pvz.view.fx.FxView;
import ir.sharif.pvz.view.fx.GameUi;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;
import javax.imageio.ImageIO;

/**
 * A development aid, not a test: renders each screen of the graphical view and
 * writes it to a PNG so the layout can be checked without a human at the
 * keyboard. Run it with {@code ./gradlew snapshots}.
 *
 * <p>Every step runs as its own pulse on the JavaFX thread, never blocking it,
 * so the animation timers get a chance to paint between snapshots.
 */
public final class ScreenSnapshots extends Application {

    private static final Path OUT =
            Path.of(System.getProperty("pvz.snapshot.dir", "build/snapshots"));
    private static final Duration SETTLE = Duration.millis(220);

    private final Deque<Runnable> steps = new ArrayDeque<>();

    private GameApp app;
    private GameUi ui;
    private Scene scene;

    @Override
    public void start(Stage stage) {
        FxView view = new FxView();
        app = new GameApp(view);
        ui = new GameUi(app, view);

        scene = new Scene(ui.root(), GameUi.WIDTH, GameUi.HEIGHT);
        var css = GameUi.class.getResource("/style/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        stage.show();

        OUT.toFile().mkdirs();
        planSteps();
        runNext();
    }

    /**
     * Enters a menu, photographs it under the given name and comes back out.
     */
    private void planMenuShot(MenuType menu, String name) {
        steps.add(() -> {
            app.submit("menu enter " + menu.id());
            ui.refresh();
        });
        steps.add(() -> shoot(name));
        steps.add(() -> {
            app.submit("menu exit");
            ui.refresh();
        });
    }

    /**
     * Sets a scene up and photographs it.
     */
    private void planShot(Runnable setUp, String name) {
        steps.add(setUp);
        steps.add(() -> shoot(name));
    }

    private void planSteps() {
        steps.add(() -> shoot("00-signup"));
        steps.add(this::signIn);
        steps.add(() -> shoot("01-main-menu"));

        for (MenuType menu : new MenuType[] {MenuType.PROFILE, MenuType.SETTINGS, MenuType.NEWS,
                MenuType.COLLECTION, MenuType.GREENHOUSE, MenuType.SHOP,
                MenuType.LEADERBOARD, MenuType.TRAVEL_LOG}) {
            planMenuShot(menu, menu.id());
        }
        // the news page a second time, to show the read marks having cleared
        planMenuShot(MenuType.NEWS, "news-reread");
        planAlmanacShot();

        steps.add(this::openAdventureMap);
        steps.add(() -> shoot("09-adventure-map"));
        steps.add(this::openPicker);
        steps.add(() -> shoot("10-plant-picker"));
        steps.add(this::startBattle);
        steps.add(() -> shoot("11-battle"));
        steps.add(this::runToSandstorm);
        steps.add(() -> shoot("11-sandstorm"));
        steps.add(this::armPlantOverTile);
        steps.add(() -> shoot("12-placing"));
        steps.add(this::startDeadLineLevel);
        steps.add(() -> shoot("13-special-deadline"));
        steps.add(this::showDarkAgesGraves);
        steps.add(() -> shoot("14-dark-ages-graves"));
        steps.add(this::showIceAndArmour);
        steps.add(() -> shoot("15-ice-and-armour"));
        planChapterShots();
        steps.add(() -> startMinigame("vasebreaker"));
        steps.add(() -> shoot("16-vasebreaker"));
        steps.add(() -> startMinigame("i-zombie"));
        steps.add(() -> shoot("17-i-zombie"));
        steps.add(() -> startMinigame("beghouled"));
        steps.add(() -> shoot("17-beghouled"));
        planShot(this::showExplosion, "18-explosion");
        planShot(this::showPlantFood, "18-plant-food");
        steps.add(this::showFlyingParts);
        steps.add(() -> shoot("18-zombie-parts"));
        steps.add(this::showPauseMenu);
        steps.add(() -> shoot("19-pause-menu"));
        planBossShots();
        steps.add(this::showVersusLobby);
        steps.add(() -> shoot("20-versus-lobby"));
        steps.add(this::showVersusBattle);
        steps.add(() -> shoot("21-versus-battle"));
        steps.add(this::showCouchPlay);
        steps.add(() -> shoot("22-couch-play"));
        steps.add(Platform::exit);
    }

    /**
     * One battle per chapter, so each chapter's own lawn — the sea, the ice,
     * the graves — can be looked at rather than assumed.
     */
    private void planChapterShots() {
        for (Chapter chapter : Chapter.values()) {
            steps.add(() -> {
                startLevelAt(chapter, 1, "sunflower", "lily-pad");
                app.submit("cheat remove-cooldown");
                padTheFirstSeaTile();
                app.submit("advance time -t 40 ticks");
                ui.refresh();
            });
            steps.add(() -> shoot("15-chapter-" + chapter.id()));
        }
    }

    /**
     * Puts a pad on the leftmost sea tile of row 3, so a chapter with water
     * shows a covered tile next to the bare ones.
     */
    private void padTheFirstSeaTile() {
        GameSession session = ((ir.sharif.pvz.controller.GameMenuController)
                app.currentController()).getSession();
        for (int col = 1; col <= GameSession.COLS; col++) {
            if (session.terrainAt(col, 3) == ir.sharif.pvz.model.game.TileTerrain.WATER) {
                app.submit("plant plant -t lily-pad -l (" + col + ", 3)");
                return;
            }
        }
    }

    /**
     * Picks a seed packet up and puts the cursor over a tile, so the snapshot
     * shows the placement marker the document asks for.
     */
    private void armPlantOverTile() {
        if (ui.current() instanceof ir.sharif.pvz.view.fx.screen.BattleScreen battle) {
            battle.showPlacementPreview("peashooter", 6, 2);
        }
    }

    /**
     * Jumps to Big Wave Beach day 3, the dead line level, so the snapshot shows
     * the line the zombies must not cross.
     */
    private void startDeadLineLevel() {
        finishCurrentLevel();
        app.getContext().getCurrentUser().setLevelsPassed(10);
        ui.refresh();
        app.submit("menu enter game");
        app.submit("add plant -t peashooter");
        app.submit("start game");
        app.submit("cheat add -n 500 suns");
        app.submit("plant plant -t peashooter -l (5, 2)");
        app.submit("cheat spawn-zombie -t normal -l (7, 3)");
        // part-way through the flight: the board ticks ten times a second, so
        // this is the shot roughly half of the way across
        app.submit("advance time -t 7 ticks");
        ui.refresh();
    }

    /**
     * Opens the Dark Ages finale so the dragon boss and its three-part health
     * bar are on screen, with one section already knocked out.
     */
    private void showZomboss() {
        startLevelAt(Chapter.DARK_AGES, 5, "peashooter");
        var session = ((ir.sharif.pvz.controller.GameMenuController) app.currentController())
                .getSession();
        // put the belt's first offering down in the rows the dragon covers, then
        // stop the clock: the boss is at full strength and both its health
        // sections and the belt are on screen
        for (String plant : List.copyOf(session.conveyorBelt())) {
            for (int row = 1; row <= GameSession.ROWS; row++) {
                app.submit("plant plant -t " + plant + " -l (2, " + row + ")");
            }
        }
        // run on until the boss throws, then stop the clock part-way through
        // the flight so the shot is caught in the air rather than landed
        for (int i = 0; i < GameSession.TICKS_PER_SECOND * 120
                && session.getBossShots().isEmpty(); i++) {
            app.submit("advance time -t 1 ticks");
        }
        // part-way through the flight: the board ticks ten times a second, so
        // this is the shot roughly half of the way across
        app.submit("advance time -t 7 ticks");
        ui.refresh();
        if (ui.current() instanceof ir.sharif.pvz.view.fx.screen.BattleScreen battle) {
            battle.freezeForSnapshot();
        }
    }

    /**
     * The two-players-on-one-device board.
     */
    private void showCouchPlay() {
        ui.show(new ir.sharif.pvz.view.fx.screen.CouchPlayScreen(ui));
    }

    /**
     * The lobby where a two-player game is arranged. It is shown offline here,
     * which is why the online list comes back empty.
     */
    private void showVersusLobby() {
        finishCurrentLevel();
        returnToMainMenu();
        ui.show(new ir.sharif.pvz.view.fx.screen.VersusLobbyScreen(ui));
    }

    /**
     * The versus board, fed one handmade snapshot so the layout can be checked
     * without a server and a second player.
     */
    private void showVersusBattle() {
        var screen = new ir.sharif.pvz.view.fx.screen.VersusScreen(ui, "plants", "vahid");
        ui.show(screen);
        screen.showBoardForSnapshot(ir.sharif.pvz.devtools.SampleBoard.build());
    }

    /**
     * Sets a cherry bomb off in a crowd, to capture the blast and the shake.
     */
    private void showExplosion() {
        startLevelAt(0, "cherry-bomb");
        var session = ((ir.sharif.pvz.controller.GameMenuController) app.currentController())
                .getSession();
        for (int row = 2; row <= 4; row++) {
            session.cheats().spawnZombie("normal", 6, row);
        }
        // graves are laid at random, so take the first tile that will have it
        for (int col = 5; col <= 7 && session.plantAtTile(col, 3) == null; col++) {
            app.submit("plant plant -t cherry-bomb -l (" + col + ", 3)");
        }
        // let the fuse burn down, but stop while the blast is still on screen
        for (int i = 0; i < 30 && session.getBursts().isEmpty(); i++) {
            app.submit("advance time -t 1 ticks");
        }
        app.submit("advance time -t 2 ticks");
        ui.refresh();
        // the blast lasts under a second, so stop the clock before the
        // screenshot settles or there would be nothing left to see
        if (ui.current() instanceof ir.sharif.pvz.view.fx.screen.BattleScreen battle) {
            battle.freezeForSnapshot();
        }
    }

    /**
     * Opens the pause menu over a running level.
     */
    /**
     * Feeds a sunflower and a peashooter and catches them mid-show.
     */
    private void showPlantFood() {
        int[] fedTiles = new int[2];
        startLevelAt(0, "sunflower");
        var session = ((ir.sharif.pvz.controller.GameMenuController) app.currentController())
                .getSession();
        app.submit("cheat add -n 900 suns");
        // graves are laid at random, so take tiles that are actually free
        int fed = 0;
        for (int row = 1; row <= 5 && fed < 2; row++) {
            if (session.plantAtTile(3, row) == null
                    && !app.currentController().toString().isEmpty()) {
                app.submit("plant plant -t sunflower -l (3, " + row + ")");
                if (session.plantAtTile(3, row) != null) {
                    fedTiles[fed++] = row;
                }
            }
        }
        session.cheats().spawnZombie("normal", 8, 3);
        for (int i = 0; i < 3; i++) {
            app.submit("cheat add-plant-food");
        }
        for (int row : fedTiles) {
            if (row > 0) {
                app.submit("feed plant -l (3, " + row + ")");
            }
        }
        // far enough in for the sun to have carried clear of the plant
        app.submit("advance time -t 7 ticks");
        ui.refresh();
    }

    /**
     * Knocks a few armoured zombies down and catches the heads, arms and
     * armour while they are still in the air.
     */
    private void showFlyingParts() {
        startLevelAt(0, "peashooter");
        var session = ((ir.sharif.pvz.controller.GameMenuController) app.currentController())
                .getSession();
        session.cheats().spawnZombie("conehead", 6, 2);
        session.cheats().spawnZombie("buckethead", 7, 3);
        session.cheats().spawnZombie("normal", 5, 4);
        session.cheats().spawnZombie("conehead", 8, 5);
        app.submit("release the nuke");
        // a couple of ticks in, the pieces are up off the ground
        app.submit("advance time -t 3 ticks");
        ui.refresh();
    }

    private void showPauseMenu() {
        if (ui.current() instanceof ir.sharif.pvz.view.fx.screen.BattleScreen battle) {
            battle.openPauseMenuForSnapshot();
        }
    }

    /**
     * Starts a Dark Ages night so the three kinds of headstone are on screen.
     */
    private void showDarkAgesGraves() {
        startLevelAt(Chapter.DARK_AGES, 1, "sunflower");
        var session = ((ir.sharif.pvz.controller.GameMenuController) app.currentController())
                .getSession();
        session.cheats().spawnZombie("tombraiser", 8, 3);
        app.submit("advance time -t 200 ticks");
        ui.refresh();
    }

    /** The boss at full strength, mid-attack, and on its way down. */
    private void planBossShots() {
        steps.add(this::showZomboss);
        steps.add(() -> shoot("20-zomboss"));
        steps.add(this::showBossSweep);
        steps.add(() -> shoot("20-zomboss-sweep"));
        steps.add(this::showBossFalling);
        steps.add(() -> shoot("20-zomboss-beaten"));
    }

    /**
     * The boss's wide move part-way across the lawn. It is picked at random
     * among its three, so this runs on until that is the one it chose.
     */
    private void showBossSweep() {
        GameSession session = bossLevel();
        for (int i = 0; i < GameSession.TICKS_PER_SECOND * 300
                && session.getBossSweep() == null; i++) {
            app.submit("advance time -t 1 ticks");
        }
        app.submit("advance time -t 8 ticks");
        freeze();
    }

    /** The boss on its way down, once the last part of its armour is off. */
    private void showBossFalling() {
        GameSession session = bossLevel();
        session.cheats().releaseTheNuke();
        app.submit("advance time -t 8 ticks");
        freeze();
    }

    /** Starts a boss level and hands back its session. */
    private GameSession bossLevel() {
        startLevelAt(Chapter.DARK_AGES, 5, "peashooter");
        return ((ir.sharif.pvz.controller.GameMenuController) app.currentController())
                .getSession();
    }

    private void freeze() {
        ui.refresh();
        if (ui.current() instanceof ir.sharif.pvz.view.fx.screen.BattleScreen battle) {
            battle.freezeForSnapshot();
        }
    }

    /**
     * Drops the player straight into one adventure level, whatever they were
     * doing before.
     */
    private void startLevelAt(Chapter chapter, int day, String... plants) {
        startLevelAt(Levels.indexOf(chapter, day), plants);
    }

    private void startLevelAt(int levelsPassed, String... plants) {
        finishCurrentLevel();
        returnToMainMenu();
        app.getContext().getCurrentUser().setLevelsPassed(levelsPassed);
        ui.refresh();
        app.submit("menu enter game");
        for (String plant : plants) {
            app.submit("add plant -t " + plant);
        }
        app.submit("start game");
        app.submit("cheat add -n 2000 suns");
        ui.refresh();
    }

    /**
     * Sets up a lawn that shows the frostbite effects and the armour swap: a
     * plant at each iciness step, a frozen zombie, and a conehead stripped of
     * its cone.
     */
    private void showIceAndArmour() {
        startLevelAt(Chapter.FROSTBITE_CAVES, 1, "sunflower");
        for (int col = 2; col <= 4; col++) {
            app.submit("plant plant -t sunflower -l (" + col + ", " + col + ")");
        }
        var session = ((ir.sharif.pvz.controller.GameMenuController) app.currentController())
                .getSession();
        session.cheats().spawnZombie("hunter", 9, 1);
        session.cheats().spawnZombie("conehead", 7, 5);
        app.submit("advance time -t 260 ticks");
        ui.refresh();
    }

    /**
     * Drops into a minigame's first stage through the quest menu, the way a
     * player reaches it.
     */
    private void startMinigame(String name) {
        finishCurrentLevel();
        returnToMainMenu();
        app.submit("menu enter travel-log");
        app.submit("menu enter minigame");
        app.submit("play -m " + name + " -s 1");
        app.submit("advance time -t 10 ticks");
        ui.refresh();
    }

    /**
     * Walks back out of however many menus deep we are, since a minigame sits
     * two levels down from the main menu.
     */
    private void returnToMainMenu() {
        for (int i = 0; i < 5 && app.getContext().getCurrentMenu() != MenuType.MAIN; i++) {
            app.submit("menu exit");
        }
    }

    /**
     * Nukes and runs the clock until the level in progress is over, so the next
     * one can be started from a clean slate.
     */
    private void finishCurrentLevel() {
        var controller = (ir.sharif.pvz.controller.GameMenuController) app.currentController();
        // giving up ends any level, minigames included, where nuking only ends
        // the ones that finish by clearing the lawn
        for (int i = 0; i < 20 && controller.getSession() != null; i++) {
            app.submit("forfeit level");
            // only tick while a level is still running: once it is over the
            // selection menu refuses this, and the refusal shows up as an
            // error toast in whatever gets photographed next
            if (controller.getSession() != null) {
                app.submit("advance time -t 1 ticks");
            }
        }
    }

    /**
     * Creates a throwaway account if there is not one already, then signs in.
     */
    private void signIn() {
        String user = "devsnap";
        String password = "Aa1!aaaa";
        // the game restores the last session on start, so the account may
        // already be signed in; anything submitted here would be refused and
        // the refusal would show as an error toast in the first screenshot
        if (app.getContext().getCurrentUser() != null) {
            app.getContext().getCurrentUser().setLevelsPassed(0);
            app.getContext().getCurrentUser().setDebugMode(true);
            ui.refresh();
            return;
        }
        // otherwise sign in, and only make the account when there is none
        app.submit("menu enter login");
        app.submit("login -u " + user + " -p " + password);
        if (app.getContext().getCurrentUser() == null) {
            app.submit("menu exit");
            app.submit("register -u " + user + " -p " + password + " " + password
                    + " -n Dev -e dev@example.com -g female");
            app.submit("pick question -q 1 -a green -c green");
            app.submit("menu enter login");
            app.submit("login -u " + user + " -p " + password);
        }
        // the account is reused between runs, so wind its progress back to make
        // every snapshot show the same level it did last time
        app.getContext().getCurrentUser().setLevelsPassed(0);
        // the scenes below are staged with cheat commands, and the debug
        // setting decides whether those are answered at all
        app.getContext().getCurrentUser().setDebugMode(true);
        ui.refresh();
    }

    /**
     * Winds an Ancient Egypt level on to the middle of a sandstorm.
     */
    private void runToSandstorm() {
        var session = ((ir.sharif.pvz.controller.GameMenuController) app.currentController())
                .getSession();
        double period = ir.sharif.pvz.model.game.Sandstorm.PERIOD_SECONDS;
        double wanted = ir.sharif.pvz.model.game.Sandstorm.CROSSING_SECONDS / 2;
        // wind on to the middle of the next crossing, wherever the clock is now
        double now = session.getElapsedSeconds();
        double target = Math.ceil((now - wanted) / period) * period + wanted;
        int ticks = (int) Math.round((target - now)
                * ir.sharif.pvz.model.game.GameSession.TICKS_PER_SECOND);
        app.submit("advance time -t " + Math.max(1, ticks) + " ticks");
        ui.refresh();
    }

    /**
     * The collection opened on one plant's almanac page and closed again.
     */
    private void planAlmanacShot() {
        steps.add(this::openPlantDetail);
        steps.add(() -> shoot("collection-plant"));
        steps.add(() -> {
            app.submit("menu exit");
            ui.refresh();
        });
    }

    /**
     * The collection with one plant picked, so its almanac page shows.
     */
    private void openPlantDetail() {
        app.submit("menu enter collection");
        ui.show(new ir.sharif.pvz.view.fx.screen.CollectionScreen(ui, "kiwibeast"));
    }

    /**
     * The chapter map, which is where a level is chosen and where finishing
     * one lands again.
     */
    private void openAdventureMap() {
        ui.show(new ir.sharif.pvz.view.fx.screen.AdventureScreen(ui));
    }

    private void openPicker() {
        app.submit("menu enter game");
        app.submit("add plant -t sunflower");
        app.submit("add plant -t peashooter");
        app.submit("add plant -t wall-nut");
        ui.refresh();
    }

    private void startBattle() {
        app.submit("start game");
        app.submit("cheat add -n 500 suns");
        app.submit("plant plant -t sunflower -l (2, 2)");
        app.submit("plant plant -t peashooter -l (3, 3)");
        app.submit("plant plant -t wall-nut -l (5, 4)");
        app.submit("cheat spawn-zombie -t conehead -l (8, 3)");
        app.submit("cheat spawn-zombie -t normal -l (7, 2)");
        app.submit("cheat spawn-zombie -t buckethead -l (9, 5)");
        // run past the ten second mark so the first wave is under way and the
        // wave bar has something to show
        app.submit("advance time -t 130 ticks");
        ui.refresh();
    }

    /**
     * Runs the next step after a pause, so layout and the battle screen's
     * animation timer have both had a chance to run.
     */
    private void runNext() {
        if (steps.isEmpty()) {
            return;
        }
        Runnable step = steps.poll();
        PauseTransition wait = new PauseTransition(SETTLE);
        wait.setOnFinished(event -> {
            try {
                step.run();
            } catch (RuntimeException e) {
                System.out.println("step failed: " + e);
            }
            runNext();
        });
        wait.play();
    }

    private void shoot(String name) {
        WritableImage image = scene.snapshot(null);
        File file = OUT.resolve(name + ".png").toFile();
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            System.out.println("wrote " + file);
        } catch (java.io.IOException e) {
            System.out.println("could not write " + file + ": " + e);
        }
    }
}
