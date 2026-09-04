package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.model.User;
import ir.sharif.pvz.view.fx.Assets;
import ir.sharif.pvz.view.fx.GameUi;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * The bar every menu shares: a way back, the page title, and the player's
 * coins and diamonds, which the project document asks to show in all menus.
 */
public final class Chrome {

    private static final double CURRENCY_ICON = 26;

    private Chrome() {
    }

    /**
     * A title bar whose back button leaves the current menu the way the
     * controllers define it.
     */
    public static HBox bar(GameUi ui, String title) {
        return bar(ui, title, ui::exitMenu);
    }

    /**
     * A title bar with a custom back action, for pages that are not menus of
     * their own (the adventure map, the plant picker).
     */
    public static HBox bar(GameUi ui, String title, Runnable onBack) {
        Button back = new Button("←");
        back.getStyleClass().add("back-button");
        back.setOnAction(event -> onBack.run());

        Label heading = new Label(title);
        heading.getStyleClass().add("screen-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(16, back, heading, spacer, wallet(ui, ui.user()));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(14, 22, 14, 22));
        bar.getStyleClass().add("chrome-bar");
        return bar;
    }

    /**
     * The coin and diamond readout.
     */
    public static HBox wallet(User user) {
        return wallet(null, user);
    }

    /**
     * The same readout, but each side can be clicked to top itself up. The
     * document lists adding coins and diamonds among the things every menu
     * shares, and the wallet is the one part of the chrome that is in all of
     * them.
     *
     * @param ui the game, or null for a readout that cannot be clicked
     */
    public static HBox wallet(GameUi ui, User user) {
        HBox wallet = new HBox(18);
        wallet.setAlignment(Pos.CENTER_RIGHT);
        wallet.getStyleClass().add("wallet");
        if (user == null) {
            return wallet;
        }
        wallet.getChildren().addAll(
                currency(ui, "coin", user.getCoins(), "cheat add -n 500 coins"),
                currency(ui, "gem", user.getDiamonds(), "cheat add -n 10 diamonds"));
        return wallet;
    }

    private static HBox currency(GameUi ui, String icon, int amount, String topUp) {
        Label value = new Label(String.valueOf(amount));
        value.getStyleClass().add("wallet-value");
        HBox box = new HBox(6, Assets.view(Assets.ui(icon), CURRENCY_ICON), value);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("wallet-item");
        if (ui != null) {
            box.setOnMouseClicked(event -> ui.submit(topUp));
        }
        return box;
    }
}
