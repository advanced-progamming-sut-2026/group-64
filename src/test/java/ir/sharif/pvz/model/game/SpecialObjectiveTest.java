package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The battle screen draws its objective readout and its lawn markers straight
 * off the special-level engine, so that state has to be readable from outside.
 */
class SpecialObjectiveTest {

    private GameSession level(SpecialRules rules) {
        LevelSpec spec = new LevelSpec(Chapter.BIG_WAVE_BEACH, 3, 3, 1000,
                List.of("normal"), java.util.Map.of(), 0, false, false, false, rules);
        return new GameSession(spec, 3, List.of("peashooter"), new HashSet<>(), new Random(3));
    }

    @Test
    void anOrdinaryLevelHasNoSpecialRules() {
        GameSession session =
                new GameSession(3, List.of("peashooter"), new HashSet<>(), new Random(3));
        assertNotNull(session.getSpecial());
        assertNull(session.getSpecial().getRules(), "a plain level reports no rules");
    }

    @Test
    void theDeadLineColumnIsReadable() {
        GameSession session = level(SpecialRules.deadLine(3));
        assertEquals(SpecialRules.Type.DEAD_LINE, session.getSpecial().getRules().getType());
        assertEquals(3, session.getSpecial().getRules().getDeadlineColumn());
    }

    @Test
    void timedWarExposesItsKillCountAndClock() {
        GameSession session = level(SpecialRules.timedWar(5, 60));
        assertEquals(0, session.getSpecial().getKills());
        assertEquals(5, session.getSpecial().getRules().getTargetKills());

        session.cheats().spawnZombie("normal", 8, 1);
        session.cheats().releaseTheNuke();
        assertEquals(1, session.getSpecial().getKills(), "a kill should be counted");

        session.advance(GameSession.TICKS_PER_SECOND * 3);
        assertTrue(session.getElapsedSeconds() >= 3, "the clock should be readable");
    }

    @Test
    void loveYourPlantsExposesHowManyPlantsAreGone() {
        GameSession session = level(SpecialRules.loveYourPlants(3));
        assertEquals(0, session.getSpecial().getPlantLosses());
        assertEquals(3, session.getSpecial().getRules().getMaxPlantLosses());
    }

    @Test
    void protectedPlantsAreMarkedOnTheBoard() {
        GameSession session = level(SpecialRules.saveOurSeeds(
                java.util.Map.of(GameSession.COLS + 1, "wall-nut")));
        Plant guarded = session.plantAtTile(2, 2);
        assertNotNull(guarded, "the level should place its protected plant");
        assertTrue(session.isProtectedPlant(guarded), "and mark it as protected");
    }
}
