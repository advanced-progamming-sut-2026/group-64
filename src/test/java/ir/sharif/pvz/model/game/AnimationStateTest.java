package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The state the view animates off. None of this draws anything, but a plant
 * cannot be seen to recoil and a zombie cannot be seen to use its trick unless
 * the engine says when those happened.
 */
class AnimationStateTest {

    private static GameSession quietSession(String plant) {
        GameSession session = new GameSession(3, List.of(plant), new HashSet<>(), new Random(7));
        session.setWavesEnabled(false);
        session.cheats().addSuns(3000);
        session.cheats().removeCooldown();
        return session;
    }

    @Test
    void aPlantThatHasJustFiredReadsAsHavingJustFired() {
        GameSession session = quietSession("peashooter");
        session.plant("peashooter", 2, 3);
        session.spawnZombie(GameCatalog.get().zombie("normal"), 2, 7);
        Plant peashooter = session.plantAtTile(2, 3);
        assertEquals(1, peashooter.sinceItActed(), 0.001, "a fresh plant is ready");

        session.advance(1);
        assertEquals(0, peashooter.sinceItActed(), 0.001, "it has just fired");

        session.advance(GameSession.TICKS_PER_SECOND);
        assertTrue(peashooter.sinceItActed() > 0.5,
                "and winds back up: " + peashooter.sinceItActed());
    }

    /**
     * A plant that never acts never kicks, which is what keeps a wall-nut still
     * while everything around it moves.
     */
    @Test
    void aPlantWithNoAttackIsAlwaysReportedAsReady() {
        GameSession session = quietSession("wall-nut");
        session.plant("wall-nut", 2, 3);
        session.advance(3 * GameSession.TICKS_PER_SECOND);
        assertEquals(1, session.plantAtTile(2, 3).sinceItActed(), 0.001);
    }

    @Test
    void aZombieUsingItsTrickLeavesSomethingToDraw() {
        GameSession session = quietSession("sunflower");
        session.plant("sunflower", 1, 3);
        session.spawnZombie(GameCatalog.get().zombie("ra"), 2, 6);
        session.getBursts().clear();

        session.advance(4 * GameSession.TICKS_PER_SECOND);

        assertTrue(session.getBursts().stream()
                        .anyMatch(burst -> burst.getKind() == Burst.Kind.ABILITY),
                "the sun stealer should have been seen doing it");
    }

    @Test
    void aPumpkinIsReportedSoItCanBeDrawnOverWhatItProtects() {
        GameSession session = quietSession("sunflower");
        session.plant("sunflower", 3, 3);
        Plant sunflower = session.plantAtTile(3, 3);
        assertFalse(session.shieldOn(sunflower) != null, "no pumpkin yet");

        GameSession withPumpkin = new GameSession(3, List.of("sunflower", "pumpkin"),
                new HashSet<>(), new Random(7));
        withPumpkin.setWavesEnabled(false);
        withPumpkin.cheats().addSuns(3000);
        withPumpkin.cheats().removeCooldown();
        withPumpkin.plant("sunflower", 3, 3);
        withPumpkin.plant("pumpkin", 3, 3);
        assertTrue(withPumpkin.shieldOn(withPumpkin.plantAtTile(3, 3)) != null,
                "the pumpkin is there to be drawn");
    }
}
