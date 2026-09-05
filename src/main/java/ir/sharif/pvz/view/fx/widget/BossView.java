package ir.sharif.pvz.view.fx.widget;

import ir.sharif.pvz.model.game.BossShot;
import ir.sharif.pvz.model.game.BossSweep;
import ir.sharif.pvz.model.game.Chapter;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.Zomboss;
import ir.sharif.pvz.view.fx.Assets;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/**
 * Zomboss and the two attacks it throws.
 *
 * <p>The boss used to be a sprite that bobbed, and both of its moves were the
 * same orange explosion however different the chapter said they were: a rocket,
 * a fireball, an icy shard and a pack of sharks all looked alike, and none of
 * them came from the boss. Here each shot flies out of it in its own colours,
 * the wide move is a front crossing the rows rather than a burst dropped in
 * each, and the boss itself winds up as it throws, flinches when hit and
 * topples when the last part of its armour goes.
 *
 * <p>It sits apart from {@link LawnView} because that class is near the size
 * the project's linter allows. Geometry and sprite drawing come from the view.
 */
final class BossView {

    private final LawnView lawn;

    BossView(LawnView lawn) {
        this.lawn = lawn;
    }

    // ===== the boss itself =====

    /**
     * The boss, leaning into a throw, shaking off a hit, or falling over.
     */
    void drawBoss(GraphicsContext gc, GameSession session, String chapterId, double seconds) {
        Zomboss boss = session.getZomboss();
        if (boss == null || boss.hasFinishedFalling()) {
            return;
        }
        double height = lawn.tileHeight() * boss.getRows() * 0.95;
        double centreX = lawn.tileX(boss.getColumn()) - lawn.tileWidth() * 0.1;
        double centreY = lawn.tileY(boss.getRow() + 1)
                + lawn.tileHeight() * (boss.getRows() - 1) / 2.0;

        double fall = boss.fall();
        double flinch = boss.flinch();
        if (!boss.isStunned() && fall == 0) {
            centreY += Math.sin(seconds * 1.8) * lawn.tileHeight() * 0.04;
        }
        if (boss.isStunned()) {
            // dazed: it sags and sways rather than holding itself up
            centreY += lawn.tileHeight() * 0.05;
            centreX += Math.sin(seconds * 9) * lawn.tileWidth() * 0.06;
        }
        centreX += lungeOffset(boss) + flinch * lawn.tileWidth() * 0.10;

        gc.save();
        gc.translate(centreX, centreY + fall * height * 0.30);
        gc.rotate(fall * 78);
        if (fall > 0) {
            gc.setGlobalAlpha(1 - fall * 0.85);
        } else if (boss.isStunned()) {
            gc.setGlobalAlpha(0.65);
        }
        lawn.drawSprite(gc, Assets.image("bosses/" + chapterId), 0, 0, height,
                Color.web("#6c7a52"));
        gc.restore();

        if (flinch > 0) {
            flash(gc, centreX, centreY, height, flinch);
        }
        if (boss.isStunned()) {
            drawStunStars(gc, centreX, centreY - height * 0.42, height, seconds);
        }
    }

    /**
     * How far the boss is thrown back and then forward by its own throw: it
     * rocks backward first, which is what makes the shot read as launched.
     */
    private double lungeOffset(Zomboss boss) {
        double t = boss.lunge();
        if (t <= 0) {
            return 0;
        }
        return Math.sin(t * Math.PI * 2) * lawn.tileWidth() * 0.22;
    }

