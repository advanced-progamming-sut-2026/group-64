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
        session.zomboss.hit(part);
        assertEquals(1, boss.getPartsDestroyed(), "one section should be gone");
        assertTrue(boss.isStunned(), "and the boss reels afterwards");

        session.zomboss.hit(part);
        assertEquals(2, boss.getPartsDestroyed());
        assertFalse(boss.isDefeated());
    }

    @Test
    void takingTheLastPartOffWinsTheLevel() {
        GameSession session = bossLevel(Chapter.BIG_WAVE_BEACH);
        session.zomboss.hit(session.getZomboss().getMaxHp());
        session.advance(GameSession.TICKS_PER_SECOND * 2);

        assertTrue(session.getZomboss().isDefeated());
        assertTrue(session.isWon(), "beating the boss should win the chapter");
    }

    /**
     * The boss used to blink out the instant its last part came off, because
     * the level was won on the same tick. It topples first now, and the win
     * waits for it.
     */
    @Test
    void theBossToplesBeforeTheLevelIsCalledWon() {
        GameSession session = bossLevel(Chapter.DARK_AGES);
        Zomboss boss = session.getZomboss();
        session.zomboss.hit(boss.getMaxHp());
        session.advance(1);

        assertTrue(boss.isDefeated(), "its health is gone");
        assertFalse(session.isWon(), "but the level is not over while it is still falling");
        assertFalse(boss.hasFinishedFalling());

        double startedAt = boss.fall();
        session.advance(GameSession.TICKS_PER_SECOND / 2);
        assertTrue(boss.fall() > startedAt, "it is on its way down");

        session.advance(GameSession.TICKS_PER_SECOND * 2);
        assertTrue(boss.hasFinishedFalling(), "and eventually lands");
        assertTrue(session.isWon());
    }

    /**
     * Every chapter's signature attack is a different thing in the text, and
     * used to be the same orange explosion on the lawn. Each throws its own
     * shot now, from the boss to the tile it hits.
     */
    @Test
    void eachChaptersSignatureAttackThrowsItsOwnShot() {
        for (Chapter chapter : Chapter.values()) {
            GameSession session = bossLevel(chapter);
            BossShot.Kind expected = switch (chapter) {
                case ANCIENT_EGYPT -> BossShot.Kind.ROCKET;
                case DARK_AGES -> BossShot.Kind.FIREBALL;
                case FROSTBITE_CAVES -> BossShot.Kind.ICE;
                case BIG_WAVE_BEACH -> BossShot.Kind.SHARKS;
            };
            BossShot shot = firstShot(session);
            assertNotNull(shot, chapter + " should throw something");
            assertEquals(expected, shot.getKind(), chapter + " throws the wrong thing");
        }
    }

    /** Runs a boss level until it takes its first single-tile shot. */
    private BossShot firstShot(GameSession session) {
        for (int i = 0; i < GameSession.TICKS_PER_SECOND * 120; i++) {
            session.advance(1);
            if (!session.getBossShots().isEmpty()) {
                return session.getBossShots().get(0);
            }
        }
        return null;
    }

    @Test
    void aShotLeavesTheBossAndArrivesAtTheTileItHit() {
        GameSession session = bossLevel(Chapter.ANCIENT_EGYPT);
        BossShot shot = firstShot(session);
        assertNotNull(shot);
        double startedAt = shot.getCol();
        assertTrue(startedAt > shot.getToCol(), "it starts at the boss, out on the right");
        assertFalse(shot.hasLanded(), "and is still in the air");

        // a third of the way over, where it should be up in the air and
        // somewhere between the boss and the tile
        session.advance(GameSession.TICKS_PER_SECOND / 4);
        assertTrue(shot.getLift() > 0, "arcing rather than travelling flat");
        assertTrue(shot.getCol() < startedAt, "and closer to the tile than it was");

        session.advance(GameSession.TICKS_PER_SECOND * 2);
        assertTrue(shot.hasLanded(), "it lands");
        assertEquals(shot.getToCol(), shot.getCol(), 0.001, "on the tile it was aimed at");
    }

    /**
     * The wide move was three separate explosions; it is one front crossing
     * the rows the boss faces.
     */
    @Test
    void theWideAttackSendsOneFrontAcrossTheRowsItFaces() {
        GameSession session = bossLevel(Chapter.FROSTBITE_CAVES);
        BossSweep sweep = null;
        for (int i = 0; i < GameSession.TICKS_PER_SECOND * 120 && sweep == null; i++) {
            session.advance(1);
            sweep = session.getBossSweep();
        }
        assertNotNull(sweep, "the boss should use its wide move");
        assertEquals(Chapter.FROSTBITE_CAVES, sweep.getChapter());
        assertEquals(session.getZomboss().getRows(), sweep.getRows(),
                "it covers the rows the boss covers");

        double startedAt = sweep.progress();
        session.advance(GameSession.TICKS_PER_SECOND / 2);
        assertTrue(session.getBossSweep() == null || session.getBossSweep().progress() > startedAt,
                "the front moves across the lawn");
    }

    /** Runs a boss level until its wide move goes off. */
    private GameSession afterTheWideMove(Chapter chapter) {
        GameSession session = bossLevel(chapter);
        for (int i = 0; i < GameSession.TICKS_PER_SECOND * 200; i++) {
            session.advance(1);
            if (session.getBossSweep() != null) {
                return session;
            }
        }
        return null;
    }

    /**
     * Every chapter called its wide move something different and all four did
     * exactly the same thing. Each leaves its own mark now.
     */
    @Test
    void theEgyptBossActuallyChargesOutAcrossTheLawnAndBack() {
        GameSession session = afterTheWideMove(Chapter.ANCIENT_EGYPT);
        assertNotNull(session, "the boss should use its wide move");
        Zomboss boss = session.getZomboss();
        double home = GameSession.COLS;
        assertTrue(boss.isCharging(), "it leaves its column");

        session.advance(GameSession.TICKS_PER_SECOND);
        assertTrue(boss.getColumn() < home, "and is out on the lawn");

        session.advance(GameSession.TICKS_PER_SECOND * 2);
        assertFalse(boss.isCharging(), "then it comes back");
        assertEquals(home, boss.getColumn(), 0.001, "to where it stands");
    }

    @Test
    void theDarkAgesBossLeavesTheGroundBurning() {
        GameSession session = afterTheWideMove(Chapter.DARK_AGES);
        assertNotNull(session);
        Zomboss boss = session.getZomboss();
        assertTrue(session.scorchLeft(boss.getRow()) > 0, "the row it burned is still alight");

        // anything put down on it goes up with it
        session.cheats().addSuns(2000);
        session.cheats().removeCooldown();
        session.plant("sunflower", 2, boss.getRow() + 1);
        session.advance(2);
        assertNull(session.plantAtTile(2, boss.getRow() + 1),
                "a plant on burning ground burns too");

        session.advance(GameSession.TICKS_PER_SECOND * 8);
        assertEquals(0, session.scorchLeft(boss.getRow()), 0.001, "the fire goes out");
    }

    @Test
    void theBeachBossTorpedoDragsItsLanesTowardTheHouse() {
        GameSession session = bossLevel(Chapter.BIG_WAVE_BEACH);
        Zomboss boss = session.getZomboss();
        Zombie caught = session.spawnZombie(GameCatalog.get().zombie("normal"),
                boss.getRow(), 8);
        double before = caught.getX();

        for (int i = 0; i < GameSession.TICKS_PER_SECOND * 200
                && session.getBossSweep() == null; i++) {
            session.advance(1);
        }
        assertNotNull(session.getBossSweep(), "the boss should use its torpedo");
        assertTrue(caught.getX() < before - 1,
                "the zombie was dragged toward the house: " + before + " -> " + caught.getX());
    }

    @Test
    void theBossFlinchesWhenItIsHitAndWindsUpWhenItThrows() {
        GameSession session = bossLevel(Chapter.ANCIENT_EGYPT);
        Zomboss boss = session.getZomboss();
        assertEquals(0, boss.flinch(), 0.001, "nothing has hit it yet");

        session.zomboss.hit(10);
        assertTrue(boss.flinch() > 0, "a hit shows on it");
        session.advance(GameSession.TICKS_PER_SECOND);
        assertEquals(0, boss.flinch(), 0.001, "and it shrugs it off");

        assertNotNull(firstShot(session), "it throws something eventually");
        session.advance(GameSession.TICKS_PER_SECOND / 6);
        assertTrue(boss.lunge() > 0, "and is part-way through its wind-up just after");
    }

    @Test
    void aStunnedBossStopsActingUntilItRecovers() {
        GameSession session = bossLevel(Chapter.ANCIENT_EGYPT);
        Zomboss boss = session.getZomboss();
        session.zomboss.hit(boss.getMaxHp() / 3);
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
