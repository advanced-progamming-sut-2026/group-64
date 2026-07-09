package ir.sharif.pvz.model;

/**
 * A registered player account.
 */
public class User {

    private String username;
    private String passwordHash;
    private String nickname;
    private String email;
    private Gender gender;
    private int securityQuestionNumber;
    private String securityAnswerHash;

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
}
