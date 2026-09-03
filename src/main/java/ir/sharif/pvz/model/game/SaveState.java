package ir.sharif.pvz.model.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Turns a level in progress into a {@link SavedGame} and back again.
 *
 * <p>It lives beside {@link GameSession} rather than inside it: the session is
 * already the biggest class in the game, and reading its innards out is a job
 * of its own.
 */
public final class SaveState {

    private SaveState() {
    }

    // ===== writing =====

    /**
     * Everything about this level worth coming back to.
     */
    public static SavedGame capture(GameSession session) {
        LevelSpec level = session.getLevel();
        return new SavedGame(level.getChapter().name(), level.getDay(),
                session.difficulty(), session.ticks(),
                List.copyOf(session.getSelectedPlants()),
                new LinkedHashMap<>(session.plantLevels),
                List.copyOf(session.boostedPlants),
                new LinkedHashMap<>(session.plantCooldowns),
                session.getSunAmount(), session.getPlantFood(),
                session.getEarnedCoins(), session.getEarnedDiamonds(), session.getEarnedPots(),
                mowers(session), List.copyOf(session.getSeenZombieTypes()),
                plants(session), zombies(session), suns(session), terrain(session),
                session.waves().capture());
    }

    private static List<Boolean> mowers(GameSession session) {
        List<Boolean> left = new ArrayList<>();
        for (int row = 0; row < GameSession.ROWS; row++) {
            left.add(session.isMowerAvailable(row));
        }
        return left;
    }

    private static List<SavedGame.PlantState> plants(GameSession session) {
        List<SavedGame.PlantState> saved = new ArrayList<>();
        for (Plant plant : session.plantedPlants()) {
            Plant shield = session.shieldOn(plant);
            saved.add(new SavedGame.PlantState(plant.getSpec().getName(), plant.getRow(),
                    plant.getCol(), plant.getHp(), plant.getLevel(), plant.getStack(),
                    plant.getAgeSeconds(), plant.isBoosted(),
                    shield == null ? null : shield.getSpec().getName(),
                    shield == null ? 0 : shield.getHp(),
                    session.disabledPlants.get(plant), session.isProtectedPlant(plant)));
        }
        return saved;
    }

    private static List<SavedGame.ZombieState> zombies(GameSession session) {
        List<SavedGame.ZombieState> saved = new ArrayList<>();
        for (Zombie zombie : session.getZombies()) {
            saved.add(new SavedGame.ZombieState(zombie.getSpec().getName(), zombie.getRow(),
                    zombie.getX(), zombie.getHp(), zombie.getArmor(), zombie.isGlowing(),
                    zombie.chilledSeconds(), zombie.frozenSeconds(), zombie.poisonedSeconds(),
                    zombie.getPoisonPerSecond(), zombie.isHypnotized(),
                    session.eatProgress.getOrDefault(zombie, 0.0)));
        }
        return saved;
    }

    private static List<SavedGame.SunState> suns(GameSession session) {
        List<SavedGame.SunState> saved = new ArrayList<>();
        for (Sun sun : session.sunSystem.live()) {
            saved.add(new SavedGame.SunState(sun.getKind().name(), sun.getRow(), sun.getCol(),
                    sun.secondsUntilLanding(), sun.value()));
        }
        return saved;
    }

    /**
     * Only the tiles that no longer match the level's own layout: a lily pad
     * laid on the water, a busted grave, the crater a doom-shroom left.
     */
    private static Map<String, String> terrain(GameSession session) {
        Map<String, String> changed = new LinkedHashMap<>();
        Map<Integer, TileTerrain> original = session.getLevel().getTerrain();
        for (int row = 0; row < GameSession.ROWS; row++) {
            for (int col = 0; col < GameSession.COLS; col++) {
                TileTerrain now = session.terrainAt(col + 1, row + 1);
                TileTerrain then = original.getOrDefault(LevelSpec.tileKey(row, col),
                        TileTerrain.NORMAL);
                if (now != then) {
                    changed.put(row + "," + col, now.name());
                }
            }
        }
        return changed;
    }

    // ===== reading =====

    /**
     * Builds the level back up. The zombies, plants and suns are put back where
     * they were rather than the level being replayed from the start.
     *
     * @return the session, or null when the save names a level that is gone
     */
    public static GameSession restore(SavedGame saved, Random random) {
        LevelSpec level = levelOf(saved);
        if (level == null) {
            return null;
        }
        GameSession session = new GameSession(level, saved.difficulty(),
                saved.selectedPlants(), new HashSet<>(saved.boostedPlants()), random);
        session.setPlantLevels(saved.plantLevels());
        load(session, saved);
        return session;
    }

    /**
     * The level a save refers to, or null when no such chapter and day exist.
     */
    public static LevelSpec levelOf(SavedGame saved) {
        for (LevelSpec level : Levels.adventure()) {
            if (level.getChapter().name().equals(saved.chapter()) && level.getDay() == saved.day()) {
                return level;
            }
        }
        return null;
    }

