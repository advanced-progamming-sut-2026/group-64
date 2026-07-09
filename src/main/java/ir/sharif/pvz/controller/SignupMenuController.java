package ir.sharif.pvz.controller;

import ir.sharif.pvz.model.RegisterRequest;
import ir.sharif.pvz.model.SecurityQuestion;
import ir.sharif.pvz.view.ConsoleView;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The signup menu: creating a new account, including the retry-password and
 * security-question steps described in the spec.
 */
public class SignupMenuController extends MenuController {

    private enum State { IDLE, AWAITING_PASSWORD_RETRY, AWAITING_SECURITY_QUESTION }

    private static final Pattern REGISTER_PATTERN = Pattern.compile(
            "^register\\s+-u\\s+(\\S+)\\s+-p\\s+(\\S+)\\s+(\\S+)\\s+-n\\s+(\\S+)\\s+-e\\s+(\\S+)\\s+-g\\s+(\\S+)$");
    private static final Pattern PICK_QUESTION_PATTERN = Pattern.compile(
            "^pick\\s+question\\s+-q\\s+(\\d+)\\s+-a\\s+(\\S+)\\s+-c\\s+(\\S+)$");
    private static final Pattern PASSWORD_RETRY_PATTERN = Pattern.compile("^(\\S+)\\s+(\\S+)$");

    private State state = State.IDLE;
    private RegisterRequest pendingRequest;

    public SignupMenuController(AppContext context, ConsoleView view) {
        super(context, view);
    }

    @Override
    public MenuType type() {
        return MenuType.SIGNUP;
    }

    @Override
    protected Set<MenuType> allowedTargets() {
        return Set.of(MenuType.LOGIN);
    }

    @Override
    protected void onExit() {
        view.info("Goodbye!");
        context.stop();
    }

    @Override
    protected void handleCommand(String input) {
        switch (state) {
            case AWAITING_PASSWORD_RETRY -> handlePasswordRetry(input);
            case AWAITING_SECURITY_QUESTION -> handlePickQuestion(input);
            default -> handleRegister(input);
        }
    }

    private void handleRegister(String input) {
        Matcher matcher = REGISTER_PATTERN.matcher(input);
        if (!matcher.matches()) {
            view.unknownCommand();
            return;
        }
        RegisterRequest request = new RegisterRequest(
                matcher.group(1), matcher.group(2), matcher.group(3),
                matcher.group(4), matcher.group(5), matcher.group(6));
        List<String> errors = context.getAuthService().validateRegistration(request);
        if (!errors.isEmpty()) {
            view.errors(errors);
            return;
        }
        if (!context.getAuthService().passwordsMatch(request)) {
            pendingRequest = request;
            state = State.AWAITING_PASSWORD_RETRY;
            view.error("Passwords do not match.");
            view.info("Enter '<password> <password_confirm>' to try again, or 'cancel' to return to the menu.");
            return;
        }
        askSecurityQuestion(request);
    }

    private void handlePasswordRetry(String input) {
        if (input.equals("cancel")) {
            resetState();
            view.info("Registration cancelled.");
            return;
        }
        Matcher matcher = PASSWORD_RETRY_PATTERN.matcher(input);
        if (!matcher.matches()) {
            view.error("Enter '<password> <password_confirm>' or 'cancel'.");
            return;
        }
        RegisterRequest retried = pendingRequest.withPassword(matcher.group(1), matcher.group(2));
        List<String> passwordErrors = context.getAuthService().validateRegistration(retried);
        if (!passwordErrors.isEmpty()) {
            view.errors(passwordErrors);
            return;
        }
        if (!context.getAuthService().passwordsMatch(retried)) {
            view.error("Passwords do not match. Try again or enter 'cancel'.");
            return;
        }
        askSecurityQuestion(retried);
    }

    private void askSecurityQuestion(RegisterRequest request) {
        pendingRequest = request;
        state = State.AWAITING_SECURITY_QUESTION;
        view.showSecurityQuestions(SecurityQuestion.all());
    }

    private void handlePickQuestion(String input) {
        if (input.equals("cancel")) {
            resetState();
            view.info("Registration cancelled.");
            return;
        }
        Matcher matcher = PICK_QUESTION_PATTERN.matcher(input);
        if (!matcher.matches()) {
            view.error("Use: pick question -q <question_number> -a <answer> -c <answer_confirm>");
            return;
        }
        int questionNumber = Integer.parseInt(matcher.group(1));
        if (!SecurityQuestion.isValidNumber(questionNumber)) {
            view.error("Question number must be between 1 and " + SecurityQuestion.all().size() + ".");
            return;
        }
        if (!matcher.group(2).equals(matcher.group(3))) {
            view.error("Answer and its confirmation do not match.");
            return;
        }
        context.getAuthService().register(pendingRequest, questionNumber, matcher.group(2));
        String username = pendingRequest.username();
        resetState();
        context.setCurrentMenu(MenuType.LOGIN);
        view.info("User '" + username + "' registered successfully. You are now in the login menu.");
    }

    private void resetState() {
        state = State.IDLE;
        pendingRequest = null;
    }
}
