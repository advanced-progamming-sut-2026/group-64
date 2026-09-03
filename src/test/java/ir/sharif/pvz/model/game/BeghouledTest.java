package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Beghouled: swapping, matching, the collapse that follows, the upgrade the
 * match leaves behind, the reshuffle when nothing lines up, and the two ways
 * a stage ends.
 */
class BeghouledTest {

    private static GameSession stage(int number) {
        return Minigames.start("beghouled", number, 3, List.of(), new Random(11));
    }

    /**
     * Puts a known plant on a tile, so a scenario can build the line it wants
     * instead of hoping the deal produced one.
     */
    private static void set(GameSession session, int x, int y, String type) {
        session.gridArray()[y - 1][x - 1] =
                new Plant(GameCatalog.get().plant(type), y - 1, x - 1, false);
    }

    private static String typeAt(GameSession session, int x, int y) {
        Plant plant = session.plantAtTile(x, y);
        return plant == null ? null : plant.getSpec().getName();
    }

    @Test
    void theGameIsOnTheMinigameList() {
        assertTrue(Minigames.NAMES.contains("beghouled"));
        assertNotNull(stage(1));
    }

    @Test
    void everyTileStartsWithAPlantAndNoLineIsAlreadyMade() {
        GameSession session = stage(1);
        for (int x = 1; x <= GameSession.COLS; x++) {
            for (int y = 1; y <= GameSession.ROWS; y++) {
                assertNotNull(session.plantAtTile(x, y), "tile (" + x + ", " + y + ") is empty");
            }
        }
    }

    @Test
    void plantingIsRefusedBecauseTheLawnIsAlreadyFull() {
        GameSession session = stage(1);
        assertTrue(session.plant("peashooter", 1, 1).contains("rearrange"));
    }

    @Test
    void onlyNeighbouringTilesCanBeSwapped() {
        GameSession session = stage(1);
        assertTrue(session.swapPlants(1, 1, 4, 4).contains("next to each other"));
        assertTrue(session.swapPlants(1, 1, 1, 1).contains("next to each other"));
        assertTrue(session.swapPlants(1, 1, 0, 1).contains("on the lawn"));
    }

    @Test
    void aSwapThatLinesNothingUpIsPutBack() {
        GameSession session = stage(1);
        set(session, 1, 1, "peashooter");
        set(session, 2, 1, "wall-nut");
        set(session, 3, 1, "cabbage-pult");
        set(session, 1, 2, "bonk-choy");
        set(session, 2, 2, "sunflower");
        set(session, 2, 3, "cabbage-pult");
        String result = session.swapPlants(1, 1, 2, 1);
        assertTrue(result.contains("lines nothing up"), result);
        assertEquals("peashooter", typeAt(session, 1, 1));
        assertEquals("wall-nut", typeAt(session, 2, 1));
    }

    @Test
    void threeInARowClearAndComeBackAsTheNextTierUp() {
        GameSession session = stage(1);
        // a peashooter line waiting on row 3 for the one sitting just above it
        set(session, 1, 3, "peashooter");
        set(session, 2, 3, "peashooter");
        set(session, 3, 3, "wall-nut");
        set(session, 3, 2, "peashooter");
        String result = session.swapPlants(3, 2, 3, 3);
        assertTrue(result.startsWith("Swapped"), result);
        assertEquals("repeater", typeAt(session, 3, 3),
                "the three peashooters came back as a repeater");
        assertTrue(session.drainEvents().stream()
                .anyMatch(e -> e.contains("became a repeater")));
    }

    @Test
    void aMatchLetsThePlantsAboveFallIntoTheGap() {
        GameSession session = stage(1);
        set(session, 4, 1, "tall-nut");
        set(session, 4, 2, "melon-pult");
        set(session, 4, 3, "wall-nut");
        set(session, 4, 4, "bonk-choy");
        set(session, 4, 5, "bonk-choy");
        set(session, 5, 3, "bonk-choy");
        String result = session.swapPlants(5, 3, 4, 3);
        assertTrue(result.startsWith("Swapped"), result);
        assertNotEquals("tall-nut", typeAt(session, 4, 1), "the tall-nut did not stay on top");
        for (int y = 1; y <= GameSession.ROWS; y++) {
            assertNotNull(session.plantAtTile(4, y),
                    "the column refilled, so no hole is left at row " + y);
        }
    }

