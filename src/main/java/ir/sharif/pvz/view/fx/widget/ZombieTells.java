package ir.sharif.pvz.view.fx.widget;

import ir.sharif.pvz.model.game.Burst;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * What a zombie's trick looks like when it uses it.
 *
 * <p>Every one of them used to leave the same purple ring, so a player could
 * see that something had happened but never what: the sun-stealer taking a sun,
 * the hunter's ice, the gargantuar's hammer and the all-star's kick were
 * indistinguishable. Each is drawn as the thing it is here.
 *
 * <p>It sits apart from {@link LawnView} because that class is near the size
 * the project's linter allows. Geometry comes from the view.
 */
final class ZombieTells {

    private final LawnView lawn;

    ZombieTells(LawnView lawn) {
        this.lawn = lawn;
    }

    void draw(GraphicsContext gc, Burst.Kind kind, double x, double y, double t) {
        gc.save();
        gc.setGlobalAlpha(1 - t);
        switch (kind) {
            case SUN_STOLEN -> sunStolen(gc, x, y, t);
            case BONES -> bones(gc, x, y, t);
            case ICE_THROW -> ice(gc, x, y, t);
            case OCTOPUS_THROW -> octopus(gc, x, y, t);
            case KICK -> kick(gc, x, y, t);
            case SMASH -> smash(gc, x, y, t);
            case DIVE -> dive(gc, x, y, t);
            default -> { }
        }
        gc.restore();
    }

    /** A sun lifted off the lawn and carried away up the screen. */
    private void sunStolen(GraphicsContext gc, double x, double y, double t) {
        double size = lawn.tileHeight() * 0.34 * (1 - t * 0.4);
        double rise = t * lawn.tileHeight() * 0.9;
        gc.setFill(Color.web("#ffd94a"));
        gc.fillOval(x - size / 2, y - rise - size / 2, size, size);
        gc.setStroke(Color.web("#fff1a8", 0.8));
        gc.setLineWidth(2);
        for (int ray = 0; ray < 8; ray++) {
            double angle = ray * Math.PI / 4 + t * 3;
            double inner = size * 0.6;
            double outer = size * (0.75 + t * 0.5);
            gc.strokeLine(x + Math.cos(angle) * inner, y - rise + Math.sin(angle) * inner,
                    x + Math.cos(angle) * outer, y - rise + Math.sin(angle) * outer);
        }
    }

    /** Bones thrown out ahead of the grave-raiser. */
    private void bones(GraphicsContext gc, double x, double y, double t) {
        double reach = lawn.tileWidth() * t * 0.9;
        double size = lawn.tileHeight() * 0.16;
        gc.setFill(Color.web("#efe6cf"));
        for (int bone = 0; bone < 3; bone++) {
            double spread = (bone - 1) * lawn.tileHeight() * 0.22;
            gc.save();
            gc.translate(x - reach, y + spread * (1 + t));
            gc.rotate(t * 420 + bone * 60);
            gc.fillRoundRect(-size, -size * 0.22, size * 2, size * 0.44, size * 0.4, size * 0.4);
            gc.restore();
        }
    }

    /** The hunter's shard, thrown forward with a cold wash behind it. */
    private void ice(GraphicsContext gc, double x, double y, double t) {
        double reach = lawn.tileWidth() * t * 1.1;
        double size = lawn.tileHeight() * 0.22;
        gc.setFill(Color.web("#bfe9ff", 0.55));
        gc.fillOval(x - reach - size, y - size * 0.6, size * 2, size * 1.2);
        gc.setFill(Color.web("#8fd8ff"));
        gc.save();
        gc.translate(x - reach, y);
        gc.rotate(-t * 260);
        gc.beginPath();
        gc.moveTo(size, 0);
        gc.lineTo(-size * 0.5, -size * 0.55);
        gc.lineTo(-size * 0.2, 0);
        gc.lineTo(-size * 0.5, size * 0.55);
        gc.closePath();
        gc.fill();
        gc.restore();
    }

