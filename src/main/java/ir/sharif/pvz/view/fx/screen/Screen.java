package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.view.fx.GameUi;
import javafx.scene.Parent;

/**
 * One full-window page of the graphical interface.
 *
 * <p>Screens are cheap and rebuilt on every navigation, so they always reflect
 * the current model state instead of holding their own copy of it.
 */
public abstract class Screen {

    protected final GameUi ui;

    protected Screen(GameUi ui) {
        this.ui = ui;
    }

    /**
     * Builds the node tree for this page.
     */
    public abstract Parent build();

    /**
     * Called when this screen is replaced. Screens that start an animation
     * timer or listen to the server override it to let go of both; without
     * that a loop keeps running against state that has moved on.
     */
    public void dispose() {
    }
}
