package ir.sharif.pvz.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * The travel log: lists quests by page (critical priority always first) and
 * hands out rewards when the player claims a met quest.
 */
public class QuestService {

    private final UserRepository userRepository;
    private final LongSupplier clock;

    public QuestService(UserRepository userRepository, LongSupplier clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * The quest lines of one page, or of every page when page is null;
     * sorted by priority so critical story quests stay on top.
     */
    public List<String> lines(User user, String page) {
        List<Quest> quests = QuestCatalog.all().stream()
                .filter(quest -> page == null || quest.getPage().equals(page))
                .sorted(Comparator.comparing(Quest::getPriority))
                .toList();
        List<String> lines = new ArrayList<>();
        for (Quest quest : quests) {
            lines.add("[" + quest.getId() + "] " + quest.getTitle()
                    + " -> " + quest.getRewardDescription() + " (" + status(user, quest) + ")");
        }
        if (lines.isEmpty()) {
            lines.add("There is no quest page named '" + page + "'.");
        }
        return lines;
    }

    private String status(User user, Quest quest) {
        String claimed = user.getClaimedQuests().get(quest.getId());
        if (quest.isDaily()) {
            if (today().equals(claimed)) {
                return "claimed today";
            }
        } else if (claimed != null) {
            return "completed";
        }
        return quest.isMet(user, today()) ? "ready to claim" : "in progress";
    }

    /**
     * Claims a met quest: grants the reward and counts it for the leaderboard.
     * Daily quests can be claimed once per calendar day.
     */
    public String claim(User user, String questId) {
        Quest quest = QuestCatalog.all().stream()
                .filter(candidate -> candidate.getId().equals(questId))
                .findFirst().orElse(null);
        if (quest == null) {
            return "Error: there is no quest with id '" + questId + "'.";
        }
        String claimed = user.getClaimedQuests().get(questId);
        if (quest.isDaily() && today().equals(claimed)) {
            return "Error: you already claimed this quest today.";
        }
        if (!quest.isDaily() && claimed != null) {
            return "Error: this quest is already completed.";
        }
        if (!quest.isMet(user, today())) {
            return "Error: the quest '" + quest.getTitle() + "' is not completed yet.";
        }
        String granted = quest.grant(user);
        user.getClaimedQuests().put(questId, quest.isDaily() ? today() : "done");
        user.incrementQuestsCompleted();
        userRepository.save();
        return "Quest '" + quest.getTitle() + "' completed: " + granted;
    }

    private String today() {
        return LocalDate.ofInstant(Instant.ofEpochMilli(clock.getAsLong()), ZoneId.systemDefault()).toString();
    }
}
