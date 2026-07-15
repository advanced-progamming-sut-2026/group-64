package ir.sharif.pvz.controller;

import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.game.Minigames;
import ir.sharif.pvz.view.ConsoleView;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The minigame menu, entered from the travel log. Stages unlock one after
 * another; winning pays coins and counts on the leaderboard.
 */
public class MinigameMenuController extends GameMenuController {

    private static final int WIN_COIN_REWARD = 100;
    private static final Pattern PLAY = Pattern.compile("^play\\s+-m\\s+(\\S+)\\s+-s\\s+(\\d)$");

    private String currentName;
    private int currentStage;

    public MinigameMenuController(AppContext context, ConsoleView view) {
        super(context, view, MenuType.MINIGAME, false);
    }

    @Override
    protected void onExit() {
        if (session != null) {
            view.error("Finish the running minigame first.");
            return;
        }
        context.setCurrentMenu(MenuType.TRAVEL_LOG);
        view.info("You are back in the travel-log menu.");
    }

    @Override
    protected void handleSelectionCommand(String input) {
        Matcher matcher;
        if (input.equals("show minigames")) {
            showMinigames();
        } else if ((matcher = PLAY.matcher(input)).matches()) {
            play(matcher.group(1), Integer.parseInt(matcher.group(2)));
        } else {
            view.error("Use 'show minigames' or 'play -m <name> -s <stage>'.");
        }
    }

    private void showMinigames() {
        User user = context.getCurrentUser();
        for (String name : Minigames.NAMES) {
            int progress = user.getMinigameProgress().getOrDefault(name, 0);
            view.info("- " + name + ": " + progress + "/" + Minigames.STAGES + " stages done"
                    + (progress >= Minigames.STAGES ? "" : "; next: stage " + (progress + 1)));
        }
    }

    private void play(String name, int stage) {
        User user = context.getCurrentUser();
        if (!Minigames.NAMES.contains(name)) {
            view.error("There is no minigame named '" + name + "'; pick from " + Minigames.NAMES + ".");
            return;
        }
        if (stage < 1 || stage > Minigames.STAGES) {
            view.error("Stages go from 1 to " + Minigames.STAGES + ".");
            return;
        }
        int progress = user.getMinigameProgress().getOrDefault(name, 0);
        if (stage > progress + 1) {
            view.error("Finish stage " + (progress + 1) + " of " + name + " first.");
            return;
        }
        List<String> plants = name.equals("zombotany")
                ? new ArrayList<>(user.getUnlockedPlants()).subList(0,
                        Math.min(8, user.getUnlockedPlants().size()))
                : List.of();
        currentName = name;
        currentStage = stage;
        session = Minigames.start(name, stage, user.getDifficulty(), plants, new Random());
        view.info(name + " - stage " + stage);
        if (name.equals("zombotany")) {
            view.info("Your plants: " + plants);
        }
        flushGameState();
    }

    @Override
    protected void applyOutcome(User user) {
        if (session.isWon()) {
            user.addCoins(WIN_COIN_REWARD);
            user.incrementMinigamesCompleted();
            int progress = user.getMinigameProgress().getOrDefault(currentName, 0);
            if (currentStage > progress) {
                user.getMinigameProgress().put(currentName, currentStage);
                if (currentStage < Minigames.STAGES) {
                    user.addNews("New minigame stage unlocked: " + currentName
                            + " stage " + (currentStage + 1));
                }
            }
            view.info("Minigame won! +" + WIN_COIN_REWARD + " coins.");
        } else {
            view.info("Minigame lost; try again from the same stage.");
        }
    }
}
