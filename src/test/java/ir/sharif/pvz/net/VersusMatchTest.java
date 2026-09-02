package ir.sharif.pvz.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import ir.sharif.pvz.model.RegisterRequest;
import ir.sharif.pvz.model.net.RemoteAuthService;
import ir.sharif.pvz.model.net.RemoteUserRepository;
import ir.sharif.pvz.net.client.ServerConnection;
import ir.sharif.pvz.net.server.PvzServer;
import ir.sharif.pvz.model.AuthException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The versus round runs on the server and both clients only draw what it
 * sends, so these check that two real connections end up looking at the same
 * board and that each player may only move their own side.
 */
class VersusMatchTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path folder;

    private PvzServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new PvzServer(0, folder.resolve("users.json"));
        server.serveInBackground();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    /** A signed-in client with its pushes collected. */
    private final class Player implements AutoCloseable {
        private final ServerConnection link;
        private final BlockingQueue<Message> matches = new ArrayBlockingQueue<>(8);
        private final BlockingQueue<Message> states = new ArrayBlockingQueue<>(64);
        private final BlockingQueue<Message> reactions = new ArrayBlockingQueue<>(8);
        private String role;

        Player(String name) throws IOException, AuthException {
            link = new ServerConnection("localhost", server.port());
            link.on(Protocol.MATCH_FOUND, message -> {
                role = message.text("role");
                matches.offer(message);
            });
            link.on(Protocol.MATCH_STATE, states::offer);
            link.on(Protocol.REACTION, reactions::offer);

            RemoteUserRepository users = new RemoteUserRepository(link);
            RemoteAuthService auth = new RemoteAuthService(link, users);
            auth.register(new RegisterRequest(name, "Aa1!aaaa", "Aa1!aaaa",
                    "Nick", name + "@example.com", "female"), 1, "green");
            auth.login(name, "Aa1!aaaa");
        }

        Snapshot nextBoard() throws InterruptedException {
            Message message = states.poll(5, TimeUnit.SECONDS);
            assertNotNull(message, "a board should arrive");
            return GSON.fromJson(message.getData().get("state"), Snapshot.class);
        }

        @Override
        public void close() {
            link.close();
        }
    }

    private void pair(Player one, Player two) throws Exception {
        one.link.ask(one.link.request(Protocol.QUEUE_JOIN));
        two.link.ask(two.link.request(Protocol.QUEUE_JOIN));
        assertNotNull(one.matches.poll(5, TimeUnit.SECONDS), "both should be matched");
        assertNotNull(two.matches.poll(5, TimeUnit.SECONDS), "both should be matched");
    }

    @Test
    void thetwoPlayersAreGivenOppositeSides() throws Exception {
        try (Player one = new Player("side1"); Player two = new Player("side2")) {
            pair(one, two);
            assertNotEquals(one.role, two.role, "one grows plants, the other sends zombies");
        }
    }

    @Test
    void theServerKeepsSendingTheBoardToBothPlayers() throws Exception {
        try (Player one = new Player("board1"); Player two = new Player("board2")) {
            pair(one, two);

            Snapshot first = one.nextBoard();
            Snapshot second = two.nextBoard();
            assertEquals(5, first.brains().size(), "five brains to defend");
            assertEquals(first.roundSeconds(), second.roundSeconds(),
                    "both sides are playing the same round");
            assertTrue(first.time() >= 0);
        }
    }

    @Test
    void aPlantPlacedByThePlantSideShowsUpOnBothScreens() throws Exception {
        try (Player one = new Player("plant1"); Player two = new Player("plant2")) {
            pair(one, two);
            Player plants = "plants".equals(one.role) ? one : two;
            Player zombies = plants == one ? two : one;

            plants.link.ask(plants.link.request(Protocol.MATCH_ACTION)
                    .with("plant", "sunflower").with("col", 2).with("row", 3));

            assertTrue(waitForPlant(plants), "the planter should see it");
            assertTrue(waitForPlant(zombies), "and so should the opponent");
        }
    }

    private boolean waitForPlant(Player player) throws InterruptedException {
        for (int i = 0; i < 25; i++) {
            if (!player.nextBoard().plants().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Test
    void theZombieSideCannotGrowPlants() throws Exception {
        try (Player one = new Player("guard1"); Player two = new Player("guard2")) {
            pair(one, two);
            Player zombies = "zombies".equals(one.role) ? one : two;

            zombies.link.ask(zombies.link.request(Protocol.MATCH_ACTION)
                    .with("plant", "sunflower").with("col", 2).with("row", 3));

            // the action is read as a zombie placement, so no plant appears
            for (int i = 0; i < 12; i++) {
                assertTrue(zombies.nextBoard().plants().isEmpty(),
                        "the zombie player must not be able to grow plants");
            }
        }
    }

    @Test
    void aReactionReachesOnlyTheOpponent() throws Exception {
        try (Player one = new Player("react1"); Player two = new Player("react2")) {
            pair(one, two);

            one.link.ask(one.link.request(Protocol.REACTION)
                    .with("kind", "emoji").with("value", "😄"));

            Message got = two.reactions.poll(5, TimeUnit.SECONDS);
            assertNotNull(got, "the opponent should receive it");
            assertEquals("😄", got.text("value"));
            assertEquals("react1", got.text("from"));
            assertTrue(one.reactions.isEmpty(), "the sender should not get their own back");
        }
    }
}
