package ir.sharif.pvz.model.game;

/**
 * The sea coming in and going out on a Big Wave Beach lawn.
 *
 * <p>The water used to be laid out once when the level was built and never
 * moved, so a chapter whose whole idea is the tide had a fixed pond down its
 * right-hand side. It breathes now: the sea takes a column, holds it, and gives
 * it back, which is what makes a lily pad worth planting and what the tide line
 * has been marking all along.
 *
 * <p>Where the water is at any moment falls out of the clock, the way the
 * {@link Weather} does, so two clients watching the same board agree without
 * anything being sent between them.
 *
 * <p>A tile is only ever flooded or drained if the level laid water out there
 * to begin with — the sea reaches its own high-water mark and no further, and
 * never washes over a tile the player has been told is dry land.
 */
public final class Tide {

    /** How long a full in-and-out takes. */
    public static final double PERIOD_SECONDS = 34;

    /** How long the sea spends at its highest before it turns. */
    private static final double HIGH_SECONDS = 9;

    /** How long it spends fully out. */
    private static final double LOW_SECONDS = 9;

    private final boolean enabled;
    private final int deepest;
    private final int shallowest;

    /**
     * @param chapter the chapter this level belongs to
     * @param deepest the leftmost column the level's own water reaches, or 0
     *                when the level has no water at all
     * @param shallowest the rightmost column of that water
     */
    Tide(Chapter chapter, int deepest, int shallowest) {
        // one column of water cannot go in and out; it would simply blink
        this.enabled = chapter == Chapter.BIG_WAVE_BEACH && deepest > 0
                && shallowest > deepest;
        this.deepest = deepest;
        this.shallowest = shallowest;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * The leftmost column the sea covers at this moment. Columns from here to
     * the right edge of the level's water are wet; anything left of it is the
     * sand the tide has uncovered.
     */
    public int frontAt(double seconds) {
        if (!enabled) {
            return deepest;
        }
        double t = height(seconds);
        // t = 1 is fully in, at the level's own deepest column
        return (int) Math.round(shallowest - (shallowest - deepest) * t);
    }

    /**
     * How far in the sea is, 0 fully out and 1 fully in. It rises, sits at the
     * top, falls, and sits at the bottom, rather than sliding straight back.
     */
    public double height(double seconds) {
        double phase = seconds % PERIOD_SECONDS;
        double moving = (PERIOD_SECONDS - HIGH_SECONDS - LOW_SECONDS) / 2;
        if (phase < moving) {
            return phase / moving;
        }
        if (phase < moving + HIGH_SECONDS) {
            return 1;
        }
        if (phase < moving * 2 + HIGH_SECONDS) {
            return 1 - (phase - moving - HIGH_SECONDS) / moving;
        }
        return 0;
    }

    /** True while the sea is out far enough to walk on what it uncovered. */
    public boolean isLow(double seconds) {
        return enabled && height(seconds) < 0.5;
    }

    /**
     * Reads where the level's own water lies, so the tide knows how far it may
     * come in and how far out it may go.
     */
    static Tide forLevel(LevelSpec level) {
        int deepest = 0;
        int shallowest = 0;
        for (var entry : level.getTerrain().entrySet()) {
            if (entry.getValue() != TileTerrain.WATER) {
                continue;
            }
            int col = LevelSpec.colOf(entry.getKey()) + 1;
            deepest = deepest == 0 ? col : Math.min(deepest, col);
            shallowest = Math.max(shallowest, col);
        }
        return new Tide(level.getChapter(), deepest, shallowest);
    }

    /**
     * Moves the sea to where the clock says it should be. A tile only floods
     * or drains if the level laid water there in the first place, and a tile
     * with a plant on it is left alone: drowning something the player planted
     * on dry sand would be a rule, and the tide is scenery.
     */
    void floodAndDrain(GameSession session) {
        if (!enabled) {
            return;
        }
        int front = frontAt(session.getElapsedSeconds());
        for (var entry : session.getLevel().getTerrain().entrySet()) {
            if (entry.getValue() != TileTerrain.WATER) {
                continue;
            }
            int row = LevelSpec.rowOf(entry.getKey());
            int col = LevelSpec.colOf(entry.getKey());
            if (session.terrainAt(col + 1, row + 1) == TileTerrain.LILY
                    || session.plantAtTile(col + 1, row + 1) != null) {
                continue;
            }
            session.board.setTerrain(row, col,
                    col + 1 >= front ? TileTerrain.WATER : TileTerrain.NORMAL);
        }
    }
}
