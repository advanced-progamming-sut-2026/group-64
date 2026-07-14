package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ScoreGameTest {

    @Test
    void everyKillPaysBasePoints() {
        ScoreTracker tracker = new ScoreTracker();
        Zombie zombie = new Zombie(GameCatalog.get().zombie("normal"), 0, 9, 200, Map.of(), false);
        tracker.onSpawn(zombie, 0);
        tracker.onKill(zombie, 1000);
        assertEquals(ScoreTracker.KILL_POINTS, tracker.getPoints());
    }

    @Test
    void quickKillsAndArmorBreakersPayExtra() {
        ScoreTracker tracker = new ScoreTracker();
        Zombie armored = new Zombie(GameCatalog.get().zombie("conehead"), 0, 9, 200,
                Map.of("cone", 370), false);
        tracker.onSpawn(armored, 0);
        tracker.onKill(armored, 30);
        assertEquals(ScoreTracker.KILL_POINTS + ScoreTracker.QUICK_KILL_POINTS
                + ScoreTracker.ARMOR_BREAKER_POINTS, tracker.getPoints());
    }

    @Test
    void threeKillsInOneTickScoreABlast() {
        ScoreTracker tracker = new ScoreTracker();
        for (int i = 0; i < 3; i++) {
            Zombie zombie = new Zombie(GameCatalog.get().zombie("normal"), i, 9, 200, Map.of(), false);
            tracker.onSpawn(zombie, 0);
            tracker.onKill(zombie, 500);
        }
        assertEquals(3 * ScoreTracker.KILL_POINTS + ScoreTracker.BLAST_POINTS, tracker.getPoints());
        assertTrue(tracker.breakdown().stream().anyMatch(line -> line.startsWith("blasts")));
    }

    @Test
    void unusedMowersPayBonusAtTheWin() {
        ScoreTracker tracker = new ScoreTracker();
        tracker.addMowerBonus(5);
        assertEquals(5 * ScoreTracker.MOWER_POINTS, tracker.getPoints());
    }

    @Test
    void sessionFeedsTheTrackerThroughItsHooks() {
        GameSession session = new GameSession(3, List.of(), new HashSet<>(), new Random(1));
        session.setWavesEnabled(false);
        ScoreTracker tracker = new ScoreTracker();
        session.attachScoreTracker(tracker);
        session.cheatSpawnZombie("normal", 9, 1);
        session.releaseTheNuke();
        assertTrue(tracker.getPoints() >= ScoreTracker.KILL_POINTS);
        assertEquals(GameSession.ROWS, session.unusedMowers());
    }

    @Test
    void sameSeedProducesTheSameDailyWaves() {
        List<String> first = playScoreOpening(42);
        List<String> second = playScoreOpening(42);
        assertEquals(first, second);
    }

    private List<String> playScoreOpening(long seed) {
        GameSession session = new GameSession(Levels.scoreGame(), 3, List.of(),
                new HashSet<>(), new Random(seed));
        session.advance(15 * GameSession.TICKS_PER_SECOND);
        return session.drainEvents();
    }
}
