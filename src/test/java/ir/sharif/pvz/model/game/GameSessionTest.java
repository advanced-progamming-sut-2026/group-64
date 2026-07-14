package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GameSessionTest {

    private static GameSession newSession(List<String> plants) {
        return new GameSession(3, plants, new java.util.HashSet<>(), new Random(42));
    }

    /**
     * A session whose automatic waves are off, so scenarios control every zombie themselves.
     */
    private static GameSession newQuietSession(List<String> plants) {
        GameSession session = newSession(plants);
        session.setWavesEnabled(false);
        return session;
    }

    @Test
    void plantingCostsSunAndOccupiesTile() {
        GameSession session = newSession(List.of("sunflower", "peashooter"));
        assertEquals(50, session.getSunAmount());
        String result = session.plant("sunflower", 2, 3);
        assertEquals("Planted sunflower at (2, 3).", result);
        assertEquals(0, session.getSunAmount());
        assertNotNull(session.plantAtTile(2, 3));
        assertTrue(session.plant("sunflower", 2, 3).startsWith("Error"));
    }

    @Test
    void plantingRejectsUnselectedTypeAndBadTile() {
        GameSession session = newSession(List.of("sunflower"));
        assertTrue(session.plant("peashooter", 1, 1).startsWith("Error"));
        assertTrue(session.plant("sunflower", 10, 1).startsWith("Error"));
        assertTrue(session.plant("sunflower", 0, 0).startsWith("Error"));
    }

    @Test
    void rechargeCooldownBlocksImmediateReplanting() {
        GameSession session = newSession(List.of("sunflower"));
        session.cheatAddSuns(1000);
        session.plant("sunflower", 1, 1);
        assertTrue(session.plant("sunflower", 2, 1).startsWith("Error"));
        session.advance(5 * GameSession.TICKS_PER_SECOND + 1);
        assertFalse(session.plant("sunflower", 2, 1).startsWith("Error"));
    }

    @Test
    void cheatRemoveCooldownLiftsRecharge() {
        GameSession session = newSession(List.of("sunflower"));
        session.cheatAddSuns(1000);
        session.plant("sunflower", 1, 1);
        session.cheatRemoveCooldown();
        assertFalse(session.plant("sunflower", 2, 1).startsWith("Error"));
    }

    @Test
    void sunflowerProducesCollectableSunAfterItsPeriod() {
        GameSession session = newQuietSession(List.of("sunflower"));
        session.plant("sunflower", 1, 1);
        session.advance(24 * GameSession.TICKS_PER_SECOND + 5);
        List<String> events = session.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e.contains("produced a sun at (1, 1)")));
        int before = session.getSunAmount();
        assertEquals("Collected 25 sun; you now have " + (before + 25) + " sun.", session.collectSun(1, 1));
    }

    @Test
    void wavesSpawnZombiesAndReportCosts() {
        GameSession session = newSession(List.of());
        session.advance(11 * GameSession.TICKS_PER_SECOND);
        assertEquals(1, session.getCurrentWave());
        assertFalse(session.getZombies().isEmpty());
        assertTrue(session.drainEvents().stream().anyMatch(e -> e.startsWith("Wave 1 started.")));
    }

    @Test
    void zombieWalksLeftContinuously() {
        GameSession session = newQuietSession(List.of());
        session.cheatSpawnZombie("normal", 9, 3);
        double before = session.getZombies().get(0).getX();
        session.advance(GameSession.TICKS_PER_SECOND);
        double after = session.getZombies().get(0).getX();
        assertTrue(after < before);
        assertEquals(0.25, before - after, 1e-9);
    }

    @Test
    void armorAbsorbsDamageBeforeHealth() {
        Zombie zombie = new Zombie(GameCatalog.get().zombie("conehead"), 0, 9, 200,
                java.util.Map.of("cone", 370), false);
        assertFalse(zombie.damage(370));
        assertEquals(200, zombie.getHp());
        assertTrue(zombie.getArmor().isEmpty());
        assertTrue(zombie.damage(200));
    }

    @Test
    void mowerTriggersOnceThenBrainIsEaten() {
        GameSession session = newQuietSession(List.of());
        session.cheatSpawnZombie("imp", 1, 1);
        session.advance(40 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.getZombies().isEmpty());
        assertFalse(session.isMowerAvailable(0));
        assertFalse(session.isLost());
        session.cheatSpawnZombie("imp", 1, 1);
        session.advance(40 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.isLost());
        assertTrue(session.drainEvents().contains("The zombie ate your brain; LOSER!!!"));
    }

    @Test
    void nukeKillsEveryZombieOnTheMap() {
        GameSession session = newQuietSession(List.of());
        session.cheatSpawnZombie("normal", 9, 1);
        session.cheatSpawnZombie("gargantuar", 9, 5);
        session.releaseTheNuke();
        assertTrue(session.getZombies().isEmpty());
    }

    @Test
    void peashooterKillsAnApproachingZombie() {
        GameSession session = newQuietSession(List.of("peashooter"));
        session.cheatAddSuns(1000);
        session.plant("peashooter", 1, 1);
        session.cheatSpawnZombie("normal", 9, 1);
        session.advance(60 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.getZombies().isEmpty());
        assertNotNull(session.plantAtTile(1, 1));
    }

    @Test
    void zombieEatsPlantInItsWay() {
        GameSession session = newQuietSession(List.of("sunflower"));
        session.plant("sunflower", 8, 1);
        session.cheatSpawnZombie("normal", 9, 1);
        session.advance(30 * GameSession.TICKS_PER_SECOND);
        assertNull(session.plantAtTile(8, 1));
        assertTrue(session.drainEvents().stream()
                .anyMatch(e -> e.contains("sunflower at (8, 1) is destroyed")));
    }

    @Test
    void plantFoodOnSunProducerGrantsSun() {
        GameSession session = newQuietSession(List.of("sunflower"));
        session.plant("sunflower", 1, 1);
        session.cheatAddPlantFood();
        int before = session.getSunAmount();
        session.feedPlant(1, 1);
        assertEquals(before + 150, session.getSunAmount());
        assertEquals(0, session.getPlantFood());
        assertTrue(session.feedPlant(1, 1).startsWith("Error"));
    }

    @Test
    void spawnedZombieTypesAreRecordedForTheCollection() {
        GameSession session = newQuietSession(List.of());
        session.cheatSpawnZombie("knight", 9, 1);
        assertEquals(Set.of("knight"), session.getSeenZombieTypes());
    }
}
