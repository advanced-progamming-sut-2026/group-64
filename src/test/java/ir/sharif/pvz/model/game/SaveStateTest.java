package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import ir.sharif.pvz.model.SavedGameStore;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Putting a level away and picking it back up. What matters is that the lawn
 * the player comes back to is the one they left, down to the health of a
 * half-eaten wall-nut and how far a zombie had walked.
 */
class SaveStateTest {

    @TempDir
    Path folder;

    private static GameSession halfPlayedLevel() {
        LevelSpec level = Levels.adventure().stream()
                .filter(spec -> spec.getChapter() == Chapter.BIG_WAVE_BEACH && spec.getDay() == 2)
                .findFirst().orElseThrow();
        GameSession session = new GameSession(level, 4,
                List.of("sunflower", "peashooter", "wall-nut", "lily-pad"),
                new HashSet<>(), new Random(9));
        session.setWavesEnabled(false);
        session.cheats().addSuns(2000);
        session.cheats().removeCooldown();
        session.plant("sunflower", 1, 1);
        session.plant("peashooter", 2, 3);
        session.plant("wall-nut", 5, 3);
        session.spawnZombie(GameCatalog.get().zombie("conehead"), 2, 7);
        session.spawnZombie(GameCatalog.get().zombie("normal"), 4, 6);
        session.advance(12 * GameSession.TICKS_PER_SECOND);
        return session;
    }

    private static GameSession roundTrip(GameSession session) {
        Gson gson = new Gson();
        // through JSON and back, the way the store keeps it
        SavedGame saved = gson.fromJson(gson.toJson(SaveState.capture(session)), SavedGame.class);
        GameSession back = SaveState.restore(saved, new Random(9));
        assertNotNull(back, "the level should come back");
        return back;
    }

    @Test
    void theLawnComesBackExactlyAsItWasLeft() {
        GameSession before = halfPlayedLevel();
        GameSession after = roundTrip(before);

        assertEquals(before.getSunAmount(), after.getSunAmount());
        assertEquals(before.getPlantFood(), after.getPlantFood());
        assertEquals(before.getElapsedSeconds(), after.getElapsedSeconds(), 0.001);
        assertEquals(before.getLevel().title(), after.getLevel().title());
        assertEquals(before.getSelectedPlants(), after.getSelectedPlants());

        assertEquals(before.plantedPlants().size(), after.plantedPlants().size());
        for (Plant plant : before.plantedPlants()) {
            Plant same = after.plantAtTile(plant.getCol() + 1, plant.getRow() + 1);
            assertNotNull(same, "a plant went missing at " + plant.getCol() + "," + plant.getRow());
            assertEquals(plant.getSpec().getName(), same.getSpec().getName());
            assertEquals(plant.getHp(), same.getHp(), "health should survive the save");
        }

        assertEquals(before.getZombies().size(), after.getZombies().size());
        for (int i = 0; i < before.getZombies().size(); i++) {
            Zombie was = before.getZombies().get(i);
            Zombie now = after.getZombies().get(i);
            assertEquals(was.getSpec().getName(), now.getSpec().getName());
            assertEquals(was.getRow(), now.getRow());
            assertEquals(was.getX(), now.getX(), 0.001, "a zombie moved while it was put away");
            assertEquals(was.totalRemainingHealth(), now.totalRemainingHealth());
        }
    }

    @Test
    void aHalfEatenPlantKeepsItsDamageAndTheZombieKeepsItsArmour() {
        GameSession before = halfPlayedLevel();
        Zombie coneHead = before.getZombies().stream()
                .filter(zombie -> zombie.getSpec().getName().equals("conehead"))
                .findFirst().orElseThrow();
        assertFalse(coneHead.getArmor().isEmpty(), "the cone is still on before the save");

        GameSession after = roundTrip(before);
        Zombie back = after.getZombies().stream()
                .filter(zombie -> zombie.getSpec().getName().equals("conehead"))
                .findFirst().orElseThrow();
        assertEquals(coneHead.getArmor(), back.getArmor(), "the cone comes back as worn as it was");
    }

    @Test
    void theTilesTheLevelDidNotStartWithComeBackToo() {
        GameSession before = halfPlayedLevel();
        // a lily pad turns a water tile into one anything can stand on
        assertTrue(before.plant("lily-pad", 9, 1).startsWith("Planted"));
        assertEquals(TileTerrain.LILY, before.terrainAt(9, 1));

        GameSession after = roundTrip(before);
        assertEquals(TileTerrain.LILY, after.terrainAt(9, 1), "the pad is still on the water");
    }

    /**
     * A fresh board scatters its own graves, so a resumed Dark Ages level used
     * to come back with the ones it was saved with plus a new handful.
     */
    @Test
    void aResumedLevelHasTheGravesItWasSavedWithAndNoMore() {
        LevelSpec darkAges = Levels.adventure().stream()
                .filter(spec -> spec.getChapter() == Chapter.DARK_AGES && spec.getDay() == 1)
                .findFirst().orElseThrow();
        GameSession before = new GameSession(darkAges, 3, List.of("sunflower"),
                new HashSet<>(), new Random(4));
        before.setWavesEnabled(false);
        int graves = countGraves(before);
        assertTrue(graves > 0, "the Dark Ages start with graves");

        GameSession after = roundTrip(before);
        assertEquals(graves, countGraves(after), "resuming should not raise new graves");
    }

    private static int countGraves(GameSession session) {
        int found = 0;
        for (int x = 1; x <= GameSession.COLS; x++) {
            for (int y = 1; y <= GameSession.ROWS; y++) {
                if (session.terrainAt(x, y) == TileTerrain.GRAVE) {
                    found++;
                }
            }
        }
        return found;
    }

