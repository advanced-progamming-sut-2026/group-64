package ir.sharif.pvz.model.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mow-point scoring for the score game. Five patterns reward different
 * strategies:
 * 1. every kill pays a base amount,
 * 2. quick kills (within five seconds of spawning) pay extra,
 * 3. breaking an armored zombie pays extra,
 * 4. a blast killing three or more zombies in the same tick pays a bonus,
 * 5. every lawn mower still unused at the win pays a bonus.
 */
public class ScoreTracker {

    public static final int KILL_POINTS = 20;
    public static final int QUICK_KILL_POINTS = 30;
    public static final int ARMOR_BREAKER_POINTS = 40;
    public static final int BLAST_POINTS = 100;
    public static final int MOWER_POINTS = 50;
    public static final int BLAST_SIZE = 3;
    private static final long QUICK_KILL_TICKS = 5L * GameSession.TICKS_PER_SECOND;

    private final Map<Zombie, Long> spawnTicks = new HashMap<>();
    private final Map<Zombie, Boolean> spawnedArmored = new HashMap<>();
    private final Map<String, Integer> byPattern = new LinkedHashMap<>();
    private long lastKillTick = -1;
    private int killsInTick;
    private int points;

    void onSpawn(Zombie zombie, long tick) {
        spawnTicks.put(zombie, tick);
        spawnedArmored.put(zombie, !zombie.getArmor().isEmpty());
    }

    void onKill(Zombie zombie, long tick) {
        award("kills", KILL_POINTS);
        Long spawned = spawnTicks.remove(zombie);
        if (spawned != null && tick - spawned <= QUICK_KILL_TICKS) {
            award("quick kills", QUICK_KILL_POINTS);
        }
        if (Boolean.TRUE.equals(spawnedArmored.remove(zombie))) {
            award("armor breakers", ARMOR_BREAKER_POINTS);
        }
        if (tick == lastKillTick) {
            killsInTick++;
            if (killsInTick == BLAST_SIZE) {
                award("blasts", BLAST_POINTS);
            }
        } else {
            lastKillTick = tick;
            killsInTick = 1;
        }
    }

    /**
     * Called once when the level is won; pays for every unused lawn mower.
     */
    public void addMowerBonus(int unusedMowers) {
        if (unusedMowers > 0) {
            award("unused mowers", MOWER_POINTS * unusedMowers);
        }
    }

    private void award(String pattern, int amount) {
        points += amount;
        byPattern.merge(pattern, amount, Integer::sum);
    }

    public int getPoints() {
        return points;
    }

    /**
     * One line per pattern that scored, plus the total.
     */
    public List<String> breakdown() {
        List<String> lines = new ArrayList<>();
        byPattern.forEach((pattern, amount) -> lines.add(pattern + ": " + amount));
        lines.add("Total mow points: " + points);
        return lines;
    }
}
