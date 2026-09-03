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
    private java.util.function.BiFunction<User, String, String> progress;

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

    /**
     * Attaches a readout of how far along this quest is, for the ones that
     * count towards a number rather than simply happening.
     *
     * @return this quest, so it can be attached where the quest is built
     */
    public Quest measuredBy(java.util.function.BiFunction<User, String, String> readout) {
        this.progress = readout;
        return this;
    }

    /**
     * How far along this player is, e.g. "3200 / 5000", or null when the quest
     * is one that either has happened or has not.
     */
    public String progress(User user, String today) {
        return progress == null ? null : progress.apply(user, today);
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
