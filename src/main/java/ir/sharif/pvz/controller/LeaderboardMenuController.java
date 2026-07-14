package ir.sharif.pvz.controller;

import ir.sharif.pvz.model.LeaderboardService;
import ir.sharif.pvz.view.ConsoleView;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The leaderboard, reachable from the main menu; every column can be sorted
 * both ways ("clicking a column" in CLI form).
 */
public class LeaderboardMenuController extends MenuController {

    private static final Pattern SHOW = Pattern.compile(
            "^show\\s+leaderboard(?:\\s+-s\\s+(level|minigames|quests|mowpoints))?(?:\\s+-o\\s+(asc|desc))?$");

    private final LeaderboardService leaderboardService;

    public LeaderboardMenuController(AppContext context, ConsoleView view) {
        super(context, view);
        this.leaderboardService = new LeaderboardService(context.getUserRepository());
    }

    @Override
    public MenuType type() {
        return MenuType.LEADERBOARD;
    }

    @Override
    protected Set<MenuType> allowedTargets() {
        return Set.of(MenuType.SCORE_GAME);
    }

    @Override
    protected void onExit() {
        context.setCurrentMenu(MenuType.MAIN);
        view.info("You are back in the main menu.");
    }

    @Override
    protected void handleCommand(String input) {
        Matcher matcher = SHOW.matcher(input);
        if (!matcher.matches()) {
            view.unknownCommand();
            return;
        }
        String column = matcher.group(1) == null ? "mowpoints" : matcher.group(1);
        boolean ascending = "asc".equals(matcher.group(2));
        leaderboardService.table(
                LeaderboardService.Column.valueOf(column.toUpperCase(Locale.ROOT)), ascending)
                .forEach(view::info);
    }
}
