package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The projectiles added in phase 2 are cosmetic: they must appear when a plant
 * fires, travel toward the target and then clear themselves up, without ever
 * changing the damage phase 1 already applied.
 */
class ProjectileTest {

    private GameSession session() {
        // the engine consumes boosts out of this set, so it has to be mutable
        return new GameSession(3, List.of("peashooter", "cabbage-pult"),
                new HashSet<>(), new Random(7));
    }

    @Test
    void shooterRecordsAStraightShotWhenItFires() {
        GameSession session = session();
        session.cheats().addSuns(1000);
        session.plant("peashooter", 1, 1);
        session.cheats().spawnZombie("normal", 8, 1);

        session.advance(GameSession.TICKS_PER_SECOND * 2);

        assertFalse(session.getShots().isEmpty(), "firing should leave a visible shot");
        Shot shot = session.getShots().get(0);
        assertEquals(Shot.Flight.STRAIGHT, shot.getFlight());
        assertEquals("pea", shot.getKind());
        assertEquals(0, shot.getRow());
    }

    @Test
    void lobberRecordsAnArcingShot() {
        GameSession session = session();
        session.cheats().addSuns(1000);
        session.plant("cabbage-pult", 1, 2);
        session.cheats().spawnZombie("normal", 8, 2);

        // a cabbage-pult throws every few seconds and each shot is airborne only
        // briefly, so watch the whole window rather than one moment of it
        boolean sawLob = false;
        for (int tick = 0; tick < GameSession.TICKS_PER_SECOND * 6 && !sawLob; tick++) {
            session.advance(1);
            sawLob = session.getShots().stream()
                    .anyMatch(shot -> shot.getFlight() == Shot.Flight.LOBBED);
        }

        assertTrue(sawLob, "a lobber should arc its shot over the row");
    }

    @Test
    void aShotTravelsTowardsItsTargetAndThenDisappears() {
        GameSession session = session();
        session.cheats().addSuns(1000);
        session.plant("peashooter", 1, 1);
        session.cheats().spawnZombie("normal", 8, 1);
        session.advance(GameSession.TICKS_PER_SECOND * 2);

        Shot shot = session.getShots().get(0);
        double start = shot.currentX();
        session.advance(2);
        assertTrue(shot.currentX() > start, "the shot should move down the row");

        session.advance(GameSession.TICKS_PER_SECOND);
        assertTrue(shot.isDone(), "a landed shot should report itself finished");
        assertFalse(session.getShots().contains(shot), "finished shots are cleared away");
    }

    @Test
    void shotsCarryNoDamageOfTheirOwn() {
        GameSession session = session();
        session.cheats().addSuns(1000);
        session.plant("peashooter", 1, 1);
        session.cheats().spawnZombie("normal", 8, 1);
        session.advance(GameSession.TICKS_PER_SECOND * 2);

        Zombie zombie = session.getZombies().get(0);
        int healthWhileShotIsInTheAir = zombie.getHp();
        // letting the shot land must not subtract anything a second time
        session.advance(3);
        assertEquals(healthWhileShotIsInTheAir, zombie.getHp());
    }
}
