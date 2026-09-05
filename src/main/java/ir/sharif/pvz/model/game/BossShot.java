package ir.sharif.pvz.model.game;

/**
 * Something Zomboss threw, on its way to where it lands.
 *
 * <p>The boss's attacks used to appear as an explosion on the target tile with
 * nothing in between, so a rocket, a fireball, an icy shot and a pack of sharks
 * all looked the same and none of them came from the boss. A shot carries what
 * kind it is and where it is along its flight; the damage is still dealt at the
 * moment it is fired, so this only decides what the player sees.
 */
public final class BossShot {

    /** What was thrown, which is the chapter's signature attack. */
    public enum Kind {
        /** Ancient Egypt: a rocket trailing smoke. */
        ROCKET,
        /** Dark Ages: a ball of fire. */
        FIREBALL,
        /** Frostbite Caves: a shard of ice. */
        ICE,
        /** Big Wave Beach: a pack of baby sharks. */
        SHARKS
    }

    /**
     * How long a shot takes to cross the lawn. The board only ticks ten times
     * a second, so a shorter flight moves the shot more than a tile per step
     * and reads as a jump rather than a throw.
     */
    private static final double FLIGHT_SECONDS = 1.4;
    /** How long the mark it leaves stays after it lands. */
    private static final double IMPACT_SECONDS = 0.7;

    private final Kind kind;
    private final double fromCol;
    private final double fromRow;
    private final double toCol;
    private final double toRow;

    private double age;

    BossShot(Kind kind, double fromCol, double fromRow, double toCol, double toRow) {
        this.kind = kind;
        this.fromCol = fromCol;
        this.fromRow = fromRow;
        this.toCol = toCol;
        this.toRow = toRow;
    }

    /** The shot a chapter's boss throws. */
    static Kind kindFor(Chapter chapter) {
        return switch (chapter) {
            case ANCIENT_EGYPT -> Kind.ROCKET;
            case DARK_AGES -> Kind.FIREBALL;
            case FROSTBITE_CAVES -> Kind.ICE;
            case BIG_WAVE_BEACH -> Kind.SHARKS;
        };
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * How far along the flight this shot is, 0 at the boss and 1 at the target.
     * It stays at 1 while the impact is still showing.
     */
    public double flight() {
        return Math.min(1, age / FLIGHT_SECONDS);
    }

    /** True once it has landed, so the view draws the impact instead. */
    public boolean hasLanded() {
        return age >= FLIGHT_SECONDS;
    }

    /** How far through the impact mark it is, 0 to 1. */
    public double impact() {
        if (!hasLanded()) {
            return 0;
        }
        return Math.min(1, (age - FLIGHT_SECONDS) / IMPACT_SECONDS);
    }

    /** Where it is now, in the board's 1-based column coordinate. */
    public double getCol() {
        return fromCol + (toCol - fromCol) * flight();
    }

    /** Where it is now, in the board's 1-based row coordinate. */
    public double getRow() {
        return fromRow + (toRow - fromRow) * flight();
    }

    public double getToCol() {
        return toCol;
    }

    public double getToRow() {
        return toRow;
    }

    /**
     * How high above the lawn it is arcing, as a fraction of a tile. A shot
     * lobs rather than travelling flat, which is what makes it read as thrown.
     */
    public double getLift() {
        double t = flight();
        return Math.sin(t * Math.PI) * 0.9;
    }

    /** Which way it is pointing, for the sprites that have a nose. */
    public double getAngle() {
        return Math.toDegrees(Math.atan2(toRow - fromRow, toCol - fromCol));
    }

    void passSeconds(double seconds) {
        age += seconds;
    }

    boolean isDone() {
        return age >= FLIGHT_SECONDS + IMPACT_SECONDS;
    }
}
