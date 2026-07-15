package ir.sharif.pvz.model.game;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds a playable minigame stage: the level shell plus its plugged-in
 * logic. Every minigame has three stages, each harder than the last.
 */
public final class Minigames {

    public static final List<String> NAMES = List.of("vasebreaker", "bowling", "i-zombie", "zombotany");
    public static final int STAGES = 3;

    /** The five placeable zombies of every i,Zombie stage (13 distinct overall). */
    private static final List<Map<String, Integer>> I_ZOMBIE_SETS = List.of(
            Map.of("normal", 50, "imp", 25, "conehead", 75, "newspaper", 75, "dodo-rider", 100),
            Map.of("normal", 50, "buckethead", 125, "jester", 100, "imp-dragon", 50, "all-star", 150),
            Map.of("knight", 150, "hunter", 125, "parasol", 100, "troglobite", 175, "normal", 50));

    private Minigames() {
    }

    /**
     * Builds a running session for the given minigame stage; zombotany is a
     * plain level whose zombies carry plant heads, the others plug in logic.
     */
    public static GameSession start(String name, int stage, int difficulty,
                                    List<String> selectedPlants, Random random) {
        LevelSpec level;
        MinigameLogic logic;
        switch (name) {
            case "vasebreaker" -> {
                level = shell(stage, List.of("normal"), false);
                logic = new VasebreakerGame(stage, random);
            }
            case "bowling" -> {
                level = bowlingLevel(stage);
                logic = new BowlingGame(random);
            }
            case "i-zombie" -> {
                level = shell(stage, List.of("normal"), false);
                logic = new IZombieGame(stage, I_ZOMBIE_SETS.get(stage - 1), random);
            }
            case "zombotany" -> {
                level = zombotanyLevel(stage);
                logic = null;
            }
            default -> {
                return null;
            }
        }
        GameSession session = new GameSession(level, difficulty, selectedPlants,
                new java.util.HashSet<>(), random);
        if (logic != null) {
            session.attachMinigame(logic);
        }
        return session;
    }

    /**
     * A bare board for games that drive themselves (vasebreaker, i-zombie):
     * night sky (no falling sun) and the waves get disabled by the logic.
     */
    private static LevelSpec shell(int stage, List<String> pool, boolean waveGraves) {
        return new LevelSpec(Chapter.ANCIENT_EGYPT, 90 + stage, 1, 1, pool,
                Map.of(), 0, true, false, waveGraves);
    }

    private static LevelSpec bowlingLevel(int stage) {
        List<String> pool = stage >= 3
                ? List.of("normal", "conehead", "buckethead", "newspaper")
                : List.of("normal", "conehead");
        return new LevelSpec(Chapter.ANCIENT_EGYPT, 90 + stage, 2 + stage, 3 + stage, pool,
                Map.of(), 0, true, false, false,
                SpecialRules.conveyorBelt(List.of("bowling-wallnut", "bowling-wallnut",
                        "explode-o-nut", "giant-wallnut")));
    }

    private static LevelSpec zombotanyLevel(int stage) {
        List<String> pool = switch (stage) {
            case 1 -> List.of("peashooter-zombie", "wallnut-zombie", "normal");
            case 2 -> List.of("peashooter-zombie", "wallnut-zombie", "jalapeno-zombie", "normal");
            default -> List.of("peashooter-zombie", "wallnut-zombie", "jalapeno-zombie",
                    "squash-zombie", "conehead");
        };
        return new LevelSpec(Chapter.ANCIENT_EGYPT, 90 + stage, 2 + stage, 3 + 2 * stage, pool,
                Map.of(), 0, false, false, false);
    }
}
