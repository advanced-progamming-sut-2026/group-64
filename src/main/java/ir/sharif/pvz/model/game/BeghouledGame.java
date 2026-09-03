package ir.sharif.pvz.model.game;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Beghouled: the lawn starts full of plants and the player swaps neighbouring
 * ones to line three of a kind up. A line clears, the plants above it fall
 * into the gap, fresh ones slide in at the top, and the three that matched
 * come back as one plant a tier further up its family.
 *
 * <p>The zombies keep coming the whole time and eat their way through the
 * grid, so a lane the player never matches in thins out and lets one through.
 * The round is won by making the stage's quota of matches and lost the moment
 * a zombie walks into the house.
 */
class BeghouledGame implements MinigameLogic {

    /** How many in a row or column count as a match. */
    private static final int MIN_MATCH = 3;

    /** Sun paid for a match that has nowhere left to upgrade to. */
    private static final int TOP_TIER_SUN = 100;

    /** Sun paid for each plant a match had beyond the first three. */
    private static final int EXTRA_SUN = 50;

    /** How long a swapped or falling plant takes to reach its new tile. */
    private static final double SLIDE_SECONDS = 0.25;

    /**
     * The upgrade ladders. A match of the first plant of a family brings back
     * the second, a match of the second brings back the third, and a match of
     * the third pays sun instead.
     */
    private static final List<List<String>> FAMILIES = List.of(
            List.of("peashooter", "repeater", "threepeater"),
            List.of("sunflower", "twin-sunflower", "primal-sunflower"),
            List.of("wall-nut", "tall-nut", "endurian"),
            List.of("cabbage-pult", "kernel-pult", "melon-pult"),
            List.of("bonk-choy", "wasabi-whip", "phat-beet"));

    /** A plant part-way between two tiles, aged down every tick. */
    private static final class Move {
        private final String plant;
        private final double fromCol;
        private final double fromRow;
        private final int toCol;
        private final int toRow;
        private double remaining = SLIDE_SECONDS;

        Move(String plant, double fromCol, double fromRow, int toCol, int toRow) {
            this.plant = plant;
            this.fromCol = fromCol;
            this.fromRow = fromRow;
            this.toCol = toCol;
            this.toRow = toRow;
        }
    }

    private final List<Move> moving = new ArrayList<>();
    private final int stage;
    private final int target;
    private final Random random;
    private int matches;

    BeghouledGame(int stage, Random random) {
        this.stage = stage;
        this.random = random;
        this.target = 5 + 3 * stage;
    }

    @Override
    public void init(GameSession session) {
        session.disableMowers();
        deal(session);
        session.eventLog().add("Beghouled! Swap two neighbouring plants with "
                + "'swap plant -l (x1, y1) -l (x2, y2)' to line up three of a kind.");
        session.eventLog().add("Match " + target + " lines to win; a zombie reaching the house loses it.");
    }

    @Override
    public String plantingRejection(int x, int y) {
        return "Error: you rearrange the lawn here rather than planting on it.";
    }

    @Override
    public void tick(GameSession session, double seconds) {
        double step = 1.0 / GameSession.TICKS_PER_SECOND;
        for (Move move : moving) {
            move.remaining -= step;
        }
        moving.removeIf(move -> move.remaining <= 0);
    }

    @Override
    public List<MinigameSlide> slides() {
        List<MinigameSlide> live = new ArrayList<>();
        for (Move move : moving) {
            live.add(new MinigameSlide(move.plant, move.fromCol, move.fromRow,
                    move.toCol, move.toRow, 1 - move.remaining / SLIDE_SECONDS));
        }
        return live;
    }

    /**
     * Notes a plant travelling from one tile to another. Board coordinates are
     * 0-based here and 1-based in the slide, which is what the view draws in.
     */
    private void slide(String plant, double fromRow, double fromCol, int toRow, int toCol) {
        moving.add(new Move(plant, fromCol + 1, fromRow + 1, toCol + 1, toRow + 1));
    }

    /**
     * How far along the stage's quota the player is, for the view's objective
     * line.
     */
    String progress() {
        return matches + " / " + target + " matches";
    }

    /**
     * How many swaps on the board would still line three up, so the player can
     * see whether the lawn is running out of moves.
     */
    int movesLeft(GameSession session) {
        int moves = 0;
        for (int row = 0; row < GameSession.ROWS; row++) {
            for (int col = 0; col < GameSession.COLS; col++) {
                if (wouldMatch(session, row, col, row, col + 1)) {
                    moves++;
                }
                if (wouldMatch(session, row, col, row + 1, col)) {
                    moves++;
                }
            }
        }
        return moves;
    }

    // ===== the swap =====

