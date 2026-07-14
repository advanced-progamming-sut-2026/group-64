package ir.sharif.pvz.model.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the sixteen adventure levels (four days per chapter). Day 4 of each
 * chapter is the flag-heavy finale; boss fights arrive in the next phase.
 */
public final class Levels {

    private static final List<String> COMMON = List.of("normal", "conehead", "buckethead", "imp");
    private static final List<LevelSpec> ADVENTURE = build();

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
        return new LevelSpec(Chapter.ANCIENT_EGYPT, 0, 5, 6,
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
        days.add(new LevelSpec(Chapter.ANCIENT_EGYPT, 1, 3, 3, COMMON,
                Map.of(), 2, false, false, false));
        days.add(new LevelSpec(Chapter.ANCIENT_EGYPT, 2, 3, 4, merge(COMMON, "ra", "newspaper"),
                Map.of(), 3, false, false, false,
                SpecialRules.lockedPlants(java.util.Set.of("cherry-bomb", "potato-mine"),
                        List.of("wall-nut"))));
        days.add(new LevelSpec(Chapter.ANCIENT_EGYPT, 3, 4, 4, pool,
                Map.of(), 3, false, true, false,
                SpecialRules.timedWar(10, 120)));
        days.add(new LevelSpec(Chapter.ANCIENT_EGYPT, 4, 5, 5, merge(pool, "gargantuar"),
                Map.of(), 4, false, true, false));
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
        days.add(new LevelSpec(Chapter.FROSTBITE_CAVES, 1, 3, 4, merge(COMMON, "dodo-rider"),
                slides, 0, false, false, false));
        days.add(new LevelSpec(Chapter.FROSTBITE_CAVES, 2, 3, 5, merge(COMMON, "dodo-rider", "hunter"),
                slides, 0, false, false, false,
                SpecialRules.saveOurSeeds(Map.of(
                        LevelSpec.tileKey(1, 2), "wall-nut",
                        LevelSpec.tileKey(3, 2), "wall-nut"))));
        days.add(new LevelSpec(Chapter.FROSTBITE_CAVES, 3, 4, 5, pool,
                moreSlides, 0, false, false, false,
                SpecialRules.conveyorBelt(List.of("peashooter", "snow-pea", "wall-nut", "cherry-bomb"))));
        days.add(new LevelSpec(Chapter.FROSTBITE_CAVES, 4, 5, 6, merge(pool, "gargantuar"),
                moreSlides, 0, false, false, false));
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
        days.add(new LevelSpec(Chapter.BIG_WAVE_BEACH, 1, 3, 5, merge(COMMON, "snorkel"),
                shallows, 0, false, false, false));
        days.add(new LevelSpec(Chapter.BIG_WAVE_BEACH, 2, 4, 5, merge(COMMON, "snorkel", "parasol"),
                shallows, 0, false, false, false,
                SpecialRules.deadLine(3)));
        days.add(new LevelSpec(Chapter.BIG_WAVE_BEACH, 3, 4, 6, pool,
                deeps, 0, false, false, false,
                SpecialRules.loveYourPlants(5)));
        days.add(new LevelSpec(Chapter.BIG_WAVE_BEACH, 4, 5, 7, merge(pool, "gargantuar"),
                deeps, 0, false, false, false));
        return days;
    }

    private static List<LevelSpec> darkAges() {
        List<String> pool = merge(COMMON, "jester", "wizard", "king", "imp-dragon", "knight");
        Map<Integer, TileTerrain> necromancy = new HashMap<>();
        necromancy.put(LevelSpec.tileKey(0, 5), TileTerrain.SPAWNER);
        necromancy.put(LevelSpec.tileKey(3, 6), TileTerrain.SPAWNER);
        List<LevelSpec> days = new ArrayList<>();
        days.add(new LevelSpec(Chapter.DARK_AGES, 1, 3, 5, merge(COMMON, "jester"),
                Map.of(), 2, true, false, true));
        days.add(new LevelSpec(Chapter.DARK_AGES, 2, 4, 6, merge(COMMON, "jester", "wizard"),
                necromancy, 2, true, false, true,
                SpecialRules.nightOps()));
        days.add(new LevelSpec(Chapter.DARK_AGES, 3, 4, 6, pool,
                necromancy, 3, true, false, true,
                SpecialRules.plantWhatYouGet(800, java.util.Set.of("sunflower", "sun-shroom"))));
        days.add(new LevelSpec(Chapter.DARK_AGES, 4, 5, 8, merge(pool, "gargantuar"),
                necromancy, 3, true, false, true));
        return days;
    }

    private static List<String> merge(List<String> base, String... extras) {
        List<String> merged = new ArrayList<>(base);
        merged.addAll(List.of(extras));
        return merged;
    }
}
