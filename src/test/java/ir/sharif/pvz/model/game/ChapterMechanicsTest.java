package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ChapterMechanicsTest {

    private static LevelSpec level(Map<Integer, TileTerrain> terrain, boolean night, int graves) {
        return new LevelSpec(Chapter.ANCIENT_EGYPT, 1, 4, 4, List.of("normal"),
                terrain, graves, night, false, false);
    }

    private static GameSession session(LevelSpec spec, List<String> plants) {
        GameSession session = new GameSession(spec, 3, plants, new HashSet<>(), new Random(7));
        session.setWavesEnabled(false);
        return session;
    }

    @Test
    void adventureHasSixteenLevelsAcrossFourChapters() {
        assertEquals(16, Levels.adventure().size());
        assertEquals(Chapter.ANCIENT_EGYPT, Levels.byProgress(0).getChapter());
        assertEquals(Chapter.DARK_AGES, Levels.byProgress(15).getChapter());
        assertEquals(Levels.byProgress(15), Levels.byProgress(99));
    }

    @Test
    void gravesBlockPlanting() {
        Map<Integer, TileTerrain> terrain = Map.of(LevelSpec.tileKey(0, 0), TileTerrain.GRAVE);
        GameSession session = session(level(terrain, false, 0), List.of("sunflower"));
        assertTrue(session.plant("sunflower", 1, 1).contains("grave"));
        assertTrue(session.plant("sunflower", 2, 1).startsWith("Planted"));
    }

    @Test
    void directShotHitsGraveUntilItFalls() {
        Map<Integer, TileTerrain> terrain = Map.of(LevelSpec.tileKey(0, 4), TileTerrain.GRAVE);
        GameSession session = session(level(terrain, false, 0), List.of("peashooter"));
        session.cheatAddSuns(1000);
        session.plant("peashooter", 1, 1);
        session.cheatSpawnZombie("normal", 9, 1);
        session.advance(60 * GameSession.TICKS_PER_SECOND);
        List<String> events = session.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e.contains("The grave at (5, 1) is destroyed.")));
        assertEquals(TileTerrain.NORMAL, session.terrainAt(5, 1));
    }

    @Test
    void nightLevelsDropNoSkySuns() {
        GameSession session = session(level(Map.of(), true, 0), List.of());
        session.advance(60 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.drainEvents().stream().noneMatch(e -> e.contains("sun is dropping")));
    }

    @Test
    void waterNeedsLilyPadForLandPlants() {
        Map<Integer, TileTerrain> terrain = Map.of(LevelSpec.tileKey(0, 8), TileTerrain.WATER);
        GameSession session = session(level(terrain, false, 0), List.of("sunflower", "lily-pad"));
        session.cheatAddSuns(1000);
        assertTrue(session.plant("sunflower", 9, 1).contains("lily-pad"));
        assertTrue(session.plant("lily-pad", 9, 1).contains("plantable"));
        assertEquals(TileTerrain.LILY, session.terrainAt(9, 1));
        assertTrue(session.plant("sunflower", 9, 1).startsWith("Planted"));
    }

    @Test
    void slipperyIceMovesZombieToNeighbourRow() {
        Map<Integer, TileTerrain> terrain = Map.of(LevelSpec.tileKey(1, 4), TileTerrain.SLIPPERY_DOWN);
        GameSession session = session(level(terrain, false, 0), List.of());
        session.cheatSpawnZombie("normal", 5, 2);
        session.advance(2 * GameSession.TICKS_PER_SECOND);
        assertEquals(2, session.getZombies().get(0).getRow());
    }

    @Test
    void raStealsGroundSunsAndReturnsThemOnDeath() {
        GameSession session = session(level(Map.of(), true, 0), List.of("sunflower"));
        session.plant("sunflower", 1, 1);
        session.advance(25 * GameSession.TICKS_PER_SECOND);
        assertFalse(session.groundSuns().isEmpty());
        session.cheatSpawnZombie("ra", 9, 1);
        session.advance(3 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.drainEvents().stream().anyMatch(e -> e.contains("Ra stole a sun")));
        session.releaseTheNuke();
        assertTrue(session.drainEvents().stream().anyMatch(e -> e.contains("Ra dropped the stolen suns")));
    }

    @Test
    void kingKnightsANormalZombie() {
        GameSession session = session(level(Map.of(), false, 0), List.of());
        session.cheatSpawnZombie("king", 9, 1);
        session.cheatSpawnZombie("normal", 7, 1);
        session.advance(9 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.getZombies().stream().anyMatch(z -> z.getSpec().getName().equals("knight")));
        assertTrue(session.getZombies().stream().noneMatch(z -> z.getSpec().getName().equals("normal")));
    }

    @Test
    void wizardSheepsAPlantAndDeathFreesIt() {
        GameSession session = session(level(Map.of(), false, 0), List.of("sunflower"));
        session.plant("sunflower", 1, 1);
        session.cheatSpawnZombie("wizard", 9, 5);
        session.advance(7 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.isPlantDisabled(1, 1));
        session.releaseTheNuke();
        assertFalse(session.isPlantDisabled(1, 1));
    }

    @Test
    void dodoRiderFliesOverNonWallPlants() {
        GameSession session = session(level(Map.of(), false, 0), List.of("sunflower")) ;
        session.plant("sunflower", 5, 1);
        session.cheatSpawnZombie("dodo-rider", 6, 1);
        session.advance(8 * GameSession.TICKS_PER_SECOND);
        assertNotNull(session.plantAtTile(5, 1));
        assertTrue(session.getZombies().get(0).getX() < 4);
    }

    @Test
    void explorerBurnsThePlantAheadUnlessTorchIsOut() {
        GameSession session = session(level(Map.of(), false, 0), List.of("sunflower"));
        session.plant("sunflower", 5, 1);
        session.cheatSpawnZombie("explorer", 7, 1);
        session.advance(4 * GameSession.TICKS_PER_SECOND);
        assertNull(session.plantAtTile(5, 1));
        assertTrue(session.drainEvents().stream().anyMatch(e -> e.contains("torch burned")));
    }

    @Test
    void hunterFreezesAPlantAfterThreeHits() {
        GameSession session = session(level(Map.of(), false, 0), List.of("wall-nut"));
        session.plant("wall-nut", 1, 1);
        session.cheatSpawnZombie("hunter", 9, 1);
        session.advance(16 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.isPlantDisabled(1, 1));
    }
}
