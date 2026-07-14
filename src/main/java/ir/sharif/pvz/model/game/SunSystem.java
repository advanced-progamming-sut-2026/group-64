package ir.sharif.pvz.model.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Owns every sun in play: the ones falling from the sky (never at night) and
 * the ones lying on the ground waiting to be collected.
 */
class SunSystem {

    private static final double SUN_LANDING_SECONDS = 5;

    private final List<Sun> suns = new ArrayList<>();
    private final List<String> events;
    private final Random random;
    private final boolean night;
    private final double difficultyUp;
    private double nextFallAtSeconds;

    SunSystem(LevelSpec level, double difficultyUp, Random random, List<String> events) {
        this.night = level.isNight();
        this.difficultyUp = difficultyUp;
        this.random = random;
        this.events = events;
        this.nextFallAtSeconds = interval(0);
    }

    /** The document's drop pacing: x = max(6 + 0.05t, 12), stretched by difficulty. */
    private double interval(double seconds) {
        return Math.max(6 + 0.05 * seconds, 12) * difficultyUp;
    }

    void tick(double dt, double seconds) {
        for (Sun sun : new ArrayList<>(suns)) {
            if (sun.passSeconds(dt)) {
                events.add("Sun reached the ground at position (" + (sun.getCol() + 1)
                        + ", " + (sun.getRow() + 1) + ")");
            }
        }
        if (night || seconds < nextFallAtSeconds) {
            return;
        }
        nextFallAtSeconds = seconds + interval(seconds);
        Sun.Kind kind = rollKind();
        int row = random.nextInt(GameSession.ROWS);
        int col = random.nextInt(GameSession.COLS);
        suns.add(new Sun(kind, row, col, SUN_LANDING_SECONDS));
        events.add("New " + kind.name().toLowerCase(Locale.ROOT) + " sun is dropping at position ("
                + (col + 1) + ", " + (row + 1) + ")");
    }

    private Sun.Kind rollKind() {
        int roll = random.nextInt(100);
        if (roll < 80) {
            return Sun.Kind.NORMAL;
        }
        return roll < 95 ? Sun.Kind.SPECIAL : Sun.Kind.RADIOACTIVE;
    }

    /** Live list shared with zombie abilities (Ra steals from it). */
    List<Sun> live() {
        return suns;
    }

    void add(Sun sun) {
        suns.add(sun);
    }

    void remove(Sun sun) {
        suns.remove(sun);
    }

    List<Sun> ground() {
        return suns.stream().filter(Sun::isOnGround).toList();
    }

    Sun groundAt(int row, int col) {
        return suns.stream().filter(s -> s.isOnGround() && s.getRow() == row && s.getCol() == col)
                .findFirst().orElse(null);
    }

    Sun fallingRadioactiveAt(int row, int col) {
        return suns.stream().filter(s -> !s.isOnGround() && s.getKind() == Sun.Kind.RADIOACTIVE
                && s.getRow() == row && s.getCol() == col).findFirst().orElse(null);
    }
}