    @Test
    void aResumedLevelCarriesOnRatherThanStartingOver() {
        GameSession after = roundTrip(halfPlayedLevel());
        double resumedAt = after.getElapsedSeconds();
        after.advance(5 * GameSession.TICKS_PER_SECOND);
        assertEquals(resumedAt + 5, after.getElapsedSeconds(), 0.001);
        assertFalse(after.isOver(), "picking it up does not end it");
    }

    /**
     * The command path, not just the model: saving has to leave the menu in a
     * state where the next command does not walk into a session that is gone.
     */
    @Test
    void savingFromInsideAGameLeavesTheMenuUsable() {
        ir.sharif.pvz.controller.GameApp app = harness();
        app.submit("menu enter game");
        app.submit("add plant -t sunflower");
        app.submit("select level -c egypt -d 1");
        app.submit("start game");
        app.submit("cheat add -n 500 suns");
        app.submit("plant plant -t sunflower -l (1, 1)");
        app.submit("advance time -t 50 ticks");
        app.submit("save game");
        // the crash this pins down happened on the command right after saving
        app.submit("show all plants");
        app.submit("resume game");
        app.submit("show map");
        assertTrue(app.getContext().getSavedGames().of("saver") == null,
                "a resumed level is no longer waiting to be resumed");
    }

    private ir.sharif.pvz.controller.GameApp harness() {
        ir.sharif.pvz.model.UserRepository users =
                new ir.sharif.pvz.model.UserRepository(folder.resolve("users.json"));
        ir.sharif.pvz.model.User user = new ir.sharif.pvz.model.User("saver", "h", "S",
                "s@example.com", ir.sharif.pvz.model.Gender.FEMALE);
        users.add(user);
        ir.sharif.pvz.controller.AppContext context = new ir.sharif.pvz.controller.AppContext(
                users, new ir.sharif.pvz.model.SessionStore(folder.resolve("session.txt")),
                new ir.sharif.pvz.model.AuthService(users),
                new ir.sharif.pvz.model.ProfileService(users),
                new SavedGameStore(folder.resolve("saves.json")));
        context.setCurrentUser(user);
        context.setCurrentMenu(ir.sharif.pvz.controller.MenuType.MAIN);
        return new ir.sharif.pvz.controller.GameApp(
                new ir.sharif.pvz.view.ConsoleView(new java.io.PrintStream(
                        java.io.OutputStream.nullOutputStream())), context);
    }

    /**
     * The win/lose screen offers to play the level again. It used to do
     * nothing the "back to the menu" button did not, so this pins down that
     * the command behind it actually starts the same level over.
     */
    @Test
    void tryingAgainStartsTheSameLevelWithTheSameLineUp() {
        ir.sharif.pvz.controller.GameApp app = harness();
        app.submit("menu enter game");
        app.submit("add plant -t sunflower");
        app.submit("add plant -t peashooter");
        app.submit("select level -c egypt -d 2");
        app.submit("start game");
        String title = session(app).getLevel().title();
        app.submit("forfeit level");
        app.submit("advance time -t 1 ticks");

        app.submit("replay level");
        assertNotNull(session(app), "the level should be running again");
        assertEquals(title, session(app).getLevel().title(), "and it is the same level");
        assertEquals(List.of("sunflower", "peashooter"), session(app).getSelectedPlants(),
                "with the plants it was played with");
    }

    @Test
    void thereIsNothingToPlayAgainBeforeAnyLevelHasBeenPlayed() {
        ir.sharif.pvz.controller.GameApp app = harness();
        app.submit("menu enter game");
        app.submit("replay level");
        assertNull(session(app), "nothing starts");
    }

    private GameSession session(ir.sharif.pvz.controller.GameApp app) {
        return ((ir.sharif.pvz.controller.GameMenuController) app.currentController()).getSession();
    }

    // ===== the store =====

    @Test
    void theStoreKeepsOneLevelPerAccountAndForgetsItOnceItIsTakenBack() {
        SavedGameStore store = new SavedGameStore(folder.resolve("saves.json"));
        assertNull(store.of("rose"));
        assertFalse(store.has("rose"));

        store.put("rose", SaveState.capture(halfPlayedLevel()));
        assertTrue(store.has("rose"));
        assertFalse(store.has("vahid"), "one account's save is not another's");

        SavedGameStore reopened = new SavedGameStore(folder.resolve("saves.json"));
        assertNotNull(reopened.of("rose"), "the save should survive a restart");
        assertEquals("BIG_WAVE_BEACH", reopened.of("rose").chapter());

        reopened.clear("rose");
        assertFalse(new SavedGameStore(folder.resolve("saves.json")).has("rose"));
    }

    @Test
    void aSaveNamingALevelTheBuildNoLongerHasIsRefusedRatherThanCrashing() {
        SavedGame saved = SaveState.capture(halfPlayedLevel());
        SavedGame bogus = new SavedGame("ATLANTIS", 1, saved.difficulty(), saved.ticks(),
                saved.selectedPlants(), saved.plantLevels(), saved.boostedPlants(),
                saved.cooldowns(), saved.sun(), saved.plantFood(), saved.coins(),
                saved.diamonds(), saved.pots(), saved.mowers(), saved.seenZombies(),
                saved.plants(), saved.zombies(), saved.suns(), saved.terrain(), saved.wave());
        assertNull(SaveState.levelOf(bogus));
        assertNull(SaveState.restore(bogus, new Random(1)));
        assertTrue(SaveState.describe(bogus).contains("no longer"));
    }
}
