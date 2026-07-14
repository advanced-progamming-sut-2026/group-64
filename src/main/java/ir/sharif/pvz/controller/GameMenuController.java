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
    private final MenuType menuType;
    private final boolean scoreMode;
    private GameSession session;

    public GameMenuController(AppContext context, ConsoleView view) {
        this(context, view, MenuType.GAME, false);
    }

    /**
     * The score game reuses all game commands but plays the deterministic
     * daily level and reports mow points instead of advancing the adventure.
     */
    public GameMenuController(AppContext context, ConsoleView view, MenuType menuType, boolean scoreMode) {
        super(context, view);
        this.menuType = menuType;
        this.scoreMode = scoreMode;
    }

    @Override
    public MenuType type() {
        return menuType;
    }

    @Override
    protected Set<MenuType> allowedTargets() {
        return scoreMode ? Set.of() : Set.of(MenuType.COLLECTION);
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

    /**
     * The level the player is about to start; special levels restrict the
     * plant selection before the game even begins.
     */
    private ir.sharif.pvz.model.game.SpecialRules upcomingSpecial() {
        if (scoreMode) {
            return null;
        }
        return ir.sharif.pvz.model.game.Levels
                .byProgress(context.getCurrentUser().getLevelsPassed()).getSpecial();
    }

    private void addPlant(String type) {
        User user = context.getCurrentUser();
        ir.sharif.pvz.model.game.SpecialRules special = upcomingSpecial();
        if (special != null && special.getType() == ir.sharif.pvz.model.game.SpecialRules.Type.CONVEYOR_BELT) {
            view.error("This level uses a conveyor belt; there is no plant selection.");
        } else if (special != null && special.getLockedPlants().contains(type)) {
            view.error("Plant '" + type + "' is locked on this level.");
        } else if (GameCatalog.get().plant(type) == null) {
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
        User user = context.getCurrentUser();
        ir.sharif.pvz.model.game.LevelSpec level = scoreMode
                ? ir.sharif.pvz.model.game.Levels.scoreGame()
                : ir.sharif.pvz.model.game.Levels.byProgress(user.getLevelsPassed());
        if (level.getSpecial() != null) {
            for (String forced : level.getSpecial().getForcedPlants()) {
                if (selectedPlants.add(forced)) {
                    view.info("This level forces " + forced + " into your selection.");
                }
            }
        }
        Set<String> boosts = new HashSet<>(boostedPlants);
        for (String type : selectedPlants) {
            if (user.getStoredBoosts().remove(type)) {
                boosts.add(type);
                view.info("The " + type + " you grew in the greenhouse starts boosted!");
            }
        }
        Random random = scoreMode ? new Random(java.time.LocalDate.now().toEpochDay()) : new Random();
        session = new GameSession(level, user.getDifficulty(),
                new ArrayList<>(selectedPlants), boosts, random);
        if (scoreMode) {
            session.attachScoreTracker(new ir.sharif.pvz.model.game.ScoreTracker());
        }
        if (user.getPendingPlantFood() > 0) {
            session.grantPlantFood(user.getPendingPlantFood());
            view.info("You start with " + user.getPendingPlantFood() + " plant food(s) from the shop.");
            user.setPendingPlantFood(0);
        }
        view.info(level.title() + (level.isNight() ? " (night)" : ""));
        view.info("The game started! Zombies are coming; use 'advance time -t <count> ticks'.");
        flushGameState();
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
        } else if (input.equals("start zombie waves")) {
            view.info(session.startZombieWaves());
        } else if (input.equals("show conveyor belt")) {
            List<String> belt = session.conveyorBelt();
            view.info(belt.isEmpty() ? "The conveyor belt is empty." : "Belt: " + String.join(", ", belt));
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
        user.setLastPlayedDate(java.time.LocalDate.now().toString());
        user.addCoins(session.getEarnedCoins());
        user.addDiamonds(session.getEarnedDiamonds());
        user.addPots(session.getEarnedPots());
        for (int i = 0; i < session.getEarnedPots(); i++) {
            if (user.unlockNextPot()) {
                view.info("A dropped pot unlocked a new greenhouse slot!");
            }
        }
        user.getObservedZombies().addAll(session.getSeenZombieTypes());
        if (scoreMode) {
            finishScoreGame(user);
        } else if (session.isWon()) {
            user.addCoins(WIN_COIN_REWARD);
            user.setLevelsPassed(user.getLevelsPassed() + 1);
            view.info("You won! You earned " + (session.getEarnedCoins() + WIN_COIN_REWARD) + " coins.");
        } else {
            view.info("You lost! Better luck next time.");
        }
        context.getUserRepository().save();
        session = null;
        boostedPlants.clear();
        view.info("You are back in the plant selection of the " + menuType.id() + " menu.");
    }

    private void finishScoreGame(User user) {
        ir.sharif.pvz.model.game.ScoreTracker tracker = session.getScoreTracker();
        if (session.isWon()) {
            tracker.addMowerBonus(session.unusedMowers());
            view.info("You survived the score game!");
        } else {
            view.info("The zombies got you; your points still count.");
        }
        tracker.breakdown().forEach(view::info);
        if (tracker.getPoints() > user.getMaxMewPoints()) {
            view.info("New personal best!");
        }
        user.updateMaxMewPoints(tracker.getPoints());
    }
}
