package ir.sharif.pvz.controller;

import ir.sharif.pvz.view.ConsoleView;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shop, reachable from inside the greenhouse: permanent items and the
 * once-a-day discounted seed-packet offer.
 */
public class ShopMenuController extends MenuController {

    private static final Pattern BUY = Pattern.compile(
            "^shop\\s+buy\\s+-i\\s+(\\S+)\\s+-n\\s+(\\d+)(?:\\s+-t\\s+(\\S+))?$");

    public ShopMenuController(AppContext context, ConsoleView view) {
        super(context, view);
    }

    @Override
    public MenuType type() {
        return MenuType.SHOP;
    }

    @Override
    protected Set<MenuType> allowedTargets() {
        return Set.of(MenuType.GREENHOUSE);
    }

    @Override
    protected void onExit() {
        context.setCurrentMenu(MenuType.GREENHOUSE);
        view.info("You are back in the greenhouse.");
    }

    @Override
    protected void handleCommand(String input) {
        Matcher matcher;
        if (input.equals("shop list")) {
            context.getShopService().listItems().forEach(view::info);
        } else if (input.equals("shop daily")) {
            context.getShopService().describeDaily(context.getCurrentUser()).forEach(view::info);
        } else if ((matcher = BUY.matcher(input)).matches()) {
            view.info(context.getShopService().buy(context.getCurrentUser(),
                    matcher.group(1), Integer.parseInt(matcher.group(2)), matcher.group(3)));
        } else {
            view.unknownCommand();
        }
    }
}
