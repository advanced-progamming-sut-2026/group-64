package ir.sharif.pvz.view.fx.widget;

import ir.sharif.pvz.model.game.Debris;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.PlantFoodShow;
import ir.sharif.pvz.model.game.Sandstorm;
import ir.sharif.pvz.model.game.Zombie;
import ir.sharif.pvz.view.fx.Assets;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

/**
 * The things on the lawn that flash and fade: a plant showing off its plant
 * food, the bits coming off a zombie, the sandstorm crossing the board, the
 * ring under a zombie that is nearly at the house, and the plant food waiting
 * to be picked up.
 *
 * <p>They sit here rather than in {@link LawnView} because that class had
 * grown past what the project's linter allows, and none of this is about
 * drawing the board itself. Geometry and sprite drawing come from the view.
 */
final class LawnEffects {

    /** A zombie this far across is close enough to the house to be flagged. */
    private static final double DANGER_COLUMN = 2.2;

    private final LawnView lawn;

    LawnEffects(LawnView lawn) {
        this.lawn = lawn;
    }

    /**
     * A plant showing off what its plant food just did. Every family gets its
     * own show, because "the plant food animation" in the document means the
     * plant doing its own thing rather than one flash for all of them: the sun
     * producers throw sun into the air, the shooters flare as they rake the
     * lane, the walls put on armour, the melee plants send out shock rings and
     * the mints wash green over their family.
     */
    void drawPlantFoodShows(GraphicsContext gc, GameSession session) {
        for (PlantFoodShow show : session.getPlantFoodShows()) {
            double x = lawn.tileX(show.getCol());
            double y = lawn.tileY(show.getRow());
            double t = show.progress();
            drawSwellingPlant(gc, show, x, y, t);
            switch (show.getFamily()) {
                case SUN_PRODUCER -> throwSun(gc, x, y, t);
                case WALL -> drawArmourShine(gc, x, y, t);
                case MELEE -> drawShockRings(gc, x, y, t);
                case EXPLOSIVE, TRAP -> lawn.drawExplosion(gc, x, y, Math.min(1, t * 1.6));
                case MODIFIER, MINT -> drawFamilyWave(gc, x, y, t);
                default -> drawMuzzleFlare(gc, x, y, t);
            }
        }
    }


    /**
     * The plant itself swells and glows for the length of the show, which is
     * what makes it read as that plant's moment rather than a stray effect.
     */
    void drawSwellingPlant(GraphicsContext gc, PlantFoodShow show,
                                   double x, double y, double t) {
        Image art = Assets.plant(show.getPlant());
        if (art == null) {
            return;
        }
        double swell = 1 + 0.35 * Math.sin(t * Math.PI);
        double height = lawn.tileHeight() * 0.95 * swell;
        gc.save();
        gc.setGlobalAlpha(1 - t * 0.15);
        lawn.drawSprite(gc, art, x, y, height, Color.web("#6ec03a"));
        gc.setGlobalAlpha((1 - t) * 0.55);
        gc.setFill(Color.web("#b6ff5a"));
        double halo = lawn.tileHeight() * (0.5 + t * 0.7);
        gc.fillOval(x - halo / 2, y - halo / 2, halo, halo);
        gc.restore();
    }


