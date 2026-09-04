package ir.sharif.pvz.view.fx;

import ir.sharif.pvz.model.NewsItem;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.Zombie;
import ir.sharif.pvz.view.GameView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The graphical counterpart of {@link ir.sharif.pvz.view.ConsoleView}.
 *
 * <p>The controllers are unchanged: they still narrate what happened by calling
 * {@code info} and {@code error}. Here those lines become temporary on-screen
 * notifications instead of console output. The screens themselves read the
 * model directly rather than parsing text, so the report-style methods that the
 * console needs have nothing to do.
 */
public final class FxView implements GameView {

    private final Toast toast = new Toast();
    private final List<String> captured = new ArrayList<>();

    private boolean capturing;
    private boolean suppressInfo;
    private Consumer<String> errorListener;

    public Toast toast() {
        return toast;
    }

    /**
     * Runs an action while collecting every line the controllers emit instead of
     * showing it, and returns the collected lines. Screens use this for the few
     * flows where the controller's own wording is worth showing verbatim.
     */
    public List<String> capture(Runnable action) {
        capturing = true;
        captured.clear();
        try {
            action.run();
        } finally {
            capturing = false;
        }
        return List.copyOf(captured);
    }

    /**
     * Runs an action without showing its routine narration. Errors still
     * surface, so a refused navigation is still reported to the player.
     *
     * <p>Used for menu changes, where the controller's "You are back in the
     * main menu" only repeats what the player can already see.
     */
    public void runQuietly(Runnable action) {
        suppressInfo = true;
        try {
            action.run();
        } finally {
            suppressInfo = false;
        }
    }

    /**
     * Registers a callback fired whenever a controller reports an error, so a
     * screen can react (keep a dialog open, highlight a field) beyond the toast.
     */
    public void onError(Consumer<String> listener) {
        this.errorListener = listener;
    }

    @Override
    public void info(String message) {
        if (capturing) {
            captured.add(message);
            return;
        }
        if (suppressInfo) {
            return;
        }
        toast.info(message);
    }

    @Override
    public void error(String message) {
        if (errorListener != null) {
            errorListener.accept(message);
        }
        if (capturing) {
            captured.add(message);
            return;
        }
        toast.error(message);
    }

    // The graphical menus render all of the state below themselves, straight
    // from the model, so these console-shaped reports are never requested.

    @Override
    public void showCurrentMenu(String menuName) {
        // the current screen is self-evident in a windowed UI
    }

    @Override
    public void showSecurityQuestions(List<String> questions) {
        // the signup screen shows the questions in a combo box
    }

    @Override
    public void showUserInfo(User user) {
        // the profile screen renders these fields as labels
    }

    @Override
    public void showNews(List<NewsItem> items, String emptyMessage) {
        // the news screen renders the list itself
    }

    @Override
    public void showMap(GameSession session, boolean grid) {
        // the battle screen redraws the lawn every frame
    }

    @Override
    public void showPlantsStatus(GameSession session) {
        // the seed bar shows cost and cooldown on each card
    }

    @Override
    public void showTileStatus(GameSession session, int x, int y) {
        // hovering a tile shows the same information in place
    }

    @Override
    public void showZombiesInfo(List<Zombie> zombies) {
        // zombies are visible on the lawn with their health bars
    }
}
