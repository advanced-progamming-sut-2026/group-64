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
    final Set<String> boostedPlants;
    private final LevelSpec level;
    private final ZombieAbilities abilities;
    private final PlantCombat combat;
    private final PlantAbilities plantAbilities = new PlantAbilities(this);
    final SpecialLevelEngine special;
    private final ZombossEngine zomboss;
    private final Set<Plant> protectedPlants = new java.util.HashSet<>();
    private final Cheats cheats = new Cheats(this);
    private final Planting planting = new Planting(this);

    private final Plant[][] grid = new Plant[ROWS][COLS];
    final Board board;
    private final SunSystem sunSystem;
    private final WaveSystem waves;
    private final Map<Plant, String> disabledPlants = new HashMap<>();
    private final boolean[] mowers = new boolean[ROWS];
    final List<Zombie> zombies = new ArrayList<>();
    final Map<String, Double> plantCooldowns = new HashMap<>();
    /** Collection-menu upgrade levels per plant type; missing means level 1. */
    final Map<String, Integer> plantLevels = new HashMap<>();
    private final Map<Zombie, Double> eatProgress = new HashMap<>();
    private final List<String> events = new ArrayList<>();
    private final List<Shot> shots = new ArrayList<>();
    private final List<Burst> bursts = new ArrayList<>();
    private final Set<String> seenZombieTypes = new java.util.LinkedHashSet<>();

    private long tickCount;
    int sunAmount = INITIAL_SUN;
    private int plantFood;
    boolean cooldownsDisabled;
    boolean cooldownsSuspended;
    private ScoreTracker scoreTracker;
    MinigameLogic minigame;
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
        this.zomboss = level.isBoss()
                ? new ZombossEngine(this, level.getChapter(), random) : null;
        this.special.init();
        if (zomboss != null) {
            waves.setEnabled(false);
            events.add("Zomboss is here! Knock out all three parts of its health.");
        }
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

    /**
     * How long this level has been running, in seconds of game time.
     */
    public double getElapsedSeconds() {
        return tickCount / (double) TICKS_PER_SECOND;
    }

    public void advance(int ticks) {
        for (int i = 0; i < ticks && !isOver(); i++) {
            tickCount++;
            passTimers();
            plantAbilities.tick(1.0 / TICKS_PER_SECOND);
            produceSuns();
            sunSystem.tick(1.0 / TICKS_PER_SECOND, getElapsedSeconds());
            waves.tick(getElapsedSeconds());
            special.tick(getElapsedSeconds());
            if (zomboss != null) {
                zomboss.tick(getElapsedSeconds());
            }
            if (minigame != null) {
                minigame.tick(this, getElapsedSeconds());
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
        for (Shot shot : shots) {
            shot.passSeconds(dt);
        }
        shots.removeIf(Shot::isDone);
        for (Burst burst : bursts) {
            burst.passSeconds(dt);
        }
        bursts.removeIf(Burst::isDone);
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
        if (plantAbilities.absorbedByShield(plant, damage)) {
            return;
        }
        if (plant.damage(damage)) {
            removePlant(plant);
            events.add("Plant " + plant.getSpec().getName() + " at (" + (plant.getCol() + 1)
                    + ", " + (plant.getRow() + 1) + ") is destroyed.");
            recordBurst(Burst.Kind.PLANT_LOST, plant.getCol() + 1.0, plant.getRow() + 1.0);
            special.onPlantDestroyed(plant);
            protectedPlants.remove(plant);
        }
    }

    private void explode(Plant plant, int radius) {
        recordBurst(Burst.Kind.EXPLOSION, plant.getCol() + 1.0, plant.getRow() + 1.0);
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

    void killZombie(Zombie zombie) {
        recordBurst(Burst.Kind.ZOMBIE_DOWN, zombie.getX(), zombie.getRow() + 1.0);
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
            events.add("The glowing zombie dropped a plant food; you have " + plantFood + " plant foods now.");
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
            events.add("A zombie dropped a diamond; you have " + earnedDiamonds + " diamonds now.");
        } else if (kind == 1) {
            earnedCoins += 50;
            events.add("A zombie dropped a coin; you have " + earnedCoins + " coins now.");
        } else {
            earnedPots++;
            events.add("A zombie dropped a pot; you have " + earnedPots + " pots now.");
        }
    }

    private void zombiesAct() {
        double dt = 1.0 / TICKS_PER_SECOND;
        abilities.tick(dt);
        for (Zombie zombie : new ArrayList<>(zombies)) {
            if (!zombies.contains(zombie)) {
                continue;
            }
            if (zombie.isHypnotized()) {
                plantAbilities.walkBackAndFight(zombie, dt);
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
        zombie.startEating();
        double progress = eatProgress.merge(zombie, dt, Double::sum);
        if (progress >= 1) {
            eatProgress.put(zombie, progress - 1);
            plantAbilities.onEaten(plant, zombie);
            if (grid[plant.getRow()][plant.getCol()] == plant) {
                plantHit(plant, (int) Math.round(zombie.getSpec().getDamagePerSecond() * difficultyUp));
            }
        }
    }

    private void reachHouse(Zombie zombie) {
        if (minigame != null && minigame.onHouseReached(this, zombie)) {
            return;
        }
        int row = zombie.getRow();
        if (mowers[row]) {
            mowers[row] = false;
            recordBurst(Burst.Kind.MOWER, 1, row + 1.0);
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
        if (zomboss != null) {
            if (!lost && zomboss.boss().isDefeated()) {
                won = true;
                events.add("Zomboss is beaten! The chapter is yours.");
            }
            return;
        }
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

    /**
     * The level being played, so a view can pick the right background and show
     * the wave count without keeping its own copy of the level data.
     */
    public LevelSpec getLevel() {
        return level;
    }

    /**
     * Gives the level up. Used by the pause menu's "save and exit", which has
     * to leave the session finished so the menu can be exited normally.
     */
    public void forfeit() {
        if (!isOver()) {
            lost = true;
            events.add("You left the level; the zombies win this one.");
        }
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

    /**
     * How far this level has come, from 0 at the start to 1 when it is won.
     */
    public double getWaveProgress() {
        return waves.progress();
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

    /**
     * The projectiles currently in the air. They are drawn, never simulated:
     * the damage they represent was already applied when the plant fired.
     */
    /**
     * The special rules engine for this level, so the view can show whatever
     * objective the player is being judged on.
     */
    public SpecialLevelEngine getSpecial() {
        return special;
    }

    /**
     * The minigame running in this session, or null on an adventure level.
     */
    public MinigameLogic getMinigame() {
        return minigame;
    }

    /**
     * The one-off effects currently playing on the lawn.
     */
    /**
     * The boss of this level, or null on an ordinary one.
     */
    public Zomboss getZomboss() {
        return zomboss == null ? null : zomboss.boss();
    }

    /**
     * The boss engine of this level, or null on an ordinary one.
     */
    ZombossEngine zombossEngine() {
        return zomboss;
    }

    public List<Burst> getBursts() {
        return bursts;
    }

    /**
     * Notes that something worth showing just happened at a tile.
     */
    void recordBurst(Burst.Kind kind, double col, double row) {
        bursts.add(new Burst(kind, col, row));
    }

    public List<Shot> getShots() {
        return shots;
    }

    /**
     * Remembers that a plant just fired, so the view can show the shot flying.
     */
    void recordShot(Plant from, double toX, Shot.Flight flight) {
        shots.add(Shot.from(from, toX, flight));
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
     * Applies the upgrade levels the player bought in the collection menu, so
     * an upgraded plant is planted stronger for the rest of this level.
     */
    public void setPlantLevels(Map<String, Integer> levels) {
        plantLevels.clear();
        plantLevels.putAll(levels);
    }

    /**
     * The level this plant type was upgraded to; 1 when it was never upgraded.
     */
    public int plantLevel(String type) {
        return plantLevels.getOrDefault(type, 1);
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
        return planting.plant(type, x, y);
    }

    public String pluck(int x, int y) {
        return planting.pluck(x, y);
    }

    public String feedPlant(int x, int y) {
        return planting.feedPlant(x, y);
    }

    /**
     * Burns one plant food on this plant, returning the ability it broke free
     * of, if any.
     */
    String spendPlantFoodOn(Plant plant) {
        plantFood--;
        String cured = disabledPlants.remove(plant);
        applyPlantFoodEffect(plant);
        return cured;
    }

    void applyPlantFoodEffect(Plant plant) {
        recordBurst(Burst.Kind.PLANT_FOOD, plant.getCol() + 1.0, plant.getRow() + 1.0);
        combat.applyPlantFood(plant);
    }

    /**
     * Collects a sun on the given tile. Collecting a still-falling radioactive sun makes it explode.
     */
    public String collectSun(int x, int y) {
        return planting.collectSun(x, y);
    }

    private void removePlant(Plant plant) {
        if (grid[plant.getRow()][plant.getCol()] == plant) {
            grid[plant.getRow()][plant.getCol()] = null;
        }
        disabledPlants.remove(plant);
        plantAbilities.dropShield(plant);
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

    SunSystem sunSystem() {
        return sunSystem;
    }

    PlantCombat combat() {
        return combat;
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

    public boolean isProtectedPlant(Plant plant) {
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

    /**
     * How frozen a plant is, from 0 for untouched to 3 for frozen solid, which
     * is the three-step iciness phase 1 defines.
     */
    /**
     * What the grave on this tile holds, for the three Dark Ages headstones.
     */
    public String graveContentAt(int x, int y) {
        return board.graveContentAt(y - 1, x - 1);
    }

    public int iceLevelAt(int x, int y) {
        Plant plant = plantAtTile(x, y);
        return plant == null ? 0 : abilities.iceLevel(plant);
    }

    /**
     * Why the plant on this tile is out of action — "ice", "octopus", "spell" —
     * or null when it is fine, so the view can draw the right thing on top.
     */
    public String disableCauseAt(int x, int y) {
        Plant plant = plantAtTile(x, y);
        return plant == null ? null : disabledPlants.get(plant);
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

    boolean validTile(int x, int y) {
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

    /**
     * The debug commands, kept in their own class.
     */
    public Cheats cheats() {
        return cheats;
    }

    /**
     * Plant food bought in the shop before the level started.
     */
    public void grantPlantFood(int count) {
        plantFood = Math.min(MAX_PLANT_FOOD, plantFood + count);
    }

    // ===== hooks for the plant abilities =====

    PlantAbilities plantAbilities() {
        return plantAbilities;
    }

    /**
     * The pumpkin wrapped around this plant, if any; the view draws it on top.
     */
    public Plant shieldOn(Plant plant) {
        return plantAbilities.shieldOn(plant);
    }

    /**
     * The session's own die, so the abilities stay reproducible under a seed.
     */
    int roll(int bound) {
        return random.nextInt(bound);
    }

    /**
     * The level's difficulty multiplier, which the abilities scale speed by.
     */
    double difficultyScale() {
        return difficultyUp;
    }
}
