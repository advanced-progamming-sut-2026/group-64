package ir.sharif.pvz.model.game;

import java.util.List;
import java.util.Map;

/**
 * Static description of one adventure level: its chapter, waves, zombie pool
 * and the special terrain the chapter lays out on the board.
 */
public class LevelSpec {

    private final Chapter chapter;
    private final int day;
    private final int totalWaves;
    private final double firstWaveBudget;
    private final List<String> zombiePool;
    /** (row, col) 0-based -> terrain; unlisted tiles are NORMAL. */
    private final Map<Integer, TileTerrain> terrain;
    private final int startingGraves;
    private final boolean night;
    private final boolean whirlwind;
    private final boolean waveGraves;
    private final SpecialRules special;
    private double waveBudgetIncrement = 500;

    public LevelSpec(Chapter chapter, int day, int totalWaves, double firstWaveBudget,
                     List<String> zombiePool, Map<Integer, TileTerrain> terrain,
                     int startingGraves, boolean night, boolean whirlwind, boolean waveGraves) {
        this(chapter, day, totalWaves, firstWaveBudget, zombiePool, terrain,
                startingGraves, night, whirlwind, waveGraves, null);
    }

    public LevelSpec(Chapter chapter, int day, int totalWaves, double firstWaveBudget,
                     List<String> zombiePool, Map<Integer, TileTerrain> terrain,
                     int startingGraves, boolean night, boolean whirlwind, boolean waveGraves,
                     SpecialRules special) {
        this.chapter = chapter;
        this.day = day;
        this.totalWaves = totalWaves;
        this.firstWaveBudget = firstWaveBudget;
        this.zombiePool = List.copyOf(zombiePool);
        this.terrain = Map.copyOf(terrain);
        this.startingGraves = startingGraves;
        this.night = night;
        this.whirlwind = whirlwind;
        this.waveGraves = waveGraves;
        this.special = special;
    }

    /**
     * The special-level rules, or null for a normal level.
     */
    public SpecialRules getSpecial() {
        return special;
    }

    /**
     * How much every new wave adds to the budget (course rule: at least 500).
     */
    public double getWaveBudgetIncrement() {
        return waveBudgetIncrement;
    }

    LevelSpec waveIncrement(double increment) {
        this.waveBudgetIncrement = increment;
        return this;
    }

    /** Encodes a 0-based (row, col) pair into one map key. */
    public static int tileKey(int row, int col) {
        return row * GameSession.COLS + col;
    }

    public Chapter getChapter() {
        return chapter;
    }

    public int getDay() {
        return day;
    }

    public int getTotalWaves() {
        return totalWaves;
    }

    public double getFirstWaveBudget() {
        return firstWaveBudget;
    }

    public List<String> getZombiePool() {
        return zombiePool;
    }

    public Map<Integer, TileTerrain> getTerrain() {
        return terrain;
    }

    public int getStartingGraves() {
        return startingGraves;
    }

    public boolean isNight() {
        return night;
    }

    public boolean hasWhirlwind() {
        return whirlwind;
    }

    public boolean hasWaveGraves() {
        return waveGraves;
    }

    public String title() {
        return day == 0 ? "Score Game (daily)" : chapter.displayName() + " - Day " + day;
    }
}
