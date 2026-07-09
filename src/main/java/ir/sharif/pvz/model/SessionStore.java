package ir.sharif.pvz.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Remembers the username of a user who logged in with the stay-logged-in flag,
 * so the app can skip the login menu on the next run.
 */
public class SessionStore {

    private static final Path DEFAULT_FILE = Path.of("data", "session.txt");

    private final Path file;

    public SessionStore() {
        this(DEFAULT_FILE);
    }

    public SessionStore(Path file) {
        this.file = file;
    }

    public void save(String username) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, username);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write session file: " + file, e);
        }
    }

    /**
     * Returns the saved username, or null if no session is stored.
     */
    public String load() {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String username = Files.readString(file).trim();
            return username.isEmpty() ? null : username;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read session file: " + file, e);
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete session file: " + file, e);
        }
    }
}
