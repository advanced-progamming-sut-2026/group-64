package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The bits that come off a zombie: the armour a shot knocks loose, and the
 * head and arm that leave when the zombie does.
 */
class DismembermentTest {

    private static GameSession quietSession() {
        GameSession session = new GameSession(3, List.of("peashooter"), new HashSet<>(),
                new Random(21));
        session.setWavesEnabled(false);
        return session;
    }

    private static List<Debris> of(GameSession session, Debris.Kind kind) {
        return session.getDebris().stream().filter(piece -> piece.getKind() == kind).toList();
    }

    @Test
    void aZombieGoingDownLeavesItsHeadAndAnArmBehind() {
        GameSession session = quietSession();
        Zombie zombie = session.spawnZombie(GameCatalog.get().zombie("normal"), 2, 6);
        assertTrue(session.getDebris().isEmpty(), "nothing has come off yet");

        session.cheats().releaseTheNuke();

        assertEquals(1, of(session, Debris.Kind.HEAD).size(), "the head comes off");
        assertEquals(1, of(session, Debris.Kind.ARM).size(), "and an arm");
        assertEquals(zombie.getRow(), of(session, Debris.Kind.HEAD).get(0).getRow(),
                "they leave from the lane the zombie was in");
    }

    @Test
    void knockingTheConeOffDropsItWithoutKillingTheZombie() {
        GameSession session = quietSession();
        Zombie coneHead = session.spawnZombie(GameCatalog.get().zombie("conehead"), 1, 6);
        int cone = coneHead.getArmor().get("cone");

        session.hitZombie(coneHead, cone);

        assertTrue(coneHead.getArmor().isEmpty(), "the cone is off");
        assertTrue(session.getZombies().contains(coneHead), "but the zombie is still walking");
        List<Debris> armour = of(session, Debris.Kind.ARMOUR);
        assertEquals(1, armour.size(), "the cone itself falls away");
        assertEquals("cone", armour.get(0).getArt());
        assertTrue(of(session, Debris.Kind.HEAD).isEmpty(), "its head is still on");
    }

    @Test
    void aZombieStillWearingItsArmourDropsThatTooWhenItDies() {
        GameSession session = quietSession();
        session.spawnZombie(GameCatalog.get().zombie("buckethead"), 3, 6);
        session.cheats().releaseTheNuke();

        List<Debris> armour = of(session, Debris.Kind.ARMOUR);
        assertEquals(1, armour.size());
        assertEquals("bucket", armour.get(0).getArt());
        assertEquals(1, of(session, Debris.Kind.HEAD).size());
    }

    @Test
    void armourWeHaveNoSpriteForIsLeftUndrawnRatherThanDroppedAsSomethingElse() {
        GameSession session = quietSession();
        Zombie knight = session.spawnZombie(GameCatalog.get().zombie("knight"), 2, 6);
        assertFalse(knight.getArmor().isEmpty(), "a knight wears a crown and shoulder armour");

        session.cheats().releaseTheNuke();

        assertTrue(of(session, Debris.Kind.ARMOUR).isEmpty(),
                "no sprite for a crown, so nothing is invented for it");
        assertEquals(1, of(session, Debris.Kind.HEAD).size(), "the head still comes off");
    }

    @Test
    void aPieceTumblesUpThenLandsAndFadesAway() {
        GameSession session = quietSession();
        session.spawnZombie(GameCatalog.get().zombie("normal"), 2, 6);
        session.cheats().releaseTheNuke();
        Debris head = of(session, Debris.Kind.HEAD).get(0);
        double startedAt = head.getCol();

        session.advance(2);
        assertTrue(head.getLift() > 0, "it is thrown up first");
        assertTrue(head.getCol() > startedAt, "and back the way the zombie came");
        double spun = head.getSpin();

        session.advance(8 * GameSession.TICKS_PER_SECOND);
        assertEquals(0, head.getLift(), 0.0001, "it comes down");
        assertFalse(head.getSpin() == spun && spun == 0, "and turns on the way");
        assertTrue(session.getDebris().isEmpty(), "then it is cleared away");
    }

    @Test
    void theLawnDoesNotFillUpWithPiecesForever() {
        GameSession session = quietSession();
        for (int i = 0; i < 60; i++) {
            session.spawnZombie(GameCatalog.get().zombie("normal"), i % GameSession.ROWS, 8);
        }
        session.cheats().releaseTheNuke();
        assertTrue(session.getDebris().size() <= 60,
                "the pile is capped: " + session.getDebris().size());
    }
}
