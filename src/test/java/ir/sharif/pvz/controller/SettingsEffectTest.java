package ir.sharif.pvz.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ir.sharif.pvz.model.AuthService;
import ir.sharif.pvz.model.Gender;
import ir.sharif.pvz.model.ProfileService;
import ir.sharif.pvz.model.SavedGameStore;
import ir.sharif.pvz.model.SessionStore;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.UserRepository;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.view.ConsoleView;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Each of the four settings has to change something a player can see, or it is
 * only a number written to disk. These play a level through the commands and
 * watch the setting land: difficulty in what the zombies are made of, speed in
 * how far a tick of typed time carries, the grid in how the map is drawn, and
 * debug in whether the debug commands answer at all.
 */
class SettingsEffectTest {

    @TempDir
    Path folder;

    private GameApp app;
    private User user;
    private ByteArrayOutputStream output;

    @BeforeEach
    void setUp() {
        output = new ByteArrayOutputStream();
        ConsoleView view = new ConsoleView(new PrintStream(output, true, StandardCharsets.UTF_8));
        UserRepository users = new UserRepository(folder.resolve("users.json"));
        AppContext context = new AppContext(users, new SessionStore(folder.resolve("session.txt")),
                new AuthService(users), new ProfileService(users),
                new SavedGameStore(folder.resolve("saves.json")));
        user = new User("tuner", "hash", "Tuner", "t@example.com", Gender.FEMALE);
        users.add(user);
        context.setCurrentUser(user);
        context.setCurrentMenu(MenuType.MAIN);
        app = new GameApp(view, context);
    }

    private String run(String command) {
        output.reset();
        app.submit(command);
        return output.toString(StandardCharsets.UTF_8);
    }

    private void startALevel() {
        app.submit("menu enter game");
        app.submit("add plant -t peashooter");
        app.submit("select level -c egypt -d 1");
        app.submit("start game");
    }

    private GameSession session() {
        return ((GameMenuController) app.currentController()).getSession();
    }

    // ===== difficulty =====

    @Test
    void theDifficultySettingDecidesWhatTheZombiesAreMadeOf() {
        user.setDebugMode(true);
        int gentle = healthOfANormalZombieAtDifficulty(1);
        int harsh = healthOfANormalZombieAtDifficulty(5);
        assertTrue(harsh > gentle,
                "the same zombie should be tougher on 5 than on 1: " + harsh + " vs " + gentle);
    }

    private int healthOfANormalZombieAtDifficulty(int level) {
        app.submit("menu enter settings");
        app.submit("menu settings change-difficulty -l " + level);
        app.submit("menu exit");
        startALevel();
        app.submit("cheat spawn-zombie -t normal -l (6, 2)");
        int health = session().getZombies().get(0).totalRemainingHealth();
        app.submit("forfeit level");
        app.submit("menu exit");
        return health;
    }

    @Test
    void aDifficultyOutsideOneToFiveIsRefused() {
        app.submit("menu enter settings");
        assertTrue(run("menu settings change-difficulty -l 6").contains("between 1 and 5"));
        assertEquals(3, user.getDifficulty(), "and the old setting stands");
    }

    // ===== speed =====

    @Test
    void theSpeedSettingDecidesHowFarATickOfTypedTimeCarries() {
        startALevel();
        app.submit("advance time -t 60 ticks");
        double atOne = session().getElapsedSeconds();
        app.submit("forfeit level");

        app.submit("menu exit");
        app.submit("menu enter settings");
        app.submit("menu settings change-speed -l 3");
        app.submit("menu exit");
        startALevel();
        String said = run("advance time -t 60 ticks");

        assertEquals(atOne * 3, session().getElapsedSeconds(), 0.001,
                "the same command should carry three times as far at 3x");
        assertTrue(said.contains("3x"), "and say so: " + said);
    }

    @Test
    void aSpeedOutsideOneToThreeIsRefused() {
        app.submit("menu enter settings");
        assertTrue(run("menu settings change-speed -l 0").contains("between 1 and 3"));
        assertEquals(1, user.getGameSpeed());
    }

    // ===== the lawn grid =====

    @Test
    void theGridSettingDecidesWhetherTheMapIsRuledAndNumbered() {
        startALevel();
        String bare = run("show map");
        assertFalse(bare.contains("+--------"), "no lines when the grid is off");

        app.submit("forfeit level");
        app.submit("menu exit");
        app.submit("menu enter settings");
        assertTrue(run("menu settings toggle-grid").contains("shown"));
        app.submit("menu exit");
        startALevel();
        String ruled = run("show map");

        assertTrue(ruled.contains("+--------"), "the grid is drawn: " + ruled);
        assertTrue(ruled.contains(" 9 "), "and the columns are numbered: " + ruled);
    }

    // ===== debug mode =====

    @Test
    void theDebugSettingDecidesWhetherTheDebugCommandsAnswer() {
        startALevel();
        String refused = run("cheat add -n 500 suns");
        assertTrue(refused.contains("Debug mode is off"), "refused while it is off: " + refused);
        assertEquals(50, session().getSunAmount(), "and nothing happened");

        app.submit("forfeit level");
        app.submit("menu exit");
        app.submit("menu enter settings");
        assertTrue(run("menu settings toggle-debug").contains("on"));
        app.submit("menu exit");
        startALevel();
        int before = session().getSunAmount();

        app.submit("cheat add -n 500 suns");
        assertEquals(before + 500, session().getSunAmount(), "and now it works");
    }

    /**
     * The gate covers the debug commands only: an ordinary in-game command
     * still answers with debug off, and so does an unknown one.
     */
    @Test
    void theGateDoesNotSwallowTheOrdinaryCommands() {
        startALevel();
        assertTrue(run("show sun amount").contains("Sun:"));
        assertTrue(run("release the nuke").contains("Debug mode is off"));
        assertTrue(run("plant plant -t rutabaga -l (1, 1)").length() > 0);
    }
}
