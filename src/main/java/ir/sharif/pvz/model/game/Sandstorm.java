package ir.sharif.pvz.model.game;

/**
 * The sandstorm that sweeps across an Ancient Egypt lawn.
 *
 * <p>It is weather rather than a hazard: the document asks for it to be shown,
 * not for it to do anything to the plants, so nothing here touches the board.
 * Where it is at any moment falls straight out of the clock, so two clients
 * watching the same level see the same storm without anything being sent
 * between them, and a test can ask where it will be without running a level.
 */
public final class Sandstorm {

    /** How long between one storm rolling in and the next. */
    public static final double PERIOD_SECONDS = 22;

    /** How long one storm takes to cross the lawn. */
    public static final double CROSSING_SECONDS = 6;

    /** How far off each edge it starts and finishes, in columns. */
    private static final double MARGIN_COLUMNS = 3;

    private final boolean enabled;

    Sandstorm(Chapter chapter) {
        this.enabled = chapter == Chapter.ANCIENT_EGYPT;
    }

    /**
     * Whether this chapter gets sandstorms at all.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether a storm is crossing the lawn at this moment.
     */
    public boolean isBlowing(double seconds) {
        return enabled && phase(seconds) < CROSSING_SECONDS;
    }

    /**
     * Where the storm has reached, as a 1-based column. It starts off the right
     * edge and finishes off the left one, so the value runs outside the board
     * at both ends.
     */
    public double columnAt(double seconds) {
        double travelled = phase(seconds) / CROSSING_SECONDS;
        double from = GameSession.COLS + MARGIN_COLUMNS;
        double to = 1 - MARGIN_COLUMNS;
        return from + (to - from) * travelled;
    }

    /**
     * How solid to draw it: it builds as it arrives and thins out as it leaves,
     * so it never pops in or out at the edge of the lawn.
     */
    public double intensityAt(double seconds) {
        if (!isBlowing(seconds)) {
            return 0;
        }
        double travelled = phase(seconds) / CROSSING_SECONDS;
        return Math.sin(travelled * Math.PI);
    }

    private double phase(double seconds) {
        double phase = seconds % PERIOD_SECONDS;
        return phase < 0 ? phase + PERIOD_SECONDS : phase;
    }
}
