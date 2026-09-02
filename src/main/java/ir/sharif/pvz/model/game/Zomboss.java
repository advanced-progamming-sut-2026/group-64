package ir.sharif.pvz.model.game;

/**
 * The boss that closes out a chapter.
 *
 * <p>Zomboss sits on the right of the lawn spanning more than one row, and its
 * health is split into three parts: knocking a part out stuns it for a while
 * before it starts up again. It never walks toward the house, so the level ends
 * when the player takes the last part off — or when an ordinary zombie it
 * summoned gets through.
 */
public final class Zomboss {

    /** How long the boss is dazed after losing one part of its health. */
    private static final double STUN_SECONDS = 6;
    private static final int PARTS = 3;

    private final Chapter chapter;
    private final int partHealth;
    private final int rows;
    private final int column;

    private int hp;
    private int row;
    private double stunnedSeconds;

    Zomboss(Chapter chapter, int partHealth, int rows, int column) {
        this.chapter = chapter;
        this.partHealth = partHealth;
        this.rows = rows;
        this.column = column;
        this.hp = partHealth * PARTS;
        this.row = rows >= GameSession.ROWS ? 0 : (GameSession.ROWS - rows) / 2;
    }

    public Chapter getChapter() {
        return chapter;
    }

    /**
     * The topmost row the boss covers, 0-based.
     */
    public int getRow() {
        return row;
    }

    void setRow(int row) {
        this.row = Math.max(0, Math.min(GameSession.ROWS - rows, row));
    }

    /**
     * How many rows the boss spans; the mammoth covers the whole lawn.
     */
    public int getRows() {
        return rows;
    }

    /**
     * The column the boss is centred on, in the 1-based board coordinates.
     */
    public int getColumn() {
        return column;
    }

    public boolean covers(int zeroBasedRow) {
        return zeroBasedRow >= row && zeroBasedRow < row + rows;
    }

    public int getHp() {
        return hp;
    }

    public int getMaxHp() {
        return partHealth * PARTS;
    }

    /**
     * How many of the three parts are already gone, 0 to 3.
     */
    public int getPartsDestroyed() {
        return PARTS - partsLeft();
    }

    private int partsLeft() {
        return hp <= 0 ? 0 : (hp + partHealth - 1) / partHealth;
    }

    /**
     * How full the part currently being chewed through is, 0 to 1, which is
     * what the last lit segment of the health bar shows.
     */
    public double currentPartFraction() {
        if (hp <= 0) {
            return 0;
        }
        int within = hp % partHealth;
        return within == 0 ? 1 : within / (double) partHealth;
    }

    public boolean isStunned() {
        return stunnedSeconds > 0;
    }

    public boolean isDefeated() {
        return hp <= 0;
    }

    /**
     * Takes damage, and reports whether that knocked a whole part off — which
     * is what puts the boss out of action for a few seconds.
     */
    boolean damage(int amount) {
        int partsBefore = partsLeft();
        hp = Math.max(0, hp - amount);
        if (partsLeft() < partsBefore && hp > 0) {
            stunnedSeconds = STUN_SECONDS;
            return true;
        }
        return false;
    }

    void passSeconds(double seconds) {
        stunnedSeconds = Math.max(0, stunnedSeconds - seconds);
    }
}
