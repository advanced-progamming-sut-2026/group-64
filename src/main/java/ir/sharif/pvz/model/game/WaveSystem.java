package ir.sharif.pvz.model.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Schedules and spawns the zombie waves of a level: budgets grow 25% per
 * wave, the flag wave doubles, and the next wave breaks when 75% of the
 * previous one's health is gone.
 */
class WaveSystem {

    private static final double FIRST_WAVE_AT_SECONDS = 10;

    private final GameSession session;
    private final LevelSpec level;
    private final Random random;
    private final double difficultyDown;
    private final List<Zombie> currentWaveZombies = new ArrayList<>();
    private int currentWave;
    private double waveBudget;
    private int spawnedHealth;
    private boolean enabled = true;

    WaveSystem(GameSession session, LevelSpec level, double difficultyDown, Random random) {
        this.session = session;
        this.level = level;
        this.difficultyDown = difficultyDown;
        this.random = random;
        this.waveBudget = level.getFirstWaveBudget();
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    int getCurrentWave() {
        return currentWave;
    }

    boolean allWavesSpawned() {
        return currentWave >= level.getTotalWaves();
    }

    void tick(double seconds) {
        boolean waveCleared = currentWave > 0 && remainingHealth() <= 0.25 * spawnedHealth;
        boolean timeForFirst = currentWave == 0 && seconds >= FIRST_WAVE_AT_SECONDS;
        if (!enabled || allWavesSpawned() || (!timeForFirst && !waveCleared)) {
            return;
        }
        currentWave++;
        boolean flagWave = currentWave == level.getTotalWaves();
        if (currentWave > 1) {
            waveBudget = flagWave ? waveBudget * 2 : waveBudget * 1.25;
        }
        session.eventLog().add(flagWave ? "The final wave has come." : "Wave " + currentWave + " started.");
        onWaveStart();
        spawnWave(flagWave);
    }

    private int remainingHealth() {
        return currentWaveZombies.stream().filter(session.zombieList()::contains)
                .mapToInt(Zombie::totalRemainingHealth).sum();
    }

    /**
     * Chapter hooks firing at each wave start: Dark Ages graves and spawner
     * tiles (low tides / necromancy graves).
     */
    private void onWaveStart() {
        if (level.hasWaveGraves()) {
            String[] contents = {null, null, "sun", "plant food"};
            session.raiseGrave(random.nextInt(GameSession.ROWS), random.nextInt(GameSession.COLS),
                    contents[random.nextInt(contents.length)]);
        }
        for (int row = 0; row < GameSession.ROWS; row++) {
            for (int col = 0; col < GameSession.COLS; col++) {
                boolean spawner = session.terrainAt(col + 1, row + 1) == TileTerrain.SPAWNER
                        && session.plantAtTile(col + 1, row + 1) == null;
                if (spawner && random.nextInt(100) < 50) {
                    track(session.spawnZombie(GameCatalog.get().zombie("normal"), row, col + 1));
                    session.eventLog().add("Zombie normal emerged from under the tile at ("
                            + (col + 1) + ", " + (row + 1) + ")!");
                }
            }
        }
    }

    private void spawnWave(boolean flagWave) {
        currentWaveZombies.clear();
        spawnedHealth = 0;
        List<ZombieSpec> pool = level.getZombiePool().stream()
                .map(name -> GameCatalog.get().zombie(name))
                .filter(spec -> !spec.getName().equals("gargantuar") || flagWave)
                .toList();
        double spent = 0;
        while (spent < waveBudget) {
            ZombieSpec spec = pool.get(random.nextInt(pool.size()));
            double cost = spec.getWaveCost() * difficultyDown;
            int lane = random.nextInt(GameSession.ROWS);
            double entry = GameSession.COLS;
            if (level.hasWhirlwind() && flagWave && random.nextInt(100) < 30) {
                entry = GameSession.COLS - 1 - random.nextInt(4);
                session.eventLog().add("A whirlwind carries a zombie into the garden!");
            }
            track(session.spawnZombie(spec, lane, entry));
            spent += cost;
            session.eventLog().add("Zombie " + spec.getName() + " spawned at wave " + currentWave
                    + " in lane " + (lane + 1) + " which costed " + GameSession.trim(cost) + ".");
        }
    }

    private void track(Zombie zombie) {
        currentWaveZombies.add(zombie);
        spawnedHealth += zombie.totalRemainingHealth();
    }
}
