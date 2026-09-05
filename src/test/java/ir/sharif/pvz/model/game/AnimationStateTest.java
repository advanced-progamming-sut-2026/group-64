package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    /**
     * Each trick that looks like something leaves its own mark. They were all
     * the same purple ring, so the player could see that a zombie had done
     * something but never what.
     */
    @Test
    void eachTrickLeavesItsOwnMarkRatherThanOneSharedFlash() {
        assertEquals(Burst.Kind.SUN_STOLEN, markLeftBy("ra"));
        assertEquals(Burst.Kind.BONES, markLeftBy("tombraiser"));
        assertEquals(Burst.Kind.ICE_THROW, markLeftBy("hunter"));
        assertEquals(Burst.Kind.OCTOPUS_THROW, markLeftBy("octopus"));
        assertEquals(Burst.Kind.KICK, markLeftBy("all-star"));
        assertEquals(Burst.Kind.SMASH, markLeftBy("gargantuar"));
    }

    /**
     * The gargantuar's imp used to be put down at its landing spot with no
     * flight at all. It leaves the gargantuar and arcs over the plants, and is
     * out of the game while it is up there.
     */
    @Test
    void theHurledImpFliesInsteadOfAppearingWhereItLands() {
        GameSession session = quietSession("peashooter");
        Zombie big = session.spawnZombie(GameCatalog.get().zombie("gargantuar"), 2, 8);
        session.hitZombie(big, big.getSpec().getHp() / 2 + 1);

        Zombie imp = null;
        for (int i = 0; i < GameSession.TICKS_PER_SECOND * 30 && imp == null; i++) {
            session.advance(1);
            imp = session.getZombies().stream()
                    .filter(z -> z.getSpec().getName().equals("imp"))
                    .findFirst().orElse(null);
        }
        assertNotNull(imp, "the gargantuar should throw its imp");
        assertTrue(imp.isAirborne(), "and it starts in the air");
        assertTrue(imp.getX() > 7, "leaving from the gargantuar, not the far end");
        assertTrue(imp.getLift() >= 0);

        double startedAt = imp.getX();
        session.advance(GameSession.TICKS_PER_SECOND / 2);
        assertTrue(imp.getX() < startedAt, "it travels while it is up there");
        assertTrue(imp.getLift() > 0, "arcing over the plants");

        session.advance(GameSession.TICKS_PER_SECOND * 2);
        assertFalse(imp.isAirborne(), "it comes down");
        assertEquals(0, imp.getLift(), 0.001);
    }

    /**
     * While it is in the air it is over the plants, so nothing shoots it.
     */
    @Test
    void aThrownZombieIsNotShotWhileItIsStillUpThere() {
        GameSession session = quietSession("peashooter");
        session.cheats().addSuns(500);
        session.cheats().removeCooldown();
        session.plant("peashooter", 1, 3);
        Zombie flying = session.spawnZombie(GameCatalog.get().zombie("imp"), 2, 8);
        flying.throwTo(4, 1.5);
        int before = flying.totalRemainingHealth();

        session.advance(GameSession.TICKS_PER_SECOND);
        assertTrue(flying.isAirborne(), "still in the air");
        assertEquals(before, flying.totalRemainingHealth(), "and untouched up there");

        session.advance(GameSession.TICKS_PER_SECOND * 3);
        assertFalse(flying.isAirborne());
        assertTrue(flying.totalRemainingHealth() < before, "once down, it is fair game");
    }

    /** A trick we draw nothing special for still says something happened. */
    @Test
    void aTrickWithNoPictureOfItsOwnStillLeavesThePlainFlash() {
        assertEquals(Burst.Kind.ABILITY, markLeftBy("wizard"));
    }

    /** Runs one zombie until it uses its trick, and reports what it left. */
    private Burst.Kind markLeftBy(String zombie) {
        GameSession session = quietSession("sunflower");
        session.plant("sunflower", 1, 3);
        session.spawnZombie(GameCatalog.get().zombie(zombie), 2, 6);
        for (int i = 0; i < GameSession.TICKS_PER_SECOND * 30; i++) {
            session.getBursts().clear();
            session.advance(1);
            for (Burst burst : session.getBursts()) {
                if (burst.getKind() != Burst.Kind.PLANT_LOST
                        && burst.getKind() != Burst.Kind.ZOMBIE_DOWN) {
                    return burst.getKind();
                }
            }
        }
        return null;
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