    /** An octopus lobbed at a plant, arms trailing. */
    private void octopus(GraphicsContext gc, double x, double y, double t) {
        double reach = lawn.tileWidth() * t * 1.2;
        double lift = Math.sin(t * Math.PI) * lawn.tileHeight() * 0.5;
        double size = lawn.tileHeight() * 0.26;
        double cx = x - reach;
        double cy = y - lift;
        gc.setFill(Color.web("#a05ac0"));
        gc.fillOval(cx - size / 2, cy - size / 2, size, size * 0.9);
        gc.setStroke(Color.web("#a05ac0"));
        gc.setLineWidth(Math.max(1.5, size * 0.14));
        for (int arm = 0; arm < 4; arm++) {
            double wobble = Math.sin(t * 12 + arm) * size * 0.3;
            gc.strokeLine(cx - size * 0.3 + arm * size * 0.2, cy + size * 0.35,
                    cx - size * 0.3 + arm * size * 0.2 + wobble, cy + size * 0.9);
        }
    }

    /** The all-star's boot going through a plant. */
    private void kick(GraphicsContext gc, double x, double y, double t) {
        double swing = lawn.tileWidth() * (0.15 + t * 0.55);
        gc.setStroke(Color.web("#ffe08a"));
        gc.setLineWidth(Math.max(2.5, lawn.tileHeight() * 0.06));
        // three arcs sweeping forward, like the trail of a boot
        for (int arc = 0; arc < 3; arc++) {
            double r = swing * (0.6 + arc * 0.2);
            gc.strokeArc(x - r, y - r * 0.55, r * 2, r * 1.1, 150, 70, javafx.scene.shape.ArcType.OPEN);
        }
    }

    /** The gargantuar's hammer: a hard ring and cracks in the ground. */
    private void smash(GraphicsContext gc, double x, double y, double t) {
        double r = lawn.tileWidth() * (0.2 + t * 0.7);
        gc.setStroke(Color.web("#d8c9a8"));
        gc.setLineWidth(Math.max(3, lawn.tileHeight() * 0.08) * (1 - t));
        gc.strokeOval(x - r, y - r * 0.45, r * 2, r * 0.9);
        gc.setStroke(Color.web("#6b5540"));
        gc.setLineWidth(Math.max(1.5, lawn.tileHeight() * 0.035));
        for (int crack = 0; crack < 5; crack++) {
            double angle = crack * (Math.PI * 2 / 5) + 0.4;
            gc.strokeLine(x, y, x + Math.cos(angle) * r * 0.9, y + Math.sin(angle) * r * 0.45);
        }
    }

    /**
     * The snorkel tube and the ripple around it, drawn over a zombie that is
     * under the water so the player can see it is there and why nothing is
     * hitting it.
     */
    void drawSnorkelWake(GraphicsContext gc, double x, double y, double seconds) {
        double h = lawn.tileHeight();
        gc.save();
        gc.setStroke(Color.web("#bfeaff", 0.85));
        gc.setLineWidth(Math.max(1.5, h * 0.035));
        double bob = Math.sin(seconds * 3) * h * 0.02;
        for (int ring = 1; ring <= 2; ring++) {
            double r = h * 0.16 * ring;
            gc.strokeOval(x - r, y + bob - r * 0.35, r * 2, r * 0.7);
        }
        gc.setStroke(Color.web("#f2f7d8"));
        gc.setLineWidth(Math.max(2, h * 0.05));
        gc.strokeLine(x, y + bob, x, y + bob - h * 0.26);
        gc.restore();
    }

    /** A snorkel breaking the surface, or going back under it. */
    private void dive(GraphicsContext gc, double x, double y, double t) {
        gc.setStroke(Color.web("#bfeaff"));
        gc.setLineWidth(Math.max(2, lawn.tileHeight() * 0.05));
        for (int ring = 1; ring <= 3; ring++) {
            double r = lawn.tileWidth() * 0.18 * ring * (0.6 + t);
            gc.strokeOval(x - r, y - r * 0.4, r * 2, r * 0.8);
        }
    }
}
