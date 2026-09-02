package ir.sharif.pvz.view.fx.widget;

import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.net.Snapshot;
import ir.sharif.pvz.view.fx.Assets;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * The lawn of a versus match, drawn from the board the server sent rather than
 * from a game running here.
 *
 * <p>It reuses the single-player lawn's geometry and sprite drawing so the two
 * modes look identical; only where the entities come from differs.
 */
public final class VersusLawnView extends LawnView {

    private static final double PLANT_SCALE = 0.78;
    private static final double ZOMBIE_SCALE = 1.05;
    private static final double SUN_SCALE = 0.46;
    private static final int RED_LINE_COLUMN = 5;

    public VersusLawnView(String chapterId) {
        super(chapterId);
    }

    /**
     * Draws one board. Everything on it is exactly what the server said, so two
     * players watching the same match see the same picture.
     */
    public void render(Snapshot board, double seconds) {
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());
        drawBackground(gc);
        drawRedLine(gc);
        drawBrains(gc, board);
        drawSuns(gc, board);
        drawRows(gc, board, seconds);
    }

    /**
     * The line zombies may not be placed to the left of.
     */
    private void drawRedLine(GraphicsContext gc) {
        double x = tileX(RED_LINE_COLUMN) + tileWidth() / 2;
        double top = tileY(1) - tileHeight() / 2;
        gc.setStroke(Color.color(0.9, 0.2, 0.2, 0.8));
        gc.setLineWidth(3);
        gc.setLineDashes(10, 8);
        gc.strokeLine(x, top, x, top + tileHeight() * GameSession.ROWS);
        gc.setLineDashes();
    }

    /**
     * One brain per lane at the house end; an eaten one is greyed out.
     */
    private void drawBrains(GraphicsContext gc, Snapshot board) {
        for (int row = 0; row < board.brains().size(); row++) {
            boolean alive = Boolean.TRUE.equals(board.brains().get(row));
            double x = tileX(1) - tileWidth() * 0.75;
            double y = tileY(row + 1);
            double size = tileHeight() * 0.4;
            gc.setFill(alive ? Color.web("#f0a5c8") : Color.color(0.3, 0.3, 0.3, 0.6));
            gc.fillOval(x - size / 2, y - size / 2, size, size);
            gc.setStroke(Color.color(0, 0, 0, 0.45));
            gc.setLineWidth(1.5);
            gc.strokeOval(x - size / 2, y - size / 2, size, size);
        }
    }

    private void drawSuns(GraphicsContext gc, Snapshot board) {
        for (Snapshot.SunView sun : board.suns()) {
            double size = tileHeight() * SUN_SCALE
                    * ("SPECIAL".equals(sun.kind()) ? 1.35 : 1.0);
            double x = tileX(sun.col());
            double y = tileY(sun.row());
            drawSprite(gc, Assets.ui("sun"), x, y, size, Color.web("#ffd733"));
        }
    }

    /**
     * Row by row, so something in a nearer lane overlaps what is behind it.
     */
    private void drawRows(GraphicsContext gc, Snapshot board, double seconds) {
        for (int row = 1; row <= GameSession.ROWS; row++) {
            for (Snapshot.PlantView plant : board.plants()) {
                if (plant.row() == row) {
                    drawPlant(gc, plant, seconds);
                }
            }
            for (Snapshot.ZombieView zombie : board.zombies()) {
                if (zombie.row() + 1 == row) {
                    drawZombie(gc, zombie, seconds);
                }
            }
            for (Snapshot.ShotView shot : board.shots()) {
                if (shot.row() + 1 == row) {
                    drawShot(gc, shot);
                }
            }
        }
    }

    private void drawPlant(GraphicsContext gc, Snapshot.PlantView plant, double seconds) {
        double height = tileHeight() * PLANT_SCALE;
        double bob = Math.sin(seconds * 2.4 + plant.col() * 0.7 + plant.row()) * height * 0.03;
        double x = tileX(plant.col());
        double y = tileY(plant.row()) + bob;
        drawSprite(gc, Assets.plant(plant.type()), x, y, height, Color.web("#4caf50"));
        if (plant.hp() < plant.maxHp()) {
            drawHealthBar(gc, x, y - height * 0.62, height * 0.8,
                    plant.hp() / (double) Math.max(1, plant.maxHp()), Color.web("#7cd12a"));
        }
        if (plant.disabled()) {
            gc.setFill(Color.color(0.3, 0.3, 0.45, 0.45));
            gc.fillRect(tileX(plant.col()) - tileWidth() / 2,
                    tileY(plant.row()) - tileHeight() / 2, tileWidth(), tileHeight());
        }
    }

    private void drawZombie(GraphicsContext gc, Snapshot.ZombieView zombie, double seconds) {
        double height = tileHeight() * ZOMBIE_SCALE;
        double lurch = zombie.eating()
                ? Math.abs(Math.sin(seconds * 9)) * height * 0.05
                : Math.sin(seconds * 5 + zombie.x()) * height * 0.02;
        double x = tileX((int) Math.floor(zombie.x()))
                + (zombie.x() - Math.floor(zombie.x())) * tileWidth();
        double y = tileY(zombie.row() + 1) - height * 0.12 + lurch;

        gc.save();
        if (zombie.frozen()) {
            gc.setEffect(new javafx.scene.effect.ColorAdjust(0.55, 0.2, 0.1, 0));
        } else if (zombie.chilled()) {
            gc.setEffect(new javafx.scene.effect.ColorAdjust(0.5, -0.1, 0.05, 0));
        }
        drawSprite(gc, Assets.zombie(zombie.type()), x, y, height, Color.web("#8d9b6a"));
        gc.restore();

        int total = Math.max(1, zombie.hp() + zombie.armor());
        drawHealthBar(gc, x, y - height * 0.58, height * 0.55,
                zombie.hp() / (double) total, Color.web("#e74c3c"));
    }

    private void drawShot(GraphicsContext gc, Snapshot.ShotView shot) {
        double x = tileX((int) Math.floor(shot.col()))
                + (shot.col() - Math.floor(shot.col())) * tileWidth();
        double y = tileY(shot.row() + 1) - tileHeight() * 0.18;
        if (shot.lobbed()) {
            y -= Math.sin(shot.progress() * Math.PI) * tileHeight() * 0.65;
        }
        double size = tileWidth() * 0.18;
        gc.setFill(switch (shot.kind()) {
            case "ice" -> Color.web("#9fe4ff");
            case "fire" -> Color.web("#ff8b2e");
            case "lob" -> Color.web("#8fd14f");
            case "aoe" -> Color.web("#e0524a");
            default -> Color.web("#57c72b");
        });
        gc.fillOval(x - size / 2, y - size / 2, size, size);
    }
}
