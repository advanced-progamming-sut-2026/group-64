package ir.sharif.pvz.controller;

/**
 * All menus the player can navigate between, with their command-line names.
 */
public enum MenuType {
    SIGNUP("signup"),
    LOGIN("login"),
    MAIN("main"),
    GAME("game"),
    SETTINGS("settings"),
    NEWS("news"),
    PROFILE("profile"),
    COLLECTION("collection"),
    GREENHOUSE("greenhouse"),
    SHOP("shop");

    private final String id;

    MenuType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /**
     * Resolves a menu name typed by the user, or returns null if unknown.
     */
    public static MenuType fromId(String id) {
        for (MenuType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
