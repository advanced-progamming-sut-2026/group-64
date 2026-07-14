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

    @Test
    void criticalQuestsAreListedFirst() {
        List<String> lines = service.lines(user, null);
        assertTrue(lines.get(0).contains("story-"));
        assertTrue(lines.get(lines.size() - 1).contains("daily-"));
    }

    @Test
    void unmetQuestCannotBeClaimed() {
        assertTrue(service.claim(user, "story-first-blood").startsWith("Error"));
        assertTrue(service.claim(user, "no-such-quest").startsWith("Error"));
    }

    @Test
    void storyQuestUnlocksAPlantOnce() {
        user.setLevelsPassed(1);
        String result = service.claim(user, "story-first-blood");
        assertTrue(result.contains("snow-pea"));
        assertTrue(user.getUnlockedPlants().contains("snow-pea"));
        assertEquals(1, user.getQuestsCompleted());
        assertTrue(service.claim(user, "story-first-blood").startsWith("Error"));
    }

    @Test
    void epicQuestPaysDiamonds() {
        user.updateMaxMewPoints(600);
        assertTrue(service.claim(user, "epic-mow-master").contains("+5 diamonds"));
        assertEquals(5, user.getDiamonds());
    }

    @Test
    void dailyLoginIsClaimableAgainTheNextDay() {
        assertTrue(service.claim(user, "daily-login").contains("+100 coins"));
        assertTrue(service.claim(user, "daily-login").startsWith("Error"));
        now.addAndGet(DAY);
        assertTrue(service.claim(user, "daily-login").contains("+100 coins"));
        assertEquals(200, user.getCoins());
        assertEquals(2, user.getQuestsCompleted());
    }

    @Test
    void dailyGardenerNeedsAGamePlayedToday() {
        assertTrue(service.claim(user, "daily-gardener").startsWith("Error"));
        user.setLastPlayedDate(java.time.LocalDate.ofInstant(
                java.time.Instant.ofEpochMilli(now.get()), java.time.ZoneId.systemDefault()).toString());
        assertTrue(service.claim(user, "daily-gardener").contains("peashooter"));
        assertEquals(3, user.getSeedPackets().get("peashooter"));
    }
}
