package ir.sharif.pvz.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UserValidatorTest {

    @Test
    void acceptsValidUsername() {
        assertTrue(UserValidator.validateUsername("Zomboss-42").isEmpty());
    }

    @Test
    void rejectsUsernameWithInvalidSymbols() {
        assertFalse(UserValidator.validateUsername("crazy_dave").isEmpty());
        assertFalse(UserValidator.validateUsername("dave!").isEmpty());
    }

    @Test
    void acceptsStrongPassword() {
        assertTrue(UserValidator.validatePassword("Str0ng!Pass").isEmpty());
    }

    @Test
    void rejectsWeakPasswords() {
        assertFalse(UserValidator.validatePassword("Sh0rt!A").isEmpty());
        assertFalse(UserValidator.validatePassword("alllowercase1!").isEmpty());
        assertFalse(UserValidator.validatePassword("ALLUPPERCASE1!").isEmpty());
        assertFalse(UserValidator.validatePassword("NoDigits!!").isEmpty());
        assertFalse(UserValidator.validatePassword("NoSpecial123").isEmpty());
    }

    @Test
    void rejectsNicknameOutOfRange() {
        assertFalse(UserValidator.validateNickname("ab").isEmpty());
        assertFalse(UserValidator.validateNickname("a".repeat(31)).isEmpty());
        assertTrue(UserValidator.validateNickname("Dave").isEmpty());
    }

    @Test
    void acceptsValidEmail() {
        assertTrue(UserValidator.validateEmail("john.doe@example.com").isEmpty());
        assertTrue(UserValidator.validateEmail("a1_b-2@sub.domain.ir").isEmpty());
    }

    @Test
    void rejectsInvalidEmailsFromSpecExamples() {
        assertFalse(UserValidator.validateEmail("john..doe@example.com").isEmpty());
        assertFalse(UserValidator.validateEmail("user@domain").isEmpty());
        assertFalse(UserValidator.validateEmail("user@domain.c").isEmpty());
        assertFalse(UserValidator.validateEmail("user@domain..com").isEmpty());
        assertFalse(UserValidator.validateEmail("user@.com").isEmpty());
    }

    @Test
    void validatesGender() {
        assertTrue(UserValidator.validateGender("male").isEmpty());
        assertTrue(UserValidator.validateGender("FEMALE").isEmpty());
        assertFalse(UserValidator.validateGender("other").isEmpty());
    }
}