    /**
     * Trades two neighbouring plants. The swap only stands when it lines three
     * of a kind up; otherwise the two go straight back where they were.
     */
    String swap(GameSession session, int x1, int y1, int x2, int y2) {
        String rejection = whyNotSwappable(session, x1, y1, x2, y2);
        if (rejection != null) {
            return rejection;
        }
        int row1 = y1 - 1;
        int col1 = x1 - 1;
        int row2 = y2 - 1;
        int col2 = x2 - 1;
        String first = typeAt(session, row1, col1);
        String second = typeAt(session, row2, col2);
        put(session, row1, col1, second);
        put(session, row2, col2, first);
        if (findMatches(session).isEmpty()) {
            put(session, row1, col1, first);
            put(session, row2, col2, second);
            return "Error: swapping " + first + " and " + second + " lines nothing up.";
        }
        slide(first, row1, col1, row2, col2);
        slide(second, row2, col2, row1, col1);
        int cleared = settle(session, row2, col2);
        reshuffleWhileStuck(session);
        if (matches >= target && !session.isOver()) {
            session.winNow("That is " + matches + " matches; the lawn is yours!");
        }
        return "Swapped " + first + " and " + second + "; " + cleared + " plants cleared ("
                + progress() + ", " + movesLeft(session) + " swaps left on the board).";
    }

    private String whyNotSwappable(GameSession session, int x1, int y1, int x2, int y2) {
        if (!session.validTile(x1, y1) || !session.validTile(x2, y2)) {
            return "Error: both tiles must be on the lawn.";
        }
        if (Math.abs(x1 - x2) + Math.abs(y1 - y2) != 1) {
            return "Error: you can only swap two plants sitting next to each other.";
        }
        if (session.plantAtTile(x1, y1) == null || session.plantAtTile(x2, y2) == null) {
            return "Error: both tiles need a plant on them to swap.";
        }
        return null;
    }

    // ===== matching, collapsing and refilling =====

    /**
     * Clears every match on the board, drops the plants above into the gaps,
     * slides fresh ones in at the top, and does it again for as long as the
     * refill keeps lining new matches up. Returns how many plants went.
     *
     * @param focusRow where the upgraded plant should appear, or -1 while the
     *                 board is only being tidied up
     */
    private int settle(GameSession session, int focusRow, int focusCol) {
        int cleared = 0;
        int row = focusRow;
        int col = focusCol;
        for (Set<Integer> group = findMatches(session); !group.isEmpty();
                group = findMatches(session)) {
            cleared += group.size();
            clear(session, group, row, col);
            collapse(session, group);
            // a cascade has no swap behind it, so its upgrade lands in place
            row = -1;
            col = -1;
        }
        return cleared;
    }

    /**
     * Takes a matched line off the board and puts the upgraded plant back, or
     * pays sun when the family has no higher tier.
     */
    private void clear(GameSession session, Set<Integer> group, int focusRow, int focusCol) {
        int anyTile = group.iterator().next();
        String type = typeAt(session, anyTile / GameSession.COLS, anyTile % GameSession.COLS);
        for (int tile : group) {
            session.clearTile(tile / GameSession.COLS, tile % GameSession.COLS);
        }
        matches++;
        int extras = group.size() - MIN_MATCH;
        if (extras > 0) {
            session.setSunAmount(session.getSunAmount() + extras * EXTRA_SUN);
        }
        String upgraded = nextTier(type);
        if (upgraded == null) {
            session.setSunAmount(session.getSunAmount() + TOP_TIER_SUN);
            session.eventLog().add("Three " + type + "s is as far as that family goes; +"
                    + TOP_TIER_SUN + " sun.");
            return;
        }
        int tile = group.contains(focusRow * GameSession.COLS + focusCol)
                ? focusRow * GameSession.COLS + focusCol
                : anyTile;
        put(session, tile / GameSession.COLS, tile % GameSession.COLS, upgraded);
        session.eventLog().add("Three " + type + "s became a " + upgraded + " at ("
                + (tile % GameSession.COLS + 1) + ", " + (tile / GameSession.COLS + 1) + ").");
    }

    /**
     * Gravity: in every column a match touched, the plants still standing sink
     * to the bottom and new ones slide in above them.
     */
    private void collapse(GameSession session, Set<Integer> group) {
        Set<Integer> columns = new LinkedHashSet<>();
        for (int tile : group) {
            columns.add(tile % GameSession.COLS);
        }
        for (int col : columns) {
            List<String> standing = new ArrayList<>();
            List<Integer> standingRows = new ArrayList<>();
            for (int row = 0; row < GameSession.ROWS; row++) {
                String type = typeAt(session, row, col);
                if (type != null) {
                    standing.add(type);
                    standingRows.add(row);
                }
                session.clearTile(row, col);
            }
            int gaps = GameSession.ROWS - standing.size();
            for (int row = 0; row < gaps; row++) {
                String fresh = randomBasePlant();
                put(session, row, col, fresh);
                // the new plants come in from above the top of the lawn
                slide(fresh, row - gaps, col, row, col);
            }
            for (int i = 0; i < standing.size(); i++) {
                put(session, gaps + i, col, standing.get(i));
                slide(standing.get(i), standingRows.get(i), col, gaps + i, col);
            }
        }
    }

