package ir.sharif.pvz.view.fx;

import ir.sharif.pvz.controller.GameApp;
import ir.sharif.pvz.controller.MenuType;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.view.fx.screen.BattleScreen;
import ir.sharif.pvz.view.fx.screen.CollectionScreen;
import ir.sharif.pvz.view.fx.screen.GreenhouseScreen;
import ir.sharif.pvz.view.fx.screen.LeaderboardScreen;
import ir.sharif.pvz.view.fx.screen.LoginScreen;
import ir.sharif.pvz.view.fx.screen.MainMenuScreen;
import ir.sharif.pvz.view.fx.screen.MinigameScreen;
import ir.sharif.pvz.view.fx.screen.NewsScreen;
import ir.sharif.pvz.view.fx.screen.PlantPickerScreen;
import ir.sharif.pvz.view.fx.screen.ProfileScreen;
import ir.sharif.pvz.view.fx.screen.Screen;
import ir.sharif.pvz.view.fx.screen.SettingsScreen;
import ir.sharif.pvz.view.fx.screen.ShopScreen;
import ir.sharif.pvz.view.fx.screen.SignupScreen;
import ir.sharif.pvz.view.fx.screen.TravelLogScreen;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

/**
 * Owns the window and decides which screen is on it.
 *
 * <p>Navigation deliberately goes through the controllers' own
 * {@code menu enter} / {@code menu exit} commands rather than swapping screens
 * directly, so the access rules written in phase 1 still decide where the
 * player may go — the graphical view only draws the result.
 */
public final class GameUi {

    public static final double WIDTH = 1280;
    public static final double HEIGHT = 800;

    private static final String MODAL_ID = "modal-layer";

    private final GameApp app;
    private final FxView view;
    private final StackPane root = new StackPane();

    private Screen current;

    public GameUi(GameApp app, FxView view) {
        this.app = app;
        this.view = view;
        root.getStyleClass().add("app-root");
        listenForInvites();
    }

    /**
     * Watches for the two things the server can spring on a player at any
     * moment: a challenge from somebody, and a match actually starting.
     */
    private void listenForInvites() {
        if (!app.isOnline()) {
            return;
        }
        var link = app.connection();
        link.on(ir.sharif.pvz.net.Protocol.INVITED, message ->
                javafx.application.Platform.runLater(() -> askAboutInvite(message)));
        link.on(ir.sharif.pvz.net.Protocol.MATCH_FOUND, message ->
                javafx.application.Platform.runLater(() -> startMatch(message)));
        link.on(ir.sharif.pvz.net.Protocol.INVITE_DECLINED, message ->
                javafx.application.Platform.runLater(() ->
                        view.info(message.text("from") + " turned your challenge down.")));
    }

    /**
     * The pop-up the document asks for, so the invited player can accept or
     * refuse before being pulled into a game.
     */
    private void askAboutInvite(ir.sharif.pvz.net.Message message) {
        String from = message.text("from");
        javafx.scene.control.Button accept = new javafx.scene.control.Button("Accept");
        accept.getStyleClass().add("primary-button");
        accept.setOnAction(event -> answerInvite(true));
        javafx.scene.control.Button decline = new javafx.scene.control.Button("No thanks");
        decline.getStyleClass().add("ghost-button");
        decline.setOnAction(event -> answerInvite(false));

        javafx.scene.layout.HBox buttons =
                new javafx.scene.layout.HBox(12, accept, decline);
        buttons.setAlignment(javafx.geometry.Pos.CENTER);
        showModal(ir.sharif.pvz.view.fx.screen.Dialogs.panel(
                from + " wants to play",
                "They are challenging you to I, Zombie.", buttons));
    }

    private void answerInvite(boolean accepted) {
        closeModal();
        var link = app.connection();
        try {
            link.ask(link.request(ir.sharif.pvz.net.Protocol.INVITE_ANSWER)
                    .with("accepted", accepted));
        } catch (ir.sharif.pvz.net.client.ServerException e) {
            view.error(e.getMessage());
        }
    }

