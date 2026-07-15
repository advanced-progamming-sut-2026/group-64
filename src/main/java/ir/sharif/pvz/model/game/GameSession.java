package ir.sharif.pvz.model.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The tick-based engine of one level: time, suns, waves, combat, mowers, win/lose.
 * Coordinates in commands are (x, y) = (column 1..9, row 1..5); zombies move
 * continuously along x while plants sit on integer tiles.
 */
public class GameSession {

    public static final int ROWS = 5;
    public static final int COLS = 9;
    public static final int TICKS_PER_SECOND = 10;
    public static final int MAX_PLANT_FOOD = 3;

    private static final int INITIAL_SUN = 50;

    private final Random random;
    private final double difficultyUp;
    private final double difficultyDown;
    private final List<String> selectedPlants;
    private final Set<String> boostedPlants;
    private final LevelSpec level;
    private final ZombieAbilities abilities;
    private final PlantCombat combat;
    private final SpecialLevelEngine special;
    private final Set<Plant> protectedPlants = new java.util.HashSet<>();

    private final Plant[][] grid = new Plant[ROWS][COLS];
    private final Board board;
    private final SunSystem sunSystem;
    private final WaveSystem waves;
    private final Map<Plant, String> disabledPlants = new HashMap<>();
    private final boolean[] mowers = new boolean[ROWS];
    private final List<Zombie> zombies = new ArrayList<>();
    private final Map<String, Double> plantCooldowns = new HashMap<>();
    private final Map<Zombie, Double> eatProgress = new HashMap<>();
    private final List<String> events = new ArrayList<>();
    private final Set<String> seenZombieTypes = new java.util.LinkedHashSet<>();

    private long tickCount;
    private int sunAmount = INITIAL_SUN;
    private int plantFood;
    private boolean cooldownsDisabled;
    private boolean cooldownsSuspended;
    private ScoreTracker scoreTracker;
    private MinigameLogic minigame;
    private int earnedCoins;
    private int earnedDiamonds;
    private int earnedPots;
    private boolean won;
    private boolean lost;

    public GameSession(int difficulty, List<String> selectedPlants, Set<String> boostedPlants, Random random) {
        this(defaultLevel(), difficulty, selectedPlants, boostedPlants, random);
    }

    public GameSession(LevelSpec level, int difficulty, List<String> selectedPlants,
                       Set<String> boostedPlants, Random random) {
        this.level = level;
        this.random = random;
        this.difficultyUp = difficulty / 3.0;
        this.difficultyDown = 3.0 / difficulty;
        this.selectedPlants = List.copyOf(selectedPlants);
        this.boostedPlants = boostedPlants;
        this.abilities = new ZombieAbilities(this, random);
        this.combat = new PlantCombat(this);
        java.util.Arrays.fill(mowers, true);
        this.board = new Board(level, difficultyUp, random, events);
        this.sunSystem = new SunSystem(level, difficultyUp, random, events);
        this.waves = new WaveSystem(this, level, difficultyDown, random);
        this.special = new SpecialLevelEngine(this, level.getSpecial(), random);
        this.special.init();
    }

    private static LevelSpec defaultLevel() {
        return new LevelSpec(Chapter.ANCIENT_EGYPT, 1, 4, 1000,
                List.of("normal", "conehead", "buckethead", "knight", "blockhead", "imp",
                        "gargantuar", "all-star"),
                Map.of(), 0, false, false, false);
    }

    void raiseGrave(int row, int col, String contents) {
        board.raiseGrave(row, col, contents, grid[row][col] == null);
    }

    private double seconds() {
        return tickCount / (double) TICKS_PER_SECOND;
    }

    public void advance(int ticks) {
        for (int i = 0; i < ticks && !isOver(); i++) {
            tickCount++;
            passTimers();
            produceSuns();
            sunSystem.tick(1.0 / TICKS_PER_SECOND, seconds());
            waves.tick(seconds());
            special.tick(seconds());
            if (minigame != null) {
                minigame.tick(this, seconds());
            }
            plantsAct();
            zombiesAct();
            checkVictory();
        }
    }

    private void passTimers() {
        double dt = 1.0 / TICKS_PER_SECOND;
        plantCooldowns.replaceAll((k, v) -> Math.max(0, v - dt));
        for (Plant[] row : grid) {
            for (Plant plant : row) {
                if (plant != null) {
                    plant.passSeconds(dt);
                }
            }
        }
        for (Zombie zombie : zombies) {
            zombie.passSeconds(dt);
        }
    }

