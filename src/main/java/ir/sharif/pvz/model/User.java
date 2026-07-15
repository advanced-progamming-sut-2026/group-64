package ir.sharif.pvz.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A registered player account.
 */
public class User {

    public static final int DEFAULT_DIFFICULTY = 3;

    private String username;
    private String passwordHash;
    private String nickname;
    private String email;
    private Gender gender;
    private int securityQuestionNumber;
    private String securityAnswerHash;

    private int difficulty = DEFAULT_DIFFICULTY;
    private int gamesPlayed;
    private int coins;
    private int diamonds;
    private int levelsPassed;
    private int maxMewPoints;
    private int pots;
    private int minigamesCompleted;
    private int questsCompleted;
    private List<NewsItem> news = new ArrayList<>();
    private Set<String> unlockedPlants = defaultPlants();
    private Set<String> observedZombies = new LinkedHashSet<>();
    private Map<String, Integer> seedPackets = new HashMap<>();
    private Map<String, Integer> plantLevels = new HashMap<>();
    private List<GreenhousePot> greenhousePots;
    private Set<String> storedBoosts = new LinkedHashSet<>();
    private int pendingPlantFood;
    private String lastDailyPurchaseDate;
    private Map<String, String> claimedQuests = new HashMap<>();
    private String lastPlayedDate;
    private Map<String, Integer> minigameProgress = new HashMap<>();

    private static Set<String> defaultPlants() {
        return new LinkedHashSet<>(
                List.of("sunflower", "peashooter", "wall-nut", "cherry-bomb", "potato-mine"));
    }

    public User(String username, String passwordHash, String nickname, String email, Gender gender) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.email = email;
        this.gender = gender;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Gender getGender() {
        return gender;
    }

    public int getSecurityQuestionNumber() {
        return securityQuestionNumber;
    }

    public String getSecurityAnswerHash() {
        return securityAnswerHash;
    }

    public void setSecurityQuestion(int questionNumber, String answerHash) {
        this.securityQuestionNumber = questionNumber;
        this.securityAnswerHash = answerHash;
    }

    /**
     * Difficulty is clamped for accounts saved before this field existed (Gson leaves it 0).
     */
    public int getDifficulty() {
        return difficulty == 0 ? DEFAULT_DIFFICULTY : difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public void incrementGamesPlayed() {
        this.gamesPlayed++;
    }

    public int getCoins() {
        return coins;
    }

    public void addCoins(int amount) {
        this.coins += amount;
    }

    public int getDiamonds() {
        return diamonds;
    }

    public void addDiamonds(int amount) {
        this.diamonds += amount;
    }

    public int getLevelsPassed() {
        return levelsPassed;
    }

    public void setLevelsPassed(int levelsPassed) {
        this.levelsPassed = levelsPassed;
    }

    public int getMaxMewPoints() {
        return maxMewPoints;
    }

    public void updateMaxMewPoints(int mewPoints) {
        if (mewPoints > maxMewPoints) {
            this.maxMewPoints = mewPoints;
        }
    }

    /**
     * News list is created lazily for accounts saved before this field existed.
     */
    public List<NewsItem> getNews() {
        if (news == null) {
            news = new ArrayList<>();
        }
        return news;
    }

    public void addNews(String text) {
        getNews().add(new NewsItem(text));
    }

    public int getMinigamesCompleted() {
        return minigamesCompleted;
    }

    public void incrementMinigamesCompleted() {
        this.minigamesCompleted++;
    }

    public int getQuestsCompleted() {
        return questsCompleted;
    }

    public void incrementQuestsCompleted() {
        this.questsCompleted++;
    }

    public int getPots() {
        return pots;
    }

    public void addPots(int amount) {
        this.pots += amount;
    }

    /**
     * Collections are created lazily for accounts saved before these fields existed.
     */
    public Set<String> getUnlockedPlants() {
        if (unlockedPlants == null || unlockedPlants.isEmpty()) {
            unlockedPlants = defaultPlants();
        }
        return unlockedPlants;
    }

    public Set<String> getObservedZombies() {
        if (observedZombies == null) {
            observedZombies = new LinkedHashSet<>();
        }
        return observedZombies;
    }

    public Map<String, Integer> getSeedPackets() {
        if (seedPackets == null) {
            seedPackets = new HashMap<>();
        }
        return seedPackets;
    }

    /**
     * Plant upgrade level; every plant starts at level 1.
     */
    public int getPlantLevel(String plant) {
        if (plantLevels == null) {
            plantLevels = new HashMap<>();
        }
        return plantLevels.getOrDefault(plant, 1);
    }

    public void setPlantLevel(String plant, int level) {
        if (plantLevels == null) {
            plantLevels = new HashMap<>();
        }
        plantLevels.put(plant, level);
    }

    public void spendCoins(int amount) {
        this.coins -= amount;
    }

    public void spendDiamonds(int amount) {
        this.diamonds -= amount;
    }

    /**
     * The twenty greenhouse pots (4 rows x 5 columns); only the first row
     * starts unlocked. Created lazily for accounts saved before this field.
     */
    public List<GreenhousePot> getGreenhousePots() {
        if (greenhousePots == null || greenhousePots.isEmpty()) {
            greenhousePots = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                greenhousePots.add(new GreenhousePot(i < 5));
            }
        }
        return greenhousePots;
    }

    /**
     * Unlocks the next locked pot slot; returns false when all 20 are open.
     */
    public boolean unlockNextPot() {
        for (GreenhousePot pot : getGreenhousePots()) {
            if (!pot.isUnlocked()) {
                pot.unlock();
                return true;
            }
        }
        return false;
    }

    public Set<String> getStoredBoosts() {
        if (storedBoosts == null) {
            storedBoosts = new LinkedHashSet<>();
        }
        return storedBoosts;
    }

    /**
     * Plant food bought in the shop for the start of the next level (max 3).
     */
    public int getPendingPlantFood() {
        return pendingPlantFood;
    }

    public void setPendingPlantFood(int pendingPlantFood) {
        this.pendingPlantFood = pendingPlantFood;
    }

    public String getLastDailyPurchaseDate() {
        return lastDailyPurchaseDate;
    }

    public void setLastDailyPurchaseDate(String lastDailyPurchaseDate) {
        this.lastDailyPurchaseDate = lastDailyPurchaseDate;
    }

    /**
     * Quest id to claim marker: "done" for one-shot quests, the claim date
     * for daily quests. Created lazily for older accounts.
     */
    public Map<String, String> getClaimedQuests() {
        if (claimedQuests == null) {
            claimedQuests = new HashMap<>();
        }
        return claimedQuests;
    }

    /**
     * Highest completed stage per minigame name; created lazily.
     */
    public Map<String, Integer> getMinigameProgress() {
        if (minigameProgress == null) {
            minigameProgress = new HashMap<>();
        }
        return minigameProgress;
    }

    public String getLastPlayedDate() {
        return lastPlayedDate;
    }

    public void setLastPlayedDate(String lastPlayedDate) {
        this.lastPlayedDate = lastPlayedDate;
    }
}
