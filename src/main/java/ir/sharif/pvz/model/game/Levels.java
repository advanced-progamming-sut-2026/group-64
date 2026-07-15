package ir.sharif.pvz.model.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the sixteen adventure levels (four days per chapter). Day 3 of every
 * chapter is its special level (four of the eight implemented kinds are
 * placed; the rest stay available as extras). Day 4 is the flag-heavy finale;
 * boss fights arrive in the next phase. Wave budgets follow the course rule:
 * the first wave costs at least 1000 and every wave adds at least 500, both
 * rising with the day and the chapter so later levels are strictly harder.
 */
public final class Levels {

    private static final List<String> COMMON = List.of("normal", "conehead", "buckethead", "imp");
    private static final int[] WAVES_PER_DAY = {3, 4, 4, 5};
    private static final List<LevelSpec> ADVENTURE = build();

    /** First-wave budget of a chapter (0-based) and day (1-based). */
    private static double budget(int chapter, int day) {
        return 1000 + 200 * chapter + 500 * (day - 1);
    }

    /** Per-wave budget increment of a chapter (0-based) and day (1-based). */
    private static double increment(int chapter, int day) {
        return 500 + 100 * chapter + 100 * (day - 1);
    }

    private static int waves(int day) {
        return WAVES_PER_DAY[day - 1];
    }

    private Levels() {
    }

    public static List<LevelSpec> adventure() {
        return ADVENTURE;
    }

    /**
     * The level the given number of already-passed levels unlocks next;
     * finishing everything keeps replaying the finale.
     */
    public static LevelSpec byProgress(int levelsPassed) {
        int index = Math.min(Math.max(levelsPassed, 0), ADVENTURE.size() - 1);
        return ADVENTURE.get(index);
    }

    /**
     * The score-game level: a flat lawn where only the mow points matter.
     * Day 0 marks it as the daily score game in the level title.
     */
    public static LevelSpec scoreGame() {
        return new LevelSpec(Chapter.ANCIENT_EGYPT, 0, 5, 1500,
                merge(COMMON, "knight", "newspaper", "all-star", "ra"),
                Map.of(), 0, false, false, false);
    }

    private static List<LevelSpec> build() {
        List<LevelSpec> levels = new ArrayList<>();
        levels.addAll(egypt());
        levels.addAll(frostbite());
        levels.addAll(beach());
        levels.addAll(darkAges());
        return levels;
    }

    private static List<LevelSpec> egypt() {
        List<String> pool = merge(COMMON, "ra", "explorer", "tombraiser", "newspaper");
        List<LevelSpec> days = new ArrayList<>();
        days.add(new LevelSpec(Chapter.ANCIENT_EGYPT, 1, waves(1), budget(0, 1), COMMON,
                Map.of(), 2, false, false, false).waveIncrement(increment(0, 1)));
        days.add(new LevelSpec(Chapter.ANCIENT_EGYPT, 2, waves(2), budget(0, 2),
                merge(COMMON, "ra", "newspaper"),
                Map.of(), 3, false, false, false).waveIncrement(increment(0, 2)));
        days.add(new LevelSpec(Chapter.ANCIENT_EGYPT, 3, waves(3), budget(0, 3), pool,
                Map.of(), 3, false, true, false,
                SpecialRules.timedWar(15, 150)).waveIncrement(increment(0, 3)));
        days.add(new LevelSpec(Chapter.ANCIENT_EGYPT, 4, waves(4), budget(0, 4),
                merge(pool, "gargantuar"),
                Map.of(), 4, false, true, false).waveIncrement(increment(0, 4)));
        return days;
    }

