package ir.sharif.pvz.model;

import ir.sharif.pvz.model.game.Chapter;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.PlantCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The quests the project sheet lists, one entry per row of its Quests page.
 *
 * <p>Rows with a variables column become several quests, one per value: the
 * sun collector wants 3000, 4000 or 5000 sun; the hunter wants fifty zombies
 * from each chapter in turn; the bare column and row quests want each column
 * and each row. Everything they ask about is tallied in {@link QuestProgress}
 * as levels are finished.
 *
 * <p>The sheet's three categories are the travel log's three pages: روزانه is
 * daily, اصلی is story, and چالش is epic.
 */
public final class QuestCatalog {

    private static final List<Quest> QUESTS = build();

    private QuestCatalog() {
    }

    public static List<Quest> all() {
        return QUESTS;
    }

    private static List<Quest> build() {
        List<Quest> quests = new ArrayList<>();
        quests.addAll(sunAndHunting());
        quests.addAll(killingStyle());
        quests.addAll(winningStyle());
        quests.addAll(bareLines());
        return quests;
    }

    // ===== the sheet's first rows: sun, chapter hunting, single-plant kills =====

    private static List<Quest> sunAndHunting() {
        List<Quest> quests = new ArrayList<>();
        for (int target : new int[] {3000, 4000, 5000}) {
            int coins = target / 100;
            quests.add(new Quest("daily-sun-" + target,
                    "Collect " + target + " sun in one day", "daily", Quest.Priority.MEDIUM,
                    coins + " coins", true,
                    (user, today) -> user.getQuestProgress().todays(QuestProgress.SUN, today) >= target,
                    user -> currency(user, coins, 0)));
        }
        for (Chapter chapter : Chapter.values()) {
            String key = QuestProgress.chapterKills(chapter.name());
            quests.add(new Quest("story-hunter-" + chapter.id(),
                    "Beat 50 zombies in " + chapter.displayName(), "story", Quest.Priority.HIGH,
                    "10 seed packets", false,
                    (user, today) -> user.getQuestProgress().total(key) >= 50,
                    user -> packets(user, "peashooter", 10)));
        }
        return quests;
    }

    private static List<Quest> killingStyle() {
        List<Quest> quests = new ArrayList<>();
        quests.add(new Quest("daily-one-plant",
                "Kill ten zombies in a level with a single plant", "daily", Quest.Priority.HIGH,
                "a plant you do not own yet", true,
                (user, today) -> bestSolePlant(user, today) >= 10,
                QuestCatalog::unlockSomethingNew));
        quests.add(new Quest("daily-only-cactus",
                "Kill ten zombies in a level using only the cactus", "daily", Quest.Priority.HIGH,
                "20 diamonds", true,
                (user, today) -> user.getQuestProgress()
                        .todays(QuestProgress.soleKiller("cactus"), today) >= 10,
                user -> currency(user, 0, 20)));
        quests.add(new Quest("story-speed",
                "Kill ten zombies within thirty seconds of the first wave", "story",
                Quest.Priority.MEDIUM, "500 coins", false,
                (user, today) -> user.getQuestProgress().total(QuestProgress.FAST_TEN) >= 10,
                user -> currency(user, 500, 0)));
        quests.add(new Quest("daily-close-call",
                "Kill ten zombies at the door of a lane with no mower left", "daily",
                Quest.Priority.MEDIUM, "300 coins", true,
                (user, today) -> user.getQuestProgress()
                        .todays(QuestProgress.AT_THE_DOOR, today) >= 10,
                user -> currency(user, 300, 0)));
        quests.add(new Quest("daily-demolition",
                "Use three explosive plants in one level", "daily", Quest.Priority.LOW,
                "100 coins", true,
                (user, today) -> user.getQuestProgress()
                        .todays(QuestProgress.EXPLOSIVES_USED, today) >= 3,
                user -> currency(user, 100, 0)));
        for (int target : new int[] {10, 20, 30, 40, 50}) {
            quests.add(new Quest("epic-mowing-" + target,
                    "Finish off " + target + " zombies with lawn mowers", "epic",
                    Quest.Priority.MEDIUM, target + " diamonds", false,
                    (user, today) -> user.getQuestProgress()
                            .total(QuestProgress.MOWER_KILLS) >= target,
                    user -> currency(user, 0, target)));
        }
        return quests;
    }

    // ===== the rows about how a level was won =====

