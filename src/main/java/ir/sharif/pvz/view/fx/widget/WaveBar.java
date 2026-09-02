package ir.sharif.pvz.view.fx.widget;

import ir.sharif.pvz.view.fx.Assets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

/**
 * The zombie progress bar: empty when a level begins, full when it is won, with
 * a flag at every wave so the player can see how far the next one is.
 *
 * <p>A plain progress bar cannot carry the markers, so this draws itself. The
 * canvas is wider than the track it draws, which leaves room for the marker at
 * either end to be drawn whole instead of being clipped in half.
 */
public final class WaveBar extends Canvas {

    private static final double PAD = 18;
    private static final double TRACK_WIDTH = 336;
    private static final double TRACK_TOP = 15;
    private static final double TRACK_HEIGHT = 14;
    private static final double HEAD_SIZE = 28;
    private static final double FLAG_HEIGHT = 10;

    private int totalWaves = 1;
    private double progress;

    public WaveBar() {
        super(TRACK_WIDTH + 2 * PAD, TRACK_TOP + TRACK_HEIGHT + 8);
    }

    /**
     * How many waves this level has, which decides where the flags sit.
     */
    public void setTotalWaves(int totalWaves) {
        this.totalWaves = Math.max(1, totalWaves);
        draw();
    }

    /**
     * Where the level stands, from 0 to 1.
     */
    public void setProgress(double progress) {
        this.progress = Math.max(0, Math.min(1, progress));
        draw();
    }

    /**
     * The x of a point on the track, given as a fraction from 0 to 1.
     */
    private static double trackX(double fraction) {
        return PAD + TRACK_WIDTH * fraction;
    }

    private void draw() {
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());
        drawTrack(gc);
        drawFill(gc);
        drawWaveFlags(gc);
        drawHead(gc);
    }

    private void drawTrack(GraphicsContext gc) {
        gc.setFill(Color.color(1, 1, 1, 0.16));
        gc.fillRoundRect(PAD, TRACK_TOP, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
        gc.setStroke(Color.color(0, 0, 0, 0.55));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(PAD, TRACK_TOP, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
    }

    private void drawFill(GraphicsContext gc) {
        double filled = TRACK_WIDTH * progress;
        if (filled <= 1) {
            return;
        }
        gc.setFill(new LinearGradient(0, TRACK_TOP, 0, TRACK_TOP + TRACK_HEIGHT, false,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#a8e85a")), new Stop(1, Color.web("#4f9e1f"))));
        gc.fillRoundRect(PAD, TRACK_TOP, filled, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
    }

    /**
     * One flag per wave, at the point on the track where that wave arrives. A
     * flag already passed turns gold; the final one is drawn taller because it
     * is the flag wave that ends the level.
     */
    private void drawWaveFlags(GraphicsContext gc) {
        for (int wave = 1; wave <= totalWaves; wave++) {
            double fraction = wave / (double) totalWaves;
            double x = trackX(fraction);
            boolean finalWave = wave == totalWaves;
            boolean reached = progress >= fraction - 1e-6;
            double height = finalWave ? FLAG_HEIGHT + 4 : FLAG_HEIGHT;
            double top = TRACK_TOP - height - 2;

            gc.setStroke(Color.web("#e8e4d8"));
            gc.setLineWidth(2);
            gc.strokeLine(x, top, x, TRACK_TOP + TRACK_HEIGHT);

            gc.setFill(reached ? Color.web("#f7d13d") : Color.web("#d43b2a"));
            gc.fillPolygon(
                    new double[] {x + 1, x + 1 + (finalWave ? 14 : 11), x + 1},
                    new double[] {top, top + height / 2, top + height},
                    3);
        }
    }

    /**
     * The marker that rides along the track, so the eye has something to follow.
     */
    private void drawHead(GraphicsContext gc) {
        double x = trackX(progress);
        double y = TRACK_TOP + TRACK_HEIGHT / 2;
        Image head = Assets.ui("zombie-head");
        if (head == null) {
            gc.setFill(Color.web("#d9d2c2"));
            gc.fillOval(x - 8, y - 8, 16, 16);
            return;
        }
        gc.drawImage(head, x - HEAD_SIZE / 2, y - HEAD_SIZE / 2, HEAD_SIZE, HEAD_SIZE);
    }
}
