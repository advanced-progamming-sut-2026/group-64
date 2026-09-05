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
import ir.sharif.pvz.model.game.GameCatalog;
import ir.sharif.pvz.view.ConsoleView;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opening the whole roster for a demo. The collection shows what has been
 * bought and the almanac what has been met in a level, so on a fresh account
 * most of both pages is shut — right for playing, useless for showing.
 */
class UnlockAllTest {

    @TempDir
    Path folder;

    private GameApp app;
    private User user;

    private void signIn() {
        UserRepository users = new UserRepository(folder.resolve("users.json"));
        user = new User("shower", "h", "S", "s@example.com", Gender.FEMALE);
        users.add(user);
        AppContext context = new AppContext(users,
                new SessionStore(folder.resolve("session.txt")),
                new AuthService(users), new ProfileService(users),
                new SavedGameStore(folder.resolve("saves.json")));
        context.setCurrentUser(user);
        context.setCurrentMenu(MenuType.MAIN);
        app = new GameApp(new ConsoleView(new PrintStream(OutputStream.nullOutputStream())),
                context);
    }

    @Test
    void everyPlantAndZombieIsOpenedAtOnce() {
        signIn();
        int plants = GameCatalog.get().allPlants().size();
        int zombies = GameCatalog.get().allZombies().size();
        assertTrue(user.getUnlockedPlants().size() < plants, "it starts with a few plants");
        assertTrue(user.getObservedZombies().isEmpty(), "and has met no zombies");

        app.submit("cheat unlock-all");

        assertEquals(plants, user.getUnlockedPlants().size(), "every plant is open");
        assertEquals(zombies, user.getObservedZombies().size(), "and every zombie is known");
        for (var plant : GameCatalog.get().allPlants()) {
            assertTrue(user.getUnlockedPlants().contains(plant.getName()),
                    plant.getName() + " should be unlocked");
        }
    }

    /** It works from any menu, the way adding coins does. */
    @Test
    void itWorksFromTheCollectionAndTheShopToo() {
        for (String menu : new String[] {"collection", "shop", "greenhouse"}) {
            signIn();
            app.submit("menu enter " + menu);
            app.submit("cheat unlock-all");
            assertEquals(GameCatalog.get().allPlants().size(), user.getUnlockedPlants().size(),
                    "should work from the " + menu + " menu");
        }
    }

    /** It survives a restart, because the account is written out. */
    @Test
    void theUnlockIsSavedRatherThanLastingOnlyForTheSession() {
        signIn();
        app.submit("cheat unlock-all");

        UserRepository reopened = new UserRepository(folder.resolve("users.json"));
        User back = reopened.findByUsername("shower");
        assertEquals(GameCatalog.get().allPlants().size(), back.getUnlockedPlants().size());
        assertFalse(back.getObservedZombies().isEmpty());
    }
}
