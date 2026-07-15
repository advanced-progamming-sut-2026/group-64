package ir.sharif.pvz.controller;

import ir.sharif.pvz.view.ConsoleView;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The travel log: quest pages sorted by priority, claiming rewards, and the
 * minigames page.
 */
public class TravelLogMenuController extends MenuController {

    private static final Pattern PAGE = Pattern.compile("^travel\\s+log\\s+page\\s+(\\S+)$");
    private static final Pattern CLAIM = Pattern.compile("^travel\\s+log\\s+claim\\s+-q\\s+(\\S+)$");

    public TravelLogMenuController(AppContext context, ConsoleView view) {
        super(context, view);
    }

    @Override
    public MenuType type() {
        return MenuType.TRAVEL_LOG;
    }

    @Override
    protected Set<MenuType> allowedTargets() {
        return Set.of(MenuType.MINIGAME);
    }

    @Override
    protected void onExit() {
        context.setCurrentMenu(MenuType.MAIN);
        view.info("You are back in the main menu.");
    }

    @Override
    protected void handleCommand(String input) {
        Matcher matcher;
        if (input.equals("show travel log")) {
            context.getQuestService().lines(context.getCurrentUser(), null).forEach(view::info);
        } else if ((matcher = PAGE.matcher(input)).matches()) {
            showPage(matcher.group(1));
        } else if ((matcher = CLAIM.matcher(input)).matches()) {
            view.info(context.getQuestService().claim(context.getCurrentUser(), matcher.group(1)));
        } else {
            view.unknownCommand();
        }
    }

    private void showPage(String page) {
        if (page.equals("minigames")) {
            view.info("Play them with: menu enter minigame");
            for (String name : ir.sharif.pvz.model.game.Minigames.NAMES) {
                int progress = context.getCurrentUser().getMinigameProgress().getOrDefault(name, 0);
                view.info("- " + name + ": " + progress + "/"
                        + ir.sharif.pvz.model.game.Minigames.STAGES + " stages done");
            }
            return;
        }
        context.getQuestService().lines(context.getCurrentUser(), page).forEach(view::info);
    }
}
