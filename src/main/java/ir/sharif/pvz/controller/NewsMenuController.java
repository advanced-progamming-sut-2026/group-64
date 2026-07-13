package ir.sharif.pvz.controller;

import ir.sharif.pvz.model.NewsItem;
import ir.sharif.pvz.view.ConsoleView;
import java.util.List;
import java.util.Set;

/**
 * The news menu: unlock events and other in-game announcements for the user.
 */
public class NewsMenuController extends MenuController {

    public NewsMenuController(AppContext context, ConsoleView view) {
        super(context, view);
    }

    @Override
    public MenuType type() {
        return MenuType.NEWS;
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
        switch (input) {
            case "menu news show-unread" -> showUnread();
            case "menu news show-all" -> showAll();
            default -> view.unknownCommand();
        }
    }

    /**
     * Shows unread news once: everything shown here is marked read afterwards.
     */
    private void showUnread() {
        List<NewsItem> unread = context.getCurrentUser().getNews().stream()
                .filter(item -> !item.isRead())
                .toList();
        view.showNews(unread, "You have no unread news.");
        unread.forEach(NewsItem::markRead);
        if (!unread.isEmpty()) {
            context.getUserRepository().save();
        }
    }

    private void showAll() {
        view.showNews(context.getCurrentUser().getNews(), "There is no news yet.");
    }
}
