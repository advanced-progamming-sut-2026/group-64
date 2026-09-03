package ir.sharif.pvz.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuestServiceTest {

    private static final long DAY = 24 * 3_600_000L;

    private final AtomicLong now = new AtomicLong(1_000_000_000_000L);
    private QuestService service;
    private User user;

    @BeforeEach
    void setUp() {
        UserRepository repository = new UserRepository(
                java.nio.file.Path.of("build", "test-users-" + System.nanoTime() + ".json")) {
            @Override
            public void save() {
                // keep unit tests off the disk
            }
        };
        service = new QuestService(repository, now::get);
        user = new User("tester", "hash", "Tess", "t@mail.com", Gender.FEMALE);
    }

    private String today() {
        return java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now.get()),
                java.time.ZoneId.systemDefault()).toString();
    }

    @Test
    void criticalQuestsAreListedFirst() {
        List<String> lines = service.lines(user, null);
        assertTrue(lines.get(0).contains("epic-defence-master"),
                "the sheet's only critical quest heads the list: " + lines.get(0));
    }

    @Test
    void unmetQuestCannotBeClaimed() {
        assertTrue(service.claim(user, "story-speed").startsWith("Error"));
        assertTrue(service.claim(user, "no-such-quest").startsWith("Error"));
    }

    @Test
    void aStoryQuestPaysOnceAndOnlyOnce() {
        user.getQuestProgress().record(reportKillingTen(), today());
        assertTrue(service.claim(user, "story-speed").contains("+500 coins"));
        assertEquals(1, user.getQuestsCompleted());
        assertTrue(service.claim(user, "story-speed").startsWith("Error"));
    }

    @Test
    void aDailyQuestComesRoundAgainTheNextDay() {
        user.getQuestProgress().record(reportCollecting(5000), today());
        assertTrue(service.claim(user, "daily-sun-3000").contains("+30 coins"));
        assertTrue(service.claim(user, "daily-sun-3000").startsWith("Error"));

        now.addAndGet(DAY);
        assertTrue(service.claim(user, "daily-sun-3000").startsWith("Error"),
                "a new day starts the sun count again");
        user.getQuestProgress().record(reportCollecting(5000), today());
        assertTrue(service.claim(user, "daily-sun-3000").contains("+30 coins"));
        assertEquals(60, user.getCoins());
        assertEquals(2, user.getQuestsCompleted());
    }

    @Test
    void theMowingQuestsCountUpOverTime() {
        assertTrue(service.claim(user, "epic-mowing-10").startsWith("Error"));
        for (int level = 0; level < 3; level++) {
            user.getQuestProgress().record(reportMowing(5), today());
        }
        assertTrue(service.claim(user, "epic-mowing-10").contains("+10 diamonds"));
        assertTrue(service.claim(user, "epic-mowing-20").startsWith("Error"),
                "fifteen mower kills is not twenty yet");
    }

    private ir.sharif.pvz.model.game.LevelReport reportCollecting(int sun) {
        return report(builder -> { }, sun, 0, java.util.Map.of());
    }

    private ir.sharif.pvz.model.game.LevelReport reportKillingTen() {
        return report(builder -> { }, 0, 10, java.util.Map.of("peashooter", 10));
    }

    private ir.sharif.pvz.model.game.LevelReport reportMowing(int kills) {
        return report(builder -> { }, 0, 0,
                java.util.Map.of(ir.sharif.pvz.model.game.LevelLog.MOWER, kills));
    }

    /**
     * A finished level with only the fields a test cares about filled in.
     */
    private ir.sharif.pvz.model.game.LevelReport report(java.util.function.Consumer<Object> unused,
            int sunCollected, int fastKills, java.util.Map<String, Integer> killsBy) {
        int kills = killsBy.values().stream().mapToInt(Integer::intValue).sum();
        return new ir.sharif.pvz.model.game.LevelReport(false,
                ir.sharif.pvz.model.game.Chapter.ANCIENT_EGYPT, 3, false, 0, sunCollected,
                kills, killsBy, fastKills, 0, 0, java.util.Set.of(),
                java.util.Set.of(), java.util.Set.of(), false, false);
    }
}
