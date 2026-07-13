package ir.sharif.pvz.model;

import ir.sharif.pvz.util.PasswordHasher;
import java.util.List;

/**
 * Business logic for editing the current user's account in the profile menu.
 */
public class ProfileService {

    private final UserRepository userRepository;

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void changeUsername(User user, String newUsername) throws AuthException {
        if (newUsername.equals(user.getUsername())) {
            throw new AuthException("New username is the same as your current username.");
        }
        List<String> errors = UserValidator.validateUsername(newUsername);
        if (!errors.isEmpty()) {
            throw new AuthException(errors.get(0));
        }
        if (userRepository.usernameExists(newUsername)) {
            throw new AuthException("Username '" + newUsername + "' already exists.");
        }
        user.setUsername(newUsername);
        userRepository.save();
    }

    public void changeNickname(User user, String newNickname) throws AuthException {
        if (newNickname.equals(user.getNickname())) {
            throw new AuthException("New nickname is the same as your current nickname.");
        }
        List<String> errors = UserValidator.validateNickname(newNickname);
        if (!errors.isEmpty()) {
            throw new AuthException(errors.get(0));
        }
        user.setNickname(newNickname);
        userRepository.save();
    }

    public void changeEmail(User user, String newEmail) throws AuthException {
        if (newEmail.equals(user.getEmail())) {
            throw new AuthException("New email is the same as your current email.");
        }
        List<String> errors = UserValidator.validateEmail(newEmail);
        if (!errors.isEmpty()) {
            throw new AuthException(String.join(" ", errors));
        }
        user.setEmail(newEmail);
        userRepository.save();
    }

    public void changePassword(User user, String newPassword, String oldPassword) throws AuthException {
        if (!PasswordHasher.matches(oldPassword, user.getPasswordHash())) {
            throw new AuthException("Old password is incorrect.");
        }
        if (newPassword.equals(oldPassword)) {
            throw new AuthException("New password is the same as your current password.");
        }
        List<String> errors = UserValidator.validatePassword(newPassword);
        if (!errors.isEmpty()) {
            throw new AuthException(String.join(" ", errors));
        }
        user.setPasswordHash(PasswordHasher.sha256(newPassword));
        userRepository.save();
    }
}
