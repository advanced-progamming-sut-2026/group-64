package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The chapter finale: Zomboss stands in for the wave system, its health comes
 * off in three parts, and the level is decided by it rather than by clearing
 * the lawn.
 */
class ZombossTest {

    private GameSession bossLevel(Chapter chapter) {
        LevelSpec spec = Levels.adventure().stream()
                .filter(level -> level.getChapter() == chapter && level.isBoss())
                .findFirst().orElseThrow();
        return new GameSession(spec, 3, List.of("peashooter"), new HashSet<>(), new Random(5));
    }

    @Test
    void everyChapterEndsWithABossLevel() {
        for (Chapter chapter : Chapter.values()) {
            long bosses = Levels.adventure().stream()
                    .filter(level -> level.getChapter() == chapter && level.isBoss()).count();
            assertEquals(1, bosses, chapter + " should have exactly one boss level");
        }
    }

    @Test
    void anOrdinaryLevelHasNoBoss() {
        GameSession plain =
                new GameSession(3, List.of("peashooter"), new HashSet<>(), new Random(5));
        assertNull(plain.getZomboss());
    }

    @Test
    void aBossLevelDealsPlantsFromABeltInsteadOfASelection() {
        GameSession session = bossLevel(Chapter.ANCIENT_EGYPT);
        assertNotNull(session.getZomboss());
        assertTrue(session.isConveyorLevel(), "boss levels have no plant selection");
    }

    @Test
    void healthComesOffInThreeParts() {
        GameSession session = bossLevel(Chapter.DARK_AGES);
        Zomboss boss = session.getZomboss();
        int part = boss.getMaxHp() / 3;

        assertEquals(0, boss.getPartsDestroyed());
        session.zombossEngine().hit(part);
        assertEquals(1, boss.getPartsDestroyed(), "one section should be gone");
        assertTrue(boss.isStunned(), "and the boss reels afterwards");

        session.zombossEngine().hit(part);
        assertEquals(2, boss.getPartsDestroyed());
        assertFalse(boss.isDefeated());
    }

    @Test
    void takingTheLastPartOffWinsTheLevel() {
        GameSession session = bossLevel(Chapter.BIG_WAVE_BEACH);
        session.zombossEngine().hit(session.getZomboss().getMaxHp());
        session.advance(1);

        assertTrue(session.getZomboss().isDefeated());
        assertTrue(session.isWon(), "beating the boss should win the chapter");
    }

    @Test
    void aStunnedBossStopsActingUntilItRecovers() {
        GameSession session = bossLevel(Chapter.ANCIENT_EGYPT);
        Zomboss boss = session.getZomboss();
        session.zombossEngine().hit(boss.getMaxHp() / 3);
        assertTrue(boss.isStunned());

        session.advance(GameSession.TICKS_PER_SECOND * 7);
        assertFalse(boss.isStunned(), "the stun should wear off");
    }

    @Test
    void theMammothCoversEveryRowWhileOthersCoverTwo() {
        assertEquals(GameSession.ROWS, bossLevel(Chapter.FROSTBITE_CAVES).getZomboss().getRows());
        assertEquals(2, bossLevel(Chapter.DARK_AGES).getZomboss().getRows());
    }

    @Test
    void theBossPutsZombiesOnTheLawnItself() {
        GameSession session = bossLevel(Chapter.DARK_AGES);
        // mowers clear the lawn again between attacks, so watch the whole
        // window rather than whatever happens to be standing at the end of it
        boolean sawZombies = false;
        for (int i = 0; i < GameSession.TICKS_PER_SECOND * 60 && !sawZombies; i++) {
            session.advance(1);
            sawZombies = !session.getZombies().isEmpty();
        }

        assertTrue(sawZombies, "the boss should be putting zombies on the lawn on its own");
    }

    @Test
    void theBossAttacksTheLawnWithoutAnyWaves() {
        GameSession session = bossLevel(Chapter.DARK_AGES);
        session.advance(GameSession.TICKS_PER_SECOND * 30);

        assertTrue(session.drainEvents().stream().anyMatch(line -> line.contains("Zomboss")),
                "the boss should be doing something the player can read about");
    }
}
