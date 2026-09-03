package ir.sharif.pvz.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LeaderboardServiceTest {

    private static UserRepository isolatedRepository() {
        return new UserRepository(java.nio.file.Path.of("build", "test-users-" + System.nanoTime() + ".json")) {
            @Override
            public void save() {
                // keep unit tests off the disk
            }
        };
    }

    private static UserRepository repositoryWithPlayers() {
        UserRepository repository = isolatedRepository();
        User strong = new User("strong", "h", "S", "s@mail.com", Gender.FEMALE);
        strong.updateMaxMewPoints(900);
        strong.setLevelsPassed(3);
        User weak = new User("weak", "h", "W", "w@mail.com", Gender.MALE);
        weak.updateMaxMewPoints(100);
        repository.add(strong);
        repository.add(weak);
        return repository;
    }

    /**
     * The score game played against the server has a column of its own, and it
     * belongs in the console table as much as in the graphical one. A player
     * who never played one online shows a dash, not a zero.
     */
    @Test
    void theTableCarriesTheNetworkedScoreGameColumn() {
        UserRepository repository = repositoryWithPlayers();
        repository.findByUsername("strong").submitNetworkPoints(420);
        List<String> lines = new LeaderboardService(repository)
                .table(LeaderboardService.Column.MYPOINT, false);
        assertTrue(lines.get(0).contains("strong"), lines.get(0));
        assertTrue(lines.get(0).contains("my point: 420"), lines.get(0));
        assertTrue(lines.get(1).contains("my point: -"),
                "a player with no online record shows a dash: " + lines.get(1));
    }

    @Test
    void sortsByMowPointsDescendingByDefaultDirection() {
        LeaderboardService service = new LeaderboardService(repositoryWithPlayers());
        List<String> lines = service.table(LeaderboardService.Column.MOWPOINTS, false);
        assertTrue(lines.get(0).contains("strong"));
        assertTrue(lines.get(1).contains("weak"));
    }

    @Test
    void ascendingOrderFlipsTheTable() {
        LeaderboardService service = new LeaderboardService(repositoryWithPlayers());
        List<String> lines = service.table(LeaderboardService.Column.MOWPOINTS, true);
        assertTrue(lines.get(0).contains("weak"));
    }

    @Test
    void showsLastPassedLevelTitle() {
        LeaderboardService service = new LeaderboardService(repositoryWithPlayers());
        List<String> lines = service.table(LeaderboardService.Column.LEVEL, false);
        assertTrue(lines.get(0).contains("Ancient Egypt - Day 3"));
        assertTrue(lines.get(1).contains("last passed: -"));
    }

    @Test
    void emptyRepositoryShowsAFriendlyLine() {
        assertEquals(List.of("No player has registered yet."),
                new LeaderboardService(isolatedRepository()).table(LeaderboardService.Column.QUESTS, false));
    }
}
