package ir.sharif.pvz.model;

import java.util.List;

/**
 * The built-in quests, one list per travel-log page. Rewards follow the
 * document's three kinds: currency (coins/gems), unlockable (a plant flips
 * from locked to available) and inventory (seed packets).
 */
public final class QuestCatalog {

    private static final List<Quest> QUESTS = List.of(
            new Quest("story-first-blood", "Win your first level", "story", Quest.Priority.CRITICAL,
                    "unlocks snow-pea", false,
                    (user, today) -> user.getLevelsPassed() >= 1,
                    user -> unlock(user, "snow-pea")),
            new Quest("story-pharaoh", "Finish Ancient Egypt (5 levels)", "story", Quest.Priority.CRITICAL,
                    "unlocks the Ancient Egypt plants", false,
                    (user, today) -> user.getLevelsPassed() >= 5,
                    user -> unlock(user, "repeater", "threepeater", "grave-buster", "squash")),
            new Quest("story-thaw", "Finish Frostbite Caves (10 levels)", "story", Quest.Priority.CRITICAL,
                    "unlocks the Frostbite Caves plants", false,
                    (user, today) -> user.getLevelsPassed() >= 10,
                    user -> unlock(user, "cabbage-pult", "hot-potato", "winter-melon",
                            "iceberg-lettuce")),
            new Quest("story-dry-land", "Finish Big Wave Beach (15 levels)", "story", Quest.Priority.CRITICAL,
                    "unlocks the Big Wave Beach plants", false,
                    (user, today) -> user.getLevelsPassed() >= 15,
                    user -> unlock(user, "melon-pult", "tangle-kelp", "sea-shroom", "cattail")),
            new Quest("story-dark-knight", "Finish the Dark Ages (20 levels)", "story",
                    Quest.Priority.CRITICAL, "unlocks the Dark Ages plants", false,
                    (user, today) -> user.getLevelsPassed() >= 20,
                    user -> unlock(user, "fume-shroom", "hypno-shroom", "magnet-shroom",
                            "doom-shroom")),
            new Quest("epic-mow-master", "Reach 500 mow points in the score game", "epic", Quest.Priority.HIGH,
                    "5 diamonds", false,
                    (user, today) -> user.getMaxMewPoints() >= 500,
                    user -> currency(user, 0, 5)),
            new Quest("epic-collector", "Own 20 different plants", "epic", Quest.Priority.HIGH,
                    "10 diamonds", false,
                    (user, today) -> user.getUnlockedPlants().size() >= 20,
                    user -> currency(user, 0, 10)),
            new Quest("epic-mint-family", "Own 25 different plants", "epic", Quest.Priority.HIGH,
                    "unlocks the whole mint family", false,
                    (user, today) -> user.getUnlockedPlants().size() >= 25,
                    user -> unlock(user, "enlighten-mint", "appease-mint", "arma-mint",
                            "bombard-mint", "enforce-mint", "reinforce-mint", "enchant-mint",
                            "pierce-mint", "cattail-mint")),
            new Quest("epic-zombologist", "Observe 10 zombie types", "epic", Quest.Priority.HIGH,
                    "5 diamonds", false,
                    (user, today) -> user.getObservedZombies().size() >= 10,
                    user -> currency(user, 0, 5)),
            new Quest("daily-login", "Log in today", "daily", Quest.Priority.MEDIUM,
                    "100 coins", true,
                    (user, today) -> true,
                    user -> currency(user, 100, 0)),
            new Quest("daily-gardener", "Play a level today", "daily", Quest.Priority.MEDIUM,
                    "3 peashooter seed packets", true,
                    (user, today) -> today.equals(user.getLastPlayedDate()),
                    user -> packets(user, "peashooter", 3)));

    private QuestCatalog() {
    }

    public static List<Quest> all() {
        return QUESTS;
    }

    private static String unlock(User user, String... plants) {
        for (String plant : plants) {
            if (user.getUnlockedPlants().add(plant)) {
                user.addNews("New plant unlocked: " + plant);
            }
        }
        return "Now available: " + String.join(", ", plants) + "!";
    }

    private static String currency(User user, int coins, int diamonds) {
        user.addCoins(coins);
        user.addDiamonds(diamonds);
        return coins > 0 ? "+" + coins + " coins" : "+" + diamonds + " diamonds";
    }

    private static String packets(User user, String plant, int count) {
        user.getSeedPackets().merge(plant, count, Integer::sum);
        return "+" + count + " " + plant + " seed packets";
    }
}
