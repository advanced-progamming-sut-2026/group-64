package ir.sharif.pvz.controller;

import ir.sharif.pvz.view.GameView;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for all menus: handles the navigation commands shared by every menu
 * (menu enter / menu exit / menu show current) and delegates the rest.
 */
public abstract class MenuController {

    private static final Pattern ENTER_PATTERN = Pattern.compile("^menu\\s+enter\\s+(\\S+)$");

    /**
     * The document lists adding coins and diamonds among the things every menu
     * shares, and the wallet is shown in every menu's top bar. It used to be
     * accepted only while a level was running, which is the one place the
     * player cannot spend them.
     */
    private static final Pattern WALLET_PATTERN =
            Pattern.compile("^cheat\\s+add\\s+-n\\s+(\\d+)\\s+(coins|diamonds)$");

    protected final AppContext context;
    protected final GameView view;

    protected MenuController(AppContext context, GameView view) {
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
        Matcher wallet = WALLET_PATTERN.matcher(input);
        if (wallet.matches() && context.getCurrentUser() != null) {
            view.info(addToWallet(Integer.parseInt(wallet.group(1)), wallet.group(2)));
            return;
        }
        Matcher enterMatcher = ENTER_PATTERN.matcher(input);
        if (enterMatcher.matches()) {
            enterMenu(enterMatcher.group(1));
            return;
        }
        handleCommand(input);
    }

    /**
     * Tops up one of the two currencies and says what the player now has.
     */
    private String addToWallet(int amount, String currency) {
        ir.sharif.pvz.model.User user = context.getCurrentUser();
        if ("coins".equals(currency)) {
            user.addCoins(amount);
            return "Added " + amount + " coins; you now have " + user.getCoins() + ".";
        }
        user.addDiamonds(amount);
        return "Added " + amount + " diamonds; you now have " + user.getDiamonds() + ".";
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
        context.rememberOrigin(target, type());
        context.setCurrentMenu(target);
        view.info("You entered the " + target.id() + " menu.");
    }
}
