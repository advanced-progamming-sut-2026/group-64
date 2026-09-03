package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Plant food from a glowing zombie. The document asks the player to pick it
 * up; it used to drop straight into the bar without them touching it.
 */
class PlantFoodDropTest {

    /**
     * Which zombies glow is decided at random as waves spawn, so rather than
     * playing until one turns up this puts one on the lawn directly.
     */
    private static GameSession sessionWithAGlowingZombie() {
        GameSession session = new GameSession(3, List.of("peashooter"), new HashSet<>(),
                new Random(3));
        session.setWavesEnabled(false);
        ZombieSpec spec = GameCatalog.get().zombie("normal");
        session.zombies.add(new Zombie(spec, 2, 7, spec.getHp(), java.util.Map.of(), true));
        return session;
    }

    @Test
    void aGlowingZombieLeavesItsPlantFoodOnTheLawn() {
        GameSession session = sessionWithAGlowingZombie();
        Zombie glowing = session.getZombies().stream()
                .filter(Zombie::isGlowing).findFirst().orElseThrow();
        int row = glowing.getRow();
        int had = session.getPlantFood();
        assertTrue(session.getDroppedPlantFood().isEmpty(), "nothing dropped yet");

        session.slayZombie(glowing);

        assertEquals(had, session.getPlantFood(), "it does not go straight into the bar");
        assertEquals(1, session.getDroppedPlantFood().size(), "it is lying on the lawn");
        assertEquals(row + 1, session.getDroppedPlantFood().get(0)[1], "in its own lane");
    }

    @Test
    void pickingItUpIsWhatPutsItInTheBar() {
        GameSession session = sessionWithAGlowingZombie();
        Zombie glowing = session.getZombies().stream()
                .filter(Zombie::isGlowing).findFirst().orElseThrow();
        int had = session.getPlantFood();
        session.slayZombie(glowing);
        int[] where = session.getDroppedPlantFood().get(0);

        assertTrue(session.collectPlantFood(where[0], where[1]).contains("Picked up"));
        assertEquals(had + 1, session.getPlantFood());
        assertTrue(session.getDroppedPlantFood().isEmpty(), "and it is gone from the lawn");
    }

    @Test
    void thereIsNothingToPickUpOnAnEmptyTile() {
        GameSession session = sessionWithAGlowingZombie();
        assertTrue(session.collectPlantFood(4, 3).startsWith("Error"));
        assertFalse(session.collectPlantFood(4, 3).contains("Picked up"));
    }
}
