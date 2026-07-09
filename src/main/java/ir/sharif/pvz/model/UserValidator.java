package ir.sharif.pvz.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Field validation rules for user accounts, as required by the project spec.
 */
public final class UserValidator {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9-]+$");
    private static final Pattern PASSWORD_CHARSET_PATTERN =
            Pattern.compile("^[A-Za-z0-9!#$%^&*()=+{}\\[\\]|\\\\/:;'\",<>?]+$");
    private static final Pattern PASSWORD_SPECIAL_PATTERN =
            Pattern.compile("[!#$%^&*()=+{}\\[\\]|\\\\/:;'\",<>?]");
    private static final Pattern EMAIL_PART_CHARSET_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final Pattern DOMAIN_CHARSET_PATTERN = Pattern.compile("^[A-Za-z0-9.-]+$");
    private static final Pattern LETTER_OR_DIGIT_PATTERN = Pattern.compile("[A-Za-z0-9]");

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MIN_NICKNAME_LENGTH = 3;
    private static final int MAX_NICKNAME_LENGTH = 30;
    private static final int MIN_DOMAIN_SUFFIX_LENGTH = 2;

    private UserValidator() {
    }

    public static List<String> validateUsername(String username) {
        List<String> errors = new ArrayList<>();
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            errors.add("Username can only contain English letters, digits and '-'.");
        }
        return errors;
    }

    public static List<String> validatePassword(String password) {
        List<String> errors = new ArrayList<>();
        if (!PASSWORD_CHARSET_PATTERN.matcher(password).matches()) {
            errors.add("Password can only contain English letters, digits and special symbols.");
            return errors;
        }
        List<String> weaknesses = new ArrayList<>();
        if (password.length() < MIN_PASSWORD_LENGTH) {
            weaknesses.add("it must be at least 8 characters long");
        }
        if (!password.chars().anyMatch(Character::isLowerCase)) {
            weaknesses.add("it must contain a lowercase letter");
        }
        if (!password.chars().anyMatch(Character::isUpperCase)) {
            weaknesses.add("it must contain an uppercase letter");
        }
        if (!password.chars().anyMatch(Character::isDigit)) {
            weaknesses.add("it must contain a digit");
        }
        if (!PASSWORD_SPECIAL_PATTERN.matcher(password).find()) {
            weaknesses.add("it must contain a special symbol");
        }
        if (!weaknesses.isEmpty()) {
            errors.add("Password is weak: " + String.join(", ", weaknesses) + ".");
        }
        return errors;
    }

    public static List<String> validateNickname(String nickname) {
        List<String> errors = new ArrayList<>();
        if (nickname.length() < MIN_NICKNAME_LENGTH || nickname.length() > MAX_NICKNAME_LENGTH) {
            errors.add("Nickname must be between 3 and 30 characters long.");
        }
        return errors;
    }

    public static List<String> validateEmail(String email) {
        List<String> errors = new ArrayList<>();
        int firstAt = email.indexOf('@');
        if (firstAt < 0 || firstAt != email.lastIndexOf('@')) {
            errors.add("Email must contain exactly one '@' symbol.");
            return errors;
        }
        String localPart = email.substring(0, firstAt);
        String domainPart = email.substring(firstAt + 1);
        errors.addAll(validateEmailLocalPart(localPart));
        errors.addAll(validateEmailDomainPart(domainPart));
        return errors;
    }

    private static List<String> validateEmailLocalPart(String localPart) {
        List<String> errors = new ArrayList<>();
        if (localPart.isEmpty() || !EMAIL_PART_CHARSET_PATTERN.matcher(localPart).matches()) {
            errors.add("Email local part can only contain English letters, digits, '.', '-' and '_'.");
            return errors;
        }
        if (!startsAndEndsWithLetterOrDigit(localPart)) {
            errors.add("Email local part must start and end with a letter or a digit.");
        }
        if (localPart.contains("..")) {
            errors.add("Email local part must not contain consecutive dots.");
        }
        return errors;
    }

    private static List<String> validateEmailDomainPart(String domainPart) {
        List<String> errors = new ArrayList<>();
        if (domainPart.isEmpty() || !DOMAIN_CHARSET_PATTERN.matcher(domainPart).matches()) {
            errors.add("Email domain can only contain English letters, digits, '.' and '-'.");
            return errors;
        }
        if (!domainPart.contains(".")) {
            errors.add("Email domain must contain at least one dot.");
            return errors;
        }
        if (domainPart.contains("..")) {
            errors.add("Email domain must not contain consecutive dots.");
        }
        if (!startsAndEndsWithLetterOrDigit(domainPart)) {
            errors.add("Email domain must start and end with a letter or a digit.");
        }
        String suffix = domainPart.substring(domainPart.lastIndexOf('.') + 1);
        if (suffix.length() < MIN_DOMAIN_SUFFIX_LENGTH) {
            errors.add("Email domain suffix must be at least 2 characters long.");
        }
        return errors;
    }

    private static boolean startsAndEndsWithLetterOrDigit(String part) {
        return LETTER_OR_DIGIT_PATTERN.matcher(part.substring(0, 1)).matches()
                && LETTER_OR_DIGIT_PATTERN.matcher(part.substring(part.length() - 1)).matches();
    }

    public static List<String> validateGender(String gender) {
        List<String> errors = new ArrayList<>();
        if (Gender.fromString(gender) == null) {
            errors.add("Gender must be either 'male' or 'female'.");
        }
        return errors;
    }
}