    /** The red wash over the boss at the moment a shot lands on it. */
    private void flash(GraphicsContext gc, double x, double y, double height, double strength) {
        gc.save();
        gc.setGlobalAlpha(strength * 0.55);
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#ff5a4a")),
                new Stop(1, Color.web("#ff5a4a", 0))));
        gc.fillOval(x - height * 0.42, y - height * 0.5, height * 0.84, height);
        gc.restore();
    }

    /** Stars going round a dazed boss, so the stun is visible on the lawn. */
    private void drawStunStars(GraphicsContext gc, double x, double y,
                               double height, double seconds) {
        gc.save();
        gc.setFill(Color.web("#ffe680"));
        for (int star = 0; star < 3; star++) {
            double angle = seconds * 3 + star * (Math.PI * 2 / 3);
            double sx = x + Math.cos(angle) * height * 0.26;
            double sy = y + Math.sin(angle) * height * 0.07;
            double size = height * 0.05 * (0.75 + 0.25 * Math.sin(seconds * 6 + star));
            gc.fillOval(sx - size / 2, sy - size / 2, size, size);
        }
        gc.restore();
    }

    // ===== what it throws =====

    void drawShots(GraphicsContext gc, GameSession session, double seconds) {
        for (BossShot shot : session.getBossShots()) {
            if (shot.hasLanded()) {
                drawImpact(gc, shot);
            } else {
                drawInFlight(gc, shot, seconds);
            }
        }
    }

    private void drawInFlight(GraphicsContext gc, BossShot shot, double seconds) {
        double x = lawn.tileX(1) + (shot.getCol() - 1) * lawn.tileWidth();
        double y = lawn.tileY(1) + (shot.getRow() - 1) * lawn.tileHeight()
                - shot.getLift() * lawn.tileHeight();
        double size = lawn.tileHeight() * 0.58;

        drawTrail(gc, shot, x, y, size);
        gc.save();
        gc.translate(x, y);
        switch (shot.getKind()) {
            case ROCKET -> drawRocket(gc, shot, size);
            case FIREBALL -> drawFireball(gc, size, seconds);
            case ICE -> drawIceShard(gc, shot, size);
            case SHARKS -> drawSharks(gc, size, seconds);
        }
        gc.restore();
    }

    /** The line of smoke, sparks or spray a shot leaves behind it. */
    private void drawTrail(GraphicsContext gc, BossShot shot, double x, double y, double size) {
        gc.save();
        Color tint = trailColour(shot.getKind());
        for (int puff = 1; puff <= 5; puff++) {
            double back = puff * size * 0.42;
            double fade = 0.40 * (1 - puff / 6.0);
            gc.setGlobalAlpha(fade * Math.min(1, shot.flight() * 4));
            gc.setFill(tint);
            double blob = size * (0.45 - puff * 0.05);
            gc.fillOval(x + back - blob / 2, y - blob / 2 + puff * size * 0.10, blob, blob);
        }
        gc.restore();
    }

    private Color trailColour(BossShot.Kind kind) {
        return switch (kind) {
            case ROCKET -> Color.web("#d9d2c4");
            case FIREBALL -> Color.web("#ff9b3d");
            case ICE -> Color.web("#bfe9ff");
            case SHARKS -> Color.web("#9fd8e8");
        };
    }

    private void drawRocket(GraphicsContext gc, BossShot shot, double size) {
        gc.rotate(shot.getAngle());
        gc.setFill(Color.web("#b8352c"));
        gc.fillOval(-size * 0.6, -size * 0.22, size * 1.2, size * 0.44);
        gc.setFill(Color.web("#e8e2d0"));
        gc.fillOval(-size * 0.62, -size * 0.10, size * 0.42, size * 0.20);
        gc.setFill(Color.web("#ffcf5a"));
        gc.fillOval(size * 0.42, -size * 0.16, size * 0.30, size * 0.32);
    }

    private void drawFireball(GraphicsContext gc, double size, double seconds) {
        double flicker = 1 + 0.12 * Math.sin(seconds * 30);
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#fff2b0")),
                new Stop(0.45, Color.web("#ff8a1f")),
                new Stop(1, Color.web("#c02f0c", 0.15))));
        double r = size * 0.72 * flicker;
        gc.fillOval(-r, -r, r * 2, r * 2);
    }

    private void drawIceShard(GraphicsContext gc, BossShot shot, double size) {
        gc.rotate(shot.getAngle());
        gc.setFill(Color.web("#8fd8ff", 0.95));
        gc.beginPath();
        gc.moveTo(size * 0.75, 0);
        gc.lineTo(-size * 0.35, -size * 0.34);
        gc.lineTo(-size * 0.15, 0);
        gc.lineTo(-size * 0.35, size * 0.34);
        gc.closePath();
        gc.fill();
        gc.setStroke(Color.web("#e8fbff"));
        gc.setLineWidth(Math.max(1, size * 0.06));
        gc.stroke();
    }

    private void drawSharks(GraphicsContext gc, double size, double seconds) {
        gc.setFill(Color.web("#4a6b80"));
        for (int shark = 0; shark < 3; shark++) {
            double wobble = Math.sin(seconds * 12 + shark * 2) * size * 0.18;
            double sx = shark * size * 0.34 - size * 0.34;
            gc.beginPath();
            gc.moveTo(sx - size * 0.30, wobble + size * 0.16);
            gc.lineTo(sx, wobble - size * 0.34);
            gc.lineTo(sx + size * 0.24, wobble + size * 0.16);
            gc.closePath();
            gc.fill();
        }
    }

    /**
     * What the shot leaves at the tile: fire and smoke for the hot ones, a
     * spread of frost for the ice, a splash for the sharks.
     */
    private void drawImpact(GraphicsContext gc, BossShot shot) {
        double x = lawn.tileX((int) Math.round(shot.getToCol()));
        double y = lawn.tileY((int) Math.round(shot.getToRow()));
        double t = shot.impact();
        double r = lawn.tileHeight() * (0.35 + t * 0.75);
        gc.save();
        gc.setGlobalAlpha(1 - t);
        switch (shot.getKind()) {
            case ICE -> {
                gc.setFill(Color.web("#cfefff", 0.85));
                gc.fillOval(x - r, y - r * 0.7, r * 2, r * 1.4);
                gc.setStroke(Color.web("#7fd0ff"));
                gc.setLineWidth(2.2);
                for (int spike = 0; spike < 6; spike++) {
                    double angle = spike * Math.PI / 3;
                    gc.strokeLine(x, y, x + Math.cos(angle) * r, y + Math.sin(angle) * r * 0.7);
                }
            }
            case SHARKS -> {
                gc.setStroke(Color.web("#bfeaff"));
                gc.setLineWidth(Math.max(2, r * 0.10));
                for (int ring = 1; ring <= 3; ring++) {
                    double rr = r * ring / 3.0;
                    gc.strokeOval(x - rr, y - rr * 0.55, rr * 2, rr * 1.1);
                }
            }
            default -> lawn.drawExplosion(gc, x, y, t);
        }
        gc.restore();
    }

    // ===== the wide attack =====

    /**
     * The front of the wide move crossing the rows the boss faces, in the
     * colours of whatever the chapter calls it.
     */
    void drawSweep(GraphicsContext gc, GameSession session) {
        BossSweep sweep = session.getBossSweep();
        if (sweep == null) {
            return;
        }
        double t = sweep.progress();
        double right = lawn.tileX(GameSession.COLS) + lawn.tileWidth() / 2;
        double left = lawn.tileX(1) - lawn.tileWidth() / 2;
        double front = right - (right - left) * t;
        double top = lawn.tileY(sweep.getTopRow() + 1) - lawn.tileHeight() / 2;
        double height = lawn.tileHeight() * sweep.getRows();
        Color tint = sweepColour(sweep.getChapter());

        gc.save();
        // the wall itself, brightest at the front and trailing away behind it
        gc.setGlobalAlpha(0.70 * (1 - t * 0.55));
        gc.setFill(tint);
        gc.fillRect(front, top, right - front, height);
        gc.setGlobalAlpha(0.95 * (1 - t * 0.4));
        gc.setFill(Color.WHITE);
        gc.fillRect(front, top, lawn.tileWidth() * 0.12, height);
        gc.restore();
    }

    private Color sweepColour(Chapter chapter) {
        return switch (chapter) {
            case DARK_AGES -> Color.web("#ff7a1f");
            case ANCIENT_EGYPT -> Color.web("#e0b455");
            case FROSTBITE_CAVES -> Color.web("#a9e6ff");
            case BIG_WAVE_BEACH -> Color.web("#3fa3d8");
        };
    }
}
