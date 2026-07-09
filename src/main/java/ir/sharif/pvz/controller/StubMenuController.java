package ir.sharif.pvz.controller;

import ir.sharif.pvz.view.ConsoleView;
import java.util.Set;

/**
 * Placeholder for menus whose commands will be implemented in later steps
 * (game, settings, news, profile, collection). Navigation already works.
 */
public class StubMenuController extends MenuController {

    private final MenuType type;
    private final MenuType parent;
    private final Set<MenuType> targets;

    public StubMenuController(AppContext context, ConsoleView view,
                              MenuType type, MenuType parent, Set<MenuType> targets) {
        super(context, view);
        this.type = type;
        this.parent = parent;
        this.targets = targets;
    }

    @Override
    public MenuType type() {
        return type;
    }

    @Override
    protected Set<MenuType> allowedTargets() {
        return targets;
    }

    @Override
    protected void onExit() {
        context.setCurrentMenu(parent);
        view.info("You are back in the " + parent.id() + " menu.");
    }

    @Override
    protected void handleCommand(String input) {
        view.error("This command is not implemented yet in the " + type.id() + " menu.");
    }
}
