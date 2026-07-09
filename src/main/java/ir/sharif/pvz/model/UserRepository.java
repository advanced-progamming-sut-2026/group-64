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
        if (!Files.exists(file)) {
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

    public List<User> all() {
        return List.copyOf(users);
    }
}
