package ir.sharif.pvz.controller;

import ir.sharif.pvz.view.GameView;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The settings menu: game difficulty (1 to 5, default 3) plus the three
 * presentation settings the graphical view needs — how fast the game runs,
 * whether the lawn grid is drawn, and whether the debug cheats are offered.
 */
public class SettingsMenuController extends MenuController {

    private static final Pattern CHANGE_DIFFICULTY_PATTERN =
            Pattern.compile("^menu\\s+settings\\s+change-difficulty\\s+-l\\s+(\\S+)$");

    private static final Pattern CHANGE_SPEED_PATTERN =
            Pattern.compile("^menu\\s+settings\\s+change-speed\\s+-l\\s+(\\S+)$");

    private static final int MIN_DIFFICULTY = 1;
    private static final int MAX_DIFFICULTY = 5;
    private static final int MIN_SPEED = 1;
    private static final int MAX_SPEED = 3;

    public SettingsMenuController(AppContext context, GameView view) {
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
        Matcher matcher;
        if ((matcher = CHANGE_DIFFICULTY_PATTERN.matcher(input)).matches()) {
            changeDifficulty(matcher.group(1));
        } else if ((matcher = CHANGE_SPEED_PATTERN.matcher(input)).matches()) {
            changeSpeed(matcher.group(1));
        } else if (input.equals("menu settings toggle-grid")) {
            toggleGrid();
        } else if (input.equals("menu settings toggle-debug")) {
            toggleDebug();
        } else {
            view.unknownCommand();
        }
    }

    private void changeDifficulty(String argument) {
        int level = parseNumber(argument);
        if (level < MIN_DIFFICULTY || level > MAX_DIFFICULTY) {
            view.error("Difficulty level must be between 1 and 5.");
            return;
        }
        context.getCurrentUser().setDifficulty(level);
        context.getUserRepository().save();
        view.info("Difficulty changed to " + level + ".");
    }

    private void changeSpeed(String argument) {
        int speed = parseNumber(argument);
        if (speed < MIN_SPEED || speed > MAX_SPEED) {
            view.error("Game speed must be between 1 and 3.");
            return;
        }
        context.getCurrentUser().setGameSpeed(speed);
        context.getUserRepository().save();
        view.info("Game speed changed to " + speed + ".");
    }

    private void toggleGrid() {
        boolean enabled = !context.getCurrentUser().isShowGrid();
        context.getCurrentUser().setShowGrid(enabled);
        context.getUserRepository().save();
        view.info("Lawn grid is now " + (enabled ? "shown" : "hidden") + ".");
    }

    private void toggleDebug() {
        boolean enabled = !context.getCurrentUser().isDebugMode();
        context.getCurrentUser().setDebugMode(enabled);
        context.getUserRepository().save();
        view.info("Debug mode is now " + (enabled ? "on" : "off") + ".");
    }

    /**
     * Returns the parsed number, or -1 when the argument is not a number at all
     * so the callers can report their own out-of-range message.
     */
    private static int parseNumber(String argument) {
        try {
            return Integer.parseInt(argument);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
