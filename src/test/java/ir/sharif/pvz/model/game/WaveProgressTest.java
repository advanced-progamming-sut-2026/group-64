package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The wave bar promises the player two things: it is empty when the level
 * starts and full when the level is won, and it never runs backwards.
 */
class WaveProgressTest {

    private GameSession session() {
        return new GameSession(3, List.of("peashooter"), new HashSet<>(), new Random(11));
    }

    @Test
    void progressIsEmptyBeforeTheFirstWaveArrives() {
        GameSession session = session();
        session.advance(GameSession.TICKS_PER_SECOND * 5);

        assertEquals(0, session.getCurrentWave());
        assertEquals(0.0, session.getWaveProgress(), 1e-9);
    }

    @Test
    void progressStartsMovingOnceAWaveIsOnTheLawn() {
        GameSession session = session();
        // the first wave breaks in at the ten second mark
        session.advance(GameSession.TICKS_PER_SECOND * 11);

        assertTrue(session.getCurrentWave() >= 1, "a wave should have started by now");
        assertTrue(session.getWaveProgress() >= 0, "progress stays within its range");
        assertTrue(session.getWaveProgress() < 1, "the level is not over yet");
    }

    @Test
    void killingZombiesPushesTheBarForward() {
        GameSession session = session();
        session.advance(GameSession.TICKS_PER_SECOND * 11);
        double before = session.getWaveProgress();

        session.cheats().releaseTheNuke();
        session.advance(1);

        assertTrue(session.getWaveProgress() > before,
                "clearing the wave should move the bar along");
    }

    @Test
    void progressIsFullOnceTheLevelIsWon() {
        GameSession session = session();
        for (int i = 0; i < 400 && !session.isOver(); i++) {
            session.advance(GameSession.TICKS_PER_SECOND);
            session.cheats().releaseTheNuke();
        }

        assertTrue(session.isWon(), "nuking every wave should win the level");
        assertEquals(1.0, session.getWaveProgress(), 1e-9);
    }

    @Test
    void progressNeverRunsBackwards() {
        GameSession session = session();
        double highest = 0;
        for (int i = 0; i < 300 && !session.isOver(); i++) {
            session.advance(GameSession.TICKS_PER_SECOND / 2);
            double now = session.getWaveProgress();
            assertTrue(now >= highest - 1e-9,
                    "progress dropped from " + highest + " to " + now);
            highest = Math.max(highest, now);
        }
    }
}
