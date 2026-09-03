package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The zombies the project sheet lists, and the numbers it gives them. The
 * collection reads all of this straight off the spec, so a wrong number here
 * is a wrong number in front of the player.
 */
class ZombieRosterTest {

    /**
     * Every row of the sheet's zombie table: our id, then hit points, speed,
     * eat damage per second and wave cost exactly as written there.
     */
    private static final Map<String, double[]> SHEET = Map.ofEntries(
            Map.entry("normal", new double[] {190, 0.185, 100, 100}),
            Map.entry("conehead", new double[] {190, 0.185, 100, 200}),
            Map.entry("buckethead", new double[] {190, 0.185, 100, 400}),
            Map.entry("blockhead", new double[] {190, 0.185, 100, 700}),
            Map.entry("knight", new double[] {190, 0.185, 100, 550}),
            Map.entry("gargantuar", new double[] {3600, 0.24, 1500, 1500}),
            Map.entry("imp", new double[] {190, 0.22, 100, 100}),
            Map.entry("ra", new double[] {190, 0.2, 100, 100}),
            Map.entry("explorer", new double[] {250, 0.25, 100, 250}),
            Map.entry("tombraiser", new double[] {380, 0.185, 100, 300}),
            Map.entry("dodo-rider", new double[] {490, 0.3, 100, 600}),
            Map.entry("hunter", new double[] {700, 0.12, 100, 500}),
            Map.entry("troglobite", new double[] {470, 0.185, 100, 600}),
            Map.entry("fisherman", new double[] {1000, 0.185, 100, 700}),
            Map.entry("octopus", new double[] {910, 0.12, 100, 900}),
            Map.entry("snorkel", new double[] {350, 0.185, 100, 200}),
            Map.entry("jester", new double[] {420, 0.2, 100, 450}),
            Map.entry("wizard", new double[] {490, 0.12, 100, 800}),
            Map.entry("king", new double[] {1000, 0, 100, 750}),
            Map.entry("imp-dragon", new double[] {190, 0.185, 100, 150}),
            Map.entry("all-star", new double[] {1100, 0.16, 100, 1000}),
            Map.entry("arcade", new double[] {490, 0.19, 100, 600}),
            Map.entry("parasol", new double[] {350, 0.25, 100, 200}),
            Map.entry("turquoise", new double[] {250, 0.185, 100, 500}),
            Map.entry("prospector", new double[] {190, 0.16, 100, 200}),
            Map.entry("piano", new double[] {840, 0.12, 4000, 450}),
            Map.entry("newspaper", new double[] {460, 0.22, 200, 700}));

    @Test
    void everyZombieTheSheetListsIsInTheCatalogWithItsOwnNumbers() {
        assertEquals(27, SHEET.size(), "the sheet lists 27 zombies");
        SHEET.forEach((name, stats) -> {
            ZombieSpec spec = GameCatalog.get().zombie(name);
            assertNotNull(spec, name + " is missing from the catalog");
            assertEquals((int) stats[0], spec.getHp(), name + " hit points");
            assertEquals(stats[1], spec.getTilesPerSecond(), 1e-9, name + " speed");
            assertEquals((int) stats[2], spec.getDamagePerSecond(), name + " eat damage");
            assertEquals((int) stats[3], spec.getWaveCost(), name + " wave cost");
        });
    }

    @Test
    void everyZombieHasArtworkAndSomethingToSayForItself() {
        for (ZombieSpec spec : GameCatalog.get().allZombies()) {
            assertFalse(spec.getDescription().isBlank(),
                    spec.getName() + " has no description for the collection");
            assertNotNull(ZombieRosterTest.class.getResource(
                    "/assets/zombies/" + spec.getName() + ".png"),
                    spec.getName() + " has no portrait");
        }
    }

    /**
     * The armoured ones are the only ones the sheet gives armour, and the
     * pieces have to be named the way the falling-parts code expects.
     */
    @Test
    void onlyTheArmouredZombiesWearAnything() {
        List<String> armoured = GameCatalog.get().allZombies().stream()
                .filter(spec -> !spec.getArmor().isEmpty())
                .map(ZombieSpec::getName)
                .toList();
        assertEquals(List.of("conehead", "buckethead", "blockhead", "knight", "newspaper"),
                armoured, "the sheet gives armour to these and no others");
        assertTrue(GameCatalog.get().zombie("conehead").getArmor().containsKey("cone"));
        assertTrue(GameCatalog.get().zombie("buckethead").getArmor().containsKey("bucket"));
        assertTrue(GameCatalog.get().zombie("blockhead").getArmor().containsKey("block"));
    }

    /**
     * The four the sheet lists from chapters this build has no levels for are
     * still reachable: i,Zombie is the mode where the player picks zombies, so
     * they are placeable there rather than sitting only in the data.
     */
    @Test
    void theZombiesWithNoChapterOfTheirOwnAreStillPlayable() {
        List<String> placeable = new java.util.ArrayList<>();
        for (int stage = 1; stage <= Minigames.STAGES; stage++) {
            GameSession session = Minigames.start("i-zombie", stage, 3, List.of(),
                    new java.util.Random(3));
            placeable.addAll(session.getMinigame().cardsInsteadOfPlants().keySet());
        }
        for (String name : List.of("arcade", "turquoise", "prospector", "piano")) {
            assertTrue(placeable.contains(name),
                    name + " is in the sheet but nowhere in the game");
        }
    }

    /**
     * The five we added ourselves for the zombotany and i-Zombie minigames are
     * not the sheet's, and are the only extras.
     */
    @Test
    void theOnlyZombiesBeyondTheSheetAreTheMinigameOnes() {
        List<String> extra = GameCatalog.get().allZombies().stream()
                .map(ZombieSpec::getName)
                .filter(name -> !SHEET.containsKey(name))
                .toList();
        assertEquals(List.of("peashooter-zombie", "wallnut-zombie", "jalapeno-zombie",
                "squash-zombie", "sun-zombie"), extra);
    }
}
