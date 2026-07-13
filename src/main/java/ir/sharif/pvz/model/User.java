package ir.sharif.pvz.model;

import java.util.ArrayList;
import java.util.List;

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
    private List<NewsItem> news = new ArrayList<>();

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
}
