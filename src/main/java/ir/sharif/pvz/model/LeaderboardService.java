package ir.sharif.pvz.model;

import ir.sharif.pvz.model.game.Levels;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The score table over all registered players; every column can be sorted
 * ascending or descending.
 */
public class LeaderboardService {

    /** The sortable columns of the table. */
    public enum Column { LEVEL, MINIGAMES, QUESTS, MOWPOINTS }

    private final UserRepository userRepository;

    public LeaderboardService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * One line per registered player, sorted by the given column.
     */
    public List<String> table(Column sortBy, boolean ascending) {
        Comparator<User> comparator = comparatorFor(sortBy);
        if (!ascending) {
            comparator = comparator.reversed();
        }
        List<User> users = new ArrayList<>(userRepository.all());
        users.sort(comparator);
        List<String> lines = new ArrayList<>();
        int rank = 1;
        for (User user : users) {
            lines.add(rank++ + ". " + user.getUsername()
                    + " | last passed: " + lastPassed(user)
                    + " | minigames: " + user.getMinigamesCompleted()
                    + " | quests: " + user.getQuestsCompleted()
                    + " | mow points: " + user.getMaxMewPoints());
        }
        if (lines.isEmpty()) {
            lines.add("No player has registered yet.");
        }
        return lines;
    }

    private Comparator<User> comparatorFor(Column column) {
        return switch (column) {
            case LEVEL -> Comparator.comparingInt(User::getLevelsPassed);
            case MINIGAMES -> Comparator.comparingInt(User::getMinigamesCompleted);
            case QUESTS -> Comparator.comparingInt(User::getQuestsCompleted);
            case MOWPOINTS -> Comparator.comparingInt(User::getMaxMewPoints);
        };
    }

    private String lastPassed(User user) {
        if (user.getLevelsPassed() <= 0) {
            return "-";
        }
        int index = Math.min(user.getLevelsPassed(), Levels.adventure().size()) - 1;
        return Levels.adventure().get(index).title();
    }
}
