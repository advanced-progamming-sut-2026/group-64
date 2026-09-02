package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The graphical view draws minigames entirely off the contract on
 * {@link MinigameLogic}, so each game has to report what it puts on the lawn
 * and what belongs in the card bar.
 */
class MinigameViewTest {

    private GameSession start(String name) {
        return Minigames.start(name, 1, 3, List.of("bowling-wallnut"), new Random(5));
    }

    @Test
    void vasebreakerReportsEveryVaseOnTheLawn() {
        GameSession session = start("vasebreaker");
        List<MinigameProp> props = session.getMinigame().props();

        assertFalse(props.isEmpty(), "the yard starts stacked with vases");
        for (MinigameProp prop : props) {
            assertEquals("vase", prop.art());
            assertTrue(prop.col() >= 1 && prop.col() <= GameSession.COLS, "vase sits on the board");
            assertTrue(prop.row() >= 1 && prop.row() <= GameSession.ROWS, "vase sits on the board");
        }
        assertTrue(props.stream().anyMatch(p -> p.kind().equals("plant")),
                "one of the vases holds a plant");
    }

    @Test
    void aSmashedVaseDropsAPacketThatCanBePickedUp() {
        GameSession session = start("vasebreaker");
        assertTrue(session.getMinigame().freePlantMode(), "vase plants cost nothing");

        for (MinigameProp vase : List.copyOf(session.getMinigame().props())) {
            session.breakVase((int) vase.col(), (int) vase.row());
        }

        // the plant vase leaves a packet lying on the lawn rather than going
        // straight into the hand, and the view has to draw it so it can be taken
        MinigameProp packet = session.getMinigame().props().stream()
                .filter(prop -> prop.kind().equals("packet"))
                .findFirst().orElse(null);
        assertNotNull(packet, "the plant vase should leave a packet on the ground");

        session.takePacket((int) packet.col(), (int) packet.row());
        assertFalse(session.getMinigame().handContents().isEmpty(),
                "picking the packet up should hand the player something to plant");
    }

    @Test
    void vasesLeaveTheLawnOnceSmashed() {
        GameSession session = start("vasebreaker");
        MinigameProp first = session.getMinigame().props().get(0);
        int before = session.getMinigame().props().size();

        session.breakVase((int) first.col(), (int) first.row());

        assertEquals(before - 1, session.getMinigame().props().size());
    }

    @Test
    void iZombieOffersZombiesInsteadOfSeedPackets() {
        GameSession session = start("i-zombie");
        var cards = session.getMinigame().cardsInsteadOfPlants();

        assertFalse(cards.isEmpty(), "the player is dealt zombies");
        cards.forEach((type, price) -> {
            assertNotNull(GameCatalog.get().zombie(type), type + " should be a real zombie");
            assertTrue(price > 0, type + " should cost something");
        });
        assertTrue(session.getMinigame().restrictedColumn() > 0,
                "there is a line zombies may not be placed across");
    }

    @Test
    void bowlingDealsItsNutsOnABeltAndRollsWhatIsPlanted() {
        GameSession session = start("bowling");
        assertTrue(session.getMinigame().props().isEmpty(), "nothing rolls before a nut is bowled");
        // bowling runs on the conveyor belt, so the card bar reads the belt
        assertTrue(session.isConveyorLevel(), "bowling deals its nuts from a belt");
        assertFalse(session.conveyorBelt().isEmpty(), "the belt should start with a nut on it");
        String nut = session.conveyorBelt().get(0);

        session.plant(nut, 1, 1);
        List<MinigameProp> rolling = session.getMinigame().props();
        assertEquals(1, rolling.size(), "the bowled nut should start rolling");
        assertEquals("nut", rolling.get(0).kind());
        assertEquals(nut, rolling.get(0).art());

        double startedAt = rolling.get(0).col();
        session.advance(GameSession.TICKS_PER_SECOND);
        assertTrue(session.getMinigame().props().get(0).col() > startedAt,
                "and keep rolling down its lane");
    }
}
