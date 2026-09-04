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
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Recovering a forgotten password, all three steps of it.
 *
 * <p>The graphical screen had only the first two: it asked for the account and
 * the security answer, and when the menu came back with "enter your new
 * password" there was nowhere to type one. Worse, submitting anything rebuilt
 * the screen from scratch, which threw the player back to the sign-in form
 * after the very first click, so the flow never got past step one.
 */
class ForgotPasswordTest {

    @TempDir
    Path folder;

    private ByteArrayOutputStream output;
    private LoginMenuController login;
    private User user;

    @BeforeEach
    void setUp() {
        output = new ByteArrayOutputStream();
        ConsoleView view = new ConsoleView(new PrintStream(output, true, StandardCharsets.UTF_8));
        UserRepository users = new UserRepository(folder.resolve("users.json"));
        AppContext context = new AppContext(users, new SessionStore(folder.resolve("session.txt")),
                new AuthService(users), new ProfileService(users));
        user = new User("rose", PasswordHasher.sha256("Passw0rd!"), "Rosie",
                "rose@example.com", Gender.FEMALE);
        user.setSecurityQuestion(1, PasswordHasher.sha256("green"));
        users.add(user);
        context.setCurrentMenu(MenuType.LOGIN);
        login = new LoginMenuController(context, view);
        output.reset();
    }

    private String run(String command) {
        login.handle(command);
        String text = output.toString(StandardCharsets.UTF_8);
        output.reset();
        return text;
    }

    @Test
    void theWholeFlowEndsWithAPasswordThatWorks() {
        assertFalse(login.isRecovering(), "nothing is under way yet");

        String asked = run("forget password -u rose -e rose@example.com");
        assertTrue(asked.contains("Security question"), asked);
        assertTrue(login.isRecovering(), "the screen has a second step to draw");
        assertFalse(login.isAwaitingNewPassword());

        String answered = run("answer -a green");
        assertTrue(answered.contains("Correct"), answered);
        assertTrue(login.isAwaitingNewPassword(), "and a third step");

        String set = run("Newp4ss!");
        assertTrue(set.contains("Password changed"), set);
        assertFalse(login.isRecovering(), "the flow is finished");
        assertEquals(PasswordHasher.sha256("Newp4ss!"), user.getPasswordHash());
    }

    @Test
    void theScreenCanAskWhichQuestionToShow() {
        run("forget password -u rose -e rose@example.com");
        assertEquals("What is your favorite plant?", login.recoveryQuestion());
    }

    @Test
    void aWrongAnswerStopsTheFlow() {
        run("forget password -u rose -e rose@example.com");
        String reply = run("answer -a purple");
        assertTrue(reply.contains("Wrong answer"), reply);
        assertFalse(login.isRecovering(), "it starts over rather than letting them guess on");
        assertEquals(PasswordHasher.sha256("Passw0rd!"), user.getPasswordHash());
    }

    @Test
    void aNewPasswordThatIsTooWeakIsRefusedAndTheStepStays() {
        run("forget password -u rose -e rose@example.com");
        run("answer -a green");
        String reply = run("abc");
        assertFalse(reply.contains("Password changed"), reply);
        assertTrue(login.isAwaitingNewPassword(), "they get to try again");
        assertEquals(PasswordHasher.sha256("Passw0rd!"), user.getPasswordHash());
    }

    /**
     * The Back button on the recovery panel, which has to be understood even
     * at the step where anything typed is taken for a password.
     */
    @Test
    void backingOutWorksFromEveryStep() {
        run("forget password -u rose -e rose@example.com");
        run("cancel recovery");
        assertFalse(login.isRecovering());

        run("forget password -u rose -e rose@example.com");
        run("answer -a green");
        assertTrue(login.isAwaitingNewPassword());
        run("cancel recovery");
        assertFalse(login.isRecovering(), "and not taken for the new password");
        assertEquals(PasswordHasher.sha256("Passw0rd!"), user.getPasswordHash());
    }
}
