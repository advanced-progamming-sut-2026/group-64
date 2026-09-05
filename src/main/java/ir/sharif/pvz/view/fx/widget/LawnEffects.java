package ir.sharif.pvz.view.fx.widget;

import ir.sharif.pvz.model.game.Debris;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.PlantFoodShow;
import ir.sharif.pvz.model.game.Weather;
import ir.sharif.pvz.model.game.Zombie;
import ir.sharif.pvz.view.fx.Assets;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

/**
 * The things on the lawn that flash and fade: a plant showing off its plant
 * food, the bits coming off a zombie, the weather crossing the board, the
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
            if (piece.getKind() == Debris.Kind.BODY) {
                drawBody(gc, piece);
                continue;
            }
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
     * A zombie going down. It keels over onto its back, sinking as it goes,
     * and then goes to dust: the sprite fades while flecks of it drift up off
     * the lawn. Before this the body simply stopped being drawn on the frame
     * its health ran out, which made a kill read as a disappearance.
     */
    private void drawBody(GraphicsContext gc, Debris piece) {
        Image art = Assets.zombie(piece.getArt());
        double topple = piece.getTopple();
        double crumble = piece.getCrumble();
        double height = lawn.tileHeight() * LawnView.ZOMBIE_SCALE;
        double x = lawn.tileX(1) + (piece.getCol() - 1) * lawn.tileWidth();
        double y = lawn.tileY(piece.getRow() + 1) - height * 0.12;

        if (crumble < 1) {
            gc.save();
            gc.setGlobalAlpha((1 - crumble) * (1 - crumble));
            // it pivots about its feet rather than its middle, so it falls
            // over instead of sliding down the screen
            gc.translate(x, y + height * 0.42);
            gc.rotate(topple * 84);
            gc.translate(0, -height * 0.42);
            lawn.drawSprite(gc, art, 0, 0, height, Color.web("#8d9b6a"));
            gc.restore();
        }
        if (crumble > 0) {
            drawDust(gc, x, y, height, crumble);
        }
    }

    /** The flecks a crumbling body gives off, drifting up and thinning out. */
    private void drawDust(GraphicsContext gc, double x, double y, double height, double t) {
        gc.save();
        gc.setFill(Color.web("#9bbf6a"));
        for (int fleck = 0; fleck < 10; fleck++) {
            double angle = fleck * 2.4;
            double spread = height * 0.30 * t;
            double fx = x + Math.cos(angle) * spread;
            double fy = y + Math.sin(angle) * spread * 0.5 - t * height * 0.45;
            double size = height * 0.07 * (1 - t);
            gc.setGlobalAlpha(0.75 * (1 - t));
            gc.fillOval(fx - size / 2, fy - size / 2, size, size);
        }
        gc.restore();
    }

    /**
     * The chapter's weather, in two layers: one behind the lawn and one over
     * the top of it, so the plants sit inside the storm rather than behind a
     * sheet of it. Egypt's sand has artwork; the ice caves' gale is drawn.
     */
    void drawWeather(GraphicsContext gc, GameSession session, String layer) {
        Weather storm = session.getWeather();
        double now = session.getElapsedSeconds();
        double strength = storm.intensityAt(now);
        if (strength <= 0) {
            return;
        }
        double centre = lawn.tileX(1) + (storm.columnAt(now) - 1) * lawn.tileWidth();
        if (storm.kind() == Weather.Kind.ICE) {
            drawIcyGale(gc, layer, centre, strength, now);
            return;
        }
        Image art = Assets.image("props/sandstorm-" + layer);
        if (art == null) {
            return;
        }
        double centreX = centre;
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
     * The Frostbite Caves' gale. There is no artwork for it, so it is drawn:
     * a pale wash with snow streaking across it, leaning the way it blows.
     */
    private void drawIcyGale(GraphicsContext gc, String layer, double centreX,
                             double strength, double seconds) {
        double width = lawn.tileWidth() * 3.2;
        double top = 0;
        double height = lawn.getHeight();
        gc.save();
        gc.setGlobalAlpha(strength * ("front".equals(layer) ? 0.28 : 0.45));
        gc.setFill(Color.web("#dff2ff"));
        gc.fillRect(centreX - width / 2, top, width, height);

        gc.setGlobalAlpha(strength * ("front".equals(layer) ? 0.75 : 0.5));
        gc.setStroke(Color.web("#ffffff"));
        gc.setLineWidth(Math.max(1.2, lawn.tileHeight() * 0.03));
        // streaks of snow, each on its own drift so the gale does not pulse
        for (int flake = 0; flake < 26; flake++) {
            double lane = (flake * 37 % 100) / 100.0;
            double drift = ((seconds * 1.5) + lane * 3) % 1.0;
            double x = centreX + width / 2 - drift * width * 1.6;
            double y = top + lane * height;
            double dash = lawn.tileWidth() * 0.22;
            gc.strokeLine(x, y, x + dash, y + dash * 0.35);
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
