package ir.sharif.pvz.model.game;

import java.util.List;
import java.util.Random;

/**
 * What Zomboss actually does during a boss level: every few seconds it picks
 * one of its moves at random, shuffles between rows, and drops fresh zombies on
 * the lawn. Each chapter's boss has two moves of its own on top of that.
 */
final class ZombossEngine {

    private static final double MOVE_PERIOD_SECONDS = 7;
    private static final int PART_HEALTH = 2500;
    private static final int STRIKE_ROWS = 2;
    /** How long the Dark Ages boss leaves the ground alight. */
    private static final double SCORCH_SECONDS = 6;
    /** How many tiles the beach boss's torpedo drags a zombie. */
    private static final double TORPEDO_PULL_TILES = 1.4;

    private static final List<String> SUMMONS =
            List.of("normal", "conehead", "buckethead", "imp", "newspaper");

    private final GameSession session;
    private final Random random;
    private final Zomboss boss;

    private double nextMoveAt = MOVE_PERIOD_SECONDS;

    ZombossEngine(GameSession session, Chapter chapter, Random random) {
        this.session = session;
        this.random = random;
        boolean mammoth = chapter == Chapter.FROSTBITE_CAVES;
        this.boss = new Zomboss(chapter, PART_HEALTH,
                mammoth ? GameSession.ROWS : STRIKE_ROWS, GameSession.COLS);
    }

    Zomboss boss() {
        return boss;
    }

    /**
     * Damage dealt to the boss by a plant in one of the rows it covers.
     */
    void hit(int damage) {
        if (boss.isDefeated()) {
            return;
        }
        if (boss.damage(damage)) {
            session.eventLog().add("A part of Zomboss's armour gives way; it reels!");
            session.recordBurst(Burst.Kind.EXPLOSION, GameSession.COLS, boss.getRow() + 1.0);
        }
    }

    /**
     * Blows a plant up where it stands, which is how every boss attack clears
     * the lawn.
     */
    private void wreck(Plant plant) {
        session.recordBurst(Burst.Kind.PLANT_LOST, plant.getCol() + 1.0, plant.getRow() + 1.0);
        session.destroyPlantSilently(plant);
    }

    /**
     * Freezes a whole column, the ice boss's signature move.
     */
    private void freezeColumn(int col) {
        for (Zombie zombie : session.getZombies()) {
            if (Math.round(zombie.getX()) == col) {
                zombie.freeze(6);
            }
        }
        session.eventLog().add("Column " + col + " freezes solid!");
    }

    void tick(double seconds) {
        boss.passSeconds(1.0 / GameSession.TICKS_PER_SECOND);
        if (boss.isDefeated() || boss.isStunned() || seconds < nextMoveAt) {
            return;
        }
        nextMoveAt = seconds + MOVE_PERIOD_SECONDS;
        act();
    }

    /**
     * One turn: a chapter move, then usually a summon and a shuffle between rows.
     */
    private void act() {
        switch (random.nextInt(3)) {
            case 0 -> signatureStrike();
            case 1 -> sweepingMove();
            default -> summon();
        }
        if (boss.getRows() < GameSession.ROWS && random.nextBoolean()) {
            boss.setRow(random.nextInt(GameSession.ROWS - boss.getRows() + 1));
            session.eventLog().add("Zomboss shifts to row " + (boss.getRow() + 1) + "!");
        }
    }

    /**
     * The single-tile attack each boss has: a rocket, a fireball, an icy shot
     * or a pack of baby sharks. They all destroy whatever plant they land on.
     */
    private void signatureStrike() {
        int col = 1 + random.nextInt(GameSession.COLS);
        int row = 1 + random.nextInt(GameSession.ROWS);
        Plant victim = session.plantAtTile(col, row);
        if (victim != null) {
            wreck(victim);
        }
        // the shot is what the player sees; the damage is already done
        boss.lunged();
        session.throwBossShot(BossShot.kindFor(boss.getChapter()),
                boss.getColumn(), boss.getRow() + boss.getRows() / 2.0 + 0.5, col, row);
        session.eventLog().add(strikeName() + " hit (" + col + ", " + row + ")!");
        afterStrike(col, row);
    }