    /**
     * How the save reads on a menu, e.g. "Ancient Egypt - Day 3".
     */
    public static String describe(SavedGame saved) {
        LevelSpec level = levelOf(saved);
        if (level == null) {
            return "a level that is no longer in the game";
        }
        return level.title();
    }

    /**
     * Fills a freshly built session with a saved level's state.
     */
    private static void load(GameSession session, SavedGame saved) {
        session.tickCount = saved.ticks();
        session.sunAmount = saved.sun();
        session.plantFood = saved.plantFood();
        session.earnedCoins = saved.coins();
        session.earnedDiamonds = saved.diamonds();
        session.earnedPots = saved.pots();
        session.plantCooldowns.putAll(saved.cooldowns());
        session.seenZombieTypes.addAll(saved.seenZombies());
        for (int row = 0; row < GameSession.ROWS && row < saved.mowers().size(); row++) {
            session.mowers[row] = saved.mowers().get(row);
        }
        for (int row = 0; row < GameSession.ROWS; row++) {
            java.util.Arrays.fill(session.gridArray()[row], null);
        }
        restoreTerrain(session, saved);
        saved.plants().forEach(plant -> placePlant(session, plant));
        session.zombies.clear();
        saved.zombies().forEach(zombie -> placeZombie(session, zombie));
        session.sunSystem.live().clear();
        saved.suns().forEach(sun -> session.sunSystem.add(sunOf(sun)));
        session.waves().load(saved.wave());
        session.events.clear();
    }

    /**
     * Lays the ground back out. A fresh board scatters its own graves, so every
     * tile goes back to the level's own layout first and the save's changes go
     * on top; otherwise a level would gain a handful of graves every time it
     * was picked back up.
     */
    private static void restoreTerrain(GameSession session, SavedGame saved) {
        Map<Integer, TileTerrain> original = session.getLevel().getTerrain();
        for (int row = 0; row < GameSession.ROWS; row++) {
            for (int col = 0; col < GameSession.COLS; col++) {
                session.board.setTerrain(row, col,
                        original.getOrDefault(LevelSpec.tileKey(row, col), TileTerrain.NORMAL));
            }
        }
        saved.terrain().forEach((tile, name) -> {
            String[] parts = tile.split(",");
            int row = Integer.parseInt(parts[0]);
            int col = Integer.parseInt(parts[1]);
            TileTerrain kind = TileTerrain.valueOf(name);
            if (kind == TileTerrain.GRAVE) {
                // a grave carries hit points, which only raising one sets up
                session.board.raiseGrave(row, col, null, true);
            } else {
                session.board.setTerrain(row, col, kind);
            }
        });
    }

    /**
     * Puts one saved plant back on its tile, with its pumpkin if it had one.
     */
    static void placePlant(GameSession session, SavedGame.PlantState state) {
        PlantSpec spec = GameCatalog.get().plant(state.type());
        if (spec == null) {
            return;
        }
        Plant plant = new Plant(spec, state.row(), state.col(), state.boosted(), state.level());
        plant.restoreTo(state.hp(), state.stack(), state.age());
        session.gridArray()[state.row()][state.col()] = plant;
        if (state.shield() != null) {
            PlantSpec shellSpec = GameCatalog.get().plant(state.shield());
            if (shellSpec != null) {
                Plant shell = new Plant(shellSpec, state.row(), state.col(), false);
                shell.restoreTo(state.shieldHp(), 1, 0);
                session.plantAbilities().putShield(plant, shell);
            }
        }
        if (state.disabledBy() != null) {
            session.disablePlant(plant, state.disabledBy());
        }
        if (state.protectedPlant()) {
            session.protectedPlants.add(plant);
        }
    }

    /**
     * Puts one saved zombie back where it was walking.
     */
    static void placeZombie(GameSession session, SavedGame.ZombieState state) {
        ZombieSpec spec = GameCatalog.get().zombie(state.type());
        if (spec == null) {
            return;
        }
        Zombie zombie = new Zombie(spec, state.row(), state.x(), state.hp(),
                state.armor() == null ? Map.of() : state.armor(), state.glowing());
        zombie.restoreEffects(state.chilled(), state.frozen(), state.poisoned(),
                state.poisonPerSecond(), state.hypnotized());
        session.zombies.add(zombie);
        session.eatProgress.put(zombie, state.eatProgress());
        session.seenZombieTypes.add(zombie.getSpec().getName());
    }

    /**
     * Puts one saved sun back, in the air or on the ground.
     */
    static Sun sunOf(SavedGame.SunState state) {
        Sun.Kind kind = Sun.Kind.valueOf(state.kind().toUpperCase(Locale.ROOT));
        return new Sun(kind, state.row(), state.col(), state.falling(), state.value());
    }
}
