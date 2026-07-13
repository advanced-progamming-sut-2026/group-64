package ir.sharif.pvz.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ir.sharif.pvz.util.PasswordHasher;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileServiceTest {

    @TempDir
    Path tempDir;

    private UserRepository repository;
    private ProfileService service;
    private User user;

    @BeforeEach
    void setUp() {
        repository = new UserRepository(tempDir.resolve("users.json"));
        service = new ProfileService(repository);
        user = new User("dave", PasswordHasher.sha256("Str0ng!Pass"), "Crazy Dave",
                "dave@example.com", Gender.MALE);
        repository.add(user);
    }

    @Test
    void changesUsername() throws AuthException {
        service.changeUsername(user, "dave-2");
        assertEquals("dave-2", user.getUsername());
    }

    @Test
    void rejectsSameUsername() {
        assertThrows(AuthException.class, () -> service.changeUsername(user, "dave"));
    }

    @Test
    void rejectsTakenUsername() {
        repository.add(new User("penny", "hash", "Penny", "penny@example.com", Gender.FEMALE));
        assertThrows(AuthException.class, () -> service.changeUsername(user, "penny"));
    }

    @Test
    void rejectsSameNickname() {
        assertThrows(AuthException.class, () -> service.changeNickname(user, "Crazy Dave"));
    }

    @Test
    void rejectsInvalidEmail() {
        assertThrows(AuthException.class, () -> service.changeEmail(user, "not-an-email"));
    }

    @Test
    void rejectsSameEmail() {
        assertThrows(AuthException.class, () -> service.changeEmail(user, "dave@example.com"));
    }

    @Test
    void changesPasswordWithCorrectOldPassword() throws AuthException {
        service.changePassword(user, "N3w!Passw0rd", "Str0ng!Pass");
        assertTrue(PasswordHasher.matches("N3w!Passw0rd", user.getPasswordHash()));
    }

    @Test
    void rejectsWrongOldPassword() {
        assertThrows(AuthException.class, () -> service.changePassword(user, "N3w!Passw0rd", "wrong"));
    }

    @Test
    void rejectsUnchangedPassword() {
        assertThrows(AuthException.class, () -> service.changePassword(user, "Str0ng!Pass", "Str0ng!Pass"));
    }

    @Test
    void rejectsWeakNewPassword() {
        assertThrows(AuthException.class, () -> service.changePassword(user, "weak", "Str0ng!Pass"));
    }
}
