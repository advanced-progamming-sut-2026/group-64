package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The two heavies the sheet asks to see doing more than walking: the
 * gargantuar's hammer and the imp it throws, and the all-star's kick.
 */
class BigZombiesTest {

    private static GameSession lawnWith(String plant, int col, int row) {
        GameSession session = new GameSession(3, List.of(plant), new HashSet<>(), new Random(8));
        session.setWavesEnabled(false);
        session.cheats().addSuns(3000);
        session.cheats().removeCooldown();
        session.plant(plant, col, row);
        return session;
    }

    @Test
    void theGargantuarSmashesWhateverItStandsOver() {
        GameSession session = lawnWith("wall-nut", 5, 3);
        Plant nut = session.plantAtTile(5, 3);
        assertNotNull(nut);
        assertEquals(4000, nut.getHp(), "a wall-nut takes a lot of eating");

        session.cheats().spawnZombie("gargantuar", 5, 3);
        session.advance(4 * GameSession.TICKS_PER_SECOND);

        assertNull(session.plantAtTile(5, 3), "the hammer goes through it in one blow");
    }

    @Test
    void aWoundedGargantuarThrowsItsImpOverYourPlants() {
        GameSession session = lawnWith("wall-nut", 3, 2);
        Zombie giant = session.spawnZombie(GameCatalog.get().zombie("gargantuar"), 1, 7);
        // half its health gone is what makes it let the imp go
        session.hitZombie(giant, giant.getSpec().getHp() / 2 + 1);
        session.advance(4 * GameSession.TICKS_PER_SECOND);

        Zombie imp = session.getZombies().stream()
                .filter(zombie -> zombie.getSpec().getName().equals("imp"))
                .findFirst().orElse(null);
        assertNotNull(imp, "the imp should have been thrown");
        assertEquals(1, imp.getRow(), "it lands in the same lane");
        assertTrue(imp.getX() < giant.getX(), "and ahead of the gargantuar");
    }

    @Test
    void theGargantuarCarriesOnlyOneImp() {
        GameSession session = lawnWith("wall-nut", 3, 2);
        Zombie giant = session.spawnZombie(GameCatalog.get().zombie("gargantuar"), 1, 8);
        session.hitZombie(giant, giant.getSpec().getHp() / 2 + 1);
        session.advance(20 * GameSession.TICKS_PER_SECOND);

        long imps = session.getZombies().stream()
                .filter(zombie -> zombie.getSpec().getName().equals("imp"))
                .count();
        assertTrue(imps <= 1, "it threw " + imps + " imps");
    }

    @Test
    void theAllStarKicksThePlantInFrontOfHim() {
        GameSession session = lawnWith("wall-nut", 5, 4);
        int before = session.plantAtTile(5, 4).getHp();
        session.cheats().spawnZombie("all-star", 5, 4);
        session.advance(5 * GameSession.TICKS_PER_SECOND);

        Plant nut = session.plantAtTile(5, 4);
        assertTrue(nut == null || nut.getHp() < before,
                "the kick takes a good half of it");
        assertFalse(session.drainEvents().stream()
                .noneMatch(event -> event.contains("kicked")), "and says so");
    }
}
