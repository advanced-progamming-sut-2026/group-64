package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MinigameTest {

    @Test
    void vasebreakerBreaksVasesAndWinsWhenYardIsClear() {
        GameSession session = Minigames.start("vasebreaker", 1, 3, List.of(), new Random(7));
        List<String> vases = session.vasesInfo();
        assertEquals(9, vases.size());
        int broken = 0;
        for (int y = 1; y <= GameSession.ROWS; y++) {
            for (int x = 5; x <= GameSession.COLS; x++) {
                if (!session.breakVase(x, y).startsWith("Error")) {
                    broken++;
                }
            }
        }
        assertEquals(9, broken);
        session.releaseTheNuke();
        session.advance(1);
        assertTrue(session.isWon());
    }

    @Test
    void vasebreakerPacketsGoToHandAndPlantFree() {
        GameSession session = Minigames.start("vasebreaker", 1, 3, List.of(), new Random(7));
        boolean planted = false;
        for (int y = 1; y <= GameSession.ROWS && !planted; y++) {
            for (int x = 5; x <= GameSession.COLS && !planted; x++) {
                String broken = session.breakVase(x, y);
                if (broken.contains("seed packet")) {
                    String type = broken.split(" ")[1];
                    assertFalse(session.takePacket(x, y).startsWith("Error"));
                    assertEquals(List.of(type), session.conveyorBelt());
                    assertTrue(session.plant(type, 1, 1).startsWith("Planted"));
                    planted = true;
                }
            }
        }
        assertTrue(planted);
    }

    @Test
    void bowlingRestrictsPlantingAndNutsRollIntoZombies() {
        GameSession session = Minigames.start("bowling", 1, 3, List.of(), new Random(7));
        session.setWavesEnabled(false);
        assertTrue(session.plant("bowling-wallnut", 5, 1).startsWith("Error"));
        session.cheatSpawnZombie("normal", 8, 1);
        String belt = session.conveyorBelt().get(0);
        assertTrue(session.plant(belt, 1, 1).startsWith("Planted"));
        assertEquals(0, session.plantedPlants().size());
        session.advance(6 * GameSession.TICKS_PER_SECOND);
        List<String> events = session.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e.contains("rolling")),
                "nut should start rolling: " + events);
        assertTrue(session.getZombies().isEmpty() || events.stream().anyMatch(e -> e.contains("bounced")));
    }

    @Test
    void iZombiePlacesZombiesForSunAndEatsBrains() {
        GameSession session = Minigames.start("i-zombie", 1, 3, List.of(), new Random(7));
        assertEquals(150, session.getSunAmount());
        assertEquals(5, session.getZombies().size());
        assertTrue(session.placeZombie("gargantuar", 9, 1).startsWith("Error"));
        assertTrue(session.placeZombie("imp", 3, 1).startsWith("Error"));
        assertFalse(session.placeZombie("imp", 9, 1).startsWith("Error"));
        assertEquals(125, session.getSunAmount());
        assertTrue(session.plant("peashooter", 1, 5).startsWith("Error"));
    }

    @Test
    void iZombieWinsWhenAllBrainsAreEaten() {
        GameSession session = Minigames.start("i-zombie", 1, 3, List.of(), new Random(7));
        session.cheatAddSuns(100000);
        for (int wave = 0; wave < 8; wave++) {
            for (int row = 1; row <= GameSession.ROWS; row++) {
                assertFalse(session.placeZombie("conehead", 6, row).startsWith("Error"));
                assertFalse(session.placeZombie("conehead", 7, row).startsWith("Error"));
                assertFalse(session.placeZombie("normal", 6, row).startsWith("Error"));
                assertFalse(session.placeZombie("imp", 6, row).startsWith("Error"));
            }
            session.advance(25 * GameSession.TICKS_PER_SECOND);
            if (session.isWon()) {
                return;
            }
        }
        session.advance(150 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.isWon(), String.join("\n", session.drainEvents()));
    }

    @Test
    void zombotanySpawnsPlantHeadedZombies() {
        GameSession session = Minigames.start("zombotany", 1, 3,
                List.of("peashooter", "sunflower"), new Random(7));
        session.advance(12 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.getZombies().stream().anyMatch(z ->
                z.getSpec().getName().endsWith("-zombie") || z.getSpec().getName().equals("normal")));
    }

    @Test
    void jalapenoZombieBurnsItsRowAfterTenSeconds() {
        GameSession session = new GameSession(3, List.of("wall-nut"), new HashSet<>(), new Random(7));
        session.setWavesEnabled(false);
        session.cheatAddSuns(1000);
        session.plant("wall-nut", 2, 1);
        session.cheatSpawnZombie("jalapeno-zombie", 9, 1);
        session.advance(11 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.drainEvents().stream().anyMatch(e -> e.contains("ignited")));
        assertEquals(0, session.plantedPlants().size());
        assertTrue(session.getZombies().isEmpty());
    }
}
