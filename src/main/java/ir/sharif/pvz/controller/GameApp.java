package ir.sharif.pvz.controller;

import ir.sharif.pvz.model.AuthService;
import ir.sharif.pvz.model.ProfileService;
import ir.sharif.pvz.model.SessionStore;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.UserRepository;
import ir.sharif.pvz.view.ConsoleView;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Wires the application together and runs the read-eval-print loop.
 */
public final class GameApp {

    private final AppContext context;
    private final ConsoleView view;
    private final Map<MenuType, MenuController> controllers = new EnumMap<>(MenuType.class);

    public GameApp() {
        this.view = new ConsoleView();
        UserRepository userRepository = new UserRepository();
        SessionStore sessionStore = new SessionStore();
        AuthService authService = new AuthService(userRepository);
        ProfileService profileService = new ProfileService(userRepository);
        this.context = new AppContext(userRepository, sessionStore, authService, profileService);
        registerControllers();
        restoreSession();
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
        register(new GameMenuController(context, view, MenuType.SCORE_GAME, true));
        register(new LeaderboardMenuController(context, view));
    }

    private void register(MenuController controller) {
        controllers.put(controller.type(), controller);
    }

    /**
     * If a user logged in with stay-logged-in, skip signup/login and go straight to the main menu.
     */
    private void restoreSession() {
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

    public void run() {
        view.info("Plants vs Zombies 2 - CLI");
        if (context.getCurrentUser() != null) {
            view.info("Welcome back, " + context.getCurrentUser().getNickname() + "! You are in the main menu.");
        }
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (context.isRunning() && scanner.hasNextLine()) {
                controllers.get(context.getCurrentMenu()).handle(scanner.nextLine());
            }
        }
        context.getUserRepository().save();
    }
}
