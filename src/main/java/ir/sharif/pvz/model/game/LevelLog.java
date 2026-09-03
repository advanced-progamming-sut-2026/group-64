package ir.sharif.pvz.model.game;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What happened during one level, kept so the travel log's quests can ask
 * about it afterwards.
 *
 * <p>Most of the sheet's quests are about how a level was played rather than
 * that it was won — which plant did the killing, how much sun was picked up,
 * how many plants were lost — and none of that survives the level otherwise.
 *
 * <p>A kill is credited to whatever was attacking at the time: {@link
 * PlantCombat} names the plant before it strikes and the mower names itself.
 * A zombie that dies to something with no obvious owner, such as another
 * zombie's blast, is counted but credited to nobody.
 */
public class LevelLog {

    /** The name the mower is credited under, which is no plant's name. */
    public static final String MOWER = "lawn mower";

    /** How long after the first wave starts the "quick kills" window runs. */
    private static final double FAST_WINDOW_SECONDS = 30;

    private final Map<String, Integer> killsBy = new HashMap<>();
    private final Set<String> planted = new LinkedHashSet<>();
    private String credit;
    private long firstWaveTick = -1;
    private int kills;
    private int killsInFastWindow;
    private int plantsLost;
    private int sunCollected;
    private int killsAtTheDoor;

    /**
     * Names what is about to deal damage, so any kill it causes is credited to
     * it. Passing null goes back to crediting nobody.
     */
    void creditTo(String source) {
        this.credit = source;
    }

    void onFirstWave(long tick) {
        if (firstWaveTick < 0) {
            firstWaveTick = tick;
        }
    }

    /**
     * @param atHouse   true when the zombie died in the first column
     * @param mowerGone true when that lane's mower had already been spent
     */
    void onKill(long tick, boolean atHouse, boolean mowerGone) {
        kills++;
        if (atHouse && mowerGone) {
            killsAtTheDoor++;
        }
        if (credit != null) {
            killsBy.merge(credit, 1, Integer::sum);
        }
        boolean started = firstWaveTick >= 0;
        double since = (tick - firstWaveTick) / (double) GameSession.TICKS_PER_SECOND;
        if (started && since <= FAST_WINDOW_SECONDS) {
            killsInFastWindow++;
        }
    }

    void onPlantLost() {
        plantsLost++;
    }

    void onPlanted(String type) {
        planted.add(type);
    }

    void onSunCollected(int amount) {
        sunCollected += amount;
    }

    public int getKills() {
        return kills;
    }

    /**
     * How many zombies each plant killed, keyed by plant name; the mower's
     * tally is under {@link #MOWER}.
     */
    public Map<String, Integer> getKillsBy() {
        return Map.copyOf(killsBy);
    }

    /**
     * Every plant type the player put down, whether or not it survived.
     */
    public Set<String> getPlanted() {
        return Set.copyOf(planted);
    }

    public int getKillsInFastWindow() {
        return killsInFastWindow;
    }

    /**
     * Kills in the first column of a lane whose mower was already gone — the
     * last place a zombie can be stopped.
     */
    public int getKillsAtTheDoor() {
        return killsAtTheDoor;
    }

    public int getPlantsLost() {
        return plantsLost;
    }

    public int getSunCollected() {
        return sunCollected;
    }
}
