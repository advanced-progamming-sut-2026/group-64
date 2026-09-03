package ir.sharif.pvz.model.game;

/**
 * A lawn mower rolling down its lane after a zombie got past everything else.
 *
 * <p>It used to be instant: the lane was cleared the moment a zombie reached
 * the house and the mower itself never moved. The sheet asks to see it travel
 * and to see it take the zombies as it goes, so it now drives across the lawn
 * and kills whatever it catches up with.
 */
public class Mower {

    /** How fast it rolls, in columns per second. */
    private static final double SPEED = 3.5;

    private final int row;
    private double col = 0.2;

    Mower(int row) {
        this.row = row;
    }

    public int getRow() {
        return row;
    }

    /**
     * How far across the lawn it has got, as a continuous 1-based column.
     */
    public double getCol() {
        return col;
    }

    void advance(double seconds) {
        col += SPEED * seconds;
    }

    /**
     * True once it has driven off the far edge and is done.
     */
    public boolean isGone() {
        return col > GameSession.COLS + 1;
    }

    /**
     * Whether this zombie is under the blades right now.
     */
    boolean catches(Zombie zombie) {
        return zombie.getRow() == row && Math.abs(zombie.getX() - col) < 0.6;
    }
}
