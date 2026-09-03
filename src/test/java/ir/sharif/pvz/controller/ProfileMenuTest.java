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
import ir.sharif.pvz.util.PasswordHasher;
import ir.sharif.pvz.view.ConsoleView;
import ir.sharif.pvz.view.fx.screen.ProfileScreen;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Changing the details on the profile page.
 *
 * <p>The graphical screen drives the same controller the console does, and it
 * was sending commands of its own invention — "change username -u x" where the
 * menu answers to "menu profile change-username -u x", and -n for the nickname
 * where the menu wants -u. Every Save button was quietly refused.
 */
class ProfileMenuTest {

    @TempDir
    Path folder;

    private ByteArrayOutputStream output;
    private ProfileMenuController profile;
    private User user;

    @BeforeEach
    void signIn() {
        output = new ByteArrayOutputStream();
        ConsoleView view = new ConsoleView(new PrintStream(output, true, StandardCharsets.UTF_8));
        UserRepository users = new UserRepository(folder.resolve("users.json"));
        AppContext context = new AppContext(users, new SessionStore(folder.resolve("session.txt")),
                new AuthService(users), new ProfileService(users));
        user = new User("ali", PasswordHasher.sha256("Passw0rd!"), "all",
                "vhjvjhvjh@gmail.com", Gender.FEMALE);
        users.add(user);
        context.setCurrentUser(user);
        context.setCurrentMenu(MenuType.PROFILE);
        profile = new ProfileMenuController(context, view);
        output.reset();
    }

    private String run(String command) {
        profile.handle(command);
        String text = output.toString(StandardCharsets.UTF_8);
        output.reset();
        return text;
    }

    private static boolean refused(String reply) {
        return reply.toLowerCase(Locale.ROOT).contains("invalid command");
    }

    @Test
    void theScreensSaveButtonsAreCommandsTheMenuUnderstands() {
        assertFalse(refused(run(ProfileScreen.CHANGE_USERNAME + "rose")),
                "the username Save button");
        assertFalse(refused(run(ProfileScreen.CHANGE_NICKNAME + "Rosie")),
                "the nickname Save button");
        assertFalse(refused(run(ProfileScreen.CHANGE_EMAIL + "rose@example.com")),
                "the email Save button");
        assertFalse(refused(run(ProfileScreen.CHANGE_PASSWORD + " -p Newp4ss! -o Passw0rd!")),
                "the change password button");
    }

    @Test
    void eachSaveActuallyChangesTheField() {
        run(ProfileScreen.CHANGE_USERNAME + "rose");
        assertEquals("rose", user.getUsername());

        run(ProfileScreen.CHANGE_NICKNAME + "Rosie");
        assertEquals("Rosie", user.getNickname());

        run(ProfileScreen.CHANGE_EMAIL + "rose@example.com");
        assertEquals("rose@example.com", user.getEmail());
    }

    @Test
    void theNewPasswordIsTheOneThatWorksAfterwards() {
        String was = user.getPasswordHash();
        String reply = run(ProfileScreen.CHANGE_PASSWORD + " -p Newp4ss! -o Passw0rd!");
        assertTrue(reply.contains("Password changed"), reply);
        assertFalse(was.equals(user.getPasswordHash()), "the stored hash moved on");
        assertEquals(PasswordHasher.sha256("Newp4ss!"), user.getPasswordHash());
    }

    @Test
    void aWrongCurrentPasswordIsRefused() {
        String was = user.getPasswordHash();
        String reply = run(ProfileScreen.CHANGE_PASSWORD + " -p Newp4ss! -o notmypassword");
        assertFalse(reply.contains("Password changed"), reply);
        assertEquals(was, user.getPasswordHash(), "and nothing changed");
    }

    @Test
    void aBadValueIsReportedRatherThanApplied() {
        String reply = run(ProfileScreen.CHANGE_EMAIL + "not-an-email");
        assertFalse(reply.isBlank(), "the player is told why");
        assertEquals("vhjvjhvjh@gmail.com", user.getEmail(), "and the old one stands");
    }
}
