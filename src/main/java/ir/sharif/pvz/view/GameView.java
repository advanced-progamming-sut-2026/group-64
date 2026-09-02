package ir.sharif.pvz.view;

import ir.sharif.pvz.model.NewsItem;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.Zombie;
import java.util.List;

/**
 * Everything the controllers are allowed to say to the outside world.
 *
 * <p>Phase 1 had a single console implementation; phase 2 adds a JavaFX one.
 * Controllers depend on this interface only, so swapping the view never
 * touches the model or the controller logic.
 */
public interface GameView {

    void info(String message);

    void error(String message);

    void showCurrentMenu(String menuName);

    void showSecurityQuestions(List<String> questions);

    void showUserInfo(User user);

    void showNews(List<NewsItem> items, String emptyMessage);

    void showMap(GameSession session);

    void showPlantsStatus(GameSession session);

    void showTileStatus(GameSession session, int x, int y);

    void showZombiesInfo(List<Zombie> zombies);

    default void errors(List<String> messages) {
        for (String message : messages) {
            error(message);
        }
    }

    default void unknownCommand() {
        error("invalid command.");
    }
}
