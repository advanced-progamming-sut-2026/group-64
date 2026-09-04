package ir.sharif.pvz.view.fx.widget;

import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.TileTerrain;
import ir.sharif.pvz.view.fx.Assets;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/**
 * The tiles a chapter is made of: the sea and its lily pads on Big Wave Beach,
 * the slippery ice of Frostbite Caves, the tiles zombies climb out of, and the
 * crater a doom-shroom leaves.
 *
 * <p>Each of these used to be one flat translucent rectangle, which on a
 * painted lawn read as a coloured square rather than as water or ice — worst
 * of all in the ice caves, where a pale blue wash over pale blue art was
 * invisible. They are drawn here instead, and they move: the sea ripples and
 * the ice glints, so a tile that changes the rules looks like it does.
 *
 * <p>Geometry and sprites come from {@link LawnView}; this only paints.
 */
final class LawnTerrain {

    private static final Color DEEP = Color.web("#10365f");
    private static final Color SHALLOW = Color.web("#2f86c4");
    private static final Color ICE_DARK = Color.web("#5aa8d8");
    private static final Color ICE_LIGHT = Color.web("#dff4ff");

    private final LawnView lawn;

    LawnTerrain(LawnView lawn) {
        this.lawn = lawn;
    }

    void draw(GraphicsContext gc, GameSession session, double seconds) {
        for (int row = 1; row <= GameSession.ROWS; row++) {
            for (int col = 1; col <= GameSession.COLS; col++) {
                TileTerrain terrain = session.terrainAt(col, row);
                if (terrain != TileTerrain.NORMAL) {
                    paint(gc, session, col, row, terrain, seconds);
                }
            }
        }
    }

    private void paint(GraphicsContext gc, GameSession session, int col, int row,
                       TileTerrain terrain, double seconds) {
        double left = lawn.tileX(col) - lawn.tileWidth() / 2;
        double top = lawn.tileY(row) - lawn.tileHeight() / 2;
        double width = lawn.tileWidth();
        double height = lawn.tileHeight();
        // a tile's own phase, so neighbours do not ripple or glint in step
        double phase = seconds + (col * 0.7) + (row * 1.3);
        switch (terrain) {
            case WATER -> water(gc, left, top, width, height, phase);
            case LILY -> lily(gc, col, row, left, top, width, height, phase);
            case SLIPPERY_UP -> ice(gc, left, top, width, height, phase, -1);
            case SLIPPERY_DOWN -> ice(gc, left, top, width, height, phase, 1);
            case SPAWNER -> spawner(gc, left, top, width, height, phase);
            case CRATER -> crater(gc, left, top, width, height);
            case GRAVE -> lawn.drawGrave(gc, session, col, row);
            default -> { }
        }
    }

    /**
     * Open sea: deep at the back, shallow at the front, with two crests
     * sliding across it so the tile is plainly moving water.
     */
    private void water(GraphicsContext gc, double left, double top,
                       double width, double height, double phase) {
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, DEEP.deriveColor(0, 1, 1, 0.78)),
                new Stop(1, SHALLOW.deriveColor(0, 1, 1, 0.62))));
        gc.fillRect(left, top, width, height);
        gc.setStroke(Color.color(1, 1, 1, 0.30));
        gc.setLineWidth(Math.max(1.2, height * 0.035));
        for (int crest = 0; crest < 2; crest++) {
            double drift = ((phase * 0.28) + (crest * 0.5)) % 1.0;
            double y = top + height * (0.22 + drift * 0.62);
            double swell = height * 0.06 * Math.sin(phase * 1.7 + crest);
            gc.beginPath();
            gc.moveTo(left + width * 0.10, y);
            gc.bezierCurveTo(left + width * 0.35, y - swell,
                    left + width * 0.65, y + swell,
                    left + width * 0.90, y);
            gc.stroke();
        }
    }

    /**
     * A lily pad, drawn with the plant's own sprite so a covered water tile
     * looks like the thing the player planted rather than a green square.
     */
    private void lily(GraphicsContext gc, int col, int row, double left, double top,
                      double width, double height, double phase) {
        water(gc, left, top, width, height, phase);
        // it bobs on the swell it is floating on
        double bob = height * 0.02 * Math.sin(phase * 1.5);
        lawn.drawSprite(gc, Assets.image("plants/lily-pad"),
                lawn.tileX(col), lawn.tileY(row) + bob, height * 0.92,
                Color.web("#3f8f43"));
    }

    /**
     * Slippery ice, with a chevron pointing the way it shoves a zombie. The
     * direction is the whole mechanic, and nothing on the lawn used to say it.
     */
    private void ice(GraphicsContext gc, double left, double top,
                     double width, double height, double phase, int towards) {
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, ICE_LIGHT.deriveColor(0, 1, 1, 0.80)),
                new Stop(1, ICE_DARK.deriveColor(0, 1, 1, 0.70))));
        gc.fillRect(left, top, width, height);
        gc.setStroke(Color.web("#0d3c5c", 0.55));
        gc.setLineWidth(1.4);
        gc.strokeRect(left + 1, top + 1, width - 2, height - 2);

        // a glint travelling across the sheet, the way light runs over ice
        double sweep = ((phase * 0.22) % 1.0) * width;
        gc.setStroke(Color.color(1, 1, 1, 0.55));
        gc.setLineWidth(Math.max(2, width * 0.05));
        gc.strokeLine(left + sweep, top, left + sweep - width * 0.25, top + height);

        double midX = left + width / 2;
        double midY = top + height / 2;
        double arm = width * 0.16;
        double reach = height * 0.16 * towards;
        gc.setStroke(Color.web("#0d3c5c", 0.85));
        gc.setLineWidth(Math.max(2.5, height * 0.06));
        for (int chevron = 0; chevron < 2; chevron++) {
            double y = midY + reach * chevron * 0.9 - reach * 0.45;
            gc.strokeLine(midX - arm, y, midX, y + reach);
            gc.strokeLine(midX + arm, y, midX, y + reach);
        }
    }

    /**
     * A tile zombies climb out of: a dark hole with a pulse coming off it, so
     * the player can see where the next one is going to appear.
     */
    private void spawner(GraphicsContext gc, double left, double top,
                         double width, double height, double phase) {
        double midX = left + width / 2;
        double midY = top + height / 2;
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#1b0a24", 0.85)),
                new Stop(1, Color.web("#5c2470", 0.25))));
        gc.fillOval(left + width * 0.08, top + height * 0.14,
                width * 0.84, height * 0.72);

        double pulse = (phase * 0.6) % 1.0;
        gc.setStroke(Color.web("#c77bff", 0.75 * (1 - pulse)));
        gc.setLineWidth(Math.max(1.5, height * 0.04));
        double ringW = width * (0.30 + pulse * 0.60);
        double ringH = height * (0.24 + pulse * 0.52);
        gc.strokeOval(midX - ringW / 2, midY - ringH / 2, ringW, ringH);
    }

    /** The hole a doom-shroom leaves, which nothing can be planted in. */
    private void crater(GraphicsContext gc, double left, double top,
                        double width, double height) {
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.45, 0.55, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#120d09", 0.92)),
                new Stop(0.75, Color.web("#3a2a1d", 0.80)),
                new Stop(1, Color.web("#3a2a1d", 0.15))));
        gc.fillOval(left + width * 0.06, top + height * 0.12,
                width * 0.88, height * 0.76);
        gc.setStroke(Color.web("#6b533a", 0.65));
        gc.setLineWidth(Math.max(1.5, height * 0.045));
        gc.strokeOval(left + width * 0.06, top + height * 0.12,
                width * 0.88, height * 0.76);
    }
}
