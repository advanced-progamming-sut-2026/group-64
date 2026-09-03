package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The Ancient Egypt sandstorm: which chapters get one, when it blows, and
 * where it has reached. It is weather, so it never touches the board.
 */
class SandstormTest {

    private static GameSession levelIn(Chapter chapter) {
        LevelSpec level = Levels.adventure().stream()
                .filter(spec -> spec.getChapter() == chapter)
                .findFirst().orElseThrow();
        return new GameSession(level, 3, List.of("peashooter"), new HashSet<>(), new Random(3));
    }

    @Test
    void onlyAncientEgyptGetsSandstorms() {
        assertTrue(levelIn(Chapter.ANCIENT_EGYPT).getSandstorm().isEnabled());
        for (Chapter chapter : List.of(Chapter.FROSTBITE_CAVES, Chapter.BIG_WAVE_BEACH,
                Chapter.DARK_AGES)) {
            assertFalse(levelIn(chapter).getSandstorm().isEnabled(),
                    chapter + " should have weather of its own, not sand");
        }
    }

    @Test
    void aStormCrossesTheLawnAndComesRoundAgain() {
        Sandstorm storm = levelIn(Chapter.ANCIENT_EGYPT).getSandstorm();
        assertTrue(storm.isBlowing(0.5), "one rolls in at the start of a level");
        assertFalse(storm.isBlowing(Sandstorm.CROSSING_SECONDS + 1), "then the lawn is clear");
        assertTrue(storm.isBlowing(Sandstorm.PERIOD_SECONDS + 0.5), "and the next one arrives");
    }

    @Test
    void itSweepsFromTheRightEdgeToTheLeftOne() {
        Sandstorm storm = levelIn(Chapter.ANCIENT_EGYPT).getSandstorm();
        double start = storm.columnAt(0);
        double middle = storm.columnAt(Sandstorm.CROSSING_SECONDS / 2);
        double end = storm.columnAt(Sandstorm.CROSSING_SECONDS - 0.01);
        assertTrue(start > GameSession.COLS, "it starts off the right edge: " + start);
        assertTrue(end < 1, "and finishes off the left one: " + end);
        assertTrue(middle < start && middle > end, "moving steadily across: " + middle);
    }

    @Test
    void itBuildsAndThinsRatherThanPoppingInAndOut() {
        Sandstorm storm = levelIn(Chapter.ANCIENT_EGYPT).getSandstorm();
        assertEquals(0, storm.intensityAt(Sandstorm.CROSSING_SECONDS + 1), 0.001);
        assertTrue(storm.intensityAt(0.05) < 0.2, "it arrives faintly");
        assertTrue(storm.intensityAt(Sandstorm.CROSSING_SECONDS / 2) > 0.9, "and thickens");
        assertTrue(storm.intensityAt(Sandstorm.CROSSING_SECONDS - 0.05) < 0.2, "then thins out");
    }

    @Test
    void theStormLeavesThePlantsAndZombiesAlone() {
        GameSession session = levelIn(Chapter.ANCIENT_EGYPT);
        session.setWavesEnabled(false);
        session.cheats().addSuns(500);
        session.plant("peashooter", 1, 1);
        Zombie zombie = session.spawnZombie(GameCatalog.get().zombie("normal"), 4, 8);
        int plantHp = session.plantAtTile(1, 1).getHp();
        int zombieHp = zombie.totalRemainingHealth();

        session.advance((int) (Sandstorm.CROSSING_SECONDS * GameSession.TICKS_PER_SECOND) + 5);

        assertEquals(plantHp, session.plantAtTile(1, 1).getHp(), "the storm is not a hazard");
        assertEquals(zombieHp, zombie.totalRemainingHealth(), "and it does not hurt zombies");
    }
}
