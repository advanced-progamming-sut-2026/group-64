package ir.sharif.pvz.controller;

import ir.sharif.pvz.model.AuthService;
import ir.sharif.pvz.model.GreenhouseService;
import ir.sharif.pvz.model.ProfileService;
import ir.sharif.pvz.model.SessionStore;
import ir.sharif.pvz.model.ShopService;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.UserRepository;
import java.util.Random;

/**
 * Mutable application state shared between menu controllers.
 */
public class AppContext {

    private final UserRepository userRepository;
    private final SessionStore sessionStore;
    private final AuthService authService;
    private final ProfileService profileService;
    private final GreenhouseService greenhouseService;
    private final ShopService shopService;
    private final ir.sharif.pvz.model.QuestService questService;

    private User currentUser;
    private MenuType currentMenu = MenuType.SIGNUP;
    private boolean running = true;

    public AppContext(UserRepository userRepository, SessionStore sessionStore,
                      AuthService authService, ProfileService profileService) {
        this.userRepository = userRepository;
        this.sessionStore = sessionStore;
        this.authService = authService;
        this.profileService = profileService;
        this.greenhouseService = new GreenhouseService(userRepository,
                System::currentTimeMillis, new Random());
        this.shopService = new ShopService(userRepository, System::currentTimeMillis, new Random());
        this.questService = new ir.sharif.pvz.model.QuestService(userRepository, System::currentTimeMillis);
    }

    public ir.sharif.pvz.model.QuestService getQuestService() {
        return questService;
    }

    public GreenhouseService getGreenhouseService() {
        return greenhouseService;
    }

    public ShopService getShopService() {
        return shopService;
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }

    public SessionStore getSessionStore() {
        return sessionStore;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public ProfileService getProfileService() {
        return profileService;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }

    public MenuType getCurrentMenu() {
        return currentMenu;
    }

    public void setCurrentMenu(MenuType currentMenu) {
        this.currentMenu = currentMenu;
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        this.running = false;
    }
}
