package ir.sharif.pvz.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ir.sharif.pvz.model.game.Chapter;
import ir.sharif.pvz.model.game.GameCatalog;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.LevelLog;
import ir.sharif.pvz.model.game.LevelReport;
import ir.sharif.pvz.model.game.PlantCategory;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The quests the sheet lists, and the level statistics they read.
 */
class QuestCatalogTest {

    private static final String TODAY = "2026-09-03";

    private static Quest quest(String id) {
        Quest found = QuestCatalog.all().stream()
                .filter(candidate -> candidate.getId().equals(id))
                .findFirst().orElse(null);
        assertNotNull(found, "there is no quest called " + id);
        return found;
    }

    private static User player() {
        return new User("quester", "hash", "Q", "q@example.com", Gender.FEMALE);
    }

    // ===== the catalogue itself =====

    @Test
    void everyRowOfTheSheetIsRepresented() {
        Set<String> ids = new HashSet<>(QuestCatalog.all().stream().map(Quest::getId).toList());
        assertEquals(QuestCatalog.all().size(), ids.size(), "quest ids have to be unique");
        for (String id : List.of("daily-sun-3000", "daily-sun-4000", "daily-sun-5000",
                "story-hunter-ancient-egypt", "daily-one-plant", "daily-only-cactus",
                "story-thrifty-0", "story-thrifty-5", "epic-defence-master", "story-speed",
                "daily-demolition", "daily-symmetry", "daily-no-symmetry", "epic-night-shift",
                "daily-streak", "daily-close-call", "daily-overcast", "daily-bare-column-1",
                "daily-bare-row-1", "daily-bare-cross-1", "epic-mowing-10", "epic-mowing-50")) {
            assertTrue(ids.contains(id), "the sheet asks for " + id);
        }
    }

    @Test
    void everyQuestSitsOnOneOfTheThreePagesTheSheetUses() {
        for (Quest quest : QuestCatalog.all()) {
            assertTrue(List.of("daily", "story", "epic").contains(quest.getPage()),
                    quest.getId() + " is on the page '" + quest.getPage() + "'");
            assertFalse(quest.getTitle().isBlank(), quest.getId() + " has no title");
            assertFalse(quest.getRewardDescription().isBlank(), quest.getId() + " pays nothing");
        }
    }

    @Test
    void theSheetsVariablesEachBecomeTheirOwnQuest() {
        assertEquals(3, countStarting("daily-sun-"), "3000, 4000 and 5000 sun");
        assertEquals(Chapter.values().length, countStarting("story-hunter-"), "one per chapter");
        assertEquals(6, countStarting("story-thrifty-"), "losing zero through five plants");
        assertEquals(5, countStarting("epic-mowing-"), "ten through fifty mower kills");
        assertEquals(GameSession.COLS, countStarting("daily-bare-column-"));
        assertEquals(GameSession.ROWS, countStarting("daily-bare-row-"));
    }

    private static long countStarting(String prefix) {
        return QuestCatalog.all().stream().filter(q -> q.getId().startsWith(prefix)).count();
    }

    // ===== conditions =====

    @Test
    void theSunQuestCountsOverADayAndStartsAgainTheNext() {
        User user = player();
        user.getQuestProgress().record(sunPicked(3200), TODAY);
        assertTrue(quest("daily-sun-3000").isMet(user, TODAY));
        assertFalse(quest("daily-sun-4000").isMet(user, TODAY));
        assertFalse(quest("daily-sun-3000").isMet(user, "2026-09-04"),
                "tomorrow starts from nothing");
    }

    @Test
    void theHunterQuestCountsKillsInItsOwnChapterOnly() {
        User user = player();
        for (int level = 0; level < 5; level++) {
            user.getQuestProgress().record(killsIn(Chapter.DARK_AGES, 11), TODAY);
        }
        assertTrue(quest("story-hunter-dark-ages").isMet(user, TODAY));
        assertFalse(quest("story-hunter-ancient-egypt").isMet(user, TODAY));
    }

