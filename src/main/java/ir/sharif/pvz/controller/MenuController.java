package ir.sharif.pvz.controller;

import ir.sharif.pvz.view.ConsoleView;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for all menus: handles the navigation commands shared by every menu
 * (menu enter / menu exit / menu show current) and delegates the rest.
 */
public abstract class MenuController {

    private static final Pattern ENTER_PATTERN = Pattern.compile("^menu\\s+enter\\s+(\\S+)$");

    protected final AppContext context;
    protected final ConsoleView view;

    protected MenuController(AppContext context, ConsoleView view) {
        this.context = context;
        this.view = view;
    }

    public abstract MenuType type();

    /**
     * Menus reachable from this menu via the "menu enter" command.
     */
    protected abstract Set<MenuType> allowedTargets();

    /**
     * Handles menu-specific commands after common navigation commands are ruled out.
     */
    protected abstract void handleCommand(String input);

    /**
     * Called on "menu exit"; each menu decides where the user goes.
     */
    protected abstract void onExit();

    public void handle(String rawInput) {
        String input = rawInput.trim();
        if (input.isEmpty()) {
            return;
        }
        if (input.equals("menu show current")) {
            view.showCurrentMenu(type().id());
            return;
        }
        if (input.equals("menu exit")) {
            onExit();
            return;
        }
        Matcher enterMatcher = ENTER_PATTERN.matcher(input);
        if (enterMatcher.matches()) {
            enterMenu(enterMatcher.group(1));
            return;
        }
        handleCommand(input);
    }

    private void enterMenu(String menuName) {
        MenuType target = MenuType.fromId(menuName);
        if (target == null) {
            view.error("There is no menu named '" + menuName + "'.");
            return;
        }
        if (!allowedTargets().contains(target)) {
            view.error("You cannot enter the " + target.id() + " menu from the " + type().id() + " menu.");
            return;
        }
        context.setCurrentMenu(target);
        view.info("You entered the " + target.id() + " menu.");
    }
}
