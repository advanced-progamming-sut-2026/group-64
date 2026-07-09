package ir.sharif.pvz.controller;

import ir.sharif.pvz.model.AuthService;
import ir.sharif.pvz.model.SessionStore;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.UserRepository;

/**
 * Mutable application state shared between menu controllers.
 */
public class AppContext {

    private final UserRepository userRepository;
    private final SessionStore sessionStore;
    private final AuthService authService;

    private User currentUser;
    private MenuType currentMenu = MenuType.SIGNUP;
    private boolean running = true;

    public AppContext(UserRepository userRepository, SessionStore sessionStore, AuthService authService) {
        this.userRepository = userRepository;
        this.sessionStore = sessionStore;
        this.authService = authService;
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
