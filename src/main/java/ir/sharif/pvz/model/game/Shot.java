package ir.sharif.pvz.model.game;

/**
 * One projectile in flight.
 *
 * <p>Phase 1 resolves damage the moment a plant fires, and that stays true: a
 * shot carries no damage of its own. It exists so the player can see what a
 * plant just did, which the phase 2 document asks for — straight shots travel
 * in a line, lobbed ones arc over whatever is in the way.
 */
public final class Shot {

    /** How the projectile travels, which decides how the view draws it. */
    public enum Flight { STRAIGHT, LOBBED }

    private static final double TILES_PER_SECOND = 9.0;
    private static final double LOB_SECONDS = 0.55;

    private final int row;
    private final double fromX;
    private final double toX;
    private final Flight flight;
    private final String kind;
    private final double duration;

    private double age;

    /**
     * The shot a plant would fire at a target in the given column.
     */
    static Shot from(Plant plant, double toX, Flight flight) {
        return new Shot(plant.getRow(), plant.getCol() + 1.4, toX, flight, kindOf(plant));
    }

    /**
     * What the plant throws — pea, ice, fire, a lobbed vegetable or a beam —
     * so the view can colour it without knowing anything about plants.
     */
    private static String kindOf(Plant plant) {
        PlantSpec spec = plant.getSpec();
        if (spec.getCategory() == PlantCategory.STRIKE_THROUGH) {
            return "laser";
        }
        if (spec.hasTag("ice")) {
            return "ice";
        }
        if (spec.hasTag("fire")) {
            return "fire";
        }
        if (spec.hasTag("aoe")) {
            // melons and the like splash on impact, so they read differently
            return "aoe";
        }
        return spec.getCategory() == PlantCategory.LOBBER ? "lob" : "pea";
    }

    private Shot(int row, double fromX, double toX, Flight flight, String kind) {
        this.row = row;
        this.fromX = fromX;
        this.toX = toX;
        this.flight = flight;
        this.kind = kind;
        this.duration = flight == Flight.LOBBED
                ? LOB_SECONDS
                : Math.max(0.08, Math.abs(toX - fromX) / TILES_PER_SECOND);
    }

    void passSeconds(double seconds) {
        age += seconds;
    }

    public int getRow() {
        return row;
    }

    public Flight getFlight() {
        return flight;
    }

    /**
     * What was fired — "pea", "ice", "fire", "lob" or "laser" — so the view can
     * pick a colour without knowing anything about plants.
     */
    public String getKind() {
        return kind;
    }

    /**
     * How far along its path the shot is, from 0 at the muzzle to 1 on impact.
     */
    public double progress() {
        return duration <= 0 ? 1 : Math.min(1, age / duration);
    }

    /**
     * The column the shot is over right now, in the same 1-based coordinates
     * the rest of the engine uses.
     */
    public double currentX() {
        return fromX + (toX - fromX) * progress();
    }

    public boolean isDone() {
        return progress() >= 1;
    }
}