    /**
     * What each boss leaves behind at the tile it just hit.
     */
    private void afterStrike(int col, int row) {
        switch (boss.getChapter()) {
            case DARK_AGES -> {
                ZombieSpec dragon = GameCatalog.get().zombie("imp-dragon");
                if (dragon != null) {
                    session.spawnZombie(dragon, row - 1, col);
                    session.eventLog().add("A dragon imp climbs out of the crater!");
                }
            }
            case ANCIENT_EGYPT -> {
                for (int i = 0; i < 2; i++) {
                    session.raiseGrave(random.nextInt(GameSession.ROWS),
                            random.nextInt(GameSession.COLS), null);
                }
            }
            case FROSTBITE_CAVES -> freezeColumn(col);
            default -> { }
        }
    }

    /**
     * The wide attack: burning, charging, an icy gale or a torpedo. All of them
     * clear the rows the boss is facing.
     */
    private void sweepingMove() {
        session.eventLog().add(sweepName() + "!");
        boss.lunged();
        for (int row = boss.getRow(); row < boss.getRow() + boss.getRows(); row++) {
            for (int col = 1; col <= GameSession.COLS; col++) {
                Plant victim = session.plantAtTile(col, row + 1);
                if (victim != null) {
                    wreck(victim);
                }
            }
        }
        // one front crossing the rows, rather than a burst dropped in each
        session.startBossSweep(boss.getChapter(), boss.getRow(), boss.getRows());
        afterSweep();
    }

    /**
     * What each chapter's wide move leaves behind it. The names said the moves
     * were different things and the board never showed any of it.
     */
    private void afterSweep() {
        switch (boss.getChapter()) {
            // Egypt's is a charge: it actually leaves its column and comes back
            case ANCIENT_EGYPT -> boss.charge();
            // the Dark Ages one sets the ground alight where it burned
            case DARK_AGES -> scorchRows();
            // the beach one drags the lane in with it
            case BIG_WAVE_BEACH -> pullZombiesForward();
            default -> { }
        }
    }

    /**
     * The rows the fire went through are left burning: anything planted there
     * while they are still alight goes up with them.
     */
    private void scorchRows() {
        for (int row = boss.getRow(); row < boss.getRow() + boss.getRows(); row++) {
            session.scorchRow(row, SCORCH_SECONDS);
        }
        session.eventLog().add("The ground is still burning where it went!");
    }

    /**
     * The torpedo sucks the rows in toward the house, which is what makes it
     * worse than the fire it replaces: the zombies arrive sooner.
     */
    private void pullZombiesForward() {
        int moved = 0;
        for (Zombie zombie : session.getZombies()) {
            if (boss.covers(zombie.getRow())) {
                zombie.dragForward(TORPEDO_PULL_TILES);
                moved++;
            }
        }
        if (moved > 0) {
            session.eventLog().add("The torpedo dragged " + moved
                    + " zombies toward your house!");
        }
    }

    private void summon() {
        String type = SUMMONS.get(random.nextInt(SUMMONS.size()));
        ZombieSpec spec = GameCatalog.get().zombie(type);
        if (spec == null) {
            return;
        }
        int row = random.nextInt(GameSession.ROWS);
        session.spawnZombie(spec, row, GameSession.COLS);
        session.eventLog().add("Zomboss summons a " + type + " in row " + (row + 1) + "!");
    }

    private String strikeName() {
        return switch (boss.getChapter()) {
            case DARK_AGES -> "A fireball";
            case ANCIENT_EGYPT -> "A rocket";
            case FROSTBITE_CAVES -> "An icy rocket";
            case BIG_WAVE_BEACH -> "A pack of baby sharks";
        };
    }

    private String sweepName() {
        return switch (boss.getChapter()) {
            case DARK_AGES -> "Zomboss torches two whole rows";
            case ANCIENT_EGYPT -> "Zomboss charges forward and back";
            case FROSTBITE_CAVES -> "An icy gale sweeps the lawn";
            case BIG_WAVE_BEACH -> "Zomboss sucks the rows in with its torpedo";
        };
    }
}
