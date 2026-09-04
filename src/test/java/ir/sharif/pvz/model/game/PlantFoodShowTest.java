package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The plant food animation, from the model's side: a plant that has just been
 * fed puts on a show, and the show says which plant and which family so the
 * view can draw that plant's own thing rather than one flash for all of them.
 */
class PlantFoodShowTest {

    private static GameSession lawnWith(String plant, int col, int row) {
        GameSession session = new GameSession(3, List.of(plant), new HashSet<>(),
                new Random(12));
        session.setWavesEnabled(false);
        session.cheats().addSuns(3000);
        session.cheats().removeCooldown();
        session.plant(plant, col, row);
        session.cheats().addPlantFood();
        return session;
    }

    @Test
    void feedingAPlantStartsItsOwnShow() {
        GameSession session = lawnWith("sunflower", 2, 3);
        assertTrue(session.getPlantFoodShows().isEmpty(), "nothing is showing off yet");

        session.feedPlant(2, 3);

        assertEquals(1, session.getPlantFoodShows().size());
        PlantFoodShow show = session.getPlantFoodShows().get(0);
        assertEquals("sunflower", show.getPlant());
        assertEquals(PlantCategory.SUN_PRODUCER, show.getFamily(),
                "the family is what the show is built around");
        assertEquals(2, show.getCol());
        assertEquals(3, show.getRow());
    }

    @Test
    void theShowRunsItsCourseAndClearsItself() {
        GameSession session = lawnWith("wall-nut", 4, 2);
        session.feedPlant(4, 2);
        PlantFoodShow show = session.getPlantFoodShows().get(0);
        assertEquals(0, show.progress(), 0.001, "it starts at the beginning");

        session.advance(7 * GameSession.TICKS_PER_SECOND / 10);
        assertTrue(show.progress() > 0.4 && show.progress() < 0.6,
                "half way through: " + show.progress());
        assertFalse(session.getPlantFoodShows().isEmpty());

        session.advance(2 * GameSession.TICKS_PER_SECOND);
        assertTrue(session.getPlantFoodShows().isEmpty(), "and then it is over");
    }

    @Test
    void everyFamilyGetsAShowOfItsOwn() {
        for (String plant : List.of("sunflower", "peashooter", "wall-nut", "bonk-choy",
                "torchwood", "cabbage-pult")) {
            GameSession session = lawnWith(plant, 3, 3);
            session.feedPlant(3, 3);
            List<PlantFoodShow> shows = session.getPlantFoodShows();
            assertEquals(1, shows.size(), plant + " should put on a show");
            assertEquals(GameCatalog.get().plant(plant).getCategory(), shows.get(0).getFamily(),
                    plant + " shows off as its own family");
        }
    }

    /**
     * A shooter's plant food rakes the lane. The volley is recorded as shots so
     * there is something to see, rather than damage landing out of nowhere.
     */
    @Test
    void aShootersPlantFoodPutsAVolleyInTheAir() {
        GameSession session = lawnWith("peashooter", 2, 3);
        session.spawnZombie(GameCatalog.get().zombie("normal"), 2, 7);
        session.drainEvents();
        assertTrue(session.getShots().isEmpty());

        session.feedPlant(2, 3);

        assertTrue(session.getShots().size() >= 5,
                "the barrage should be visible: " + session.getShots().size());
    }

    /**
     * A boosted plant is fed the moment it lands, so it shows off then too.
     */
    @Test
    void aBoostedPlantShowsOffAsItArrives() {
        GameSession session = new GameSession(3, List.of("sunflower"),
                new HashSet<>(java.util.Set.of("sunflower")), new Random(5));
        session.setWavesEnabled(false);
        session.cheats().addSuns(3000);
        session.plant("sunflower", 1, 1);
        assertEquals(1, session.getPlantFoodShows().size(),
                "the boost feeds it on arrival, so the show runs then");
    }
}