    private static List<LevelSpec> frostbite() {
        List<String> pool = merge(COMMON, "dodo-rider", "hunter", "troglobite");
        Map<Integer, TileTerrain> slides = new HashMap<>();
        slides.put(LevelSpec.tileKey(1, 4), TileTerrain.SLIPPERY_DOWN);
        slides.put(LevelSpec.tileKey(3, 5), TileTerrain.SLIPPERY_UP);
        Map<Integer, TileTerrain> moreSlides = new HashMap<>(slides);
        moreSlides.put(LevelSpec.tileKey(2, 6), TileTerrain.SLIPPERY_DOWN);
        List<LevelSpec> days = new ArrayList<>();
        days.add(new LevelSpec(Chapter.FROSTBITE_CAVES, 1, waves(1), budget(1, 1),
                merge(COMMON, "dodo-rider"),
                slides, 0, false, false, false).waveIncrement(increment(1, 1)));
        days.add(new LevelSpec(Chapter.FROSTBITE_CAVES, 2, waves(2), budget(1, 2),
                merge(COMMON, "dodo-rider", "hunter"),
                slides, 0, false, false, false).waveIncrement(increment(1, 2)));
        days.add(new LevelSpec(Chapter.FROSTBITE_CAVES, 3, waves(3), budget(1, 3), pool,
                moreSlides, 0, false, false, false,
                SpecialRules.conveyorBelt(List.of("peashooter", "snow-pea", "wall-nut", "cherry-bomb")))
                .waveIncrement(increment(1, 3)));
        days.add(new LevelSpec(Chapter.FROSTBITE_CAVES, 4, waves(4), budget(1, 4),
                merge(pool, "gargantuar"),
                moreSlides, 0, false, false, false).waveIncrement(increment(1, 4)));
        return days;
    }

    private static List<LevelSpec> beach() {
        List<String> pool = merge(COMMON, "fisherman", "snorkel", "octopus", "parasol");
        Map<Integer, TileTerrain> shallows = new HashMap<>();
        Map<Integer, TileTerrain> deeps = new HashMap<>();
        for (int row = 0; row < GameSession.ROWS; row++) {
            shallows.put(LevelSpec.tileKey(row, 8), TileTerrain.WATER);
            deeps.put(LevelSpec.tileKey(row, 8), TileTerrain.WATER);
            deeps.put(LevelSpec.tileKey(row, 7), TileTerrain.WATER);
        }
        deeps.put(LevelSpec.tileKey(2, 4), TileTerrain.SPAWNER);
        deeps.put(LevelSpec.tileKey(4, 3), TileTerrain.SPAWNER);
        List<LevelSpec> days = new ArrayList<>();
        days.add(new LevelSpec(Chapter.BIG_WAVE_BEACH, 1, waves(1), budget(2, 1),
                merge(COMMON, "snorkel"),
                shallows, 0, false, false, false).waveIncrement(increment(2, 1)));
        days.add(new LevelSpec(Chapter.BIG_WAVE_BEACH, 2, waves(2), budget(2, 2),
                merge(COMMON, "snorkel", "parasol"),
                shallows, 0, false, false, false).waveIncrement(increment(2, 2)));
        days.add(new LevelSpec(Chapter.BIG_WAVE_BEACH, 3, waves(3), budget(2, 3), pool,
                deeps, 0, false, false, false,
                SpecialRules.deadLine(3)).waveIncrement(increment(2, 3)));
        days.add(new LevelSpec(Chapter.BIG_WAVE_BEACH, 4, waves(4), budget(2, 4),
                merge(pool, "gargantuar"),
                deeps, 0, false, false, false).waveIncrement(increment(2, 4)));
        return days;
    }

    private static List<LevelSpec> darkAges() {
        List<String> pool = merge(COMMON, "jester", "wizard", "king", "imp-dragon", "knight");
        Map<Integer, TileTerrain> necromancy = new HashMap<>();
        necromancy.put(LevelSpec.tileKey(0, 5), TileTerrain.SPAWNER);
        necromancy.put(LevelSpec.tileKey(3, 6), TileTerrain.SPAWNER);
        List<LevelSpec> days = new ArrayList<>();
        days.add(new LevelSpec(Chapter.DARK_AGES, 1, waves(1), budget(3, 1),
                merge(COMMON, "jester"),
                Map.of(), 2, true, false, true).waveIncrement(increment(3, 1)));
        days.add(new LevelSpec(Chapter.DARK_AGES, 2, waves(2), budget(3, 2),
                merge(COMMON, "jester", "wizard"),
                necromancy, 2, true, false, true).waveIncrement(increment(3, 2)));
        days.add(new LevelSpec(Chapter.DARK_AGES, 3, waves(3), budget(3, 3), pool,
                necromancy, 3, true, false, true,
                SpecialRules.plantWhatYouGet(800, java.util.Set.of("sunflower", "sun-shroom")))
                .waveIncrement(increment(3, 3)));
        days.add(new LevelSpec(Chapter.DARK_AGES, 4, waves(4), budget(3, 4),
                merge(pool, "gargantuar"),
                necromancy, 3, true, false, true).waveIncrement(increment(3, 4)));
        return days;
    }

    private static List<String> merge(List<String> base, String... extras) {
        List<String> merged = new ArrayList<>(base);
        merged.addAll(List.of(extras));
        return merged;
    }
}
