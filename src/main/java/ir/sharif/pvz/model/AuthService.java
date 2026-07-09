package ir.sharif.pvz.model;

import ir.sharif.pvz.util.PasswordHasher;
import java.util.ArrayList;
import java.util.List;

/**
 * Business logic for registration, login and password recovery.
 */
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Validates every field of a register request except password confirmation.
     * Returns the full list of errors so the caller can show all of them at once.
     */
    public List<String> validateRegistration(RegisterRequest request) {
        List<String> errors = new ArrayList<>(UserValidator.validateUsername(request.username()));
        if (errors.isEmpty() && userRepository.usernameExists(request.username())) {
            errors.add("Username '" + request.username() + "' already exists.");
        }
        errors.addAll(UserValidator.validatePassword(request.password()));
        errors.addAll(UserValidator.validateNickname(request.nickname()));
        errors.addAll(UserValidator.validateEmail(request.email()));
        errors.addAll(UserValidator.validateGender(request.gender()));
        return errors;
    }

    public boolean passwordsMatch(RegisterRequest request) {
        return request.password().equals(request.passwordConfirm());
    }

    /**
     * Creates the account after all validations passed and the security question was answered.
     */
    public User register(RegisterRequest request, int questionNumber, String answer) {
        User user = new User(
                request.username(),
                PasswordHasher.sha256(request.password()),
                request.nickname(),
                request.email(),
                Gender.fromString(request.gender()));
        user.setSecurityQuestion(questionNumber, PasswordHasher.sha256(answer));
        userRepository.add(user);
        return user;
    }

    public User login(String username, String password) throws AuthException {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new AuthException("Username '" + username + "' does not exist.");
        }
        if (!PasswordHasher.matches(password, user.getPasswordHash())) {
            throw new AuthException("Incorrect password.");
        }
        return user;
    }

    /**
     * Starts the forget-password flow; returns the user whose security question must be answered.
     */
    public User startForgetPassword(String username, String email) throws AuthException {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new AuthException("Username '" + username + "' does not exist.");
        }
        if (!user.getEmail().equals(email)) {
            throw new AuthException("Email does not match this account.");
        }
        return user;
    }

    public boolean checkSecurityAnswer(User user, String answer) {
        return PasswordHasher.matches(answer, user.getSecurityAnswerHash());
    }

    public void resetPassword(User user, String newPassword) {
        user.setPasswordHash(PasswordHasher.sha256(newPassword));
        userRepository.save();
    }
}
