package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Two things the lawn draws straight off the model: how frozen a plant is, and
 * whether a zombie is still wearing its armour.
 */
class FrostAndArmourTest {

    private GameSession session() {
        return new GameSession(3, List.of("wall-nut"), new HashSet<>(), new Random(4));
    }

    @Test
    void anUntouchedPlantHasNoIceOnIt() {
        GameSession session = session();
        session.cheats().addSuns(500);
        session.plant("wall-nut", 3, 3);

        assertEquals(0, session.iceLevelAt(3, 3));
    }

    @Test
    void hunterIceBuildsUpOverThreeSteps() {
        GameSession session = session();
        session.cheats().addSuns(500);
        session.plant("wall-nut", 3, 3);
        session.cheats().spawnZombie("hunter", 9, 3);

        int highest = 0;
        for (int i = 0; i < GameSession.TICKS_PER_SECOND * 40 && highest < 3; i++) {
            session.advance(1);
            highest = Math.max(highest, session.iceLevelAt(3, 3));
        }

        assertEquals(3, highest, "three hits should freeze the plant solid");
        assertTrue(session.isPlantDisabled(3, 3), "and a solid plant stops working");
    }

    @Test
    void anArmouredZombieReportsItsArmourUntilItIsGone() {
        GameSession session = session();
        session.cheats().spawnZombie("conehead", 8, 1);
        Zombie conehead = session.getZombies().get(0);

        assertFalse(conehead.getArmor().isEmpty(), "the cone starts on its head");
        assertFalse(conehead.getSpec().getArmor().isEmpty(), "and the type is an armoured one");

        // strip the cone without killing the zombie underneath
        int cone = conehead.getArmor().values().stream().mapToInt(Integer::intValue).sum();
        conehead.damage(cone);

        assertTrue(conehead.getArmor().isEmpty(), "a spent cone stops being reported");
        assertTrue(conehead.getHp() > 0, "the zombie itself is still walking");
    }

    @Test
    void aPlainZombieNeverClaimsArmour() {
        GameSession session = session();
        session.cheats().spawnZombie("normal", 8, 1);

        assertTrue(session.getZombies().get(0).getSpec().getArmor().isEmpty());
    }

    @Test
    void freezingAZombieIsVisibleInItsState() {
        GameSession session = session();
        session.cheats().spawnZombie("normal", 8, 1);
        Zombie zombie = session.getZombies().get(0);

        assertFalse(zombie.isFrozen());
        zombie.freeze(3);
        assertTrue(zombie.isFrozen(), "a frozen zombie is drawn inside an ice block");
    }
}
