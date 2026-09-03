package ir.sharif.pvz.controller;

import ir.sharif.pvz.view.GameView;
import java.util.Set;

/**
 * The main menu, reachable only after a successful login.
 */
public class MainMenuController extends MenuController {

    public MainMenuController(AppContext context, GameView view) {
        super(context, view);
    }

    @Override
    public MenuType type() {
        return MenuType.MAIN;
    }

    @Override
    protected Set<MenuType> allowedTargets() {
        return Set.of(MenuType.GAME, MenuType.SETTINGS, MenuType.NEWS, MenuType.PROFILE,
                MenuType.GREENHOUSE, MenuType.SCORE_GAME, MenuType.LEADERBOARD, MenuType.TRAVEL_LOG,
                MenuType.COLLECTION, MenuType.SHOP);
    }

    /**
     * There is no menu above this one, so leaving it is leaving the game. The
     * account is written out first; logging out and staying in the game is
     * "menu logout".
     */
    @Override
    protected void onExit() {
        context.getUserRepository().save();
        view.info("Goodbye!");
        context.stop();
    }

    @Override
    protected void handleCommand(String input) {
        if (input.equals("exit game")) {
            onExit();
            return;
        }
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
