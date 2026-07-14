package ir.sharif.pvz.controller;

import ir.sharif.pvz.view.ConsoleView;
import java.util.Set;

/**
 * The main menu, reachable only after a successful login.
 */
public class MainMenuController extends MenuController {

    public MainMenuController(AppContext context, ConsoleView view) {
        super(context, view);
    }

    @Override
    public MenuType type() {
        return MenuType.MAIN;
    }

    @Override
    protected Set<MenuType> allowedTargets() {
        return Set.of(MenuType.GAME, MenuType.SETTINGS, MenuType.NEWS, MenuType.PROFILE,
                MenuType.GREENHOUSE);
    }

    @Override
    protected void onExit() {
        view.error("You must log out first. Use: menu logout");
    }

    @Override
    protected void handleCommand(String input) {
        if (input.equals("menu logout")) {
            context.getSessionStore().clear();
            context.setCurrentUser(null);
            context.setCurrentMenu(MenuType.SIGNUP);
            view.info("Logged out. You are now in the signup menu.");
            return;
        }
        view.unknownCommand();
    }
}
