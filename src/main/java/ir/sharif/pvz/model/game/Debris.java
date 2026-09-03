package ir.sharif.pvz.model.game;

/**
 * A piece that came off a zombie and is on its way to the ground: its head, an
 * arm, or the armour a shot finally knocked loose.
 *
 * <p>The flight is worked out here rather than in the view so that two players
 * watching the same networked board see the same bits land in the same places,
 * and so a test can say where one ends up.
 */
public class Debris {

    /** What came off, which is all the view needs to pick a sprite. */
    public enum Kind { HEAD, ARM, ARMOUR }

    /** How hard the ground pulls, in lane-heights per second squared. */
    private static final double GRAVITY = 9;

    /** How long a piece lies on the ground before it fades away. */
    private static final double REST_SECONDS = 2.5;

    private final Kind kind;
    private final String art;
    private final int row;
    private double col;
    private double lift;
    private double speedAcross;
    private double speedUp;
    private double spin;
    private final double spinRate;
    private double restingFor = -1;

    Debris(Kind kind, String art, int row, double col, double speedAcross, double speedUp,
           double spinRate) {
        this.kind = kind;
        this.art = art;
        this.row = row;
        this.col = col;
        this.speedAcross = speedAcross;
        this.speedUp = speedUp;
        this.spinRate = spinRate;
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * The sprite under {@code assets/parts}, e.g. "head" or "cone".
     */
    public String getArt() {
        return art;
    }

    public int getRow() {
        return row;
    }

    /**
     * Where it is across the lawn, as a continuous 1-based column.
     */
    public double getCol() {
        return col;
    }

    /**
     * How far above the lane it is, in lane heights; zero once it has landed.
     */
    public double getLift() {
        return lift;
    }

    /**
     * How far it has turned, in radians.
     */
    public double getSpin() {
        return spin;
    }

    /**
     * How solid to draw it: it lies on the ground a while and then fades.
     */
    public double getOpacity() {
        if (restingFor < 0) {
            return 1;
        }
        return Math.max(0, 1 - restingFor / REST_SECONDS);
    }

    public boolean isGone() {
        return restingFor >= REST_SECONDS;
    }

    /**
     * Moves it along; it tumbles until it hits the ground, then lies there.
     */
    public void passSeconds(double seconds) {
        if (restingFor >= 0) {
            restingFor += seconds;
            return;
        }
        col += speedAcross * seconds;
        lift += speedUp * seconds;
        speedUp -= GRAVITY * seconds;
        spin += spinRate * seconds;
        if (lift <= 0) {
            lift = 0;
            restingFor = 0;
            speedAcross = 0;
            speedUp = 0;
        }
    }
}
