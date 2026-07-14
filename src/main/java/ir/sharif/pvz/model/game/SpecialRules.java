package ir.sharif.pvz.model.game;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The knobs of one special level. Each of the eight kinds appears at least
 * once in the adventure, on days 2 and 3 of every chapter.
 */
public final class SpecialRules {

    /** The eight special level kinds of the project document. */
    public enum Type {
        CONVEYOR_BELT,
        LOCKED_PLANTS,
        SAVE_OUR_SEEDS,
        TIMED_WAR,
        NIGHT_OPS,
        DEAD_LINE,
        LOVE_YOUR_PLANTS,
        PLANT_WHAT_YOU_GET
    }

    private final Type type;
    private final List<String> conveyorPlants;
    private final Set<String> lockedPlants;
    private final List<String> forcedPlants;
    /** tileKey -> plant type pre-placed and protected; losing one loses the level. */
    private final Map<Integer, String> protectedPlants;
    private final int targetKills;
    private final int timerSeconds;
    private final int deadlineColumn;
    private final int maxPlantLosses;
    private final int initialSun;

    private SpecialRules(Type type, List<String> conveyorPlants, Set<String> lockedPlants,
                         List<String> forcedPlants, Map<Integer, String> protectedPlants,
                         int targetKills, int timerSeconds, int deadlineColumn,
                         int maxPlantLosses, int initialSun) {
        this.type = type;
        this.conveyorPlants = List.copyOf(conveyorPlants);
        this.lockedPlants = Set.copyOf(lockedPlants);
        this.forcedPlants = List.copyOf(forcedPlants);
        this.protectedPlants = Map.copyOf(protectedPlants);
        this.targetKills = targetKills;
        this.timerSeconds = timerSeconds;
        this.deadlineColumn = deadlineColumn;
        this.maxPlantLosses = maxPlantLosses;
        this.initialSun = initialSun;
    }

    public static SpecialRules conveyorBelt(List<String> plants) {
        return new SpecialRules(Type.CONVEYOR_BELT, plants, Set.of(), List.of(), Map.of(),
                0, 0, 0, 0, 0);
    }

    public static SpecialRules lockedPlants(Set<String> locked, List<String> forced) {
        return new SpecialRules(Type.LOCKED_PLANTS, List.of(), locked, forced, Map.of(),
                0, 0, 0, 0, 0);
    }

    public static SpecialRules saveOurSeeds(Map<Integer, String> protectedPlants) {
        return new SpecialRules(Type.SAVE_OUR_SEEDS, List.of(), Set.of(), List.of(), protectedPlants,
                0, 0, 0, 0, 0);
    }

    public static SpecialRules timedWar(int targetKills, int timerSeconds) {
        return new SpecialRules(Type.TIMED_WAR, List.of(), Set.of(), List.of(), Map.of(),
                targetKills, timerSeconds, 0, 0, 0);
    }

    public static SpecialRules nightOps() {
        return new SpecialRules(Type.NIGHT_OPS, List.of(), Set.of(), List.of(), Map.of(),
                0, 0, 0, 0, 0);
    }

    public static SpecialRules deadLine(int column) {
        return new SpecialRules(Type.DEAD_LINE, List.of(), Set.of(), List.of(), Map.of(),
                0, 0, column, 0, 0);
    }

    public static SpecialRules loveYourPlants(int maxPlantLosses) {
        return new SpecialRules(Type.LOVE_YOUR_PLANTS, List.of(), Set.of(), List.of(), Map.of(),
                0, 0, 0, maxPlantLosses, 0);
    }

    public static SpecialRules plantWhatYouGet(int initialSun, Set<String> lockedSunProducers) {
        return new SpecialRules(Type.PLANT_WHAT_YOU_GET, List.of(), lockedSunProducers, List.of(),
                Map.of(), 0, 0, 0, 0, initialSun);
    }

    public Type getType() {
        return type;
    }

    public List<String> getConveyorPlants() {
        return conveyorPlants;
    }

    public Set<String> getLockedPlants() {
        return lockedPlants;
    }

    public List<String> getForcedPlants() {
        return forcedPlants;
    }

    public Map<Integer, String> getProtectedPlants() {
        return protectedPlants;
    }

    public int getTargetKills() {
        return targetKills;
    }

    public int getTimerSeconds() {
        return timerSeconds;
    }

    public int getDeadlineColumn() {
        return deadlineColumn;
    }

    public int getMaxPlantLosses() {
        return maxPlantLosses;
    }

    public int getInitialSun() {
        return initialSun;
    }

    public String describe() {
        return switch (type) {
            case CONVEYOR_BELT -> "Conveyor belt: plants arrive on the belt every 12s; plant them for free.";
            case LOCKED_PLANTS -> "Locked plants: " + lockedPlants + " are unavailable"
                    + (forcedPlants.isEmpty() ? "." : "; you must bring " + forcedPlants + ".");
            case SAVE_OUR_SEEDS -> "Save our seeds: protect the pre-planted plants or lose instantly!";
            case TIMED_WAR -> "Timed war: kill " + targetKills + " zombies within " + timerSeconds + "s!";
            case NIGHT_OPS -> "Night ops: no sun falls from the sky.";
            case DEAD_LINE -> "Dead line: no zombie may cross column " + deadlineColumn + "!";
            case LOVE_YOUR_PLANTS -> "Love your plants: losing " + maxPlantLosses + " plants loses the level!";
            case PLANT_WHAT_YOU_GET -> "Plant what you get: " + initialSun
                    + " starting sun, no sky sun; type 'start zombie waves' when ready.";
        };
    }
}
