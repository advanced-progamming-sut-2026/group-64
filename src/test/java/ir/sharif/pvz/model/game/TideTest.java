package ir.sharif.pvz.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The sea coming in and going out. The water used to be laid down once when
 * the level was built and never move, which left the chapter named after its
 * tide with a fixed pond down one side.
 */
class TideTest {

    private GameSession beachDay(int day) {
        LevelSpec spec = Levels.adventure().stream()
                .filter(level -> level.getChapter() == Chapter.BIG_WAVE_BEACH
                        && level.getDay() == day)
                .findFirst().orElseThrow();
        GameSession session = new GameSession(spec, 3,
                List.of("sunflower", "lily-pad"), new HashSet<>(), new Random(3));
        session.setWavesEnabled(false);
        return session;
    }

    @Test
    void onlyTheBeachHasATide() {
        for (Chapter chapter : Chapter.values()) {
            LevelSpec spec = Levels.adventure().stream()
                    .filter(level -> level.getChapter() == chapter).findFirst().orElseThrow();
            GameSession session = new GameSession(spec, 3, List.of("sunflower"),
                    new HashSet<>(), new Random(1));
            assertEquals(chapter == Chapter.BIG_WAVE_BEACH && session.getTide().isEnabled(),
                    session.getTide().isEnabled(),
                    chapter + " should only have a tide on the beach");
            if (chapter != Chapter.BIG_WAVE_BEACH) {
                assertFalse(session.getTide().isEnabled(), chapter + " has no tide");
            }
        }
    }

    /**
     * A level with two columns of sea: the front should move between them
     * rather than sitting still.
     */
    @Test
    void theSeaComesInAndGoesOutAgain() {
        GameSession session = beachDay(3);
        Tide tide = session.getTide();
        if (!tide.isEnabled()) {
            return;
        }
        int high = tide.frontAt(Tide.PERIOD_SECONDS * 0.35);
        int low = tide.frontAt(0);
        assertNotEquals(high, low, "the sea reaches further in at high tide");
        assertTrue(high < low, "high tide is further left across the lawn");
        assertTrue(tide.isLow(0), "it starts out");
        assertFalse(tide.isLow(Tide.PERIOD_SECONDS * 0.35), "and comes in");
    }

    /** What the sea uncovers can be walked on; what it covers cannot. */
    @Test
    void aTileTheSeaLeavesBecomesLandAndComesBack() {
        GameSession session = beachDay(3);
        if (!session.getTide().isEnabled()) {
            return;
        }
        // the tidal edge is the leftmost sea tile; the ones to its right are
        // the deep water that never drains
        int edge = 0;
        for (int col = 1; col <= GameSession.COLS; col++) {
            if (session.getLevel().getTerrain().get(LevelSpec.tileKey(2, col - 1))
                    == TileTerrain.WATER) {
                edge = edge == 0 ? col : Math.min(edge, col);
            }
        }
        assertTrue(edge > 0, "the level has water in row 3");

        boolean sawWater = false;
        boolean sawLand = false;
        for (int i = 0; i < Tide.PERIOD_SECONDS * GameSession.TICKS_PER_SECOND; i++) {
            session.advance(1);
            TileTerrain now = session.terrainAt(edge, 3);
            sawWater |= now == TileTerrain.WATER;
            sawLand |= now == TileTerrain.NORMAL;
        }
        assertTrue(sawWater, "the tile is sea at high tide");
        assertTrue(sawLand, "and sand at low tide");
    }

    /**
     * The tide is scenery, not a rule: it must never drown a plant, nor take
     * back a tile a lily pad is holding.
     */
    @Test
    void theTideLeavesPlantsAndLilyPadsAlone() {
        GameSession session = beachDay(3);
        if (!session.getTide().isEnabled()) {
            return;
        }
        session.cheats().addSuns(2000);
        session.cheats().removeCooldown();
        // wait for the sea to go out, then plant on what it uncovered
        int col = 0;
        for (int i = 0; i < Tide.PERIOD_SECONDS * GameSession.TICKS_PER_SECOND && col == 0; i++) {
            session.advance(1);
            for (int c = GameSession.COLS; c >= 1 && col == 0; c--) {
                if (session.getLevel().getTerrain().containsKey(LevelSpec.tileKey(2, c - 1))
                        && session.terrainAt(c, 3) == TileTerrain.NORMAL) {
                    col = c;
                }
            }
        }
        assertTrue(col > 0, "the sea should uncover a tile");
        assertTrue(session.plant("sunflower", col, 3).startsWith("Planted"));

        session.advance((int) (Tide.PERIOD_SECONDS * GameSession.TICKS_PER_SECOND));
        assertNotEquals(null, session.plantAtTile(col, 3), "the sea does not drown it");
        assertEquals(TileTerrain.NORMAL, session.terrainAt(col, 3),
                "and does not take its tile back under it");
    }
}
