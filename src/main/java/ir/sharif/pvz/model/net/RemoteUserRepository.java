package ir.sharif.pvz.model.net;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.UserRepository;
import ir.sharif.pvz.net.Message;
import ir.sharif.pvz.net.Protocol;
import ir.sharif.pvz.net.client.ServerConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * A user store that lives on the server.
 *
 * <p>It extends the file-backed repository so every controller written in
 * phase 1 keeps working untouched; only where the data comes from changes. The
 * signed-in account is cached so the many {@code save()} calls scattered
 * through the menus each cost one small message rather than a round trip for
 * every read.
 */
public final class RemoteUserRepository extends UserRepository {

    private static final Gson GSON = new Gson();

    private final ServerConnection connection;

    private User current;
    private String syncedAs;

    public RemoteUserRepository(ServerConnection connection) {
        super(NO_FILE);
        this.connection = connection;
    }

    /**
     * Keeps the signed-in account so it can be pushed up on every save.
     */
    public void track(User user) {
        this.current = user;
        this.syncedAs = user == null ? null : user.getUsername();
    }

    @Override
    public void save() {
        if (current == null) {
            return;
        }
        // the profile menu can rename the account, so the server is told which
        // record to overwrite rather than matching on the new name and
        // inserting a duplicate
        connection.ask(connection.request(Protocol.SAVE_USER)
                .with("previous", syncedAs)
                .with("user", GSON.toJsonTree(current)));
        syncedAs = current.getUsername();
    }

    @Override
    public void add(User user) {
        track(user);
        save();
    }

    @Override
    public User findByUsername(String username) {
        if (current != null && current.getUsername().equals(username)) {
            return current;
        }
        Message reply = connection.ask(
                connection.request(Protocol.FIND_USER).with("username", username));
        JsonElement user = reply.getData().get("user");
        return user == null || user.isJsonNull() ? null : GSON.fromJson(user, User.class);
    }

    @Override
    public boolean usernameExists(String username) {
        return connection.ask(connection.request(Protocol.USERNAME_TAKEN)
                .with("username", username)).flag("taken");
    }

    /**
     * Every account, which is what the leaderboard is built from.
     */
    @Override
    public List<User> all() {
        Message reply = connection.ask(connection.request(Protocol.ALL_USERS));
        JsonElement users = reply.getData().get("users");
        List<User> result = new ArrayList<>();
        if (users != null && users.isJsonArray()) {
            users.getAsJsonArray().forEach(element -> result.add(GSON.fromJson(element, User.class)));
        }
        return result;
    }
}
