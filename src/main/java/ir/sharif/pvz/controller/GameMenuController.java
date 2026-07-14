package ir.sharif.pvz.controller;

import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.game.GameCatalog;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.PlantSpec;
import ir.sharif.pvz.view.ConsoleView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The game menu: plant selection before the level starts, then all in-game
 * commands (time, planting, suns, cheats) once a session is running.
 */
public class GameMenuController extends MenuController {

    private static final int MAX_SELECTED_PLANTS = 8;
    private static final int BOOST_DIAMOND_COST = 2;
    private static final int WIN_COIN_REWARD = 150;
    private static final String LOCATION = "\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)";

    private static final Pattern ADD_PLANT = Pattern.compile("^add\\s+plant\\s+-t\\s+(\\S+)$");
    private static final Pattern REMOVE_PLANT = Pattern.compile("^remove\\s+plant\\s+-t\\s+(\\S+)$");
    private static final Pattern BOOST_PLANT = Pattern.compile("^boost\\s+plant\\s+-t\\s+(\\S+)$");
    private static final Pattern ADVANCE_TIME = Pattern.compile("^advance\\s+time\\s+-t\\s+(\\d+)\\s+ticks?$");
    private static final Pattern PLANT = Pattern.compile("^plant\\s+plant\\s+-t\\s+(\\S+)\\s+-l\\s+" + LOCATION + "$");
    private static final Pattern PLUCK = Pattern.compile("^pluck\\s+plant\\s+-l\\s+" + LOCATION + "$");
    private static final Pattern FEED = Pattern.compile("^feed\\s+plant\\s+-l\\s+" + LOCATION + "$");
    private static final Pattern COLLECT_SUN = Pattern.compile("^collect\\s+sun\\s+-l\\s+" + LOCATION + "$");
    private static final Pattern TILE_STATUS = Pattern.compile("^show\\s+tile\\s+status\\s+-l\\s+" + LOCATION + "$");
    private static final Pattern CHEAT_SUNS = Pattern.compile("^cheat\\s+add\\s+-n\\s+(\\d+)\\s+suns$");
    private static final Pattern CHEAT_ZOMBIE =
            Pattern.compile("^cheat\\s+spawn-zombie\\s+-t\\s+(\\S+)\\s+-l\\s+" + LOCATION + "$");

    private final Set<String> selectedPlants = new LinkedHashSet<>();
    private final Set<String> boostedPlants = new HashSet<>();
    private GameSession session;

    public GameMenuController(AppContext context, ConsoleView view) {
        super(context, view);
    }

    @Override
    public MenuType type() {
        return MenuType.GAME;
    }

    @Override
    protected Set<MenuType> allowedTargets() {
        return Set.of(MenuType.COLLECTION);
    }

    @Override
    protected void onExit() {
        if (session != null) {
            view.error("Finish the running game first.");
            return;
        }
        selectedPlants.clear();
        boostedPlants.clear();
        context.setCurrentMenu(MenuType.MAIN);
        view.info("You are back in the main menu.");
    }

    @Override
    protected void handleCommand(String input) {
        if (session == null) {
            handleSelectionCommand(input);
        } else {
            handleInGameCommand(input);
        }
    }

    // ===== plant selection phase =====

    private void handleSelectionCommand(String input) {
        Matcher matcher;
        if (input.equals("show all plants")) {
            showPlantList(GameCatalog.get().allPlants().stream().map(PlantSpec::getName).toList());
        } else if (input.equals("show available plants")) {
            showPlantList(new ArrayList<>(context.getCurrentUser().getUnlockedPlants()));
        } else if ((matcher = ADD_PLANT.matcher(input)).matches()) {
            addPlant(matcher.group(1));
        } else if ((matcher = REMOVE_PLANT.matcher(input)).matches()) {
            removePlant(matcher.group(1));
        } else if ((matcher = BOOST_PLANT.matcher(input)).matches()) {
            boostPlant(matcher.group(1));
        } else if (input.equals("start game")) {
            startGame();
        } else {
            view.unknownCommand();
        }
    }

    private void showPlantList(List<String> names) {
        if (names.isEmpty()) {
            view.info("There is no plant to show.");
            return;
        }
        for (String name : names) {
            String marker = selectedPlants.contains(name) ? " [selected]" : "";
            view.info("- " + name + marker);
        }
    }

    private void addPlant(String type) {
        User user = context.getCurrentUser();
        if (GameCatalog.get().plant(type) == null) {
            view.error("There is no plant named '" + type + "'.");
        } else if (!user.getUnlockedPlants().contains(type)) {
            view.error("Plant '" + type + "' is locked; unlock it in the collection first.");
        } else if (selectedPlants.contains(type)) {
            view.error("Plant '" + type + "' is already selected.");
        } else if (selectedPlants.size() >= MAX_SELECTED_PLANTS) {
            view.error("You cannot select more than " + MAX_SELECTED_PLANTS + " plants.");
        } else {
            selectedPlants.add(type);
            view.info("Plant '" + type + "' added to your selection.");
        }
    }

