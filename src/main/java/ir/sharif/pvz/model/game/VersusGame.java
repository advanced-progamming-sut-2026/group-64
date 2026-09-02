package ir.sharif.pvz.model.game;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The two-player form of "I, Zombie": one player grows the lawn, the other
 * sends the zombies at it.
 *
 * <p>Unlike the solo minigame nothing is placed for either side — both build
 * from scratch out of their own sun, which ticks up on its own so neither has
 * to farm before they can play. The zombie side wins by eating all five brains;
 * the plant side wins by still having one when the clock runs out.
 */
public final class VersusGame implements MinigameLogic {

    /** Zombies may only be dropped to the right of this column. */
    public static final int RED_LINE_COLUMN = 5;

    /** What the plant side may grow in a versus round. */
    public static final java.util.List<String> PLANTS = java.util.List.of(
            "sunflower", "peashooter", "snow-pea", "repeater", "wall-nut", "cabbage-pult");

    /** How long the plant side has to survive, in seconds. */
    public static final double ROUND_SECONDS = 120;

    private static final int STARTING_SUN = 150;
    private static final int SUN_TICK = 25;
    private static final double SUN_PERIOD_SECONDS = 5;

    private static final Map<String, Integer> ZOMBIE_PRICES = new LinkedHashMap<>();

    static {
        ZOMBIE_PRICES.put("normal", 50);
        ZOMBIE_PRICES.put("conehead", 75);
        ZOMBIE_PRICES.put("buckethead", 125);
        ZOMBIE_PRICES.put("imp", 25);
        ZOMBIE_PRICES.put("newspaper", 75);
        ZOMBIE_PRICES.put("all-star", 150);
    }

    private final boolean[] brains = new boolean[GameSession.ROWS];

    private int zombieSun = STARTING_SUN;
    private double nextSunAt = SUN_PERIOD_SECONDS;
    private boolean finished;

    public VersusGame() {
        java.util.Arrays.fill(brains, true);
    }

    /**
     * What the zombie side can afford to send.
     */
    public static Map<String, Integer> zombiePrices() {
        return Map.copyOf(ZOMBIE_PRICES);
    }

    /**
     * The sun the zombie side has banked, which is its own pool.
     */
    public int getZombieSun() {
        return zombieSun;
    }

    /**
     * Which lanes still have a brain in them.
     */
    public boolean[] brains() {
        return brains.clone();
    }

    public int brainsLeft() {
        int left = 0;
        for (boolean brain : brains) {
            if (brain) {
                left++;
            }
        }
        return left;
    }

    @Override
    public void init(GameSession session) {
        session.setWavesEnabled(false);
        session.disableMowers();
        session.setSunAmount(STARTING_SUN);
        session.eventLog().add("Versus! Plants defend five brains for "
                + (int) ROUND_SECONDS + " seconds.");
    }

    /**
     * Plants may only go left of the red line, leaving the right of the lawn as
     * the zombie side's staging ground.
     */
    @Override
    public String plantingRejection(int x, int y) {
        return x > RED_LINE_COLUMN
                ? "Error: plants go left of column " + RED_LINE_COLUMN + "."
                : null;
    }

    @Override
    public int restrictedColumn() {
        return RED_LINE_COLUMN;
    }

    @Override
    public Map<String, Integer> cardsInsteadOfPlants() {
        // the plant side keeps its seed packets; this is what the zombie side sees
        return Map.of();
    }

    @Override
    public String placeZombie(GameSession session, String type, int x, int y) {
        Integer price = ZOMBIE_PRICES.get(type);
        if (price == null) {
            return "Error: '" + type + "' is not one of yours; pick from " + ZOMBIE_PRICES.keySet() + ".";
        }
        if (x <= RED_LINE_COLUMN || x > GameSession.COLS || y < 1 || y > GameSession.ROWS) {
            return "Error: zombies go right of column " + RED_LINE_COLUMN + ".";
        }
        if (zombieSun < price) {
            return "Error: " + type + " costs " + price + " sun; you have " + zombieSun + ".";
        }
        zombieSun -= price;
        session.spawnZombie(GameCatalog.get().zombie(type), y - 1, x);
        return "Zombie " + type + " placed at (" + x + ", " + y + ").";
    }

    /**
     * A zombie that gets through eats that lane's brain; the last one ends it.
     */
    @Override
    public boolean onHouseReached(GameSession session, Zombie zombie) {
        int row = zombie.getRow();
        session.removeZombieQuietly(zombie);
        if (brains[row]) {
            brains[row] = false;
            session.eventLog().add("The brain in lane " + (row + 1) + " is gone!");
        }
        if (brainsLeft() == 0 && !finished) {
            finished = true;
            session.loseNow("Every brain is eaten; the zombies win!");
        }
        return true;
    }

    @Override
    public void tick(GameSession session, double seconds) {
        if (seconds >= nextSunAt) {
            nextSunAt = seconds + SUN_PERIOD_SECONDS;
            zombieSun += SUN_TICK;
            session.setSunAmount(session.getSunAmount() + SUN_TICK);
        }
        if (!finished && seconds >= ROUND_SECONDS) {
            finished = true;
            session.winNow("Time is up and a brain still stands; the plants win!");
        }
    }
}
