package ir.sharif.pvz.model.game;

/**
 * A plant in the middle of its plant food moment.
 *
 * <p>What the plant food actually does is applied the instant it is spent, the
 * same way damage is. This is what the view draws while it happens, and it
 * carries the plant's family because that is what decides the show: a sun
 * producer throws out sun, a shooter opens fire, a wall puts on armour.
 *
 * <p>Like {@link Burst} it carries no rules and ages out on its own.
 */
public final class PlantFoodShow {

    /** How long the show runs. Long enough to read, short enough not to nag. */
    private static final double LIFETIME_SECONDS = 1.4;

    private final String plant;
    private final PlantCategory family;
    private final int col;
    private final int row;

    private double age;

    PlantFoodShow(String plant, PlantCategory family, int col, int row) {
        this.plant = plant;
        this.family = family;
        this.col = col;
        this.row = row;
    }

    /**
     * The plant type, so the view can draw its own sprite swelling.
     */
    public String getPlant() {
        return plant;
    }

    /**
     * What kind of plant it is, which is what the show is built around.
     */
    public PlantCategory getFamily() {
        return family;
    }

    /** Column on the board, 1-based. */
    public int getCol() {
        return col;
    }

    /** Row on the board, 1-based. */
    public int getRow() {
        return row;
    }

    void passSeconds(double seconds) {
        age += seconds;
    }

    /**
     * How far through the show it is, 0 at the flash and 1 when it is over.
     */
    public double progress() {
        return Math.min(1, age / LIFETIME_SECONDS);
    }

    public boolean isDone() {
        return age >= LIFETIME_SECONDS;
    }
}
