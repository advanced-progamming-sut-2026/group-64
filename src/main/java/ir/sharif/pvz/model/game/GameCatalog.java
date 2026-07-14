package ir.sharif.pvz.model.game;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and holds the static plant/zombie definitions shipped in resources.
 */
public final class GameCatalog {

    private static final GameCatalog INSTANCE = new GameCatalog();

    private final Map<String, PlantSpec> plants = new LinkedHashMap<>();
    private final Map<String, ZombieSpec> zombies = new LinkedHashMap<>();

    private GameCatalog() {
        for (String[] row : readCsv("/data/plants.csv")) {
            PlantSpec spec = new PlantSpec(row[0], PlantCategory.valueOf(row[1]),
                    Integer.parseInt(row[2]), Double.parseDouble(row[3]), Integer.parseInt(row[4]),
                    Integer.parseInt(row[5]), Double.parseDouble(row[6]),
                    Arrays.asList(row[7].split(";")));
            plants.put(spec.getName(), spec);
        }
        for (String[] row : readCsv("/data/zombies.csv")) {
            Map<String, Integer> armor = new LinkedHashMap<>();
            if (!row[2].isEmpty()) {
                for (String piece : row[2].split(";")) {
                    String[] parts = piece.split(":");
                    armor.put(parts[0], Integer.valueOf(parts[1]));
                }
            }
            ZombieSpec spec = new ZombieSpec(row[0], Integer.parseInt(row[1]), armor,
                    Double.parseDouble(row[3]), Integer.parseInt(row[4]), Integer.parseInt(row[5]), row[6]);
            zombies.put(spec.getName(), spec);
        }
    }

    public static GameCatalog get() {
        return INSTANCE;
    }

    private static List<String[]> readCsv(String resource) {
        InputStream stream = GameCatalog.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Missing resource: " + resource);
        }
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            int columns = header.split(",", -1).length;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    rows.add(line.split(",", columns));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }

    public PlantSpec plant(String name) {
        return plants.get(name);
    }

    public ZombieSpec zombie(String name) {
        return zombies.get(name);
    }

    public List<PlantSpec> allPlants() {
        return new ArrayList<>(plants.values());
    }

    public List<ZombieSpec> allZombies() {
        return new ArrayList<>(zombies.values());
    }
}
