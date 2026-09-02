package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The lawn's one-off effects — blasts, zombies coming apart, the camera shake —
 * are driven entirely by the bursts the engine records, so those have to appear
 * at the right moments and clean themselves up afterwards.
 */
class BurstTest {

    private GameSession session(String... plants) {
        return new GameSession(3, List.of(plants), new HashSet<>(), new Random(1));
    }

    @Test
    void anExplosivePlantRecordsABlastWhereItWent() {
        GameSession session = session("cherry-bomb");
        session.cheats().addSuns(2000);
        session.plant("cherry-bomb", 5, 3);
        session.advance(1);

        Burst blast = session.getBursts().stream()
                .filter(burst -> burst.getKind() == Burst.Kind.EXPLOSION)
                .findFirst().orElse(null);
        assertTrue(blast != null, "a cherry bomb should leave a blast to draw");
        assertEquals(5, blast.getCol(), 0.001);
        assertEquals(3, blast.getRow(), 0.001);
    }

    @Test
    void aBlastShakesTheCameraAndThenStops() {
        GameSession session = session("cherry-bomb");
        session.cheats().addSuns(2000);
        session.plant("cherry-bomb", 5, 3);
        session.advance(1);

        double atTheFlash = session.getBursts().stream()
                .mapToDouble(Burst::shake).max().orElse(0);
        assertTrue(atTheFlash > 0, "an explosion should shake the camera");

        session.advance(3);
        double later = session.getBursts().stream().mapToDouble(Burst::shake).max().orElse(0);
        assertTrue(later < atTheFlash, "and the shake should die down");
    }

    @Test
    void everyKilledZombieLeavesAnEffect() {
        GameSession session = session("cherry-bomb");
        session.cheats().spawnZombie("normal", 8, 1);
        session.cheats().spawnZombie("normal", 7, 2);
        session.cheats().releaseTheNuke();

        long downed = session.getBursts().stream()
                .filter(burst -> burst.getKind() == Burst.Kind.ZOMBIE_DOWN).count();
        assertEquals(2, downed, "both zombies should be shown going down");
    }

    @Test
    void burstsClearThemselvesUp() {
        GameSession session = session("cherry-bomb");
        session.cheats().spawnZombie("normal", 8, 1);
        session.cheats().releaseTheNuke();
        assertFalse(session.getBursts().isEmpty());

        session.advance(GameSession.TICKS_PER_SECOND);

        assertTrue(session.getBursts().isEmpty(), "a spent effect should be dropped");
    }

    @Test
    void feedingAPlantIsShown() {
        GameSession session = session("wall-nut");
        session.cheats().addSuns(2000);
        session.plant("wall-nut", 2, 2);
        session.cheats().addPlantFood();
        session.feedPlant(2, 2);

        assertTrue(session.getBursts().stream()
                        .anyMatch(burst -> burst.getKind() == Burst.Kind.PLANT_FOOD),
                "plant food should glow behind the plant");
    }
}
