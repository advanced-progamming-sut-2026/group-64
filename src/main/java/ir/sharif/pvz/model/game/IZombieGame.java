package ir.sharif.pvz.model.game;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * i, Zombie: the player commands the zombies. Random plants defend the left
 * columns, a brain sits at the end of every lane, and the stationary
 * sun-zombies produce the sun the player spends on placing attackers.
 */
class IZombieGame implements MinigameLogic {

    private static final int RED_LINE_COLUMN = 5;
    private static final int STARTING_SUN = 150;
    private static final List<String> DEFENDERS =
            List.of("peashooter", "snow-pea", "repeater", "wall-nut", "cabbage-pult", "chomper");

    private final Map<String, Integer> prices;
    private final int stage;
    private final Random random;
    private final boolean[] brains = new boolean[GameSession.ROWS];
    private double nextSunAt;

    IZombieGame(int stage, Map<String, Integer> prices, Random random) {
        this.stage = stage;
        this.prices = prices;
        this.random = random;
        java.util.Arrays.fill(brains, true);
    }

    @Override
    public void init(GameSession session) {
        session.setWavesEnabled(false);
        session.disableMowers();
        session.setSunAmount(STARTING_SUN);
        for (int row = 0; row < GameSession.ROWS; row++) {
            for (int i = 0; i < 1 + stage; i++) {
                placeDefender(session, row);
            }
            session.spawnZombie(GameCatalog.get().zombie("sun-zombie"), row, 8);
        }
        session.eventLog().add("You ARE the zombies! Place them right of column " + RED_LINE_COLUMN
                + " with 'place zombie -t <type> -l (x, y)' and eat all five brains.");
        session.eventLog().add("Available zombies: " + prices);
    }

    private void placeDefender(GameSession session, int row) {
        int col = random.nextInt(3);
        if (session.plantAtTile(col + 1, row + 1) == null) {
            session.placePlant(row, col, DEFENDERS.get(random.nextInt(DEFENDERS.size())));
        }
    }

    @Override
    public String plantingRejection(int x, int y) {
        return "Error: you play the zombies here; plants cannot be planted.";
    }

    @Override
    public String placeZombie(GameSession session, String type, int x, int y) {
        Integer price = prices.get(type);
        if (price == null) {
            return "Error: '" + type + "' is not available in this stage; pick from " + prices.keySet() + ".";
        }
        if (x <= RED_LINE_COLUMN || x > GameSession.COLS || y < 1 || y > GameSession.ROWS) {
            return "Error: zombies go right of column " + RED_LINE_COLUMN + ".";
        }
        if (session.getSunAmount() < price) {
            return "Error: " + type + " costs " + price + " sun; you have " + session.getSunAmount() + ".";
        }
        session.spendSun(price);
        session.spawnZombie(GameCatalog.get().zombie(type), y - 1, x);
        return "Zombie " + type + " placed at (" + x + ", " + y + ").";
    }

    @Override
    public boolean onHouseReached(GameSession session, Zombie zombie) {
        int row = zombie.getRow();
        session.removeZombieQuietly(zombie);
        if (brains[row]) {
            brains[row] = false;
            session.eventLog().add("The zombie ate the brain of lane " + (row + 1) + "; yum!");
        }
        for (boolean brain : brains) {
            if (brain) {
                return true;
            }
        }
        session.winNow("All five brains are eaten; the zombies triumph!");
        return true;
    }

    @Override
    public void tick(GameSession session, double seconds) {
        long sunZombies = session.getZombies().stream()
                .filter(z -> z.getSpec().getName().equals("sun-zombie")).count();
        if (seconds >= nextSunAt) {
            nextSunAt = seconds + Math.max(3, 10 - 0.05 * seconds);
            if (sunZombies > 0) {
                session.setSunAmount(session.getSunAmount() + 25 * (int) sunZombies);
                session.eventLog().add("Your sun-zombies produced " + 25 * sunZombies
                        + " sun; you have " + session.getSunAmount() + ".");
            }
        }
        checkDefeat(session);
    }

    private void checkDefeat(GameSession session) {
        if (session.isOver() || !session.getZombies().isEmpty()) {
            return;
        }
        int cheapest = prices.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        if (session.getSunAmount() < cheapest) {
            session.loseNow("No zombie left and not enough sun; the plants survived.");
        }
    }
}
