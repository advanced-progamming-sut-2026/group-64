package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SpecialLevelTest {

    private static GameSession session(SpecialRules rules, List<String> plants) {
        LevelSpec level = new LevelSpec(Chapter.ANCIENT_EGYPT, 2, 4, 4, List.of("normal"),
                Map.of(), 0, false, false, false, rules);
        GameSession session = new GameSession(level, 3, plants, new HashSet<>(), new Random(5));
        session.setWavesEnabled(false);
        return session;
    }

    @Test
    void allEightSpecialTypesAppearInTheAdventure() {
        List<SpecialRules.Type> seen = Levels.adventure().stream()
                .map(LevelSpec::getSpecial)
                .filter(java.util.Objects::nonNull)
                .map(SpecialRules::getType)
                .distinct()
                .toList();
        assertEquals(SpecialRules.Type.values().length, seen.size());
    }

    @Test
    void conveyorBeltDeliversFreePlants() {
        GameSession session = session(SpecialRules.conveyorBelt(List.of("peashooter")), List.of());
        assertTrue(session.isConveyorLevel());
        assertEquals(List.of("peashooter"), session.conveyorBelt());
        assertTrue(session.plant("wall-nut", 1, 1).startsWith("Error"));
        int sunBefore = session.getSunAmount();
        assertTrue(session.plant("peashooter", 1, 1).startsWith("Planted"));
        assertEquals(sunBefore, session.getSunAmount());
        assertTrue(session.conveyorBelt().isEmpty());
        session.advance(13 * GameSession.TICKS_PER_SECOND);
        assertEquals(List.of("peashooter"), session.conveyorBelt());
    }

    @Test
    void protectedPlantLossFailsTheLevelImmediately() {
        SpecialRules rules = SpecialRules.saveOurSeeds(Map.of(LevelSpec.tileKey(0, 4), "wall-nut"));
        GameSession session = session(rules, List.of());
        assertNotNull(session.plantAtTile(5, 1));
        assertTrue(session.pluck(5, 1).startsWith("Error"));
        session.cheatSpawnZombie("gargantuar", 6, 1);
        session.advance(60 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.isLost());
        assertTrue(session.drainEvents().stream().anyMatch(e -> e.contains("protected plant")));
    }

    @Test
    void timedWarWinsOnTargetAndLosesOnTimeout() {
        GameSession quickWin = session(SpecialRules.timedWar(1, 60), List.of());
        quickWin.cheatSpawnZombie("normal", 9, 1);
        quickWin.releaseTheNuke();
        quickWin.advance(1);
        assertTrue(quickWin.isWon());

        GameSession timeout = session(SpecialRules.timedWar(5, 1), List.of());
        timeout.advance(2 * GameSession.TICKS_PER_SECOND);
        assertTrue(timeout.isLost());
    }

    @Test
    void deadLineLosesWhenAZombieCrossesTheColumn() {
        GameSession session = session(SpecialRules.deadLine(4), List.of());
        session.cheatSpawnZombie("imp", 5, 1);
        session.advance(6 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.isLost());
        assertTrue(session.drainEvents().stream().anyMatch(e -> e.contains("dead line")));
    }

    @Test
    void loveYourPlantsLosesAfterEnoughLosses() {
        GameSession session = session(SpecialRules.loveYourPlants(1), List.of("sunflower"));
        session.plant("sunflower", 8, 1);
        session.cheatSpawnZombie("gargantuar", 9, 1);
        session.advance(20 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.isLost());
    }

    @Test
    void plantWhatYouGetStartsCalmWithFreeRecharge() {
        SpecialRules rules = SpecialRules.plantWhatYouGet(800, java.util.Set.of("sunflower"));
        LevelSpec level = new LevelSpec(Chapter.DARK_AGES, 3, 4, 4, List.of("normal"),
                Map.of(), 0, true, false, false, rules);
        GameSession session = new GameSession(level, 3, List.of("peashooter"), new HashSet<>(),
                new Random(5));
        assertEquals(800, session.getSunAmount());
        assertTrue(session.plant("peashooter", 1, 1).startsWith("Planted"));
        assertTrue(session.plant("peashooter", 2, 1).startsWith("Planted"));
        session.advance(15 * GameSession.TICKS_PER_SECOND);
        assertEquals(0, session.getCurrentWave());
        assertFalse(session.startZombieWaves().startsWith("Error"));
        session.advance(1);
        assertEquals(1, session.getCurrentWave());
        assertTrue(session.plant("peashooter", 3, 1).startsWith("Planted"));
        assertTrue(session.plant("peashooter", 4, 1).startsWith("Error"));
    }
}
