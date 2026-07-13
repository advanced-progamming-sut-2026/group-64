package ir.sharif.pvz.controller;

import ir.sharif.pvz.model.AuthException;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.view.ConsoleView;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The profile menu: editing the logged-in user's account and showing its stats.
 */
public class ProfileMenuController extends MenuController {

    private static final Pattern CHANGE_USERNAME_PATTERN =
            Pattern.compile("^menu\\s+profile\\s+change-username\\s+-u\\s+(\\S+)$");
    private static final Pattern CHANGE_NICKNAME_PATTERN =
            Pattern.compile("^menu\\s+profile\\s+change-nickname\\s+-u\\s+(\\S+)$");
    private static final Pattern CHANGE_EMAIL_PATTERN =
            Pattern.compile("^menu\\s+profile\\s+change-email\\s+-e\\s+(\\S+)$");
    private static final Pattern CHANGE_PASSWORD_PATTERN =
            Pattern.compile("^menu\\s+profile\\s+change-password\\s+-p\\s+(\\S+)\\s+-o\\s+(\\S+)$");
    private static final Pattern SHOW_INFO_PATTERN =
            Pattern.compile("^menu\\s+profile\\s+show-info$");

    public ProfileMenuController(AppContext context, ConsoleView view) {
        super(context, view);
    }

    @Override
    public MenuType type() {
        return MenuType.PROFILE;
    }

    @Override
    protected Set<MenuType> allowedTargets() {
        return Set.of();
    }

    @Override
    protected void onExit() {
        context.setCurrentMenu(MenuType.MAIN);
        view.info("You are back in the main menu.");
    }

    @Override
    protected void handleCommand(String input) {
        try {
            dispatch(input);
        } catch (AuthException e) {
            view.error(e.getMessage());
        }
    }

    private void dispatch(String input) throws AuthException {
        User user = context.getCurrentUser();
        Matcher matcher = CHANGE_USERNAME_PATTERN.matcher(input);
        if (matcher.matches()) {
            changeUsername(user, matcher.group(1));
            return;
        }
        matcher = CHANGE_NICKNAME_PATTERN.matcher(input);
        if (matcher.matches()) {
            context.getProfileService().changeNickname(user, matcher.group(1));
            view.info("Nickname changed to '" + user.getNickname() + "'.");
            return;
        }
        matcher = CHANGE_EMAIL_PATTERN.matcher(input);
        if (matcher.matches()) {
            context.getProfileService().changeEmail(user, matcher.group(1));
            view.info("Email changed to '" + user.getEmail() + "'.");
            return;
        }
        matcher = CHANGE_PASSWORD_PATTERN.matcher(input);
        if (matcher.matches()) {
            context.getProfileService().changePassword(user, matcher.group(1), matcher.group(2));
            view.info("Password changed successfully.");
            return;
        }
        if (SHOW_INFO_PATTERN.matcher(input).matches()) {
            view.showUserInfo(user);
            return;
        }
        view.unknownCommand();
    }

    /**
     * Changing the username also refreshes the stay-logged-in session file, which stores it.
     */
    private void changeUsername(User user, String newUsername) throws AuthException {
        boolean sessionSaved = user.getUsername().equals(context.getSessionStore().load());
        context.getProfileService().changeUsername(user, newUsername);
        if (sessionSaved) {
            context.getSessionStore().save(newUsername);
        }
        view.info("Username changed to '" + newUsername + "'.");
    }
}
