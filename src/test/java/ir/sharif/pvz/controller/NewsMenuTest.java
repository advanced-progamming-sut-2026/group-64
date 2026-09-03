package ir.sharif.pvz.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ir.sharif.pvz.model.AuthService;
import ir.sharif.pvz.model.Gender;
import ir.sharif.pvz.model.NewsItem;
import ir.sharif.pvz.model.ProfileService;
import ir.sharif.pvz.model.SessionStore;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.UserRepository;
import ir.sharif.pvz.view.ConsoleView;
import ir.sharif.pvz.view.fx.screen.NewsScreen;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The news menu: reading the unread pile, rereading everything, and the read
 * marks sticking afterwards.
 */
class NewsMenuTest {

    @TempDir
    Path folder;

    private ByteArrayOutputStream output;
    private NewsMenuController news;
    private UserRepository users;
    private User user;

    @BeforeEach
    void signIn() {
        output = new ByteArrayOutputStream();
        ConsoleView view = new ConsoleView(new PrintStream(output, true, StandardCharsets.UTF_8));
        users = new UserRepository(folder.resolve("users.json"));
        AppContext context = new AppContext(users, new SessionStore(folder.resolve("session.txt")),
                new AuthService(users), new ProfileService(users));
        user = new User("reader", "hash", "Reader", "reader@example.com", Gender.FEMALE);
        users.add(user);
        context.setCurrentUser(user);
        context.setCurrentMenu(MenuType.NEWS);
        user.addNews("New plant unlocked: threepeater");
        user.addNews("New minigame stage unlocked: beghouled stage 1");
        news = new NewsMenuController(context, view);
        drain();
    }

    private String drain() {
        String text = output.toString(StandardCharsets.UTF_8);
        output.reset();
        return text;
    }

    private String run(String command) {
        news.handle(command);
        return drain();
    }

    @Test
    void theUnreadListShowsEveryItemAndMarksThemRead() {
        String shown = run("menu news show-unread");
        assertTrue(shown.contains("threepeater"), shown);
        assertTrue(shown.contains("beghouled"), shown);
        assertTrue(user.getNews().stream().allMatch(NewsItem::isRead), "reading them marks them read");

        assertTrue(run("menu news show-unread").contains("no unread news"),
                "a second look finds nothing new");
    }

    @Test
    void showAllKeepsTheOlderNewsReadable() {
        run("menu news show-unread");
        String all = run("menu news show-all");
        assertTrue(all.contains("threepeater"), all);
        assertFalse(all.contains("[new]"), "nothing is unread any more: " + all);
    }

    /**
     * The graphical news screen drives this same controller, so whatever it
     * submits has to be a command the menu actually answers to. It was not:
     * the screen sent "show-unread" where the menu expects
     * "menu news show-unread", so the list drew but nothing was marked read
     * and every item kept its NEW tag forever.
     */
    @Test
    void theCommandsTheNewsScreenSendsAreUnderstood() {
        assertFalse(run(NewsScreen.SHOW_ALL).toLowerCase(Locale.ROOT).contains("invalid command"),
                "the news menu should answer the screen's All button");
        assertFalse(run(NewsScreen.SHOW_UNREAD).toLowerCase(Locale.ROOT).contains("invalid command"),
                "the news menu should answer the screen's Unread button");
        assertTrue(user.getNews().stream().allMatch(NewsItem::isRead),
                "the screen's own command should leave the pile read");
        assertEquals(0, ir.sharif.pvz.view.fx.screen.MainMenuScreen.unreadCount(user),
                "the main menu's unread badge should be gone as well");
    }

    @Test
    void readMarksSurviveBeingSavedAndLoaded() {
        run("menu news show-unread");

        UserRepository reloaded = new UserRepository(folder.resolve("users.json"));
        User back = reloaded.findByUsername("reader");
        assertEquals(2, back.getNews().size());
        assertTrue(back.getNews().stream().allMatch(NewsItem::isRead),
                "a read item should still be read after a restart");
    }
}