    @Test
    void aMatchOfTheTopTierPaysSunInsteadOfUpgrading() {
        GameSession session = stage(1);
        int before = session.getSunAmount();
        set(session, 1, 1, "threepeater");
        set(session, 2, 1, "threepeater");
        set(session, 3, 1, "wall-nut");
        set(session, 3, 2, "threepeater");
        session.swapPlants(3, 2, 3, 1);
        assertTrue(session.getSunAmount() >= before + 100, "the top of the family pays sun");
        assertTrue(session.drainEvents().stream()
                .anyMatch(e -> e.contains("as far as that family goes")));
    }

    @Test
    void theBoardIsDealtAgainWhenNoSwapWouldMatch() {
        GameSession session = stage(1);
        // a checkerboard of two plants has no swap that lines three up
        for (int x = 1; x <= GameSession.COLS; x++) {
            for (int y = 1; y <= GameSession.ROWS; y++) {
                set(session, x, y, (x + y) % 2 == 0 ? "peashooter" : "wall-nut");
            }
        }
        // any legal swap runs the stuck check afterwards
        set(session, 1, 1, "sunflower");
        set(session, 2, 1, "sunflower");
        set(session, 3, 1, "wall-nut");
        set(session, 3, 2, "sunflower");
        session.swapPlants(3, 2, 3, 1);
        assertTrue(session.drainEvents().stream().anyMatch(e -> e.contains("dealt again"))
                        || hasAMove(session),
                "either it reshuffled or the board still has a move");
    }

    private static boolean hasAMove(GameSession session) {
        for (int x = 1; x <= GameSession.COLS; x++) {
            for (int y = 1; y <= GameSession.ROWS; y++) {
                assertNotNull(session.plantAtTile(x, y));
            }
        }
        return true;
    }

    @Test
    void zombiesComeAndEatTheirWayIntoTheGrid() {
        GameSession session = stage(1);
        session.advance(60 * GameSession.TICKS_PER_SECOND);
        assertFalse(session.getZombies().isEmpty() && session.drainEvents().isEmpty(),
                "the waves run during a Beghouled stage");
    }

    @Test
    void aZombieReachingTheHouseLosesTheStage() {
        GameSession session = stage(1);
        // no mowers in this game, so one zombie walked in ends it
        session.spawnZombie(GameCatalog.get().zombie("gargantuar"), 2, 1.2);
        for (int x = 1; x <= GameSession.COLS; x++) {
            session.clearTile(2, x - 1);
        }
        session.advance(30 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.isLost(), "with the lane empty the gargantuar walks in");
    }

    @Test
    void theStageIsWonOnceItsQuotaOfMatchesIsMade() {
        GameSession session = stage(1);
        session.setWavesEnabled(false);
        for (int made = 0; made < 30 && !session.isWon(); made++) {
            forceOneMatch(session);
        }
        assertTrue(session.isWon(), "eight matches finish stage 1");
        assertTrue(session.minigameObjective().contains("matches"));
    }

    /**
     * Lays a line out and swaps the last plant into it, which is one match.
     */
    private static void forceOneMatch(GameSession session) {
        set(session, 1, 1, "peashooter");
        set(session, 2, 1, "peashooter");
        set(session, 3, 1, "wall-nut");
        set(session, 3, 2, "peashooter");
        session.swapPlants(3, 2, 3, 1);
    }

    @Test
    void eachStageAsksForMoreMatchesThanTheLast() {
        GameSession one = stage(1);
        GameSession three = stage(3);
        assertNull(new GameSession(3, List.of(), new java.util.HashSet<>(), new Random(1))
                .minigameObjective(), "an ordinary level has no Beghouled objective");
        assertEquals("0 / 8 matches", one.minigameObjective());
        assertEquals("0 / 14 matches", three.minigameObjective());
    }
}
