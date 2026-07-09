package ir.sharif.pvz.controller;

import ir.sharif.pvz.model.AuthException;
import ir.sharif.pvz.model.SecurityQuestion;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.UserValidator;
import ir.sharif.pvz.view.ConsoleView;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The login menu: logging in (optionally staying logged in) and the
 * forget-password flow with security questions.
 */
public class LoginMenuController extends MenuController {

    private enum State { IDLE, AWAITING_ANSWER, AWAITING_NEW_PASSWORD }

    private static final Pattern LOGIN_PATTERN =
            Pattern.compile("^login\\s+-u\\s+(\\S+)\\s+-p\\s+(\\S+)(\\s+-stay-logged-in)?$");
    private static final Pattern FORGET_PATTERN =
            Pattern.compile("^forget\\s+password\\s+-u\\s+(\\S+)\\s+-e\\s+(\\S+)$");
    private static final Pattern ANSWER_PATTERN = Pattern.compile("^answer\\s+-a\\s+(\\S+)$");

    private State state = State.IDLE;
    private User recoveringUser;

    public LoginMenuController(AppContext context, ConsoleView view) {
        super(context, view);
    }

    @Override
    public MenuType type() {
        return MenuType.LOGIN;
    }

    @Override
    protected Set<MenuType> allowedTargets() {
        return Set.of();
    }

    @Override
    protected void onExit() {
        resetState();
        context.setCurrentMenu(MenuType.SIGNUP);
        view.info("You are back in the signup menu.");
    }

    @Override
    protected void handleCommand(String input) {
        if (state == State.AWAITING_NEW_PASSWORD) {
            handleNewPassword(input);
            return;
        }
        Matcher loginMatcher = LOGIN_PATTERN.matcher(input);
        if (loginMatcher.matches()) {
            handleLogin(loginMatcher.group(1), loginMatcher.group(2), loginMatcher.group(3) != null);
            return;
        }
        Matcher forgetMatcher = FORGET_PATTERN.matcher(input);
        if (forgetMatcher.matches()) {
            handleForgetPassword(forgetMatcher.group(1), forgetMatcher.group(2));
            return;
        }
        Matcher answerMatcher = ANSWER_PATTERN.matcher(input);
        if (answerMatcher.matches()) {
            handleAnswer(answerMatcher.group(1));
            return;
        }
        view.unknownCommand();
    }

    private void handleLogin(String username, String password, boolean stayLoggedIn) {
        try {
            User user = context.getAuthService().login(username, password);
            context.setCurrentUser(user);
            if (stayLoggedIn) {
                context.getSessionStore().save(username);
            }
            resetState();
            context.setCurrentMenu(MenuType.MAIN);
            view.info("Welcome, " + user.getNickname() + "! You are now in the main menu.");
        } catch (AuthException e) {
            view.error(e.getMessage());
        }
    }

    private void handleForgetPassword(String username, String email) {
        try {
            recoveringUser = context.getAuthService().startForgetPassword(username, email);
            state = State.AWAITING_ANSWER;
            view.info("Security question: " + SecurityQuestion.byNumber(recoveringUser.getSecurityQuestionNumber()));
            view.info("Reply with: answer -a <answer>");
        } catch (AuthException e) {
            view.error(e.getMessage());
        }
    }

    private void handleAnswer(String answer) {
        if (state != State.AWAITING_ANSWER) {
            view.error("Start with: forget password -u <username> -e <email>");
            return;
        }
        if (context.getAuthService().checkSecurityAnswer(recoveringUser, answer)) {
            state = State.AWAITING_NEW_PASSWORD;
            view.info("Correct! Enter your new password:");
        } else {
            resetState();
            view.error("Wrong answer.");
        }
    }

    private void handleNewPassword(String input) {
        List<String> errors = UserValidator.validatePassword(input);
        if (!errors.isEmpty()) {
            view.errors(errors);
            return;
        }
        context.getAuthService().resetPassword(recoveringUser, input);
        resetState();
        view.info("Password changed successfully. You can log in now.");
    }

    private void resetState() {
        state = State.IDLE;
        recoveringUser = null;
    }
}