    /**
     * Drops straight into the versus screen once the server pairs two players.
     */
    private void startMatch(ir.sharif.pvz.net.Message message) {
        closeModal();
        show(new ir.sharif.pvz.view.fx.screen.VersusScreen(this,
                message.text("role"), message.text("opponent")));
    }

    public GameApp app() {
        return app;
    }

    public FxView view() {
        return view;
    }

    public Parent root() {
        return root;
    }

    public User user() {
        return app.getContext().getCurrentUser();
    }

    /**
     * Sends a command to the controller of the current menu, exactly as the
     * console front-end would, then redraws whatever screen we ended up on.
     */
    public void submit(String command) {
        app.submit(command);
        refresh();
    }

    /**
     * Sends a command without redrawing; for the in-game loop, which redraws
     * itself every frame anyway.
     */
    public void submitQuietly(String command) {
        app.submit(command);
    }

    /**
     * Asks the controllers to move to another menu; if they refuse the error
     * surfaces as a toast and the screen stays put.
     */
    public void enter(MenuType target) {
        view.runQuietly(() -> app.submit("menu enter " + target.id()));
        refresh();
    }

    public void exitMenu() {
        view.runQuietly(() -> app.submit("menu exit"));
        refresh();
    }

    /**
     * Rebuilds the screen for the menu the controllers say we are in.
     */
    public void refresh() {
        show(screenFor(app.getContext().getCurrentMenu()));
    }

    /**
     * Puts a screen on the window, keeping the toast overlay on top.
     */
    public void show(Screen screen) {
        if (current != null && current != screen) {
            current.dispose();
        }
        current = screen;
        // the battle screen picks its own chapter theme once it knows the level
        if (!(screen instanceof BattleScreen)) {
            Music.menu();
        }
        root.getChildren().setAll(screen.build(), view.toast().node());
    }

    public Screen current() {
        return current;
    }

    /**
     * Floats a panel over the current screen and dims everything behind it.
     * Used for purchase confirmations, the pause menu and the level briefing.
     */
    public void showModal(javafx.scene.Node panel) {
        StackPane veil = new StackPane(panel);
        veil.getStyleClass().add("modal-veil");
        veil.setId(MODAL_ID);
        root.getChildren().add(root.getChildren().size() - 1, veil);
    }

    /**
     * Removes the topmost floating panel, if one is open.
     */
    public void closeModal() {
        for (int i = root.getChildren().size() - 1; i >= 0; i--) {
            if (MODAL_ID.equals(root.getChildren().get(i).getId())) {
                root.getChildren().remove(i);
                return;
            }
        }
    }

    public boolean hasModal() {
        return root.getChildren().stream().anyMatch(node -> MODAL_ID.equals(node.getId()));
    }

    /**
     * The game menu has two faces: choosing plants, then the lawn itself.
     */
    private Screen gameScreen(MenuType menu) {
        var controller = (ir.sharif.pvz.controller.GameMenuController) app.currentController();
        return controller.getSession() == null
                ? new PlantPickerScreen(this, menu)
                : new BattleScreen(this, menu);
    }

    /**
     * The minigame menu likewise switches between its list and a running stage.
     */
    private Screen minigameScreen() {
        var controller = (ir.sharif.pvz.controller.GameMenuController) app.currentController();
        return controller.getSession() == null
                ? new MinigameScreen(this)
                : new BattleScreen(this, MenuType.MINIGAME);
    }

    private Screen screenFor(MenuType menu) {
        return switch (menu) {
            case SIGNUP -> new SignupScreen(this);
            case LOGIN -> new LoginScreen(this);
            case MAIN -> new MainMenuScreen(this);
            case GAME, SCORE_GAME -> gameScreen(menu);
            case SETTINGS -> new SettingsScreen(this);
            case NEWS -> new NewsScreen(this);
            case PROFILE -> new ProfileScreen(this);
            case COLLECTION -> new CollectionScreen(this);
            case GREENHOUSE -> new GreenhouseScreen(this);
            case SHOP -> new ShopScreen(this);
            case LEADERBOARD -> new LeaderboardScreen(this);
            case TRAVEL_LOG -> new TravelLogScreen(this);
            case MINIGAME -> minigameScreen();
        };
    }
}
