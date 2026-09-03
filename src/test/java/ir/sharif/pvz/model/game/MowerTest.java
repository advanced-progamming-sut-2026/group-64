package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The lawn mower rolling down its lane. It used to clear the lane the instant
 * a zombie reached the house and never move; the sheet asks to see it travel
 * and to see it take the zombies on the way.
 */
class MowerTest {

    private static GameSession quietSession() {
        GameSession session = new GameSession(3, List.of("peashooter"), new HashSet<>(),
                new Random(6));
        session.setWavesEnabled(false);
        return session;
    }

    @Test
    void aZombieReachingTheHouseSetsTheMowerRollingRatherThanClearingTheLane() {
        GameSession session = quietSession();
        session.cheats().spawnZombie("normal", 2, 3);
        Zombie far = session.getZombies().get(0);
        session.cheats().spawnZombie("normal", 8, 3);

        assertTrue(session.isMowerAvailable(2));
        assertTrue(session.getRollingMowers().isEmpty(), "nothing is rolling yet");

        // far enough for the near zombie to reach the house and set it off,
        // but not for the mower to have crossed the whole lawn
        session.advance(6 * GameSession.TICKS_PER_SECOND);
        assertFalse(session.isMowerAvailable(2), "the mower has been spent");
        assertFalse(session.getZombies().contains(far), "it took the one that set it off");
        assertFalse(session.getRollingMowers().isEmpty(),
                "and is on the lawn rather than having cleared the lane at once");
        assertFalse(session.getZombies().isEmpty(),
                "the one further out has not been reached yet");
    }

    @Test
    void itCatchesUpWithTheZombiesFurtherDownTheLane() {
        GameSession session = quietSession();
        session.cheats().spawnZombie("normal", 2, 4);
        session.cheats().spawnZombie("normal", 7, 4);
        session.advance(30 * GameSession.TICKS_PER_SECOND);

        assertTrue(session.getZombies().isEmpty(), "the whole lane was mown");
        assertTrue(session.getRollingMowers().isEmpty(), "and the mower has driven off");
    }

    @Test
    void theMowerGetsTheCreditForWhatItKills() {
        GameSession session = quietSession();
        session.cheats().spawnZombie("normal", 2, 1);
        session.advance(20 * GameSession.TICKS_PER_SECOND);

        assertEquals(1, session.getLog().getKillsBy().getOrDefault(LevelLog.MOWER, 0),
                "a mower kill is a mower kill, not a plant's");
    }

    @Test
    void aLaneWithNoMowerLeftLosesTheLevel() {
        GameSession session = quietSession();
        session.cheats().spawnZombie("normal", 2, 5);
        session.advance(20 * GameSession.TICKS_PER_SECOND);
        assertFalse(session.isMowerAvailable(4));
        assertFalse(session.isLost(), "the first one through is caught by the mower");

        session.cheats().spawnZombie("normal", 2, 5);
        session.advance(30 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.isLost(), "the second one gets through");
    }
}