    private void produceSuns() {
        sunSystem.producePlantSuns(this);
    }

    /**
     * Special levels (e.g. Plant What You Get) start their waves manually.
     */
    public void setWavesEnabled(boolean wavesEnabled) {
        waves.setEnabled(wavesEnabled);
    }

    /**
     * Score-game sessions count mow points through this tracker.
     */
    public void attachScoreTracker(ScoreTracker tracker) {
        this.scoreTracker = tracker;
    }

    public ScoreTracker getScoreTracker() {
        return scoreTracker;
    }

    public int unusedMowers() {
        int count = 0;
        for (boolean mower : mowers) {
            if (mower) {
                count++;
            }
        }
        return count;
    }

    Zombie spawnZombie(ZombieSpec spec, int row, double x) {
        Map<String, Integer> armor = new java.util.LinkedHashMap<>();
        spec.getArmor().forEach((name, hp) -> armor.put(name, (int) Math.round(hp * difficultyUp)));
        int hp = (int) Math.round(spec.getHp() * difficultyUp);
        boolean glowing = random.nextInt(100) < 5;
        Zombie zombie = new Zombie(spec, row, x, hp, armor, glowing);
        zombies.add(zombie);
        seenZombieTypes.add(spec.getName());
        if (scoreTracker != null) {
            scoreTracker.onSpawn(zombie, tickCount);
        }
        return zombie;
    }

    private void plantsAct() {
        combat.tick();
    }

    void plantHit(Plant plant, int damage) {
        if (plant.damage(damage)) {
            removePlant(plant);
            events.add("Plant " + plant.getSpec().getName() + " at (" + (plant.getCol() + 1)
                    + ", " + (plant.getRow() + 1) + ") is destroyed.");
            special.onPlantDestroyed(plant);
            protectedPlants.remove(plant);
        }
    }

    private void explode(Plant plant, int radius) {
        for (Zombie zombie : new ArrayList<>(zombies)) {
            boolean inRows = Math.abs(zombie.getRow() - plant.getRow()) <= radius;
            boolean inCols = Math.abs(zombie.getX() - (plant.getCol() + 1)) <= radius + 0.5;
            if (inRows && inCols) {
                hit(zombie, plant.getSpec().getDamage());
            }
        }
        removePlant(plant);
    }

    private void damageRow(int row, double fromX, int damage) {
        for (Zombie zombie : new ArrayList<>(zombies)) {
            if (zombie.getRow() == row && zombie.getX() >= fromX) {
                hit(zombie, damage);
            }
        }
    }

    private Zombie frontmostInRow(int row, double fromX) {
        Zombie front = null;
        for (Zombie zombie : zombies) {
            if (zombie.getRow() == row && zombie.getX() >= fromX
                    && (front == null || zombie.getX() < front.getX())) {
                front = zombie;
            }
        }
        return front;
    }

    private void hit(Zombie zombie, int damage) {
        if (zombie.damage(damage)) {
            killZombie(zombie);
        }
    }

    private void killZombie(Zombie zombie) {
        zombies.remove(zombie);
        eatProgress.remove(zombie);
        abilities.onDeath(zombie);
        special.onZombieKilled();
        if (scoreTracker != null) {
            scoreTracker.onKill(zombie, tickCount);
        }
        events.add("Zombie of type " + zombie.getSpec().getName() + " is dead at ("
                + trim(zombie.getX()) + ", " + (zombie.getRow() + 1) + ")");
        if (zombie.isGlowing() && plantFood < MAX_PLANT_FOOD) {
            plantFood++;
            events.add("The glowing zombie dropeed a plant food; you have " + plantFood + " plant foods now.");
        }
        rollDeathDrop();
    }

    private void rollDeathDrop() {
        if (random.nextInt(100) >= 10) {
            return;
        }
        int kind = random.nextInt(3);
        if (kind == 0) {
            earnedDiamonds++;
            events.add("A zombie dropeed a diamond; you have " + earnedDiamonds + " diamonds now.");
        } else if (kind == 1) {
            earnedCoins += 50;
            events.add("A zombie dropeed a coin; you have " + earnedCoins + " coins now.");
        } else {
            earnedPots++;
            events.add("A zombie dropeed a pot; you have " + earnedPots + " pots now.");
        }
    }

