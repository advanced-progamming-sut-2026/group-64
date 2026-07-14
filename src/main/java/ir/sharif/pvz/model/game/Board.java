package ir.sharif.pvz.model.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The terrain layer of the lawn: tile kinds, graves (with their hit points
 * and hidden rewards) and the planting rules the terrain imposes.
 */
class Board {

    private static final int GRAVE_HP = 700;

    private final TileTerrain[][] terrain = new TileTerrain[GameSession.ROWS][GameSession.COLS];
    private final int[][] graveHp = new int[GameSession.ROWS][GameSession.COLS];
    private final Map<Integer, String> graveContents = new HashMap<>();
    private final List<String> events;
    private final double difficultyUp;

    Board(LevelSpec level, double difficultyUp, Random random, List<String> events) {
        this.difficultyUp = difficultyUp;
        this.events = events;
        for (TileTerrain[] row : terrain) {
            java.util.Arrays.fill(row, TileTerrain.NORMAL);
        }
        level.getTerrain().forEach((key, kind) ->
                terrain[key / GameSession.COLS][key % GameSession.COLS] = kind);
        for (int i = 0; i < level.getStartingGraves(); i++) {
            raiseGrave(random.nextInt(GameSession.ROWS), random.nextInt(GameSession.COLS), null, true);
        }
    }

    TileTerrain terrainAt(int row, int col) {
        return terrain[row][col];
    }

    void setTerrain(int row, int col, TileTerrain kind) {
        terrain[row][col] = kind;
    }

    int graveHpAt(int row, int col) {
        return graveHp[row][col];
    }

    /**
     * Turns a free tile into a grave; Dark Ages graves may hide sun or plant food.
     */
    void raiseGrave(int row, int col, String contents, boolean tileFree) {
        if (terrain[row][col] != TileTerrain.NORMAL || !tileFree) {
            return;
        }
        terrain[row][col] = TileTerrain.GRAVE;
        graveHp[row][col] = (int) Math.round(GRAVE_HP * difficultyUp);
        if (contents != null) {
            graveContents.put(LevelSpec.tileKey(row, col), contents);
        }
        events.add("A grave" + (contents == null ? "" : " holding a " + contents)
                + " raised at (" + (col + 1) + ", " + (row + 1) + ").");
    }

    /**
     * Why this plant cannot be placed on this tile, or null when it can.
     */
    String rejection(PlantSpec spec, int row, int col) {
        TileTerrain kind = terrain[row][col];
        String tile = "(" + (col + 1) + ", " + (row + 1) + ")";
        if (kind == TileTerrain.GRAVE) {
            return "Error: you cannot plant on the grave at " + tile + ".";
        }
        if (kind == TileTerrain.SLIPPERY_UP || kind == TileTerrain.SLIPPERY_DOWN) {
            return "Error: the slippery ice at " + tile + " cannot hold a plant.";
        }
        if (kind == TileTerrain.WATER && !spec.hasTag("water")) {
            return "Error: " + spec.getName() + " needs a lily-pad to stand on the water at " + tile + ".";
        }
        if (kind == TileTerrain.LILY && spec.getName().equals("lily-pad")) {
            return "Error: there is already a lily-pad at " + tile + ".";
        }
        return null;
    }

    /**
     * The first grave standing between a shooter and its target, if any.
     */
    int graveColumnBetween(int row, int fromCol, double targetX) {
        for (int col = fromCol; col <= Math.min(GameSession.COLS - 1, (int) Math.round(targetX) - 1); col++) {
            if (terrain[row][col] == TileTerrain.GRAVE) {
                return col;
            }
        }
        return -1;
    }

    /**
     * Chips a grave; when it crumbles, returns what it was hiding ("sun",
     * "plant food" or "" for nothing) so the session can grant it. Returns
     * null while the grave still stands.
     */
    String damageGrave(int row, int col, int damage) {
        graveHp[row][col] -= damage;
        if (graveHp[row][col] > 0) {
            return null;
        }
        terrain[row][col] = TileTerrain.NORMAL;
        graveHp[row][col] = 0;
        events.add("The grave at (" + (col + 1) + ", " + (row + 1) + ") is destroyed.");
        String contents = graveContents.remove(LevelSpec.tileKey(row, col));
        return contents == null ? "" : contents;
    }
}