    private void removePlant(String type) {
        if (!selectedPlants.remove(type)) {
            view.error("Plant '" + type + "' is not selected.");
            return;
        }
        boostedPlants.remove(type);
        view.info("Plant '" + type + "' removed from your selection.");
    }

    private void boostPlant(String type) {
        User user = context.getCurrentUser();
        if (!selectedPlants.contains(type)) {
            view.error("Plant '" + type + "' is not selected.");
        } else if (boostedPlants.contains(type)) {
            view.error("Plant '" + type + "' is already boosted.");
        } else if (user.getDiamonds() < BOOST_DIAMOND_COST) {
            view.error("Boosting costs " + BOOST_DIAMOND_COST + " diamonds.");
        } else {
            user.spendDiamonds(BOOST_DIAMOND_COST);
            boostedPlants.add(type);
            view.info("Plant '" + type + "' is boosted for the next game.");
        }
    }

    private void startGame() {
        session = new GameSession(context.getCurrentUser().getDifficulty(),
                new ArrayList<>(selectedPlants), new HashSet<>(boostedPlants), new Random());
        view.info("The game started! Zombies are coming; use 'advance time -t <count> ticks'.");
    }

    // ===== in-game phase =====

    private void handleInGameCommand(String input) {
        Matcher matcher;
        if ((matcher = ADVANCE_TIME.matcher(input)).matches()) {
            session.advance(Integer.parseInt(matcher.group(1)));
        } else if ((matcher = PLANT.matcher(input)).matches()) {
            view.info(session.plant(matcher.group(1), group(matcher, 2), group(matcher, 3)));
        } else if ((matcher = PLUCK.matcher(input)).matches()) {
            view.info(session.pluck(group(matcher, 1), group(matcher, 2)));
        } else if ((matcher = FEED.matcher(input)).matches()) {
            view.info(session.feedPlant(group(matcher, 1), group(matcher, 2)));
        } else if ((matcher = COLLECT_SUN.matcher(input)).matches()) {
            view.info(session.collectSun(group(matcher, 1), group(matcher, 2)));
        } else if (!handleInfoOrCheat(input)) {
            view.unknownCommand();
        }
        flushGameState();
    }

    private boolean handleInfoOrCheat(String input) {
        Matcher matcher;
        if (input.equals("show sun amount")) {
            view.info("Sun: " + session.getSunAmount());
        } else if (input.equals("show map")) {
            view.showMap(session);
        } else if (input.equals("show plants status")) {
            view.showPlantsStatus(session);
        } else if ((matcher = TILE_STATUS.matcher(input)).matches()) {
            view.showTileStatus(session, group(matcher, 1), group(matcher, 2));
        } else if (input.equals("zombies info")) {
            view.showZombiesInfo(session.getZombies());
        } else if ((matcher = CHEAT_SUNS.matcher(input)).matches()) {
            view.info(session.cheatAddSuns(Integer.parseInt(matcher.group(1))));
        } else if (input.equals("cheat remove-cooldown")) {
            view.info(session.cheatRemoveCooldown());
        } else if (input.equals("cheat add-plant-food")) {
            view.info(session.cheatAddPlantFood());
        } else if ((matcher = CHEAT_ZOMBIE.matcher(input)).matches()) {
            view.info(session.cheatSpawnZombie(matcher.group(1), group(matcher, 2), group(matcher, 3)));
        } else if (input.equals("release the nuke")) {
            view.info(session.releaseTheNuke());
        } else {
            return false;
        }
        return true;
    }

    private static int group(Matcher matcher, int index) {
        return Integer.parseInt(matcher.group(index));
    }

    /**
     * Prints engine events and, when the game just ended, applies the rewards to the user.
     */
    private void flushGameState() {
        for (String event : session.drainEvents()) {
            view.info(event);
        }
        if (!session.isOver()) {
            return;
        }
        User user = context.getCurrentUser();
        user.incrementGamesPlayed();
        user.addCoins(session.getEarnedCoins());
        user.addDiamonds(session.getEarnedDiamonds());
        user.addPots(session.getEarnedPots());
        user.getObservedZombies().addAll(session.getSeenZombieTypes());
        if (session.isWon()) {
            user.addCoins(WIN_COIN_REWARD);
            user.setLevelsPassed(user.getLevelsPassed() + 1);
            view.info("You won! You earned " + (session.getEarnedCoins() + WIN_COIN_REWARD) + " coins.");
        } else {
            view.info("You lost! Better luck next time.");
        }
        context.getUserRepository().save();
        session = null;
        boostedPlants.clear();
        view.info("You are back in the plant selection of the game menu.");
    }
}
