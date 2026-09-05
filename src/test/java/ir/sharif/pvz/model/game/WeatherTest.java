package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The Ancient Egypt weather: which chapters get one, when it blows, and
 * where it has reached. It is weather, so it never touches the board.
 */
class WeatherTest {

    private static GameSession levelIn(Chapter chapter) {
        LevelSpec level = Levels.adventure().stream()
                .filter(spec -> spec.getChapter() == chapter)
                .findFirst().orElseThrow();
        return new GameSession(level, 3, List.of("peashooter"), new HashSet<>(), new Random(3));
    }

    @Test
    void eachChapterGetsTheWeatherThatBelongsToIt() {
        assertEquals(Weather.Kind.SAND, levelIn(Chapter.ANCIENT_EGYPT).getWeather().kind());
        assertEquals(Weather.Kind.ICE, levelIn(Chapter.FROSTBITE_CAVES).getWeather().kind(),
                "the ice caves get a gale, not a sandstorm");
        for (Chapter chapter : List.of(Chapter.BIG_WAVE_BEACH, Chapter.DARK_AGES)) {
            assertFalse(levelIn(chapter).getWeather().isEnabled(),
                    chapter + " has no weather of its own");
        }
    }

    /** The gale blows on the same schedule the sandstorm does. */
    @Test
    void theIcyGaleCrossesTheLawnTheWayTheSandstormDoes() {
        Weather gale = levelIn(Chapter.FROSTBITE_CAVES).getWeather();
        assertTrue(gale.isBlowing(0.5));
        assertFalse(gale.isBlowing(Weather.CROSSING_SECONDS + 1));
        assertTrue(gale.columnAt(0.1) > gale.columnAt(Weather.CROSSING_SECONDS - 0.1),
                "it crosses from the right edge to the left");
    }

    @Test
    void aStormCrossesTheLawnAndComesRoundAgain() {
        Weather storm = levelIn(Chapter.ANCIENT_EGYPT).getWeather();
        assertTrue(storm.isBlowing(0.5), "one rolls in at the start of a level");
        assertFalse(storm.isBlowing(Weather.CROSSING_SECONDS + 1), "then the lawn is clear");
        assertTrue(storm.isBlowing(Weather.PERIOD_SECONDS + 0.5), "and the next one arrives");
    }

    @Test
    void itSweepsFromTheRightEdgeToTheLeftOne() {
        Weather storm = levelIn(Chapter.ANCIENT_EGYPT).getWeather();
        double start = storm.columnAt(0);
        double middle = storm.columnAt(Weather.CROSSING_SECONDS / 2);
        double end = storm.columnAt(Weather.CROSSING_SECONDS - 0.01);
        assertTrue(start > GameSession.COLS, "it starts off the right edge: " + start);
        assertTrue(end < 1, "and finishes off the left one: " + end);
        assertTrue(middle < start && middle > end, "moving steadily across: " + middle);
    }

    @Test
    void itBuildsAndThinsRatherThanPoppingInAndOut() {
        Weather storm = levelIn(Chapter.ANCIENT_EGYPT).getWeather();
        assertEquals(0, storm.intensityAt(Weather.CROSSING_SECONDS + 1), 0.001);
        assertTrue(storm.intensityAt(0.05) < 0.2, "it arrives faintly");
        assertTrue(storm.intensityAt(Weather.CROSSING_SECONDS / 2) > 0.9, "and thickens");
        assertTrue(storm.intensityAt(Weather.CROSSING_SECONDS - 0.05) < 0.2, "then thins out");
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

        session.advance((int) (Weather.CROSSING_SECONDS * GameSession.TICKS_PER_SECOND) + 5);

        assertEquals(plantHp, session.plantAtTile(1, 1).getHp(), "the storm is not a hazard");
        assertEquals(zombieHp, zombie.totalRemainingHealth(), "and it does not hurt zombies");
    }
}
