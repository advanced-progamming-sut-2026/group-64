package ir.sharif.pvz.controller;

import ir.sharif.pvz.model.AuthService;
import ir.sharif.pvz.model.ProfileService;
import ir.sharif.pvz.model.SessionStore;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.UserRepository;
import ir.sharif.pvz.model.net.RemoteAuthService;
import ir.sharif.pvz.model.net.RemoteUserRepository;
import ir.sharif.pvz.net.client.ServerConnection;
import ir.sharif.pvz.view.ConsoleView;
import ir.sharif.pvz.view.GameView;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Wires the application together. The console front-end drives it with
 * {@link #run()}; the JavaFX front-end drives the very same controllers by
 * feeding them one command at a time through {@link #submit(String)}.
 */
public final class GameApp {

    private final AppContext context;
    private final GameView view;
    private final ServerConnection connection;
    private final Map<MenuType, MenuController> controllers = new EnumMap<>(MenuType.class);

    public GameApp() {
        this(new ConsoleView());
    }

    /**
     * The offline build: accounts live in a file next to the game.
     */
    public GameApp(GameView view) {
        this(view, null);
    }

    /**
     * @param connection the link to the game server, or null to play offline
     *                   against the accounts stored on this machine
     */
    public GameApp(GameView view, ServerConnection connection) {
        this.view = view;
        this.connection = connection;
        UserRepository userRepository = connection == null
                ? new UserRepository()
                : new RemoteUserRepository(connection);
        SessionStore sessionStore = new SessionStore();
        AuthService authService = connection == null
                ? new AuthService(userRepository)
                : new RemoteAuthService(connection, (RemoteUserRepository) userRepository);
        ProfileService profileService = new ProfileService(userRepository);
        this.context = new AppContext(userRepository, sessionStore, authService, profileService);
        registerControllers();
        restoreSession();
    }

    /**
     * The server link, or null when playing offline.
     */
    public ServerConnection connection() {
        return connection;
    }

    public boolean isOnline() {
        return connection != null && connection.isOpen();
    }

    private void registerControllers() {
        register(new SignupMenuController(context, view));
        register(new LoginMenuController(context, view));
        register(new MainMenuController(context, view));
        register(new GameMenuController(context, view));
        register(new SettingsMenuController(context, view));
        register(new NewsMenuController(context, view));
        register(new ProfileMenuController(context, view));
        register(new CollectionMenuController(context, view));
        register(new GreenhouseMenuController(context, view));
        register(new ShopMenuController(context, view));
        register(scoreGameController());
        register(new LeaderboardMenuController(context, view));
        register(new TravelLogMenuController(context, view));
        register(new MinigameMenuController(context, view));
    }

    /**
     * The daily score game. When there is a server, its result is posted so the
     * leaderboard's My Point column can show it.
     */
    private GameMenuController scoreGameController() {
        GameMenuController controller =
                new GameMenuController(context, view, MenuType.SCORE_GAME, true);
        if (connection != null) {
            controller.setScoreReporter(points -> {
                try {
                    connection.ask(connection.request(ir.sharif.pvz.net.Protocol.SUBMIT_SCORE)
                            .with("points", points));
                } catch (ir.sharif.pvz.net.client.ServerException e) {
                    view.error("Could not post your score: " + e.getMessage());
                }
            });
        }
        return controller;
    }

    private void register(MenuController controller) {
        controllers.put(controller.type(), controller);
    }

    /**
     * If a user logged in with stay-logged-in, skip signup/login and go straight to the main menu.
     */
    private void restoreSession() {
        if (connection != null) {
            // the server decides who is signed in, so a remembered name on this
            // machine is not enough to skip the login screen
            return;
        }
        String savedUsername = context.getSessionStore().load();
        if (savedUsername == null) {
            return;
        }
        User user = context.getUserRepository().findByUsername(savedUsername);
        if (user != null) {
            context.setCurrentUser(user);
            context.setCurrentMenu(MenuType.MAIN);
        }
    }

    /**
     * Runs one command through the controller of the menu the player is in.
     */
    public void submit(String command) {
        controllers.get(context.getCurrentMenu()).handle(command);
    }

    /**
     * The controller currently in charge, so a graphical view can read the
     * live state it needs to draw (for example the running game session).
     */
    public MenuController currentController() {
        return controllers.get(context.getCurrentMenu());
    }

    public AppContext getContext() {
        return context;
    }

    public void greet() {
        view.info("Plants vs Zombies 2");
        if (context.getCurrentUser() != null) {
            view.info("Welcome back, " + context.getCurrentUser().getNickname() + "! You are in the main menu.");
        }
    }

    public void save() {
        context.getUserRepository().save();
    }

    public void run() {
        greet();
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (context.isRunning() && scanner.hasNextLine()) {
                submit(scanner.nextLine());
            }
        }
        save();
    }
}
