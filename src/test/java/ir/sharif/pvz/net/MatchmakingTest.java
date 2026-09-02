package ir.sharif.pvz.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ir.sharif.pvz.model.RegisterRequest;
import ir.sharif.pvz.model.net.RemoteAuthService;
import ir.sharif.pvz.model.net.RemoteUserRepository;
import ir.sharif.pvz.net.client.ServerConnection;
import ir.sharif.pvz.net.client.ServerException;
import ir.sharif.pvz.net.server.PvzServer;
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
 * Choosing an opponent, both ways the document asks for: by name, and by
 * dropping into a queue for whoever turns up.
 */
class MatchmakingTest {

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

    /**
     * A signed-in client, with a queue collecting one kind of push.
     */
    private final class Player implements AutoCloseable {
        private final ServerConnection link;
        private final BlockingQueue<Message> invites = new ArrayBlockingQueue<>(8);
        private final BlockingQueue<Message> matches = new ArrayBlockingQueue<>(8);
        private final BlockingQueue<Message> declines = new ArrayBlockingQueue<>(8);

        Player(String name) throws IOException {
            link = new ServerConnection("localhost", server.port());
            link.on(Protocol.INVITED, invites::offer);
            link.on(Protocol.MATCH_FOUND, matches::offer);
            link.on(Protocol.INVITE_DECLINED, declines::offer);
            RemoteUserRepository users = new RemoteUserRepository(link);
            RemoteAuthService auth = new RemoteAuthService(link, users);
            auth.register(new RegisterRequest(name, "Aa1!aaaa", "Aa1!aaaa",
                    "Nick", name + "@example.com", "female"), 1, "green");
            try {
                auth.login(name, "Aa1!aaaa");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        ServerConnection link() {
            return link;
        }

        Message waitFor(BlockingQueue<Message> queue) throws InterruptedException {
            Message message = queue.poll(5, TimeUnit.SECONDS);
            assertNotNull(message, "expected a push that never arrived");
            return message;
        }

        @Override
        public void close() {
            link.close();
        }
    }

    @Test
    void invitingSomebodyWhoDoesNotExistIsRefused() throws Exception {
        try (Player rose = new Player("rose")) {
            ServerException problem = assertThrows(ServerException.class,
                    () -> rose.link().ask(rose.link().request(Protocol.INVITE).with("to", "nobody")));
            assertTrue(problem.getMessage().contains("no player called"), problem.getMessage());
        }
    }

    @Test
    void invitingSomebodyOfflineIsRefused() throws Exception {
        try (Player rose = new Player("rose2")) {
            // vahid registers, then goes away again
            try (Player vahid = new Player("vahid2")) {
                assertNotNull(vahid);
            }
            Thread.sleep(200);
            ServerException problem = assertThrows(ServerException.class,
                    () -> rose.link().ask(rose.link().request(Protocol.INVITE).with("to", "vahid2")));
            assertTrue(problem.getMessage().contains("not online"), problem.getMessage());
        }
    }

    @Test
    void anInviteReachesTheOtherPlayerAndStartsAGameWhenAccepted() throws Exception {
        try (Player rose = new Player("rose3"); Player vahid = new Player("vahid3")) {
            rose.link().ask(rose.link().request(Protocol.INVITE).with("to", "vahid3"));

            Message invite = vahid.waitFor(vahid.invites);
            assertEquals("rose3", invite.text("from"));

            vahid.link().ask(vahid.link().request(Protocol.INVITE_ANSWER).with("accepted", true));

            Message forRose = rose.waitFor(rose.matches);
            Message forVahid = vahid.waitFor(vahid.matches);
            assertEquals(forRose.text("match"), forVahid.text("match"), "same game");
            assertNotEquals(forRose.text("role"), forVahid.text("role"), "opposite sides");
            assertEquals("vahid3", forRose.text("opponent"));
            assertEquals("rose3", forVahid.text("opponent"));
        }
    }

    @Test
    void decliningTellsTheInviterAndStartsNothing() throws Exception {
        try (Player rose = new Player("rose4"); Player vahid = new Player("vahid4")) {
            rose.link().ask(rose.link().request(Protocol.INVITE).with("to", "vahid4"));
            vahid.waitFor(vahid.invites);

            vahid.link().ask(vahid.link().request(Protocol.INVITE_ANSWER).with("accepted", false));

            assertEquals("vahid4", rose.waitFor(rose.declines).text("from"));
            assertTrue(rose.matches.isEmpty(), "no game should have started");
        }
    }

    @Test
    void theFirstIntoTheQueueWaitsAndTheSecondStartsTheGame() throws Exception {
        try (Player rose = new Player("rose5"); Player vahid = new Player("vahid5")) {
            Message first = rose.link().ask(rose.link().request(Protocol.QUEUE_JOIN));
            assertTrue(first.flag("waiting"), "the first player waits");
            assertTrue(rose.matches.isEmpty());

            Message second = vahid.link().ask(vahid.link().request(Protocol.QUEUE_JOIN));
            assertFalse(second.flag("waiting"), "the second player is paired at once");

            assertEquals(rose.waitFor(rose.matches).text("match"),
                    vahid.waitFor(vahid.matches).text("match"));
        }
    }

    @Test
    void leavingHandsTheGameToTheOtherPlayer() throws Exception {
        BlockingQueue<Message> over = new ArrayBlockingQueue<>(4);
        try (Player rose = new Player("rose6"); Player vahid = new Player("vahid6")) {
            vahid.link().on(Protocol.MATCH_OVER, over::offer);
            rose.link().ask(rose.link().request(Protocol.QUEUE_JOIN));
            vahid.link().ask(vahid.link().request(Protocol.QUEUE_JOIN));
            vahid.waitFor(vahid.matches);

            rose.link().ask(rose.link().request(Protocol.MATCH_LEAVE));

            Message finish = over.poll(5, TimeUnit.SECONDS);
            assertNotNull(finish, "the remaining player should be told");
            assertEquals("vahid6", finish.text("winner"));
        }
    }
}