    @Test
    void theSinglePlantQuestNeedsOnePlantToHaveDoneAllTheKilling() {
        User user = player();
        user.getQuestProgress().record(report(builder -> builder
                .killsBy(java.util.Map.of("cactus", 10, "peashooter", 4))), TODAY);
        assertFalse(quest("daily-only-cactus").isMet(user, TODAY),
                "the peashooter helped, so it does not count");

        user.getQuestProgress().record(report(builder -> builder
                .killsBy(java.util.Map.of("cactus", 10))), TODAY);
        assertTrue(quest("daily-only-cactus").isMet(user, TODAY));
        assertTrue(quest("daily-one-plant").isMet(user, TODAY));
    }

    @Test
    void theThriftyQuestsTakeTheBestRunSoFar() {
        User user = player();
        user.setLevelsPassed(1);
        user.getQuestProgress().record(report(builder -> builder.won(true).plantsLost(3)), TODAY);
        assertTrue(quest("story-thrifty-3").isMet(user, TODAY));
        assertTrue(quest("story-thrifty-5").isMet(user, TODAY));
        assertFalse(quest("story-thrifty-0").isMet(user, TODAY));

        user.getQuestProgress().record(report(builder -> builder.won(true).plantsLost(0)), TODAY);
        assertTrue(quest("story-thrifty-0").isMet(user, TODAY), "a cleaner run counts");
    }

    @Test
    void theHardestStreakBreaksOnAnEasierWinOrALoss() {
        User user = player();
        for (int win = 0; win < 5; win++) {
            user.getQuestProgress().record(report(b -> b.won(true).difficulty(5)), TODAY);
        }
        assertTrue(quest("daily-streak").isMet(user, TODAY));

        user.getQuestProgress().record(report(b -> b.won(false).difficulty(5)), TODAY);
        assertFalse(quest("daily-streak").isMet(user, TODAY), "a loss starts the count again");
    }

    @Test
    void theMowerQuestsAddUpAcrossLevels() {
        User user = player();
        user.getQuestProgress().record(report(b -> b
                .killsBy(java.util.Map.of(LevelLog.MOWER, 12))), TODAY);
        assertTrue(quest("epic-mowing-10").isMet(user, TODAY));
        assertFalse(quest("epic-mowing-20").isMet(user, TODAY));
    }

    @Test
    void theFamilyQuestsReadWhatWasPlantedAndWhatDidTheKilling() {
        User user = player();
        user.getQuestProgress().record(report(b -> b.won(true)
                .planted(Set.of("peashooter", "repeater"))
                .killsBy(java.util.Map.of("peashooter", 6, "repeater", 3))), TODAY);

        assertTrue(quest("daily-family-only-shooter").isMet(user, TODAY),
                "only shooters did the killing");
        assertTrue(quest("daily-family-free-wall").isMet(user, TODAY),
                "no wall was planted at all");
        assertFalse(quest("daily-family-free-shooter").isMet(user, TODAY));
    }

    /**
     * The travel log has to show how far along a counting quest is, not just
     * that it is unfinished — the phase 1 sheet asks for it by name.
     */
    @Test
    void theCountingQuestsSayHowFarAlongTheyAre() {
        User user = player();
        assertEquals("0 / 5000", quest("daily-sun-5000").progress(user, TODAY));

        user.getQuestProgress().record(sunPicked(3200), TODAY);
        assertEquals("3200 / 5000", quest("daily-sun-5000").progress(user, TODAY));
        assertEquals("3000 / 3000", quest("daily-sun-3000").progress(user, TODAY),
                "a finished one reads as done rather than overshooting");

        user.getQuestProgress().record(killsIn(Chapter.DARK_AGES, 12), TODAY);
        assertEquals("12 / 50", quest("story-hunter-dark-ages").progress(user, TODAY));
    }

    @Test
    void aQuestThatSimplyHappensHasNoNumberToShow() {
        User user = player();
        assertNull(quest("epic-defence-master").progress(user, TODAY));
        assertNull(quest("daily-symmetry").progress(user, TODAY));
    }

