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

    private static final double FIRST_WAVE_AT_SECONDS = 10;
    private static final double SUN_LANDING_SECONDS = 5;
    private static final int TOTAL_WAVES = 4;
    private static final double FIRST_WAVE_BUDGET = 4;
    private static final int INITIAL_SUN = 50;

    private final Random random;
    private final double difficultyUp;
    private final double difficultyDown;
    private final List<String> selectedPlants;
    private final Set<String> boostedPlants;

    private final Plant[][] grid = new Plant[ROWS][COLS];
    private final boolean[] mowers = new boolean[ROWS];
    private final List<Zombie> zombies = new ArrayList<>();
    private final List<Sun> suns = new ArrayList<>();
    private final Map<String, Double> plantCooldowns = new HashMap<>();
    private final Map<Zombie, Double> eatProgress = new HashMap<>();
    private final List<String> events = new ArrayList<>();
    private final Set<String> seenZombieTypes = new java.util.LinkedHashSet<>();

    private long tickCount;
    private int sunAmount = INITIAL_SUN;
    private int plantFood;
    private double nextFallingSunAtSeconds;
    private int currentWave;
    private double waveBudget = FIRST_WAVE_BUDGET;
    private double nextWaveAtSeconds = FIRST_WAVE_AT_SECONDS;
    private final List<Zombie> currentWaveZombies = new ArrayList<>();
    private int currentWaveSpawnedHealth;
    private boolean cooldownsDisabled;
    private boolean wavesEnabled = true;
    private int earnedCoins;
    private int earnedDiamonds;
    private int earnedPots;
    private boolean won;
    private boolean lost;

    public GameSession(int difficulty, List<String> selectedPlants, Set<String> boostedPlants, Random random) {
        this.random = random;
        this.difficultyUp = difficulty / 3.0;
        this.difficultyDown = 3.0 / difficulty;
        this.selectedPlants = List.copyOf(selectedPlants);
        this.boostedPlants = boostedPlants;
        java.util.Arrays.fill(mowers, true);
        this.nextFallingSunAtSeconds = fallingSunInterval();
    }

    private double seconds() {
        return tickCount / (double) TICKS_PER_SECOND;
    }

    private double fallingSunInterval() {
        return Math.max(6 + 0.05 * seconds(), 12) * difficultyUp;
    }

    public void advance(int ticks) {
        for (int i = 0; i < ticks && !isOver(); i++) {
            tickCount++;
            passTimers();
            produceSuns();
            dropFallingSuns();
            runWaves();
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
        for (Plant[] row : grid) {
            for (Plant plant : row) {
                if (plant == null || plant.getSpec().getCategory() != PlantCategory.SUN_PRODUCER) {
                    continue;
                }
                if (plant.isReadyToAttack() && groundSunAt(plant.getRow(), plant.getCol()) == null) {
                    suns.add(new Sun(Sun.Kind.NORMAL, plant.getRow(), plant.getCol(), 0));
                    plant.resetAttackCooldown();
                    events.add("plant " + plant.getSpec().getName() + " produced a sun at ("
                            + (plant.getCol() + 1) + ", " + (plant.getRow() + 1) + ")");
                }
            }
        }
    }

    private void dropFallingSuns() {
        double dt = 1.0 / TICKS_PER_SECOND;
        for (Sun sun : new ArrayList<>(suns)) {
            if (sun.passSeconds(dt)) {
                events.add("Sun reached the ground at position (" + (sun.getCol() + 1)
                        + ", " + (sun.getRow() + 1) + ")");
            }
        }
        if (seconds() >= nextFallingSunAtSeconds) {
            nextFallingSunAtSeconds = seconds() + fallingSunInterval();
            Sun.Kind kind = rollSunKind();
            int row = random.nextInt(ROWS);
            int col = random.nextInt(COLS);
            suns.add(new Sun(kind, row, col, SUN_LANDING_SECONDS));
            events.add("New " + kind.name().toLowerCase(Locale.ROOT) + " sun is dropping at position ("
                    + (col + 1) + ", " + (row + 1) + ")");
        }
    }

    private Sun.Kind rollSunKind() {
        int roll = random.nextInt(100);
        if (roll < 80) {
            return Sun.Kind.NORMAL;
        }
        return roll < 95 ? Sun.Kind.SPECIAL : Sun.Kind.RADIOACTIVE;
    }

    /**
     * Special levels (e.g. Plant What You Get) start their waves manually.
     */
    public void setWavesEnabled(boolean wavesEnabled) {
        this.wavesEnabled = wavesEnabled;
    }

    private void runWaves() {
        if (!wavesEnabled) {
            return;
        }
        boolean waveCleared = currentWave > 0 && waveRemainingHealth() <= 0.25 * currentWaveSpawnedHealth;
        boolean timeForFirst = currentWave == 0 && seconds() >= nextWaveAtSeconds;
        if (currentWave >= TOTAL_WAVES || (!timeForFirst && !waveCleared)) {
            return;
        }
        currentWave++;
        boolean flagWave = currentWave == TOTAL_WAVES;
        if (currentWave > 1) {
            waveBudget = flagWave ? waveBudget * 2 : waveBudget * 1.25;
        }
        events.add(flagWave ? "The final wave has come." : "Wave " + currentWave + " started.");
        spawnWave();
    }

    private void spawnWave() {
        currentWaveZombies.clear();
        currentWaveSpawnedHealth = 0;
        List<ZombieSpec> pool = GameCatalog.get().allZombies().stream()
                .filter(spec -> !spec.getName().equals("gargantuar") || currentWave == TOTAL_WAVES)
                .toList();
        double spent = 0;
        while (spent < waveBudget) {
            ZombieSpec spec = pool.get(random.nextInt(pool.size()));
            double cost = spec.getWaveCost() * difficultyDown;
            int lane = random.nextInt(ROWS);
            Zombie zombie = spawnZombie(spec, lane, COLS);
            currentWaveZombies.add(zombie);
            currentWaveSpawnedHealth += zombie.totalRemainingHealth();
            spent += cost;
            events.add("Zombie " + spec.getName() + " spawned at wave " + currentWave + " in lane "
                    + (lane + 1) + " which costed " + trim(cost) + ".");
        }
    }

    private Zombie spawnZombie(ZombieSpec spec, int row, double x) {
        Map<String, Integer> armor = new java.util.LinkedHashMap<>();
        spec.getArmor().forEach((name, hp) -> armor.put(name, (int) Math.round(hp * difficultyUp)));
        int hp = (int) Math.round(spec.getHp() * difficultyUp);
        boolean glowing = random.nextInt(100) < 5;
        Zombie zombie = new Zombie(spec, row, x, hp, armor, glowing);
        zombies.add(zombie);
        seenZombieTypes.add(spec.getName());
        return zombie;
    }

    private int waveRemainingHealth() {
        return currentWaveZombies.stream().filter(zombies::contains)
                .mapToInt(Zombie::totalRemainingHealth).sum();
    }

    private void plantsAct() {
        List<Plant> snapshot = new ArrayList<>();
        for (Plant[] row : grid) {
            for (Plant plant : row) {
                if (plant != null) {
                    snapshot.add(plant);
                }
            }
        }
        for (Plant plant : snapshot) {
            if (grid[plant.getRow()][plant.getCol()] == plant) {
                actPlant(plant);
            }
        }
    }

    private void actPlant(Plant plant) {
        PlantCategory category = plant.getSpec().getCategory();
        if (category == PlantCategory.EXPLOSIVE && plant.isArmed()) {
            explode(plant, 1);
            return;
        }
        if (category == PlantCategory.TRAP && plant.isArmed()) {
            triggerTrapIfTouched(plant);
            return;
        }
        if (category == PlantCategory.MINT && plant.isArmed()) {
            damageRow(plant.getRow(), 0, plant.getSpec().getDamage());
            removePlant(plant);
            return;
        }
        if (!plant.isReadyToAttack() || plant.getSpec().getDamage() == 0) {
            return;
        }
        attackWith(plant);
    }

    private void attackWith(Plant plant) {
        switch (plant.getSpec().getCategory()) {
            case SHOOTER, LOBBER -> shootFrontmost(plant);
            case STRIKE_THROUGH -> damageRowByPlant(plant);
            case HOMING -> shootNearestAnywhere(plant);
            case MELEE -> biteAdjacent(plant);
            default -> { }
        }
    }

    private void shootFrontmost(Plant plant) {
        Zombie target = frontmostInRow(plant.getRow(), plant.getCol() + 1.0);
        if (target == null) {
            return;
        }
        int damage = plant.getSpec().getDamage() + torchwoodBonus(plant);
        if (plant.getSpec().hasTag("ice")) {
            target.chill(5);
        }
        hit(target, damage);
        plant.resetAttackCooldown();
    }

    private void damageRowByPlant(Plant plant) {
        boolean anyZombie = zombies.stream().anyMatch(z -> z.getRow() == plant.getRow());
        if (anyZombie) {
            damageRow(plant.getRow(), plant.getCol() + 1.0, plant.getSpec().getDamage());
            plant.resetAttackCooldown();
        }
    }

    private int torchwoodBonus(Plant shooter) {
        if (!shooter.getSpec().hasTag("pea")) {
            return 0;
        }
        for (int col = shooter.getCol() + 1; col < COLS; col++) {
            Plant plant = grid[shooter.getRow()][col];
            if (plant != null && plant.getSpec().getCategory() == PlantCategory.MODIFIER
                    && plant.getSpec().hasTag("fire")) {
                return 20;
            }
        }
        return 0;
    }

    private void shootNearestAnywhere(Plant plant) {
        Zombie target = null;
        double best = Double.MAX_VALUE;
        for (Zombie zombie : zombies) {
            double distance = Math.abs(zombie.getRow() - plant.getRow()) + Math.abs(zombie.getX() - plant.getCol());
            if (distance < best) {
                best = distance;
                target = zombie;
            }
        }
        if (target != null) {
            hit(target, plant.getSpec().getDamage());
            plant.resetAttackCooldown();
        }
    }

    private void biteAdjacent(Plant plant) {
        Zombie target = frontmostInRow(plant.getRow(), plant.getCol() + 1.0);
        if (target != null && target.getX() <= plant.getCol() + 2.2) {
            hit(target, plant.getSpec().getDamage());
            plant.resetAttackCooldown();
        }
    }

    private void triggerTrapIfTouched(Plant plant) {
        for (Zombie zombie : new ArrayList<>(zombies)) {
            if (zombie.getRow() == plant.getRow() && Math.abs(zombie.getX() - (plant.getCol() + 1)) < 0.5) {
                if (plant.getSpec().hasTag("ice")) {
                    zombie.freeze(5);
                } else {
                    hit(zombie, plant.getSpec().getDamage());
                }
                removePlant(plant);
                return;
            }
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
        for (Zombie zombie : new ArrayList<>(zombies)) {
            Plant blocking = plantInFrontOf(zombie);
            if (blocking != null) {
                eat(zombie, blocking, dt);
            } else {
                zombie.walk(zombie.getSpec().getTilesPerSecond() * difficultyUp * zombie.speedMultiplier() * dt);
                if (zombie.getX() < 1) {
                    reachHouse(zombie);
                }
            }
        }
    }

    private Plant plantInFrontOf(Zombie zombie) {
        int col = (int) Math.round(zombie.getX()) - 1;
        if (col < 0 || col >= COLS) {
            return null;
        }
        return grid[zombie.getRow()][col];
    }

    private void eat(Zombie zombie, Plant plant, double dt) {
        if (zombie.isFrozen()) {
            return;
        }
        double progress = eatProgress.merge(zombie, dt, Double::sum);
        if (progress >= 1) {
            eatProgress.put(zombie, progress - 1);
            int damage = (int) Math.round(zombie.getSpec().getDamagePerSecond() * difficultyUp);
            if (plant.damage(damage)) {
                removePlant(plant);
                events.add("Plant " + plant.getSpec().getName() + " at (" + (plant.getCol() + 1)
                        + ", " + (plant.getRow() + 1) + ") is destroyed.");
            }
        }
    }

    private void reachHouse(Zombie zombie) {
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
        if (!lost && currentWave >= TOTAL_WAVES && zombies.isEmpty()) {
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
        return currentWave;
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
        return suns.stream().filter(Sun::isOnGround).toList();
    }

    // ===== player commands =====

    /**
     * Plants a selected type on tile (x=column, y=row), enforcing sun, cooldown and occupancy rules.
     */
    public String plant(String type, int x, int y) {
        if (!selectedPlants.contains(type)) {
            return "Error: plant '" + type + "' is not among your selected plants.";
        }
        if (!validTile(x, y)) {
            return "Error: (" + x + ", " + y + ") is not a valid tile.";
        }
        PlantSpec spec = GameCatalog.get().plant(type);
        if (grid[y - 1][x - 1] != null) {
            return "Error: tile (" + x + ", " + y + ") is already occupied.";
        }
        if (!cooldownsDisabled && plantCooldowns.getOrDefault(type, 0.0) > 0) {
            return "Error: " + type + " is recharging; wait " + trim(plantCooldowns.get(type)) + "s.";
        }
        if (sunAmount < spec.getSunCost()) {
            return "Error: not enough sun; " + type + " costs " + spec.getSunCost() + ".";
        }
        sunAmount -= spec.getSunCost();
        plantCooldowns.put(type, spec.getRechargeSeconds());
        Plant plant = new Plant(spec, y - 1, x - 1, boostedPlants.remove(type));
        grid[y - 1][x - 1] = plant;
        if (plant.isBoosted()) {
            plant.consumeBoost();
            applyPlantFoodEffect(plant);
        }
        return "Planted " + type + " at (" + x + ", " + y + ").";
    }

    public String pluck(int x, int y) {
        if (!validTile(x, y)) {
            return "Error: (" + x + ", " + y + ") is not a valid tile.";
        }
        Plant plant = grid[y - 1][x - 1];
        if (plant == null) {
            return "Error: there is no plant at (" + x + ", " + y + ").";
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
        applyPlantFoodEffect(plant);
        return "Plant food used on " + plant.getSpec().getName() + " at (" + x + ", " + y + ").";
    }

    private void applyPlantFoodEffect(Plant plant) {
        switch (plant.getSpec().getCategory()) {
            case SUN_PRODUCER -> sunAmount += 150;
            case WALL -> plant.heal();
            case EXPLOSIVE, TRAP -> explode(plant, 1);
            case MELEE -> {
                Zombie front = frontmostInRow(plant.getRow(), plant.getCol() + 1.0);
                if (front != null) {
                    killZombie(front);
                }
            }
            case MODIFIER, MINT -> explodeAround(plant, 150);
            default -> damageRow(plant.getRow(), plant.getCol() + 1.0, 300);
        }
    }

    private void explodeAround(Plant plant, int damage) {
        for (Zombie zombie : new ArrayList<>(zombies)) {
            if (Math.abs(zombie.getRow() - plant.getRow()) <= 1
                    && Math.abs(zombie.getX() - (plant.getCol() + 1)) <= 1.5) {
                hit(zombie, damage);
            }
        }
    }

    /**
     * Collects a sun on the given tile. Collecting a still-falling radioactive sun makes it explode.
     */
    public String collectSun(int x, int y) {
        if (!validTile(x, y)) {
            return "Error: (" + x + ", " + y + ") is not a valid tile.";
        }
        Sun falling = fallingRadioactiveAt(y - 1, x - 1);
        if (falling != null) {
            suns.remove(falling);
            radioactiveBlast(y - 1, x - 1);
            return "The radioactive sun exploded!";
        }
        Sun sun = groundSunAt(y - 1, x - 1);
        if (sun == null) {
            return "Error: there is no sun at (" + x + ", " + y + ").";
        }
        suns.remove(sun);
        sunAmount += sun.value();
        return "Collected " + sun.value() + " sun; you now have " + sunAmount + " sun.";
    }

    private void radioactiveBlast(int row, int col) {
        for (Zombie zombie : new ArrayList<>(zombies)) {
            if (Math.abs(zombie.getRow() - row) <= 2 && Math.abs(zombie.getX() - (col + 1)) <= 2.5) {
                hit(zombie, 150);
            }
        }
        for (int r = Math.max(0, row - 1); r <= Math.min(ROWS - 1, row + 1); r++) {
            for (int c = Math.max(0, col - 1); c <= Math.min(COLS - 1, col + 1); c++) {
                Plant plant = grid[r][c];
                if (plant != null && plant.damage(80)) {
                    removePlant(plant);
                    events.add("Plant " + plant.getSpec().getName() + " at (" + (c + 1)
                            + ", " + (r + 1) + ") is destroyed.");
                }
            }
        }
    }

    private Sun groundSunAt(int row, int col) {
        return suns.stream().filter(s -> s.isOnGround() && s.getRow() == row && s.getCol() == col)
                .findFirst().orElse(null);
    }

    private Sun fallingRadioactiveAt(int row, int col) {
        return suns.stream().filter(s -> !s.isOnGround() && s.getKind() == Sun.Kind.RADIOACTIVE
                && s.getRow() == row && s.getCol() == col).findFirst().orElse(null);
    }

    private void removePlant(Plant plant) {
        if (grid[plant.getRow()][plant.getCol()] == plant) {
            grid[plant.getRow()][plant.getCol()] = null;
        }
    }

    private boolean validTile(int x, int y) {
        return x >= 1 && x <= COLS && y >= 1 && y <= ROWS;
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