    private void zombiesAct() {
        double dt = 1.0 / TICKS_PER_SECOND;
        abilities.tick(dt);
        for (Zombie zombie : new ArrayList<>(zombies)) {
            if (!zombies.contains(zombie)) {
                continue;
            }
            Plant blocking = plantInFrontOf(zombie);
            if (blocking != null) {
                eat(zombie, blocking, dt);
            } else {
                zombie.walk(walkSpeed(zombie) * dt);
                board.slideIfOnIce(zombie);
                special.onZombieMoved(zombie);
                if (!lost && zombie.getX() < 1) {
                    reachHouse(zombie);
                }
            }
        }
    }

    private double walkSpeed(Zombie zombie) {
        double speed = zombie.getSpec().getTilesPerSecond() * difficultyUp * zombie.speedMultiplier();
        if (zombie.getSpec().getName().equals("newspaper") && zombie.getArmor().isEmpty()) {
            speed *= 3;
        }
        return speed;
    }

    private Plant plantInFrontOf(Zombie zombie) {
        int col = (int) Math.round(zombie.getX()) - 1;
        if (col < 0 || col >= COLS) {
            return null;
        }
        Plant plant = grid[zombie.getRow()][col];
        if (plant != null && zombie.getSpec().getName().equals("dodo-rider")
                && plant.getSpec().getCategory() != PlantCategory.WALL) {
            return null;
        }
        return plant;
    }

    private void eat(Zombie zombie, Plant plant, double dt) {
        if (zombie.isFrozen()) {
            return;
        }
        double progress = eatProgress.merge(zombie, dt, Double::sum);
        if (progress >= 1) {
            eatProgress.put(zombie, progress - 1);
            plantHit(plant, (int) Math.round(zombie.getSpec().getDamagePerSecond() * difficultyUp));
        }
    }

    private void reachHouse(Zombie zombie) {
        if (minigame != null && minigame.onHouseReached(this, zombie)) {
            return;
        }
        int row = zombie.getRow();
        if (mowers[row]) {
            mowers[row] = false;
            List<Zombie> killed = zombies.stream().filter(z -> z.getRow() == row).toList();
            events.add("The lawn mower in the row " + (row + 1) + " is triggered and killed these zombies:");
            for (Zombie victim : killed) {
                events.add("- " + victim.getSpec().getName());
                zombies.remove(victim);
                eatProgress.remove(victim);
            }
        } else {
            lost = true;
            events.add("The zombie ate your brain; LOSER!!!");
        }
    }

    private void checkVictory() {
        if (!lost && waves.allWavesSpawned() && zombies.isEmpty()) {
            won = true;
            events.add("Dear humanz, zis is not done yet; we will come back to eat your brainz, humanz.");
        }
    }

