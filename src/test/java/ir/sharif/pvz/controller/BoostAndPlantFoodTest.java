package ir.sharif.pvz.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Boosting a plant before a level and spending plant food during one.
 *
 * <p>Boosting costs diamonds, and diamonds could only be added while a level
 * was running — which is the one place they cannot be spent on a boost. From
 * the plant picker the button could only ever answer "boosting costs 2
 * diamonds".
 */
class BoostAndPlantFoodTest {

    @TempDir
    Path folder;

    private GameApp app;
    private ByteArrayOutputStream output;
    private User user;

    @BeforeEach
    void signIn() {
        output = new ByteArrayOutputStream();
        ConsoleView view = new ConsoleView(new PrintStream(output, true, StandardCharsets.UTF_8));
        UserRepository users = new UserRepository(folder.resolve("users.json"));
        AppContext context = new AppContext(users, new SessionStore(folder.resolve("session.txt")),
                new AuthService(users), new ProfileService(users),
                new ir.sharif.pvz.model.SavedGameStore(folder.resolve("saves.json")));
        user = new User("booster", "hash", "Boost", "b@example.com", Gender.FEMALE);
        // these tests reach for the in-game debug commands, which the debug
        // setting is what offers
        user.setDebugMode(true);
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

    private ir.sharif.pvz.model.game.GameSession session() {
        return ((GameMenuController) app.currentController()).getSession();
    }

    @Test
    void coinsAndDiamondsCanBeAddedFromAnyMenu() {
        for (MenuType menu : new MenuType[] {MenuType.MAIN, MenuType.SHOP, MenuType.COLLECTION,
                MenuType.PROFILE, MenuType.GREENHOUSE}) {
            app.getContext().setCurrentMenu(menu);
            String reply = run("cheat add -n 5 diamonds");
            assertFalse(reply.toLowerCase(Locale.ROOT).contains("invalid command"),
                    menu.id() + " should top up the wallet: " + reply);
        }
        assertEquals(25, user.getDiamonds());
        app.getContext().setCurrentMenu(MenuType.MAIN);
        assertTrue(run("cheat add -n 500 coins").contains("500 coins"));
        assertEquals(500, user.getCoins());
    }

    @Test
    void aPlantCanBeBoostedFromThePickerAndStartsFedOnTheLawn() {
        run("cheat add -n 10 diamonds");
        run("menu enter game");
        run("add plant -t sunflower");
        String boosted = run("boost plant -t sunflower");
        assertTrue(boosted.contains("boosted"), boosted);
        assertEquals(8, user.getDiamonds(), "boosting costs two diamonds");

        run("select level -c egypt -d 1");
        run("start game");
        run("cheat add -n 500 suns");
        int before = session().getSunAmount();
        run("plant plant -t sunflower -l (1, 1)");

        // a sunflower's plant food is an instant burst of sun, so a boosted one
        // pays out the moment it lands rather than after its first cycle
        assertTrue(session().getSunAmount() > before - 50,
                "the boost should have fed it on arrival");
    }

    @Test
    void boostingNeedsThePlantChosenAndTheDiamondsToPayForIt() {
        run("menu enter game");
        assertTrue(run("boost plant -t sunflower").contains("not selected"));

        run("add plant -t sunflower");
        assertTrue(run("boost plant -t sunflower").contains("costs"), "no diamonds yet");

        run("cheat add -n 10 diamonds");
        assertTrue(run("boost plant -t sunflower").contains("boosted"));
        assertTrue(run("boost plant -t sunflower").contains("already boosted"));
    }

    @Test
    void plantFoodIsSpentOnThePlantItIsGivenTo() {
        run("cheat add -n 10 diamonds");
        run("menu enter game");
        run("add plant -t peashooter");
        run("select level -c egypt -d 1");
        run("start game");
        run("cheat add -n 500 suns");
        run("plant plant -t peashooter -l (2, 2)");

        assertTrue(run("feed plant -l (2, 2)").contains("no plant food"),
                "there is none to spend yet");
        run("cheat add-plant-food");
        assertEquals(1, session().getPlantFood());

        assertTrue(run("feed plant -l (2, 2)").contains("Plant food used"));
        assertEquals(0, session().getPlantFood(), "and it is spent");
        assertTrue(run("feed plant -l (5, 5)").contains("no plant"),
                "an empty tile has nothing to feed");
    }
}
