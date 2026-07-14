package ir.sharif.pvz.controller;

import ir.sharif.pvz.view.ConsoleView;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The greenhouse: plant flowers in pots, watch them grow on the real clock,
 * speed them up with diamonds and collect coins or stored boosts.
 */
public class GreenhouseMenuController extends MenuController {

    private static final String LOCATION = "\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)";
    private static final Pattern PLANT_POT = Pattern.compile("^plant\\s+pot\\s+at\\s+" + LOCATION + "$");
    private static final Pattern COLLECT = Pattern.compile("^collect\\s+" + LOCATION + "$");
    private static final Pattern GROW = Pattern.compile("^grow\\s+" + LOCATION + "$");

    public GreenhouseMenuController(AppContext context, ConsoleView view) {
        super(context, view);
    }

    @Override
    public MenuType type() {
        return MenuType.GREENHOUSE;
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
        if (input.equals("show greenhouse")) {
            context.getGreenhouseService().describe(context.getCurrentUser()).forEach(view::info);
        } else if (input.equals("enter shop")) {
            context.setCurrentMenu(MenuType.SHOP);
            view.info("You entered the shop.");
        } else if ((matcher = PLANT_POT.matcher(input)).matches()) {
            view.info(context.getGreenhouseService().plantPot(context.getCurrentUser(),
                    Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
        } else if ((matcher = COLLECT.matcher(input)).matches()) {
            view.info(context.getGreenhouseService().collect(context.getCurrentUser(),
                    Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
        } else if ((matcher = GROW.matcher(input)).matches()) {
            view.info(context.getGreenhouseService().grow(context.getCurrentUser(),
                    Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
        } else {
            view.unknownCommand();
        }
    }
}
