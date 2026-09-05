package ir.sharif.pvz.model.game;

/**
 * The wide attack washing across the rows the boss is facing.
 *
 * <p>It used to be one explosion dropped in the middle of each row, which said
 * nothing about the move being a wall of fire, a charge, a gale or a torpedo
 * wake. This is the front of that wall, travelling from the boss toward the
 * house; the plants are already gone by the time it is drawn, so it only
 * decides what the player sees.
 */
public final class BossSweep {

    /**
     * How long the front takes to cross the lawn — slow enough to be followed
     * at the ten ticks a second the board runs at.
     */
    private static final double SECONDS = 1.8;

    private final Chapter chapter;
    private final int topRow;
    private final int rows;

    private double age;

    BossSweep(Chapter chapter, int topRow, int rows) {
        this.chapter = chapter;
        this.topRow = topRow;
        this.rows = rows;
    }

    public Chapter getChapter() {
        return chapter;
    }

    /** The topmost row it covers, 0-based, like the boss's own. */
    public int getTopRow() {
        return topRow;
    }

    public int getRows() {
        return rows;
    }

    /** How far across the lawn the front has come, 0 to 1. */
    public double progress() {
        return Math.min(1, age / SECONDS);
    }

    void passSeconds(double seconds) {
        age += seconds;
    }

    boolean isDone() {
        return age >= SECONDS;
    }
}