    static String trim(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((int) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public boolean isWon() {
        return won;
    }

    public boolean isLost() {
        return lost;
    }

    public boolean isOver() {
        return won || lost;
    }

    public List<String> drainEvents() {
        List<String> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    public int getSunAmount() {
        return sunAmount;
    }

    public int getPlantFood() {
        return plantFood;
    }

    public int getCurrentWave() {
        return waves.getCurrentWave();
    }

    public int getEarnedCoins() {
        return earnedCoins;
    }

    public int getEarnedDiamonds() {
        return earnedDiamonds;
    }

    public int getEarnedPots() {
        return earnedPots;
    }

    public List<Zombie> getZombies() {
        return new ArrayList<>(zombies);
    }

    public Plant plantAtTile(int x, int y) {
        return grid[y - 1][x - 1];
    }

    public boolean isMowerAvailable(int row) {
        return mowers[row];
    }

    public List<String> getSelectedPlants() {
        return selectedPlants;
    }

    public Set<String> getSeenZombieTypes() {
        return new java.util.LinkedHashSet<>(seenZombieTypes);
    }

    /**
     * Remaining recharge seconds for a selected plant type (0 when ready).
     */
    public double cooldownRemaining(String type) {
        return cooldownsDisabled ? 0 : plantCooldowns.getOrDefault(type, 0.0);
    }

    public List<Plant> plantedPlants() {
        List<Plant> planted = new ArrayList<>();
        for (Plant[] row : grid) {
            for (Plant plant : row) {
                if (plant != null) {
                    planted.add(plant);
                }
            }
        }
        return planted;
    }

    public List<Sun> groundSuns() {
        return sunSystem.ground();
    }

    // ===== player commands =====

    /**
     * Plants a selected type on tile (x=column, y=row), enforcing sun, cooldown and occupancy rules.
     */
    public String plant(String type, int x, int y) {
        boolean freeHand = minigame != null && minigame.freePlantMode();
        if (!special.conveyorMode() && !freeHand && !selectedPlants.contains(type)) {
            return "Error: plant '" + type + "' is not among your selected plants.";
        }
        if (!validTile(x, y)) {
            return "Error: (" + x + ", " + y + ") is not a valid tile.";
        }
        PlantSpec spec = GameCatalog.get().plant(type);
        if (spec == null) {
            return "Error: there is no plant named '" + type + "'.";
        }
        if (grid[y - 1][x - 1] != null) {
            return "Error: tile (" + x + ", " + y + ") is already occupied.";
        }
        String terrainError = board.rejection(spec, y - 1, x - 1);
        if (terrainError != null) {
            return terrainError;
        }
        if (minigame != null) {
            String rejection = minigame.plantingRejection(x, y);
            if (rejection != null) {
                return rejection;
            }
        }
        String paymentError = payForPlant(type, spec, freeHand);
        if (paymentError != null) {
            return paymentError;
        }
        if (spec.getName().equals("lily-pad")) {
            board.setTerrain(y - 1, x - 1, TileTerrain.LILY);
            return "Planted lily-pad at (" + x + ", " + y + "); the tile is now plantable.";
        }
        Plant plant = new Plant(spec, y - 1, x - 1, boostedPlants.remove(type));
        grid[y - 1][x - 1] = plant;
        if (plant.isBoosted()) {
            plant.consumeBoost();
            applyPlantFoodEffect(plant);
        }
        if (minigame != null) {
            minigame.onPlanted(this, plant);
        }
        return "Planted " + type + " at (" + x + ", " + y + ").";
    }

    /**
     * Charges for a plant: the conveyor belt and the vasebreaker hand are
     * free; anything else costs sun and starts the recharge timer.
     */
    private String payForPlant(String type, PlantSpec spec, boolean freeHand) {
        if (special.conveyorMode()) {
            return special.takeFromBelt(type);
        }
        if (freeHand) {
            return minigame.takeFromHand(type);
        }
        boolean recharging = !cooldownsDisabled && !cooldownsSuspended
                && plantCooldowns.getOrDefault(type, 0.0) > 0;
        if (recharging) {
            return "Error: " + type + " is recharging; wait " + trim(plantCooldowns.get(type)) + "s.";
        }
        if (sunAmount < spec.getSunCost()) {
            return "Error: not enough sun; " + type + " costs " + spec.getSunCost() + ".";
        }
        sunAmount -= spec.getSunCost();
        plantCooldowns.put(type, spec.getRechargeSeconds());
        return null;
    }

    public String pluck(int x, int y) {
        if (!validTile(x, y)) {
            return "Error: (" + x + ", " + y + ") is not a valid tile.";
        }
        Plant plant = grid[y - 1][x - 1];
        if (plant == null) {
            return "Error: there is no plant at (" + x + ", " + y + ").";
        }
        if (protectedPlants.contains(plant)) {
            return "Error: the " + plant.getSpec().getName() + " at (" + x + ", " + y
                    + ") must be protected, not plucked!";
        }
        removePlant(plant);
        return "Plucked " + plant.getSpec().getName() + " from (" + x + ", " + y + ").";
    }

    public String feedPlant(int x, int y) {
        if (!validTile(x, y)) {
            return "Error: (" + x + ", " + y + ") is not a valid tile.";
        }
        Plant plant = grid[y - 1][x - 1];
        if (plant == null) {
            return "Error: there is no plant at (" + x + ", " + y + ").";
        }
        if (plantFood <= 0) {
            return "Error: you have no plant food.";
        }
        plantFood--;
        String cured = disabledPlants.remove(plant);
        applyPlantFoodEffect(plant);
        return "Plant food used on " + plant.getSpec().getName() + " at (" + x + ", " + y + ")."
                + (cured == null ? "" : " It broke free of the " + cured + "!");
    }

    private void applyPlantFoodEffect(Plant plant) {
        combat.applyPlantFood(plant);
    }

    /**
     * Collects a sun on the given tile. Collecting a still-falling radioactive sun makes it explode.
     */
    public String collectSun(int x, int y) {
        if (!validTile(x, y)) {
            return "Error: (" + x + ", " + y + ") is not a valid tile.";
        }
        Sun falling = sunSystem.fallingRadioactiveAt(y - 1, x - 1);
        if (falling != null) {
            sunSystem.remove(falling);
            combat.radioactiveBlast(y - 1, x - 1);
            return "The radioactive sun exploded!";
        }
        Sun sun = sunSystem.groundAt(y - 1, x - 1);
        if (sun == null) {
            return "Error: there is no sun at (" + x + ", " + y + ").";
        }
        sunSystem.remove(sun);
        sunAmount += sun.value();
        return "Collected " + sun.value() + " sun; you now have " + sunAmount + " sun.";
    }

    private void removePlant(Plant plant) {
        if (grid[plant.getRow()][plant.getCol()] == plant) {
            grid[plant.getRow()][plant.getCol()] = null;
        }
        disabledPlants.remove(plant);
    }

    // ===== package-private hooks for zombie abilities =====

    List<Zombie> zombieList() {
        return zombies;
    }

    Plant[][] gridArray() {
        return grid;
    }

    List<String> eventLog() {
        return events;
    }

    List<Sun> sunList() {
        return sunSystem.live();
    }

    LevelSpec levelSpec() {
        return level;
    }

    void hitZombie(Zombie zombie, int damage) {
        hit(zombie, damage);
    }

    void disablePlant(Plant plant, String cause) {
        disabledPlants.put(plant, cause);
    }

    void enablePlant(Plant plant) {
        disabledPlants.remove(plant);
    }

    boolean isDisabled(Plant plant) {
        return disabledPlants.containsKey(plant);
    }

    ZombieAbilities abilitiesRef() {
        return abilities;
    }

    void explodePlant(Plant plant, int radius) {
        explode(plant, radius);
    }

    void damageRowFrom(int row, double fromX, int damage) {
        damageRow(row, fromX, damage);
    }

    void destroyPlantSilently(Plant plant) {
        removePlant(plant);
    }

    Zombie frontmost(int row, double fromX) {
        return frontmostInRow(row, fromX);
    }

    int graveColumnBetween(int row, int fromCol, double targetX) {
        return board.graveColumnBetween(row, fromCol, targetX);
    }

    void damageGraveAt(int row, int col, int damage) {
        damageGrave(row, col, damage);
    }

    // ===== special-level hooks =====

    void placeProtectedPlant(int row, int col, String type) {
        Plant plant = new Plant(GameCatalog.get().plant(type), row, col, false);
        grid[row][col] = plant;
        protectedPlants.add(plant);
        events.add("Protect the " + type + " at (" + (col + 1) + ", " + (row + 1) + ")!");
    }

    boolean isProtectedPlant(Plant plant) {
        return protectedPlants.contains(plant);
    }

    void winNow(String message) {
        if (!isOver()) {
            won = true;
            events.add(message);
        }
    }

    void loseNow(String message) {
        if (!isOver()) {
            lost = true;
            events.add(message);
            events.add("The zombie ate your brain; LOSER!!!");
        }
    }

    void setCooldownsSuspended(boolean suspended) {
        this.cooldownsSuspended = suspended;
    }

    void setSunAmount(int sunAmount) {
        this.sunAmount = sunAmount;
    }

    public boolean isConveyorLevel() {
        return special.conveyorMode();
    }

    public List<String> conveyorBelt() {
        List<String> contents = special.beltContents();
        return contents.isEmpty() && minigame != null ? minigame.handContents() : contents;
    }

    public String startZombieWaves() {
        return special.startZombieWaves();
    }

    // ===== minigame hooks =====

    void attachMinigame(MinigameLogic logic) {
        this.minigame = logic;
        logic.init(this);
    }

    void disableMowers() {
        java.util.Arrays.fill(mowers, false);
    }

    void spendSun(int amount) {
        sunAmount -= amount;
    }

    void slayZombie(Zombie zombie) {
        if (zombies.contains(zombie)) {
            killZombie(zombie);
        }
    }

    void removeZombieQuietly(Zombie zombie) {
        zombies.remove(zombie);
        eatProgress.remove(zombie);
    }

    void clearTile(int row, int col) {
        grid[row][col] = null;
    }

    void placePlant(int row, int col, String type) {
        grid[row][col] = new Plant(GameCatalog.get().plant(type), row, col, false);
        events.add("A " + type + " defends (" + (col + 1) + ", " + (row + 1) + ").");
    }

    public String breakVase(int x, int y) {
        if (minigame == null || !validTile(x, y)) {
            return "Error: there is no vase to break here.";
        }
        return minigame.breakVase(this, x, y);
    }

    public String takePacket(int x, int y) {
        if (minigame == null || !validTile(x, y)) {
            return "Error: there is no seed packet here.";
        }
        return minigame.takePacket(this, x, y);
    }

    public List<String> vasesInfo() {
        return minigame == null ? List.of("There is no vase in this game.") : minigame.vasesInfo();
    }

    public String placeZombie(String type, int x, int y) {
        if (minigame == null) {
            return "Error: you cannot place zombies in this game.";
        }
        return minigame.placeZombie(this, type, x, y);
    }

    public boolean isPlantDisabled(int x, int y) {
        Plant plant = grid[y - 1][x - 1];
        return plant != null && disabledPlants.containsKey(plant);
    }

    /**
     * The King crowns a simple zombie: it is replaced by a knight in place.
     */
    Zombie convertToKnight(Zombie zombie) {
        zombies.remove(zombie);
        eatProgress.remove(zombie);
        ZombieSpec knight = GameCatalog.get().zombie("knight");
        Map<String, Integer> armor = new java.util.LinkedHashMap<>();
        knight.getArmor().forEach((name, hp) -> armor.put(name, (int) Math.round(hp * difficultyUp)));
        Zombie crowned = new Zombie(knight, zombie.getRow(), zombie.getX(),
                (int) Math.round(knight.getHp() * difficultyUp), armor, zombie.isGlowing());
        zombies.add(crowned);
        seenZombieTypes.add("knight");
        return crowned;
    }

    private boolean validTile(int x, int y) {
        return x >= 1 && x <= COLS && y >= 1 && y <= ROWS;
    }

    public TileTerrain terrainAt(int x, int y) {
        return board.terrainAt(y - 1, x - 1);
    }

    public int graveHpAt(int x, int y) {
        return board.graveHpAt(y - 1, x - 1);
    }

    private void damageGrave(int row, int col, int damage) {
        String contents = board.damageGrave(row, col, damage);
        if ("sun".equals(contents)) {
            sunAmount += 50;
            events.add("The grave held 50 sun; you now have " + sunAmount + " sun.");
        } else if ("plant food".equals(contents)) {
            plantFood = Math.min(MAX_PLANT_FOOD, plantFood + 1);
            events.add("The grave held a plant food; you have " + plantFood + " plant foods now.");
        }
    }

    // ===== cheats =====

    public String cheatAddSuns(int count) {
        sunAmount += count;
        return "Added " + count + " suns; you now have " + sunAmount + " sun.";
    }

    public String cheatRemoveCooldown() {
        cooldownsDisabled = true;
        plantCooldowns.clear();
        return "All cooldowns removed.";
    }

    public String cheatAddPlantFood() {
        plantFood = Math.min(MAX_PLANT_FOOD, plantFood + 1);
        return "You have " + plantFood + " plant foods now.";
    }

    /**
     * Plant food bought in the shop before the level started.
     */
    public void grantPlantFood(int count) {
        plantFood = Math.min(MAX_PLANT_FOOD, plantFood + count);
    }

    public String cheatSpawnZombie(String type, int x, int y) {
        ZombieSpec spec = GameCatalog.get().zombie(type);
        if (spec == null) {
            return "Error: there is no zombie type named '" + type + "'.";
        }
        if (!validTile(x, y)) {
            return "Error: (" + x + ", " + y + ") is not a valid tile.";
        }
        spawnZombie(spec, y - 1, x);
        return "Zombie " + type + " spawned at (" + x + ", " + y + ").";
    }

    public String releaseTheNuke() {
        for (Zombie zombie : new ArrayList<>(zombies)) {
            killZombie(zombie);
        }
        return "The nuke wiped the whole map.";
    }
}
