package ir.sharif.pvz.view;

import ir.sharif.pvz.model.NewsItem;
import ir.sharif.pvz.model.User;
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

    public void showUserInfo(User user) {
        out.println("Username: " + user.getUsername());
        out.println("Nickname: " + user.getNickname());
        out.println("Games played: " + user.getGamesPlayed());
        out.println("Coins: " + user.getCoins());
        out.println("Diamonds: " + user.getDiamonds());
        out.println("Levels passed: " + user.getLevelsPassed());
        out.println("Max mew points: " + user.getMaxMewPoints());
    }

    public void showNews(List<NewsItem> items, String emptyMessage) {
        if (items.isEmpty()) {
            out.println(emptyMessage);
            return;
        }
        for (NewsItem item : items) {
            out.println((item.isRead() ? "" : "[new] ") + item.getText());
        }
    }
}
