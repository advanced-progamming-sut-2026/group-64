package ir.sharif.pvz.view;

import java.io.PrintStream;
import java.util.List;

/**
 * The single place that writes to the console; controllers never print directly.
 */
public class ConsoleView {

    private final PrintStream out;

    public ConsoleView() {
        this(System.out);
    }

    public ConsoleView(PrintStream out) {
        this.out = out;
    }

    public void info(String message) {
        out.println(message);
    }

    public void error(String message) {
        out.println("Error: " + message);
    }

    public void errors(List<String> messages) {
        for (String message : messages) {
            error(message);
        }
    }

    public void showCurrentMenu(String menuName) {
        out.println("You are in the " + menuName + " menu.");
    }

    public void showSecurityQuestions(List<String> questions) {
        out.println("Pick a security question with:");
        out.println("pick question -q <question_number> -a <answer> -c <answer_confirm>");
        for (int i = 0; i < questions.size(); i++) {
            out.println((i + 1) + ". " + questions.get(i));
        }
    }

    public void unknownCommand() {
        out.println("Error: invalid command.");
    }
}
