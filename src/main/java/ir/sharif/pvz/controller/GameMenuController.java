package ir.sharif.pvz.controller;

import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.game.GameCatalog;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.PlantSpec;
import ir.sharif.pvz.view.GameView;
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

    private static final Pattern SELECT_LEVEL =
            Pattern.compile("^select\\s+level\\s+-c\\s+(\\S+)\\s+-d\\s+(\\d+)$");
    private static final Pattern ADD_PLANT = Pattern.compile("^add\\s+plant\\s+-t\\s+(\\S+)$");
    private static final Pattern REMOVE_PLANT = Pattern.compile("^remove\\s+plant\\s+-t\\s+(\\S+)$");
    private static final Pattern BOOST_PLANT = Pattern.compile("^boost\\s+plant\\s+-t\\s+(\\S+)$");
    private static final Pattern ADVANCE_TIME = Pattern.compile("^advance\\s+time\\s+-t\\s+(\\d+)\\s+ticks?$");
    private static final Pattern PLANT = Pattern.compile("^plant\\s+plant\\s+-t\\s+(\\S+)\\s+-l\\s+" + LOCATION + "$");
    private static final Pattern PLUCK = Pattern.compile("^pluck\\s+plant\\s+-l\\s+" + LOCATION + "$");
    private static final Pattern SWAP = Pattern.compile(
            "^swap\\s+plant\\s+-l\\s+" + LOCATION + "\\s+-l\\s+" + LOCATION + "$");
    private static final Pattern FEED = Pattern.compile("^feed\\s+plant\\s+-l\\s+" + LOCATION + "$");
    private static final Pattern COLLECT_SUN = Pattern.compile("^collect\\s+sun\\s+-l\\s+" + LOCATION + "$");
    private static final Pattern TILE_STATUS = Pattern.compile("^show\\s+tile\\s+status\\s+-l\\s+" + LOCATION + "$");
    private static final Pattern CHEAT_SUNS = Pattern.compile("^cheat\\s+add\\s+-n\\s+(\\d+)\\s+suns$");
    private static final Pattern CHEAT_WALLET =
            Pattern.compile("^cheat\\s+add\\s+-n\\s+(\\d+)\\s+(coins|diamonds)$");
    private static final Pattern CHEAT_ZOMBIE =
            Pattern.compile("^cheat\\s+spawn-zombie\\s+-t\\s+(\\S+)\\s+-l\\s+" + LOCATION + "$");
    private static final Pattern BREAK_VASE = Pattern.compile("^break\\s+vase\\s+-l\\s+" + LOCATION + "$");
    private static final Pattern TAKE_PACKET = Pattern.compile("^take\\s+packet\\s+-l\\s+" + LOCATION + "$");
    private static final Pattern PLACE_ZOMBIE =
            Pattern.compile("^place\\s+zombie\\s+-t\\s+(\\S+)\\s+-l\\s+" + LOCATION + "$");

    protected final Set<String> selectedPlants = new LinkedHashSet<>();
    private final Set<String> boostedPlants = new HashSet<>();
    private final MenuType menuType;
    private final boolean scoreMode;
    /** Adventure index picked with "select level", or -1 to just continue. */
    private int chosenIndex = -1;
    protected GameSession session;
    private java.util.function.IntConsumer scoreReporter;

    public GameMenuController(AppContext context, GameView view) {
        this(context, view, MenuType.GAME, false);
    }

    /**
     * The score game reuses all game commands but plays the deterministic
     * daily level and reports mow points instead of advancing the adventure.
     */
    public GameMenuController(AppContext context, GameView view, MenuType menuType, boolean scoreMode) {
        super(context, view);
        this.menuType = menuType;
        this.scoreMode = scoreMode;
    }

    @Override
    public MenuType type() {
        return menuType;
    }

    /**
     * The level currently being played, or null while the player is still
     * choosing plants. A graphical view draws the lawn straight from it.
     */
    public GameSession getSession() {
        return session;
    }

    /**
     * The plants picked so far in the selection phase.
     */
    public Set<String> getSelectedPlants() {
        return java.util.Collections.unmodifiableSet(selectedPlants);
    }

    /**
     * The plants the player paid diamonds to boost before starting.
     */
    public Set<String> getBoostedPlants() {
        return java.util.Collections.unmodifiableSet(boostedPlants);
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
        chosenIndex = -1;
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

    protected void handleSelectionCommand(String input) {
        Matcher matcher;
        if (input.equals("show all plants")) {
            showPlantList(GameCatalog.get().allPlants().stream().map(PlantSpec::getName).toList());
        } else if (input.equals("show available plants")) {
            showPlantList(new ArrayList<>(context.getCurrentUser().getUnlockedPlants()));
        } else if (input.equals("show chapters")) {
            showChapters();
        } else if ((matcher = SELECT_LEVEL.matcher(input)).matches()) {
            selectLevel(matcher.group(1), Integer.parseInt(matcher.group(2)));
        } else if ((matcher = ADD_PLANT.matcher(input)).matches()) {
            addPlant(matcher.group(1));
        } else if ((matcher = REMOVE_PLANT.matcher(input)).matches()) {
            removePlant(matcher.group(1));
        } else if ((matcher = BOOST_PLANT.matcher(input)).matches()) {
            boostPlant(matcher.group(1));
        } else if (input.equals("start game")) {
            startGame();
        } else if (input.equals("resume game")) {
            resumeSavedGame();
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
     * The adventure map: every chapter with its days, marking what is cleared,
     * what is playable now and what is still locked.
     */
    private void showChapters() {
        if (scoreMode) {
            view.error("The score game always plays today's level.");
            return;
        }
        int passed = context.getCurrentUser().getLevelsPassed();
        for (ir.sharif.pvz.model.game.Chapter chapter : ir.sharif.pvz.model.game.Chapter.values()) {
            StringBuilder line = new StringBuilder(chapter.id() + ":");
            for (int day = 1; day <= ir.sharif.pvz.model.game.Levels.daysPerChapter(); day++) {
                int index = ir.sharif.pvz.model.game.Levels.indexOf(chapter, day);
                String mark = index < passed ? "cleared" : index == passed ? "playable" : "locked";
                line.append(" day ").append(day).append(" [").append(mark).append(']');
            }
            view.info(line.toString());
        }
        view.info("Play one with: select level -c <chapter> -d <day>");
    }

    /**
     * Picks a cleared or newly unlocked level instead of simply continuing from
     * where the player left off; locked levels stay out of reach.
     */
    private void selectLevel(String chapterId, int day) {
        if (scoreMode) {
            view.error("The score game always plays today's level.");
            return;
        }
        ir.sharif.pvz.model.game.Chapter chapter = ir.sharif.pvz.model.game.Chapter.fromId(chapterId);
        if (chapter == null) {
            view.error("There is no chapter named '" + chapterId + "'; try 'show chapters'.");
            return;
        }
        int index = ir.sharif.pvz.model.game.Levels.indexOf(chapter, day);
        if (index < 0) {
            view.error(chapter.displayName() + " has no day " + day + ".");
            return;
        }
        if (index > context.getCurrentUser().getLevelsPassed()) {
            view.error("That level is locked; finish the levels before it first.");
            return;
        }
        chosenIndex = index;
        view.info("Selected " + ir.sharif.pvz.model.game.Levels.adventure().get(index).title() + ".");
    }

    /**
     * The level "start game" will run: the one the player selected, or the
     * next unfinished one when nothing was selected.
     */
    private ir.sharif.pvz.model.game.LevelSpec upcomingLevel() {
        if (scoreMode) {
            return ir.sharif.pvz.model.game.Levels.scoreGame();
        }
        return chosenIndex < 0
                ? ir.sharif.pvz.model.game.Levels.byProgress(context.getCurrentUser().getLevelsPassed())
                : ir.sharif.pvz.model.game.Levels.adventure().get(chosenIndex);
    }

    /**
     * The level the player is about to start; special levels restrict the
     * plant selection before the game even begins.
     */
    private ir.sharif.pvz.model.game.SpecialRules upcomingSpecial() {
        if (scoreMode) {
            return null;
        }
        return upcomingLevel().getSpecial();
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

    /**
     * Picks up the level this player walked away from, exactly where it was.
     */
    private void resumeSavedGame() {
        User user = context.getCurrentUser();
        ir.sharif.pvz.model.game.SavedGame saved = context.getSavedGames().of(user.getUsername());
        if (saved == null) {
            view.error("You have no saved game to come back to.");
            return;
        }
        session = ir.sharif.pvz.model.game.SaveState.restore(saved, new Random());
        if (session == null) {
            view.error("That saved game is from a level this build no longer has.");
            context.getSavedGames().clear(user.getUsername());
            return;
        }
        selectedPlants.clear();
        selectedPlants.addAll(session.getSelectedPlants());
        chosenIndex = -1;
        context.getSavedGames().clear(user.getUsername());
        view.info("Picking up " + session.getLevel().title() + " where you left it.");
        flushGameState();
    }

    /**
     * Puts the running level away so it can be come back to, and leaves the
     * game menu without counting it as a loss.
     */
    protected void saveAndLeave() {
        if (session == null) {
            view.error("There is no game running to save.");
            return;
        }
        User user = context.getCurrentUser();
        context.getSavedGames().put(user.getUsername(),
                ir.sharif.pvz.model.game.SaveState.capture(session));
        context.getUserRepository().save();
        session = null;
        boostedPlants.clear();
        chosenIndex = -1;
        view.info("Saved. Come back to it with 'resume game'.");
    }

    private void startGame() {
        User user = context.getCurrentUser();
        ir.sharif.pvz.model.game.LevelSpec level = upcomingLevel();
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
        applyPlantLevels(user);
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

    /**
     * Hands the session the collection upgrades, so a plant the player paid to
     * upgrade is planted with more damage and health than a fresh one.
     */
    protected void applyPlantLevels(User user) {
        java.util.Map<String, Integer> levels = new java.util.HashMap<>();
        for (PlantSpec spec : GameCatalog.get().allPlants()) {
            int level = user.getPlantLevel(spec.getName());
            if (level > 1) {
                levels.put(spec.getName(), level);
                if (selectedPlants.contains(spec.getName())) {
                    view.info("Your " + spec.getName() + " fights at level " + level + ".");
                }
            }
        }
        session.setPlantLevels(levels);
    }

    // ===== in-game phase =====

    private void handleInGameCommand(String input) {
        Matcher matcher;
        if ((matcher = ADVANCE_TIME.matcher(input)).matches()) {
            session.advance(Integer.parseInt(matcher.group(1)));
        } else if ((matcher = PLANT.matcher(input)).matches()) {
            view.info(session.plant(matcher.group(1), group(matcher, 2), group(matcher, 3)));
        } else if ((matcher = SWAP.matcher(input)).matches()) {
            view.info(session.swapPlants(group(matcher, 1), group(matcher, 2),
                    group(matcher, 3), group(matcher, 4)));
        } else if ((matcher = PLUCK.matcher(input)).matches()) {
            view.info(session.pluck(group(matcher, 1), group(matcher, 2)));
        } else if ((matcher = FEED.matcher(input)).matches()) {
            view.info(session.feedPlant(group(matcher, 1), group(matcher, 2)));
        } else if ((matcher = COLLECT_SUN.matcher(input)).matches()) {
            view.info(session.collectSun(group(matcher, 1), group(matcher, 2)));
        } else if (!handleInfoOrCheat(input)) {
            view.unknownCommand();
        }
        // putting the level away ends the session, so there is nothing to flush
        if (session != null) {
            flushGameState();
        }
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
        } else if (input.equals("start zombie waves")) {
            view.info(session.startZombieWaves());
        } else if (input.equals("show conveyor belt")) {
            List<String> belt = session.conveyorBelt();
            view.info(belt.isEmpty() ? "The conveyor belt is empty." : "Belt: " + String.join(", ", belt));
        } else if (input.equals("save game")) {
            saveAndLeave();
        } else if (input.equals("forfeit level")) {
            session.forfeit();
        } else {
            return handleCheat(input);
        }
        return true;
    }

    /**
     * The debug commands the document lists, kept apart from the ordinary
     * in-game ones.
     */
    private boolean handleCheat(String input) {
        Matcher matcher;
        if ((matcher = CHEAT_WALLET.matcher(input)).matches()) {
            view.info(cheatWallet(Integer.parseInt(matcher.group(1)), matcher.group(2)));
        } else if ((matcher = CHEAT_SUNS.matcher(input)).matches()) {
            view.info(session.cheats().addSuns(Integer.parseInt(matcher.group(1))));
        } else if (input.equals("cheat remove-cooldown")) {
            view.info(session.cheats().removeCooldown());
        } else if (input.equals("cheat add-plant-food")) {
            view.info(session.cheats().addPlantFood());
        } else if ((matcher = CHEAT_ZOMBIE.matcher(input)).matches()) {
            view.info(session.cheats().spawnZombie(matcher.group(1), group(matcher, 2), group(matcher, 3)));
        } else if (input.equals("release the nuke")) {
            view.info(session.cheats().releaseTheNuke());
        } else {
            return handleMinigameCommand(input);
        }
        return true;
    }

    private boolean handleMinigameCommand(String input) {
        Matcher matcher;
        if ((matcher = BREAK_VASE.matcher(input)).matches()) {
            view.info(session.breakVase(group(matcher, 1), group(matcher, 2)));
        } else if ((matcher = TAKE_PACKET.matcher(input)).matches()) {
            view.info(session.takePacket(group(matcher, 1), group(matcher, 2)));
        } else if ((matcher = PLACE_ZOMBIE.matcher(input)).matches()) {
            view.info(session.placeZombie(matcher.group(1), group(matcher, 2), group(matcher, 3)));
        } else if (input.equals("show vases")) {
            session.vasesInfo().forEach(view::info);
        } else {
            return false;
        }
        return true;
    }

    /**
     * The debug-mode shortcut for topping up the player's wallet.
     */
    private String cheatWallet(int amount, String currency) {
        User user = context.getCurrentUser();
        if ("coins".equals(currency)) {
            user.addCoins(amount);
            return "Added " + amount + " coins; you now have " + user.getCoins() + ".";
        }
        user.addDiamonds(amount);
        return "Added " + amount + " diamonds; you now have " + user.getDiamonds() + ".";
    }

    private static int group(Matcher matcher, int index) {
        return Integer.parseInt(matcher.group(index));
    }

    /**
     * Prints engine events and, when the game just ended, applies the rewards to the user.
     */
    protected void flushGameState() {
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
        applyOutcome(user);
        context.getUserRepository().save();
        session = null;
        boostedPlants.clear();
        chosenIndex = -1;
        view.info("You are back in the " + menuType.id() + " menu.");
    }

    /**
     * What the finished game means for the player; minigames override this.
     */
    protected void applyOutcome(User user) {
        if (scoreMode) {
            finishScoreGame(user);
        } else if (session.isWon()) {
            user.addCoins(WIN_COIN_REWARD);
            // replaying a cleared level pays coins but must not push the
            // player past the level they have actually reached
            if (chosenIndex < 0 || chosenIndex == user.getLevelsPassed()) {
                user.setLevelsPassed(user.getLevelsPassed() + 1);
            }
            view.info("You won! You earned " + (session.getEarnedCoins() + WIN_COIN_REWARD) + " coins.");
        } else {
            view.info("You lost! Better luck next time.");
        }
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
        reportScoreToServer(tracker.getPoints());
    }

    /**
     * Sends the round's score up so it can go on the leaderboard's My Point
     * column. Offline players simply skip it, which is why that column stays
     * blank until somebody has actually played online.
     */
    private void reportScoreToServer(int points) {
        if (scoreReporter == null) {
            return;
        }
        scoreReporter.accept(points);
    }

    /**
     * Installed by the networked build to post score-game results.
     */
    public void setScoreReporter(java.util.function.IntConsumer reporter) {
        this.scoreReporter = reporter;
    }
}
