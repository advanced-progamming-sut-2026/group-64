package ir.sharif.pvz.model.game;

import java.util.List;
import java.util.Map;

/**
 * A level the player walked away from, in enough detail to pick it up exactly
 * where they left it.
 *
 * <p>Everything here is plain data so it can go straight to JSON next to the
 * accounts. Nothing that can be worked out again is stored: the level itself is
 * named by its chapter and day and looked up on the way back in. The shots,
 * blast effects and tumbling zombie parts are left behind too — they are all
 * over within a few seconds, and a player coming back to a level would never
 * know they had been mid-flight.
 */
public record SavedGame(String chapter, int day, int difficulty, long ticks,
                        List<String> selectedPlants, Map<String, Integer> plantLevels,
                        List<String> boostedPlants, Map<String, Double> cooldowns,
                        int sun, int plantFood, int coins, int diamonds, int pots,
                        List<Boolean> mowers, List<String> seenZombies,
                        List<PlantState> plants, List<ZombieState> zombies,
                        List<SunState> suns, Map<String, String> terrain,
                        WaveState wave) {

    /** A plant on the lawn, with whatever it has been through. */
    public record PlantState(String type, int row, int col, int hp, int level,
                             int stack, double age, boolean boosted, String shield,
                             int shieldHp, String disabledBy, boolean protectedPlant) {
    }

    /** A zombie mid-walk, with its armour and whatever is slowing it down. */
    public record ZombieState(String type, int row, double x, int hp,
                              Map<String, Integer> armor, boolean glowing,
                              double chilled, double frozen, double poisoned,
                              int poisonPerSecond, boolean hypnotized, double eatProgress) {
    }

    /** A sun on the ground or still falling. */
    public record SunState(String kind, int row, int col, double falling, int value) {
    }

    /** How far the waves have got. */
    public record WaveState(int currentWave, double budget, int spawnedHealth, boolean enabled) {
    }
}
