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
}