    /**
     * Sun thrown up and out, for the producers.
     */
    void throwSun(GraphicsContext gc, double x, double y, double t) {
        Image art = Assets.ui("sun-small");
        gc.save();
        gc.setGlobalAlpha(1 - t);
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI * (0.15 + 0.14 * i);
            double travel = lawn.tileHeight() * 1.6 * t;
            lawn.drawSprite(gc, art, x + Math.cos(angle) * travel,
                    y - Math.sin(angle) * travel + lawn.tileHeight() * t * t * 0.9,
                    lawn.tileHeight() * 0.32, Color.web("#ffd83d"));
        }
        gc.restore();
    }


    /**
     * A metal sheen sweeping over a wall that has just been armoured.
     */
    void drawArmourShine(GraphicsContext gc, double x, double y, double t) {
        gc.save();
        gc.setGlobalAlpha((1 - t) * 0.8);
        gc.setStroke(Color.web("#dfe6ec"));
        gc.setLineWidth(lawn.tileHeight() * 0.08);
        double size = lawn.tileHeight() * (0.7 + t * 0.4);
        gc.strokeOval(x - size / 2, y - size / 2, size, size);
        gc.restore();
    }


    /**
     * Rings thumping outward from a melee plant.
     */
    void drawShockRings(GraphicsContext gc, double x, double y, double t) {
        gc.save();
        gc.setStroke(Color.web("#ff8f4d"));
        for (int ring = 0; ring < 3; ring++) {
            double phase = t + ring * 0.25;
            if (phase > 1) {
                continue;
            }
            gc.setGlobalAlpha(1 - phase);
            gc.setLineWidth(lawn.tileHeight() * 0.06);
            double size = lawn.tileHeight() * (0.4 + phase * 1.8);
            gc.strokeOval(x - size / 2, y - size / 2, size, size);
        }
        gc.restore();
    }


    /**
     * The green wash a mint sends over the plants of its own family.
     */
    void drawFamilyWave(GraphicsContext gc, double x, double y, double t) {
        gc.save();
        gc.setGlobalAlpha((1 - t) * 0.5);
        gc.setFill(Color.web("#7ee06b"));
        double width = lawn.getWidth() * t;
        gc.fillOval(x - width / 2, y - lawn.tileHeight() * 0.6, width, lawn.tileHeight() * 1.2);
        gc.restore();
    }


    /**
     * The flare of a shooter opening up; the volley itself is drawn as shots.
     */
    void drawMuzzleFlare(GraphicsContext gc, double x, double y, double t) {
        gc.save();
        gc.setGlobalAlpha(1 - t);
        gc.setFill(Color.web("#c9ff6b"));
        double size = lawn.tileHeight() * 0.4 * (1 - t);
        gc.fillOval(x + lawn.tileWidth() * 0.35, y - size / 2, size, size);
        gc.restore();
    }


    /**
     * The heads, arms and armour tumbling off the zombies. They are drawn over
     * the lawn because they leave the zombie and land in front of it.
     */
    void drawDebris(GraphicsContext gc, GameSession session) {
        for (Debris piece : session.getDebris()) {
            Image art = Assets.image("parts/" + piece.getArt());
            if (art == null) {
                continue;
            }
            double height = lawn.tileHeight() * (piece.getKind() == Debris.Kind.ARM ? 0.28 : 0.42);
            double width = height * art.getWidth() / art.getHeight();
            double x = lawn.tileX(1) + (piece.getCol() - 1) * lawn.tileWidth();
            double y = lawn.tileY(piece.getRow() + 1) - piece.getLift() * lawn.tileHeight();
            gc.save();
            gc.setGlobalAlpha(piece.getOpacity());
            gc.translate(x, y);
            gc.rotate(Math.toDegrees(piece.getSpin()));
            gc.drawImage(art, -width / 2, -height / 2, width, height);
            gc.restore();
        }
    }


    /**
     * The Ancient Egypt sandstorm, in the two layers the artwork comes in: one
     * behind the lawn and one over the top of it, so the plants sit inside the
     * storm rather than behind a sheet of sand.
     */
    void drawSandstorm(GraphicsContext gc, GameSession session, String layer) {
        Sandstorm storm = session.getSandstorm();
        double now = session.getElapsedSeconds();
        double strength = storm.intensityAt(now);
        if (strength <= 0) {
            return;
        }
        Image art = Assets.image("props/sandstorm-" + layer);
        if (art == null) {
            return;
        }
        double centreX = lawn.tileX(1) + (storm.columnAt(now) - 1) * lawn.tileWidth();
        double height = lawn.getHeight() * 1.1;
        // a front a few tiles wide, so the sand is seen crossing the lawn
        // rather than washing the whole picture in one colour
        double width = lawn.tileWidth() * 2.6;
        gc.save();
        gc.setGlobalAlpha("front".equals(layer) ? strength * 0.5 : strength * 0.75);
        for (int i = -1; i <= 1; i++) {
            gc.drawImage(art, centreX - width / 2 + i * width * 0.62,
                    lawn.getHeight() / 2 - height / 2, width, height);
        }
        gc.restore();
    }


    /**
     * A pulsing ring under any zombie that is nearly at the house, so the
     * player can see which lane is about to go.
     */
    void drawDangerRings(GraphicsContext gc, GameSession session, double seconds) {
        Image art = Assets.ui("alert-ring");
        for (Zombie zombie : session.getZombies()) {
            if (zombie.getX() > DANGER_COLUMN) {
                continue;
            }
            double pulse = 0.75 + 0.25 * Math.sin(seconds * 6);
            double height = lawn.tileHeight() * 0.5 * pulse;
            gc.save();
            gc.setGlobalAlpha(0.85);
            lawn.drawSprite(gc, art, lawn.tileX(1) + (zombie.getX() - 1) * lawn.tileWidth(),
                    lawn.tileY(zombie.getRow() + 1) + lawn.tileHeight() * 0.32, height,
                    Color.web("#e74c3c"));
            gc.restore();
        }
    }


    /**
     * Plant food a glowing zombie left behind, bobbing until it is picked up.
     */
    void drawDroppedPlantFood(GraphicsContext gc, GameSession session, double seconds) {
        Image art = Assets.ui("plant-food");
        for (int[] tile : session.getDroppedPlantFood()) {
            double bob = Math.sin(seconds * 4 + tile[0]) * lawn.tileHeight() * 0.06;
            lawn.drawSprite(gc, art, lawn.tileX(tile[0]), lawn.tileY(tile[1]) + bob,
                    lawn.tileHeight() * 0.55, Color.web("#8bc34a"));
        }
    }

}
