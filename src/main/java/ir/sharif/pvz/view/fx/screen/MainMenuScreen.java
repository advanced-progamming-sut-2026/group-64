package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.controller.MenuType;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.view.fx.GameUi;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The hub. Every other menu is one click away, and the news button carries the
 * unread counter the project document asks for.
 */
public final class MainMenuScreen extends Screen {

    public MainMenuScreen(GameUi ui) {
        super(ui);
    }

    @Override
    public Parent build() {
        User user = ui.user();

        Label brand = new Label("Plants vs. Zombies 2");
        brand.getStyleClass().add("brand-title");
        Label greeting = new Label("Hello, " + (user == null ? "player" : user.getNickname()) + "!");
        greeting.getStyleClass().add("brand-subtitle");

        Button play = new Button("PLAY");
        play.getStyleClass().add("play-button");
        play.setOnAction(event -> ui.show(new AdventureScreen(ui)));

        FlowPane tiles = new FlowPane(16, 16,
                tile("Collection", "🌻", () -> ui.enter(MenuType.COLLECTION)),
                tile("Greenhouse", "🪴", () -> ui.enter(MenuType.GREENHOUSE)),
                tile("Shop", "🛒", () -> ui.enter(MenuType.SHOP)),
                tile("Quests", "📜", () -> ui.enter(MenuType.TRAVEL_LOG)),
                tile("Daily challenge", "🌟", () -> ui.enter(MenuType.SCORE_GAME)),
                tile("Leaderboard", "🏆", () -> ui.enter(MenuType.LEADERBOARD)),
                versusTile(),
                tile("Profile", "👤", () -> ui.enter(MenuType.PROFILE)),
                tile("Settings", "🔧", () -> ui.enter(MenuType.SETTINGS)),
                newsTile(user));
        tiles.setAlignment(Pos.CENTER);
        tiles.setMaxWidth(880);

        Button logout = new Button("Log out");
        logout.getStyleClass().add("link-button");
        logout.setOnAction(event -> ui.submit("menu logout"));

        VBox column = new VBox(22, brand, greeting, play, tiles, logout);
        column.setAlignment(Pos.CENTER);
        column.setPadding(new Insets(30));

        BorderPane layout = new BorderPane(column);
        layout.setTop(topBar(user));
        layout.getStyleClass().addAll("screen", "main-screen");
        return layout;
    }

    private HBox topBar(User user) {
        HBox bar = new HBox(Chrome.wallet(user));
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(16, 24, 0, 24));
        return bar;
    }

    /**
     * The news tile, stamped with how many items the player has not read.
     */
    private StackPane newsTile(User user) {
        long unread = unreadCount(user);
        StackPane tile = tile("News", "📰", () -> ui.enter(MenuType.NEWS));
        if (unread > 0) {
            Label badge = new Label(String.valueOf(unread));
            badge.getStyleClass().add("news-badge");
            StackPane.setAlignment(badge, Pos.TOP_RIGHT);
            tile.getChildren().add(badge);
        }
        return tile;
    }

    /**
     * The way into a two-player game, which needs a server to be there.
     */
    private StackPane versusTile() {
        // the lobby is reachable offline too, because couch play lives there
        return tile("Versus", "⚔", () -> ui.show(new VersusLobbyScreen(ui)));
    }

    private StackPane tile(String caption, String glyph, Runnable action) {
        Label icon = new Label(glyph);
        icon.getStyleClass().add("tile-icon");
        Label label = new Label(caption);
        label.getStyleClass().add("tile-label");
        VBox content = new VBox(6, icon, label);
        content.setAlignment(Pos.CENTER);

        Button button = new Button();
        button.setGraphic(content);
        button.getStyleClass().add("menu-tile");
        button.setOnAction(event -> action.run());

        StackPane holder = new StackPane(button);
        holder.setPickOnBounds(false);
        return holder;
    }

    /**
     * How many news items the player has not opened yet.
     */
    public static long unreadCount(User user) {
        if (user == null) {
            return 0;
        }
        return user.getNews().stream().filter(item -> !item.isRead()).count();
    }
}
