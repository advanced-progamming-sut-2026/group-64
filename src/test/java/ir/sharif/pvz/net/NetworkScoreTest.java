package ir.sharif.pvz.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ir.sharif.pvz.model.AuthException;
import ir.sharif.pvz.model.RegisterRequest;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.net.RemoteAuthService;
import ir.sharif.pvz.model.net.RemoteUserRepository;
import ir.sharif.pvz.net.client.ServerConnection;
import ir.sharif.pvz.net.client.ServerException;
import ir.sharif.pvz.net.server.PvzServer;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The leaderboard's "My Point" column is fed by scores posted to the server,
 * and the document is explicit that a player who has never played online must
 * not show a score there.
 */
class NetworkScoreTest {

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

    private User signUp(ServerConnection link, String name) throws AuthException {
        RemoteUserRepository users = new RemoteUserRepository(link);
        RemoteAuthService auth = new RemoteAuthService(link, users);
        auth.register(new RegisterRequest(name, "Aa1!aaaa", "Aa1!aaaa",
                "Nick", name + "@example.com", "female"), 1, "green");
        return auth.login(name, "Aa1!aaaa");
    }

    @Test
    void aFreshAccountHasNoOnlineScoreAtAll() throws Exception {
        try (ServerConnection link = connect()) {
            assertNull(signUp(link, "fresh").getNetworkPoints(),
                    "somebody who never played online has no score to show");
        }
    }

    @Test
    void postingAScoreRecordsIt() throws Exception {
        try (ServerConnection link = connect()) {
            signUp(link, "scorer");
            link.ask(link.request(Protocol.SUBMIT_SCORE).with("points", 420));

            RemoteUserRepository users = new RemoteUserRepository(link);
            assertEquals(420, users.findByUsername("scorer").getNetworkPoints());
        }
    }

    @Test
    void onlyABetterScoreReplacesTheRecord() throws Exception {
        try (ServerConnection link = connect()) {
            signUp(link, "record");
            link.ask(link.request(Protocol.SUBMIT_SCORE).with("points", 500));
            link.ask(link.request(Protocol.SUBMIT_SCORE).with("points", 120));

            RemoteUserRepository users = new RemoteUserRepository(link);
            assertEquals(500, users.findByUsername("record").getNetworkPoints(),
                    "a worse round must not overwrite the record");

            link.ask(link.request(Protocol.SUBMIT_SCORE).with("points", 900));
            assertEquals(900, users.findByUsername("record").getNetworkPoints());
        }
    }

    @Test
    void theRecordFollowsTheAccountToAnotherDevice() throws Exception {
        try (ServerConnection first = connect()) {
            signUp(first, "travel");
            first.ask(first.request(Protocol.SUBMIT_SCORE).with("points", 333));
        }
        try (ServerConnection second = connect()) {
            RemoteUserRepository users = new RemoteUserRepository(second);
            User elsewhere = new RemoteAuthService(second, users).login("travel", "Aa1!aaaa");
            assertEquals(333, elsewhere.getNetworkPoints());
        }
    }

    @Test
    void aScoreCannotBePostedWithoutSigningIn() throws Exception {
        try (ServerConnection link = connect()) {
            assertThrows(ServerException.class,
                    () -> link.ask(link.request(Protocol.SUBMIT_SCORE).with("points", 999)));
        }
    }
}