    private static List<Quest> winningStyle() {
        List<Quest> quests = new ArrayList<>();
        for (int allowed = 0; allowed <= 5; allowed++) {
            int lost = allowed;
            int reward = 20 - allowed;
            quests.add(new Quest("story-thrifty-" + allowed,
                    "Win a level losing no more than " + allowed + " plants", "story",
                    Quest.Priority.HIGH, reward + " seed packets", false,
                    (user, today) -> wonLosingAtMost(user, lost),
                    user -> packets(user, "sunflower", reward)));
        }
        quests.add(new Quest("epic-defence-master", "Finish a level with exactly no sun left",
                "epic", Quest.Priority.CRITICAL, "200 diamonds", false,
                (user, today) -> user.getQuestProgress().total(QuestProgress.ZERO_SUN_WIN) > 0,
                user -> currency(user, 0, 200)));
        quests.add(new Quest("daily-symmetry", "Win with a lawn that mirrors top to bottom",
                "daily", Quest.Priority.HIGH, "500 coins", true,
                (user, today) -> user.getQuestProgress()
                        .todays(QuestProgress.SYMMETRIC_WIN, today) > 0,
                user -> currency(user, 500, 0)));
        quests.add(new Quest("daily-no-symmetry",
                "Win with no symmetry on the lawn at all", "daily", Quest.Priority.MEDIUM,
                "800 coins", true,
                (user, today) -> user.getQuestProgress()
                        .todays(QuestProgress.ASYMMETRIC_WIN, today) > 0,
                user -> currency(user, 800, 0)));
        quests.add(new Quest("epic-night-shift",
                "Finish a day level using nothing but mushrooms", "epic", Quest.Priority.HIGH,
                "20 diamonds", false,
                (user, today) -> user.getQuestProgress()
                        .total(QuestProgress.NIGHT_ON_A_DAY_WIN) > 0,
                user -> currency(user, 0, 20)));
        quests.add(new Quest("daily-streak",
                "Win five levels in a row on the hardest difficulty", "daily",
                Quest.Priority.MEDIUM, "5000 coins", true,
                (user, today) -> user.getQuestProgress()
                        .total(QuestProgress.HARDEST_WIN_STREAK) >= 5,
                user -> currency(user, 5000, 0)));
        quests.add(new Quest("daily-overcast",
                "Win a level with no more than three sun producers", "daily",
                Quest.Priority.HIGH, "10 diamonds", true,
                (user, today) -> wonWithFewSunProducers(user, today),
                user -> currency(user, 0, 10)));
        quests.addAll(familyQuests());
        return quests;
    }

    private static List<Quest> familyQuests() {
        List<Quest> quests = new ArrayList<>();
        for (PlantCategory family : PlantCategory.values()) {
            String pretty = pretty(family);
            quests.add(new Quest("daily-family-only-" + family.name().toLowerCase(Locale.ROOT),
                    "Kill only with " + pretty + " plants", "daily", Quest.Priority.MEDIUM,
                    "1000 coins", true,
                    (user, today) -> user.getQuestProgress()
                            .todays(QuestProgress.onlyFamily(family), today) > 0,
                    user -> currency(user, 1000, 0)));
            quests.add(new Quest("daily-family-free-" + family.name().toLowerCase(Locale.ROOT),
                    "Win without planting a single " + pretty + " plant", "daily",
                    Quest.Priority.HIGH, "100 diamonds", true,
                    (user, today) -> user.getQuestProgress()
                            .todays(QuestProgress.withoutFamily(family), today) > 0,
                    user -> currency(user, 0, 100)));
        }
        return quests;
    }

    // ===== the rows about leaving part of the lawn bare =====

    private static List<Quest> bareLines() {
        List<Quest> quests = new ArrayList<>();
        for (int column = 1; column <= GameSession.COLS; column++) {
            int index = column;
            quests.add(new Quest("daily-bare-column-" + column,
                    "Win without planting in column " + column, "daily", Quest.Priority.HIGH,
                    "10 diamonds", true,
                    (user, today) -> user.getQuestProgress()
                            .todays(QuestProgress.emptyColumn(index), today) > 0,
                    user -> currency(user, 0, 10)));
        }
        for (int row = 1; row <= GameSession.ROWS; row++) {
            int index = row;
            quests.add(new Quest("daily-bare-row-" + row,
                    "Win without planting in row " + row, "daily", Quest.Priority.HIGH,
                    "20 diamonds", true,
                    (user, today) -> user.getQuestProgress()
                            .todays(QuestProgress.emptyRow(index), today) > 0,
                    user -> currency(user, 0, 20)));
        }
        for (int index = 1; index <= Math.min(GameSession.ROWS, GameSession.COLS); index++) {
            int line = index;
            quests.add(new Quest("daily-bare-cross-" + index,
                    "Win with both row and column " + index + " bare", "daily",
                    Quest.Priority.HIGH, "25 diamonds", true,
                    (user, today) -> user.getQuestProgress()
                            .todays(QuestProgress.cross(line), today) > 0,
                    user -> currency(user, 0, 25)));
        }
        return quests;
    }

    // ===== conditions that need more than one lookup =====

    private static int bestSolePlant(User user, String today) {
        int best = 0;
        for (var spec : ir.sharif.pvz.model.game.GameCatalog.get().allPlants()) {
            best = Math.max(best, user.getQuestProgress()
                    .todays(QuestProgress.soleKiller(spec.getName()), today));
        }
        return best;
    }

    private static boolean wonLosingAtMost(User user, int allowed) {
        int fewest = user.getQuestProgress().total(QuestProgress.FEWEST_PLANTS_LOST);
        return user.getLevelsPassed() > 0 && fewest <= allowed;
    }

    private static boolean wonWithFewSunProducers(User user, String today) {
        QuestProgress progress = user.getQuestProgress();
        return progress.todays(QuestProgress.SUN_PRODUCERS_WIN, today) > 0
                && progress.todays(QuestProgress.SUN_PRODUCERS_WIN, today) <= 3;
    }

    // ===== rewards =====

    private static String pretty(PlantCategory family) {
        return family.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String unlockSomethingNew(User user) {
        for (var spec : ir.sharif.pvz.model.game.GameCatalog.get().allPlants()) {
            if (user.getUnlockedPlants().add(spec.getName())) {
                user.addNews("New plant unlocked: " + spec.getName());
                return "Plant " + spec.getName() + " is now available!";
            }
        }
        return currency(user, 500, 0);
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