    /**
     * Every tile that is part of a run of three or more of the same plant,
     * along a row or down a column.
     */
    private Set<Integer> findMatches(GameSession session) {
        Set<Integer> matched = new LinkedHashSet<>();
        for (int row = 0; row < GameSession.ROWS; row++) {
            collectRun(session, matched, row, 0, 0, 1, GameSession.COLS);
        }
        for (int col = 0; col < GameSession.COLS; col++) {
            collectRun(session, matched, 0, col, 1, 0, GameSession.ROWS);
        }
        return matched;
    }

    /**
     * Walks one row or column and records any run of three or more.
     */
    private void collectRun(GameSession session, Set<Integer> matched, int startRow, int startCol,
                            int rowStep, int colStep, int length) {
        int runStart = 0;
        for (int i = 1; i <= length; i++) {
            String previous = typeAt(session, startRow + (i - 1) * rowStep,
                    startCol + (i - 1) * colStep);
            String current = i == length ? null
                    : typeAt(session, startRow + i * rowStep, startCol + i * colStep);
            if (previous != null && previous.equals(current)) {
                continue;
            }
            if (previous != null && i - runStart >= MIN_MATCH) {
                for (int j = runStart; j < i; j++) {
                    matched.add(LevelSpec.tileKey(startRow + j * rowStep, startCol + j * colStep));
                }
                // one line at a time keeps the upgrade bookkeeping simple
                return;
            }
            runStart = i;
        }
    }

    // ===== a board with no move left =====

    /**
     * A lawn where no swap lines anything up is dealt again from scratch.
     */
    private void reshuffleWhileStuck(GameSession session) {
        int guard = 0;
        while (movesLeft(session) == 0 && guard++ < 20) {
            deal(session);
            session.eventLog().add("No swap left on that lawn; the plants were dealt again.");
        }
    }

    /**
     * Deals a fresh lawn that has no line already made on it — the player
     * should have to earn the first upgrade — and that has at least one swap
     * worth making.
     */
    private void deal(GameSession session) {
        int guard = 0;
        do {
            fillEveryTile(session);
            for (Set<Integer> group = findMatches(session); !group.isEmpty();
                    group = findMatches(session)) {
                // break the run rather than deal again: it converges much faster
                int tile = group.iterator().next();
                put(session, tile / GameSession.COLS, tile % GameSession.COLS, randomBasePlant());
            }
        } while (movesLeft(session) == 0 && guard++ < 20);
    }

    /**
     * Whether any single swap of two neighbours would line three up.
     */
    private boolean wouldMatch(GameSession session, int row1, int col1, int row2, int col2) {
        if (row2 >= GameSession.ROWS || col2 >= GameSession.COLS) {
            return false;
        }
        String first = typeAt(session, row1, col1);
        String second = typeAt(session, row2, col2);
        if (first == null || second == null || first.equals(second)) {
            return false;
        }
        put(session, row1, col1, second);
        put(session, row2, col2, first);
        boolean matched = !findMatches(session).isEmpty();
        put(session, row1, col1, first);
        put(session, row2, col2, second);
        return matched;
    }

    // ===== board helpers =====

    private void fillEveryTile(GameSession session) {
        for (int row = 0; row < GameSession.ROWS; row++) {
            for (int col = 0; col < GameSession.COLS; col++) {
                put(session, row, col, randomBasePlant());
            }
        }
    }

    private String randomBasePlant() {
        List<String> family = FAMILIES.get(random.nextInt(FAMILIES.size()));
        // later stages seed a few already-upgraded plants to speed the ladder up
        int tier = stage >= 3 && random.nextInt(5) == 0 ? 1 : 0;
        return family.get(tier);
    }

    private static String nextTier(String type) {
        for (List<String> family : FAMILIES) {
            int index = family.indexOf(type);
            if (index >= 0) {
                return index + 1 < family.size() ? family.get(index + 1) : null;
            }
        }
        return null;
    }

    private static String typeAt(GameSession session, int row, int col) {
        if (row < 0 || row >= GameSession.ROWS || col < 0 || col >= GameSession.COLS) {
            return null;
        }
        Plant plant = session.gridArray()[row][col];
        return plant == null ? null : plant.getSpec().getName();
    }

    /**
     * Puts a fresh plant of this type on a tile. Plants know their own tile, so
     * moving one across the board means building it again where it lands.
     */
    private static void put(GameSession session, int row, int col, String type) {
        session.gridArray()[row][col] = new Plant(GameCatalog.get().plant(type), row, col,
                false, session.plantLevel(type));
    }
}
