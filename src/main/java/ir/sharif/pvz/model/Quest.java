package ir.sharif.pvz.model;

import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * One quest of the travel log: a completion condition over the user's state
 * and a reward that is granted once the player claims it. Daily quests can be
 * claimed again every calendar day.
 */
public class Quest {

    /** Display priority; critical story quests always sit on top. */
    public enum Priority { CRITICAL, HIGH, MEDIUM, LOW }

    private final String id;
    private final String title;
    private final String page;
    private final Priority priority;
    private final String rewardDescription;
    private final boolean daily;
    private final BiPredicate<User, String> condition;
    private final Function<User, String> reward;

    public Quest(String id, String title, String page, Priority priority, String rewardDescription,
                 boolean daily, BiPredicate<User, String> condition, Function<User, String> reward) {
        this.id = id;
        this.title = title;
        this.page = page;
        this.priority = priority;
        this.rewardDescription = rewardDescription;
        this.daily = daily;
        this.condition = condition;
        this.reward = reward;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getPage() {
        return page;
    }

    public Priority getPriority() {
        return priority;
    }

    public String getRewardDescription() {
        return rewardDescription;
    }

    public boolean isDaily() {
        return daily;
    }

    public boolean isMet(User user, String today) {
        return condition.test(user, today);
    }

    /**
     * Applies the reward to the user and returns what was granted.
     */
    public String grant(User user) {
        return reward.apply(user);
    }
}
