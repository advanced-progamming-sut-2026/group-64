package ir.sharif.pvz.controller;

import ir.sharif.pvz.view.ConsoleView;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The settings menu: currently only the game difficulty (1 to 5, default 3).
 */
public class SettingsMenuController extends MenuController {

    private static final Pattern CHANGE_DIFFICULTY_PATTERN =
            Pattern.compile("^menu\\s+settings\\s+change-difficulty\\s+-l\\s+(\\S+)$");

    private static final int MIN_DIFFICULTY = 1;
    private static final int MAX_DIFFICULTY = 5;

    public SettingsMenuController(AppContext context, ConsoleView view) {
        super(context, view);
    }

    @Override
    public MenuType type() {
        return MenuType.SETTINGS;
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
        Matcher matcher = CHANGE_DIFFICULTY_PATTERN.matcher(input);
        if (!matcher.matches()) {
            view.unknownCommand();
            return;
        }
        int level;
        try {
            level = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            view.error("Difficulty level must be a number between 1 and 5.");
            return;
        }
        if (level < MIN_DIFFICULTY || level > MAX_DIFFICULTY) {
            view.error("Difficulty level must be between 1 and 5.");
            return;
        }
        context.getCurrentUser().setDifficulty(level);
        context.getUserRepository().save();
        view.info("Difficulty changed to " + level + ".");
    }
}
