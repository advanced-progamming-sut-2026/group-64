package ir.sharif.pvz.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ir.sharif.pvz.model.RegisterRequest;
import ir.sharif.pvz.model.net.RemoteAuthService;
import ir.sharif.pvz.model.net.RemoteUserRepository;
import ir.sharif.pvz.net.client.ServerConnection;
import ir.sharif.pvz.net.server.PvzServer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The request/reply plumbing itself, rather than any one feature built on it.
 *
 * <p>These came out of a real bug: {@code ask} used to park its reply in a
 * queue that only hands a message to a consumer already waiting for it, so a
 * reply that got back before the caller reached its wait was dropped and the
 * caller then sat out the whole ten-second timeout. It surfaced as the odd
 * network test failing with "the server did not answer in time".
 *
 * <p>The window is narrow — it needs the caller to be descheduled between
 * sending and waiting — so these are a stress check rather than a proof: they
 * caught the bug on a busy machine and never on an idle one. What actually
 * guarantees the fix is the pigeonhole having room for one message, which the
 * field comment in {@code ServerConnection} spells out.
 */
class ServerConnectionTest {

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

    private ServerConnection connect() throws IOException {
        return new ServerConnection("localhost", server.port());
    }

    private static Set<String> matchThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .map(Thread::getName)
                .filter(name -> name.startsWith("pvz-match-"))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Closing the server has to stop the games it is running. It used to leave
     * each match's loop thread behind, ticking a board nobody was watching and
     * serialising it ten times a second for as long as the process lived — a
     * leak on a real server, and enough noise in a test run to push the other
     * network tests over their timeout.
     */
    @Test
    void closingTheServerStopsTheGamesItWasRunning() throws Exception {
        Set<String> before = matchThreads();
        try (ServerConnection one = signedIn("alpha"); ServerConnection two = signedIn("beta")) {
            one.ask(one.request(Protocol.QUEUE_JOIN));
            two.ask(two.request(Protocol.QUEUE_JOIN));
            Set<String> playing = new TreeSet<>(matchThreads());
            playing.removeAll(before);
            assertFalse(playing.isEmpty(), "the match should be running while both players are in");

            // the players walk out without hanging up, the way a dropped
            // connection or a killed client does
            server.close();

            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                Set<String> left = new TreeSet<>(matchThreads());
                left.removeAll(before);
                if (left.isEmpty()) {
                    return;
                }
                Thread.sleep(50);
            }
            Set<String> left = new TreeSet<>(matchThreads());
            left.removeAll(before);
            assertTrue(left.isEmpty(), "these match threads outlived the server: " + left);
        }
    }

    private ServerConnection signedIn(String name) throws Exception {
        ServerConnection link = connect();
        RemoteUserRepository users = new RemoteUserRepository(link);
        RemoteAuthService auth = new RemoteAuthService(link, users);
        auth.register(new RegisterRequest(name, "Aa1!aaaa", "Aa1!aaaa",
                "Nick", name + "@example.com", "female"), 1, "green");
        auth.login(name, "Aa1!aaaa");
        return link;
    }

    @Test
    void everyReplyComesBackMatchedToItsOwnRequest() throws Exception {
        try (ServerConnection link = connect()) {
            for (int i = 0; i < 200; i++) {
                Message request = link.request(Protocol.USERNAME_TAKEN).with("username", "nobody" + i);
                Message reply = link.ask(request);
                assertEquals(request.getId(), reply.getId(), "reply " + i + " came back mismatched");
                assertTrue(reply.isOk());
            }
        }
    }

    /**
     * Several callers sharing one link, so replies come off the socket in an
     * order that has nothing to do with the order the calls went out in.
     */
    @Test
    void repliesFindTheRightCallerWhenSeveralAreWaitingAtOnce() throws Exception {
        int callers = 8;
        int each = 40;
        try (ServerConnection link = connect()) {
            CountDownLatch go = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(callers);
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
            for (int c = 0; c < callers; c++) {
                Thread thread = new Thread(() -> {
                    try {
                        go.await();
                        for (int i = 0; i < each; i++) {
                            Message request = link.request(Protocol.USERNAME_TAKEN)
                                    .with("username", Thread.currentThread().getName() + i);
                            Message reply = link.ask(request);
                            if (reply.getId() != request.getId()) {
                                failures.add(new AssertionError("a reply went to the wrong caller"));
                            }
                        }
                    } catch (Throwable problem) {
                        failures.add(problem);
                    } finally {
                        done.countDown();
                    }
                }, "caller-" + c);
                thread.setDaemon(true);
                thread.start();
            }
            go.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS), "the callers should all finish");
            assertTrue(failures.isEmpty(), "failures: " + failures);
        }
    }
}
