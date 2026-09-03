package ir.sharif.pvz.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;

import ir.sharif.pvz.model.AuthService;
import ir.sharif.pvz.model.Gender;
import ir.sharif.pvz.model.ProfileService;
import ir.sharif.pvz.model.SessionStore;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.UserRepository;
import ir.sharif.pvz.view.ConsoleView;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every menu command the graphical screens send, run through the real menus.
 *
 * <p>The screens drive the same controllers the console does by submitting
 * command strings, and nothing checks that the string a button sends is one
 * the menu answers to. Twice it was not: the news page and the profile page
 * both sent wording of their own and every button was quietly refused, with
 * the player left wondering why nothing happened.
 */
class ScreenCommandsTest {

    @TempDir
    Path folder;

    private GameApp app;
    private ByteArrayOutputStream output;

    @BeforeEach
    void signIn() {
        output = new ByteArrayOutputStream();
        ConsoleView view = new ConsoleView(new PrintStream(output, true, StandardCharsets.UTF_8));
        UserRepository users = new UserRepository(folder.resolve("users.json"));
        AppContext context = new AppContext(users, new SessionStore(folder.resolve("session.txt")),
                new AuthService(users), new ProfileService(users),
                new ir.sharif.pvz.model.SavedGameStore(folder.resolve("saves.json")));
        User user = new User("clicker", "hash", "Click", "c@example.com", Gender.FEMALE);
        users.add(user);
        context.setCurrentUser(user);
        context.setCurrentMenu(MenuType.MAIN);
        app = new GameApp(view, context);
    }

    /**
     * Runs a command in its own menu and reports what the menu said.
     */
    private String inMenu(MenuType menu, String command) {
        app.getContext().setCurrentMenu(menu);
        output.reset();
        app.submit(command);
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void noScreensButtonSendsSomethingItsMenuDoesNotUnderstand() {
        Map<MenuType, List<String>> sent = Map.of(
                MenuType.PROFILE, List.of(
                        ir.sharif.pvz.view.fx.screen.ProfileScreen.CHANGE_USERNAME + "rose",
                        ir.sharif.pvz.view.fx.screen.ProfileScreen.CHANGE_NICKNAME + "Rosie",
                        ir.sharif.pvz.view.fx.screen.ProfileScreen.CHANGE_EMAIL + "r@example.com",
                        ir.sharif.pvz.view.fx.screen.ProfileScreen.CHANGE_PASSWORD
                                + " -p Newp4ss! -o Passw0rd!"),
                MenuType.NEWS, List.of(
                        ir.sharif.pvz.view.fx.screen.NewsScreen.SHOW_UNREAD,
                        ir.sharif.pvz.view.fx.screen.NewsScreen.SHOW_ALL),
                MenuType.SETTINGS, List.of(
                        "menu settings change-difficulty -l 4",
                        "menu settings change-speed -l 2",
                        "menu settings toggle-grid",
                        "menu settings toggle-debug"),
                MenuType.COLLECTION, List.of(
                        "menu collection upgrade-plant -p peashooter",
                        "menu collection purchase-plant -p cactus",
                        "menu collection show-plant -p peashooter",
                        "menu collection show-zombie -z normal"),
                MenuType.SHOP, List.of("shop list", "shop daily", "shop buy -i pot -n 1",
                        "shop buy -i choice-packets -n 1 -t peashooter"),
                MenuType.TRAVEL_LOG, List.of("show travel log", "travel log page daily",
                        "travel log claim -q daily-symmetry"),
                MenuType.GREENHOUSE, List.of("plant pot at (1, 1)", "collect (1, 1)",
                        "grow (1, 1)"),
                MenuType.MINIGAME, List.of("show minigames", "play -m vasebreaker -s 1"),
                MenuType.GAME, List.of("show all plants", "show chapters",
                        "select level -c egypt -d 1", "add plant -t peashooter",
                        "remove plant -t peashooter", "boost plant -t peashooter",
                        "resume game", "replay level"));

        sent.forEach((menu, commands) -> {
            for (String command : commands) {
                String reply = inMenu(menu, command);
                assertFalse(reply.toLowerCase(Locale.ROOT).contains("invalid command"),
                        menu.id() + " does not understand '" + command + "': " + reply);
            }
        });
    }

    /**
     * The commands that work from any menu.
     */
    @Test
    void theSharedNavigationCommandsWorkFromAScreenToo() {
        for (String command : List.of("menu show current", "menu enter profile", "menu exit")) {
            String reply = inMenu(MenuType.MAIN, command);
            assertFalse(reply.toLowerCase(Locale.ROOT).contains("invalid command"),
                    "'" + command + "' should be understood: " + reply);
        }
    }
}
