package ir.sharif.pvz.view.fx.widget;

import ir.sharif.pvz.model.game.Zomboss;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Zomboss's health, drawn as the three separate sections the project document
 * shows: each one has to be emptied before the next starts going down, and the
 * boss reels between them.
 */
public final class BossBar extends Canvas {

    private static final double PAD = 14;
    private static final double TRACK_WIDTH = 340;
    private static final double TRACK_TOP = 10;
    private static final double TRACK_HEIGHT = 18;
    private static final int PARTS = 3;

    public BossBar() {
        super(TRACK_WIDTH + 2 * PAD, TRACK_TOP + TRACK_HEIGHT + 8);
    }

    /**
     * Redraws for the boss's current state; a stunned boss is drawn paler so
     * the player can see the window they have to attack in.
     */
    public void setBoss(Zomboss boss) {
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());
        if (boss == null) {
            return;
        }
        double segment = TRACK_WIDTH / PARTS;
        int gone = boss.getPartsDestroyed();

        for (int part = 0; part < PARTS; part++) {
            double left = PAD + segment * part;
            gc.setFill(Color.color(0, 0, 0, 0.55));
            gc.fillRect(left, TRACK_TOP, segment - 3, TRACK_HEIGHT);

            double filled = fillOf(part, gone, boss);
            if (filled > 0) {
                gc.setFill(boss.isStunned() ? Color.web("#f2a33c") : Color.web("#d2352b"));
                gc.fillRect(left, TRACK_TOP, (segment - 3) * filled, TRACK_HEIGHT);
            }
            gc.setStroke(Color.color(0, 0, 0, 0.8));
            gc.setLineWidth(2);
            gc.strokeRect(left, TRACK_TOP, segment - 3, TRACK_HEIGHT);
        }
    }

    /**
     * How full one section is: sections already taken off are empty, sections
     * not yet reached are full, and the one in play follows the boss.
     */
    private static double fillOf(int part, int gone, Zomboss boss) {
        int remainingIndex = PARTS - 1 - part;
        if (remainingIndex < gone) {
            return 0;
        }
        if (remainingIndex > gone) {
            return 1;
        }
        return boss.currentPartFraction();
    }
}
