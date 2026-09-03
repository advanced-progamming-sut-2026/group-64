package ir.sharif.pvz.model.game;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What one finished level says about how it was played, in the terms the
 * travel log's quests are written in.
 *
 * <p>The engine knows none of this by name — it knows plants and zombies — so
 * this is where a level is read as "won with the fourth column empty" or "the
 * garden ended up symmetric".
 *
 * @param won               whether the player held the lawn
 * @param chapter           the chapter this level belongs to
 * @param difficulty        the difficulty it was played on, 1 to 5
 * @param nightLevel        whether the level itself is a night one
 * @param sunLeft           sun still unspent when it ended
 * @param sunCollected      sun picked up over the whole level
 * @param kills             zombies killed
 * @param killsBy           kills per plant, and the mower's under its own name
 * @param killsInFastWindow kills in the first thirty seconds of wave one
 * @param killsAtTheDoor    kills in column one of a lane with no mower left
 * @param plantsLost        plants the zombies ate
 * @param planted           every plant type the player put down
 * @param emptyColumns      1-based columns nothing was ever planted in
 * @param emptyRows         1-based rows nothing was ever planted in
 * @param symmetric         whether the final lawn mirrors top to bottom
 * @param asymmetric        whether no pair of mirrored rows matches at all
 */
public record LevelReport(boolean won, Chapter chapter, int difficulty, boolean nightLevel,
                          int sunLeft, int sunCollected, int kills, Map<String, Integer> killsBy,
                          int killsInFastWindow, int killsAtTheDoor, int plantsLost,
                          Set<String> planted, Set<Integer> emptyColumns, Set<Integer> emptyRows,
                          boolean symmetric, boolean asymmetric) {

    public LevelReport {
        killsBy = Map.copyOf(killsBy);
        planted = Set.copyOf(planted);
        emptyColumns = Set.copyOf(emptyColumns);
        emptyRows = Set.copyOf(emptyRows);
    }

    /**
     * Reads a finished level.
     */
    public static LevelReport of(GameSession session) {
        LevelLog log = session.getLog();
        return new LevelReport(session.isWon(), session.getLevel().getChapter(),
                session.difficultyLevel, session.getLevel().isNight(),
                session.getSunAmount(), log.getSunCollected(), log.getKills(), log.getKillsBy(),
                log.getKillsInFastWindow(), log.getKillsAtTheDoor(), log.getPlantsLost(),
                log.getPlanted(), emptyColumns(session), emptyRows(session),
                symmetric(session), noSymmetryAtAll(session));
    }

    private static Set<Integer> emptyColumns(GameSession session) {
        Set<Integer> empty = new LinkedHashSet<>();
        for (int col = 1; col <= GameSession.COLS; col++) {
            boolean bare = true;
            for (int row = 1; row <= GameSession.ROWS && bare; row++) {
                bare = session.plantAtTile(col, row) == null;
            }
            if (bare) {
                empty.add(col);
            }
        }
        return empty;
    }

    private static Set<Integer> emptyRows(GameSession session) {
        Set<Integer> empty = new LinkedHashSet<>();
        for (int row = 1; row <= GameSession.ROWS; row++) {
            boolean bare = true;
            for (int col = 1; col <= GameSession.COLS && bare; col++) {
                bare = session.plantAtTile(col, row) == null;
            }
            if (bare) {
                empty.add(row);
            }
        }
        return empty;
    }

    /**
     * Whether the lawn reads the same from the top and from the bottom. The
     * middle row is its own mirror, so it never decides the answer.
     */
    private static boolean symmetric(GameSession session) {
        for (int row = 1; row <= GameSession.ROWS / 2; row++) {
            int mirror = GameSession.ROWS + 1 - row;
            for (int col = 1; col <= GameSession.COLS; col++) {
                if (!sameTile(session, col, row, mirror)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether no pair of mirrored rows matches, which is the opposite corner
     * the sheet asks for in its own quest.
     */
    private static boolean noSymmetryAtAll(GameSession session) {
        for (int row = 1; row <= GameSession.ROWS / 2; row++) {
            int mirror = GameSession.ROWS + 1 - row;
            boolean rowMatches = true;
            for (int col = 1; col <= GameSession.COLS && rowMatches; col++) {
                rowMatches = sameTile(session, col, row, mirror);
            }
            if (rowMatches) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameTile(GameSession session, int col, int row, int mirror) {
        Plant here = session.plantAtTile(col, row);
        Plant there = session.plantAtTile(col, mirror);
        if (here == null || there == null) {
            return here == there;
        }
        return here.getSpec().getName().equals(there.getSpec().getName());
    }

    /**
     * The plant families the player actually killed with.
     */
    public Set<PlantCategory> familiesThatKilled() {
        Set<PlantCategory> families = new LinkedHashSet<>();
        killsBy.forEach((name, count) -> {
            PlantSpec spec = GameCatalog.get().plant(name);
            if (spec != null && count > 0) {
                families.add(spec.getCategory());
            }
        });
        return families;
    }

    /**
     * The plant families the player brought to the lawn at all.
     */
    public Set<PlantCategory> familiesPlanted() {
        Set<PlantCategory> families = new LinkedHashSet<>();
        for (String name : planted) {
            PlantSpec spec = GameCatalog.get().plant(name);
            if (spec != null) {
                families.add(spec.getCategory());
            }
        }
        return families;
    }

    /**
     * How many plants of a family were put down, which two quests count.
     */
    public long plantedOfFamily(PlantCategory family) {
        return planted.stream()
                .map(name -> GameCatalog.get().plant(name))
                .filter(spec -> spec != null && spec.getCategory() == family)
                .count();
    }

    /**
     * The one plant that did all the killing, or null when more than one did.
     */
    public String soleKiller() {
        String only = null;
        for (Map.Entry<String, Integer> entry : killsBy.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            if (only != null) {
                return null;
            }
            only = entry.getKey();
        }
        return only;
    }
}
