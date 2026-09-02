package ir.sharif.pvz.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores all registered users in a JSON file so accounts survive restarts.
 */
public class UserRepository {

    private static final Path DEFAULT_FILE = Path.of("data", "users.json");

    /**
     * Marker for a repository that keeps nothing on disk, which is what the
     * network-backed subclass passes up.
     */
    protected static final Path NO_FILE = null;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final List<User> users;

    public UserRepository() {
        this(DEFAULT_FILE);
    }

    public UserRepository(Path file) {
        this.file = file;
        this.users = load();
    }

    private List<User> load() {
        if (file == null || !Files.exists(file)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            List<User> loaded = gson.fromJson(reader, new TypeToken<List<User>>() { }.getType());
            return loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read users file: " + file, e);
        }
    }

    public void save() {
        if (file == null) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(file)) {
                gson.toJson(users, writer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write users file: " + file, e);
        }
    }

    public void add(User user) {
        users.add(user);
        save();
    }

    public User findByUsername(String username) {
        for (User user : users) {
            if (user.getUsername().equals(username)) {
                return user;
            }
        }
        return null;
    }

    public boolean usernameExists(String username) {
        return findByUsername(username) != null;
    }

    /**
     * Swaps the stored copy of an account for a newer one, matched by username.
     * The server uses this when a client sends its progress up.
     */
    public void replace(User updated) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getUsername().equals(updated.getUsername())) {
                users.set(i, updated);
                return;
            }
        }
        users.add(updated);
    }

    /**
     * Replaces the account stored under an old username with a renamed one.
     */
    public void rename(String previousUsername, User updated) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getUsername().equals(previousUsername)) {
                users.set(i, updated);
                return;
            }
        }
        users.add(updated);
    }

    public List<User> all() {
        return List.copyOf(users);
    }
}