    @Test
    void theProgressShowsUpOnTheTravelLogLine() {
        User user = player();
        user.getQuestProgress().record(sunPicked(1500), TODAY);
        UserRepository users = new UserRepository(
                java.nio.file.Path.of("build", "quest-lines-" + System.nanoTime() + ".json")) {
            @Override
            public void save() {
                // keep the test off the disk
            }
        };
        QuestService service = new QuestService(users, System::currentTimeMillis);
        String line = service.lines(user, "daily").stream()
                .filter(text -> text.contains("daily-sun-5000"))
                .findFirst().orElseThrow();
        assertTrue(line.contains("/ 5000"), line);
    }

    // ===== the report a real level produces =====

    @Test
    void aRealLevelFillsInTheStatisticsTheQuestsRead() {
        GameSession session = new GameSession(3, List.of("peashooter", "sunflower"),
                new HashSet<>(), new Random(11));
        session.setWavesEnabled(false);
        session.cheats().addSuns(1000);
        session.cheats().removeCooldown();
        session.plant("peashooter", 1, 3);
        session.cheats().spawnZombie("normal", 6, 3);
        session.advance(40 * GameSession.TICKS_PER_SECOND);

        LevelReport report = LevelReport.of(session);
        assertTrue(report.kills() >= 1, "the peashooter saw one off");
        assertEquals(1, report.killsBy().getOrDefault("peashooter", 0),
                "and the kill is credited to it");
        assertEquals("peashooter", report.soleKiller());
        assertTrue(report.planted().contains("peashooter"));
        assertTrue(report.emptyRows().contains(1), "nothing was planted in row 1");
        assertFalse(report.emptyRows().contains(3), "row 3 has the peashooter");
        assertEquals(Set.of(PlantCategory.SHOOTER), report.familiesThatKilled());
    }

    @Test
    void aLawnPlantedTheSameTopAndBottomReadsAsSymmetric() {
        GameSession session = new GameSession(3, List.of("wall-nut"), new HashSet<>(),
                new Random(4));
        session.setWavesEnabled(false);
        session.cheats().addSuns(5000);
        session.cheats().removeCooldown();
        assertFalse(LevelReport.of(session).asymmetric(), "an empty lawn mirrors itself");

        session.plant("wall-nut", 2, 1);
        assertFalse(LevelReport.of(session).symmetric(), "one nut on top only");

        session.plant("wall-nut", 2, 5);
        assertTrue(LevelReport.of(session).symmetric(), "and now it mirrors");
    }

    // ===== a little builder, so each test names only what it cares about =====

    private static final class Builder {
        private boolean won;
        private int difficulty = 3;
        private int sunCollected;
        private int plantsLost;
        private java.util.Map<String, Integer> killsBy = java.util.Map.of();
        private Set<String> planted = Set.of();

        Builder won(boolean value) {
            this.won = value;
            return this;
        }

        Builder difficulty(int value) {
            this.difficulty = value;
            return this;
        }

        Builder sunCollected(int value) {
            this.sunCollected = value;
            return this;
        }

        Builder plantsLost(int value) {
            this.plantsLost = value;
            return this;
        }

        Builder killsBy(java.util.Map<String, Integer> value) {
            this.killsBy = value;
            return this;
        }

        Builder planted(Set<String> value) {
            this.planted = value;
            return this;
        }

        LevelReport build(Chapter chapter) {
            int kills = killsBy.values().stream().mapToInt(Integer::intValue).sum();
            return new LevelReport(won, chapter, difficulty, false, 1, sunCollected, kills,
                    killsBy, 0, 0, plantsLost, planted, Set.of(), Set.of(), false, false);
        }
    }

    private static LevelReport report(java.util.function.Consumer<Builder> setUp) {
        Builder builder = new Builder();
        setUp.accept(builder);
        return builder.build(Chapter.ANCIENT_EGYPT);
    }

    private static LevelReport sunPicked(int sun) {
        return report(builder -> builder.sunCollected(sun));
    }

    private static LevelReport killsIn(Chapter chapter, int kills) {
        Builder builder = new Builder();
        builder.killsBy(java.util.Map.of("peashooter", kills));
        return builder.build(chapter);
    }
}
