package ir.sharif.pvz.model.game;

/**
 * The four adventure chapters, each with its own environment and zombies.
 */
public enum Chapter {
    ANCIENT_EGYPT("Ancient Egypt"),
    FROSTBITE_CAVES("Frostbite Caves"),
    BIG_WAVE_BEACH("Big Wave Beach"),
    DARK_AGES("Dark Ages");

    private final String displayName;

    Chapter(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * The command-line name of this chapter, e.g. "ancient-egypt".
     */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    /**
     * Resolves a chapter name typed by the player, or null when it is unknown.
     * Both the full id and its first word are accepted ("egypt", "beach").
     */
    public static Chapter fromId(String id) {
        for (Chapter chapter : values()) {
            if (chapter.id().equalsIgnoreCase(id)) {
                return chapter;
            }
        }
        for (Chapter chapter : values()) {
            for (String part : chapter.id().split("-")) {
                if (part.equalsIgnoreCase(id)) {
                    return chapter;
                }
            }
        }
        return null;
    }
}
