package ir.sharif.pvz.model.game;

/**
 * A sun on (or falling toward) a tile. Falling suns land after five seconds;
 * suns produced by plants are on the ground immediately.
 */
public class Sun {

    /** The three kinds of falling sun defined by the document. */
    public enum Kind { NORMAL, SPECIAL, RADIOACTIVE }

    private final Kind kind;
    private final int row;
    private final int col;
    private double secondsUntilLanding;

    public Sun(Kind kind, int row, int col, double secondsUntilLanding) {
        this.kind = kind;
        this.row = row;
        this.col = col;
        this.secondsUntilLanding = secondsUntilLanding;
    }

    public Kind getKind() {
        return kind;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public boolean isOnGround() {
        return secondsUntilLanding <= 0;
    }

    /**
     * Advances the fall; returns true at the moment the sun touches the ground.
     */
    public boolean passSeconds(double seconds) {
        if (secondsUntilLanding <= 0) {
            return false;
        }
        secondsUntilLanding -= seconds;
        return secondsUntilLanding <= 0;
    }

    public int value() {
        return kind == Kind.SPECIAL ? 100 : 25;
    }
}
