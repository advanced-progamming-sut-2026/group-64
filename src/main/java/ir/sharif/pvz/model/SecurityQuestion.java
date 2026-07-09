package ir.sharif.pvz.model;

import java.util.List;

/**
 * The fixed list of security questions offered during registration.
 */
public final class SecurityQuestion {

    private static final List<String> QUESTIONS = List.of(
            "What is your favorite plant?",
            "What was the name of your first pet?",
            "What city were you born in?",
            "What is your favorite food?",
            "Who is your favorite teacher?");

    private SecurityQuestion() {
    }

    public static List<String> all() {
        return QUESTIONS;
    }

    public static boolean isValidNumber(int number) {
        return number >= 1 && number <= QUESTIONS.size();
    }

    /**
     * Returns the question text for a 1-based question number.
     */
    public static String byNumber(int number) {
        return QUESTIONS.get(number - 1);
    }
}
