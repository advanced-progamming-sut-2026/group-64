package ir.sharif.pvz.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import ir.sharif.pvz.model.game.SavedGame;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The levels players walked away from, one per account, in a JSON file beside
 * the accounts themselves.
 *
 * <p>It is deliberately its own file rather than a field on {@link User}: a
 * suspended level is bulky, it belongs to this machine rather than to the
 * account, and losing it should never cost anybody their profile.
 */
public class SavedGameStore {

    private static final Path DEFAULT_FILE = Path.of("data", "saves.json");

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Map<String, SavedGame> saves;

    public SavedGameStore() {
        this(DEFAULT_FILE);
    }

    public SavedGameStore(Path file) {
        this.file = file;
        this.saves = load();
    }

    private Map<String, SavedGame> load() {
        if (file == null || !Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, SavedGame> loaded = gson.fromJson(reader,
                    new TypeToken<Map<String, SavedGame>>() { }.getType());
            return loaded == null ? new LinkedHashMap<>() : new LinkedHashMap<>(loaded);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read saved games: " + file, e);
        } catch (RuntimeException e) {
            // a save written by an older build is not worth losing an account over
            return new LinkedHashMap<>();
        }
    }

    /**
     * The level this player walked away from, or null when they have none.
     */
    public SavedGame of(String username) {
        return username == null ? null : saves.get(username);
    }

    public boolean has(String username) {
        return of(username) != null;
    }

    /**
     * Keeps this level for the player, replacing whatever they had before —
     * one suspended level per account, as the document describes.
     */
    public void put(String username, SavedGame game) {
        saves.put(username, game);
        write();
    }

    /**
     * Throws the saved level away, which is what finishing or abandoning one
     * does.
     */
    public void clear(String username) {
        if (saves.remove(username) != null) {
            write();
        }
    }

    private void write() {
        if (file == null) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(file)) {
                gson.toJson(saves, writer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write saved games: " + file, e);
        }
    }
}
