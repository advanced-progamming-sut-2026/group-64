package ir.sharif.pvz.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * The whole account system now lives on the server, so these run a real server
 * on a real socket and drive it through the same classes the game uses.
 */
class AccountsOverNetworkTest {

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

    private RegisterRequest form(String username) {
        return new RegisterRequest(username, "Aa1!aaaa", "Aa1!aaaa",
                "Nick", username + "@example.com", "female");
    }

    private User signUp(ServerConnection link, String username) {
        RemoteUserRepository users = new RemoteUserRepository(link);
        RemoteAuthService auth = new RemoteAuthService(link, users);
        assertTrue(auth.validateRegistration(form(username)).isEmpty(), "the form should be valid");
        return auth.register(form(username), 1, "green");
    }

    @Test
    void anAccountCreatedOnOneClientCanSignInFromAnother() throws Exception {
        try (ServerConnection first = connect()) {
            signUp(first, "rose");
        }
        // a different machine entirely: nothing was kept on the first one
        try (ServerConnection second = connect()) {
            RemoteUserRepository users = new RemoteUserRepository(second);
            RemoteAuthService auth = new RemoteAuthService(second, users);
            assertEquals("rose", auth.login("rose", "Aa1!aaaa").getUsername());
        }
    }

    @Test
    void progressFollowsTheAccountRatherThanTheDevice() throws Exception {
        try (ServerConnection first = connect()) {
            RemoteUserRepository users = new RemoteUserRepository(first);
            new RemoteAuthService(first, users);
            User user = signUp(first, "vahid");
            users.track(user);
            user.addCoins(750);
            user.addDiamonds(9);
            user.setLevelsPassed(6);
            users.save();
        }
        try (ServerConnection second = connect()) {
            RemoteUserRepository users = new RemoteUserRepository(second);
            User elsewhere = new RemoteAuthService(second, users).login("vahid", "Aa1!aaaa");
            assertEquals(750, elsewhere.getCoins(), "coins should follow the account");
            assertEquals(9, elsewhere.getDiamonds(), "diamonds should follow the account");
            assertEquals(6, elsewhere.getLevelsPassed());
        }
    }

    @Test
    void theServerRefusesADuplicateUsername() throws Exception {
        try (ServerConnection first = connect(); ServerConnection second = connect()) {
            signUp(first, "taken");

            RemoteUserRepository users = new RemoteUserRepository(second);
            RemoteAuthService auth = new RemoteAuthService(second, users);
            assertTrue(auth.validateRegistration(form("taken")).stream()
                            .anyMatch(problem -> problem.contains("already exists")),
                    "a second client should be told the name is gone");
        }
    }

    @Test
    void aWrongPasswordIsRejected() throws Exception {
        try (ServerConnection link = connect()) {
            signUp(link, "gate");
            RemoteUserRepository users = new RemoteUserRepository(link);
            RemoteAuthService auth = new RemoteAuthService(link, users);
            assertThrows(AuthException.class, () -> auth.login("gate", "wrong-password"));
        }
    }

    @Test
    void signingInAsSomebodyWhoDoesNotExistIsRejected() throws Exception {
        try (ServerConnection link = connect()) {
            RemoteUserRepository users = new RemoteUserRepository(link);
            RemoteAuthService auth = new RemoteAuthService(link, users);
            assertThrows(AuthException.class, () -> auth.login("ghost", "Aa1!aaaa"));
        }
    }

    @Test
    void theLeaderboardReadsEveryAccountFromTheServer() throws Exception {
        try (ServerConnection first = connect(); ServerConnection second = connect()) {
            signUp(first, "alpha");
            signUp(second, "beta");

            RemoteUserRepository users = new RemoteUserRepository(first);
            assertEquals(2, users.all().size(), "both accounts should come back");
        }
    }

    @Test
    void aRefusedRequestSurfacesAsAServerException() throws Exception {
        try (ServerConnection link = connect()) {
            assertThrows(ServerException.class,
                    () -> link.ask(link.request("nonsense-request")));
        }
    }
}
