package ir.sharif.pvz.view.fx.widget;

import ir.sharif.pvz.model.game.Burst;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.Plant;
import ir.sharif.pvz.model.game.MinigameProp;
import ir.sharif.pvz.model.game.Shot;
import ir.sharif.pvz.model.game.SpecialRules;
import ir.sharif.pvz.model.game.Sun;
import ir.sharif.pvz.model.game.TileTerrain;
import ir.sharif.pvz.model.game.Zombie;
import ir.sharif.pvz.model.game.Zomboss;
import ir.sharif.pvz.view.fx.Assets;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * Draws the lawn: the chapter background, then the mowers, plants, zombies and
 * loose suns on top of it.
 *
 * <p>Everything is painted onto one canvas each frame rather than kept as a
 * node per entity, which keeps the 9x5 board cheap to redraw ten times a
 * second and makes the drawing order — back rows behind front rows — explicit.
 *
 * <p>The grid rectangle is expressed as fractions of the background art so the
 * tiles keep lining up with the painted lawn at any window size.
 */
public class LawnView extends Canvas {

    private static final double GRID_LEFT = 0.2461;
    private static final double GRID_TOP = 0.2500;
    private static final double GRID_RIGHT = 0.9883;
    private static final double GRID_BOTTOM = 0.9010;

    private static final double PLANT_SCALE = 0.78;
    private static final double ZOMBIE_SCALE = 1.05;
    private static final double SUN_SCALE = 0.46;

    private final String chapterId;
    private boolean showGrid;
    private int hoverCol = -1;
    private int hoverRow = -1;
    private boolean hoverActive;

    public LawnView(String chapterId) {
        this.chapterId = chapterId;
        setWidth(GameGeometry.WIDTH);
        setHeight(GameGeometry.HEIGHT);
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    /**
     * Repaints the whole lawn from the session's current state.
     */
    /**
     * Marks the tile under the cursor so the player can see exactly where a
     * plant, the shovel or a plant food is about to land. Passing a column of
     * -1, or active = false, clears it.
     */
    public void setHoveredTile(int col, int row, boolean active) {
        this.hoverCol = col;
        this.hoverRow = row;
        this.hoverActive = active;
    }

    public void render(GameSession session, double seconds) {
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());

        double shake = shakeAmount(session);
        gc.save();
        if (shake > 0) {
            gc.translate(Math.sin(seconds * 70) * shake, Math.cos(seconds * 61) * shake);
        }
        drawBackground(gc);
        if (showGrid) {
            drawGrid(gc);
        }
        drawTerrain(gc, session);
        drawTideLine(gc, session);
        drawSpecialMarkers(gc, session);
        drawHoveredTile(gc);
        drawMowers(gc, session);
        drawSuns(gc, session);
        drawRows(gc, session, seconds);
        drawZomboss(gc, session, seconds);
        drawBursts(gc, session);
        gc.restore();
    }

    /**
     * The boss itself, sitting on the right across the rows it covers. It sways
     * gently, and slumps still and pale while it is stunned.
     */
    private void drawZomboss(GraphicsContext gc, GameSession session, double seconds) {
        Zomboss boss = session.getZomboss();
        if (boss == null || boss.isDefeated()) {
            return;
        }
        double height = tileHeight() * boss.getRows() * 0.95;
        // sat just inside the last column so the whole sprite stays on screen
        double centreX = tileX(boss.getColumn()) - tileWidth() * 0.1;
        double centreY = tileY(boss.getRow() + 1) + tileHeight() * (boss.getRows() - 1) / 2.0;
        if (!boss.isStunned()) {
            centreY += Math.sin(seconds * 1.8) * tileHeight() * 0.04;
        }
        gc.save();
        if (boss.isStunned()) {
            gc.setGlobalAlpha(0.65);
        }
        drawSprite(gc, Assets.image("bosses/" + chapterId), centreX, centreY, height,
                Color.web("#6c7a52"));
        gc.restore();
    }

    /**
     * How far the camera is thrown about right now, which is the strongest
     * shake any live explosion is asking for.
     */
    private static double shakeAmount(GameSession session) {
        double worst = 0;
        for (Burst burst : session.getBursts()) {
            worst = Math.max(worst, burst.shake());
        }
        return worst;
    }

    /**
     * The one-off effects: blasts, zombies coming apart, plants being lost.
     */
    private void drawBursts(GraphicsContext gc, GameSession session) {
        for (Burst burst : session.getBursts()) {
            double x = tileX((int) Math.floor(burst.getCol()))
                    + (burst.getCol() - Math.floor(burst.getCol())) * tileWidth();
            double y = tileY((int) Math.round(burst.getRow()));
            switch (burst.getKind()) {
                case EXPLOSION -> drawExplosion(gc, x, y, burst.progress());
                case ZOMBIE_DOWN -> drawParticles(gc, x, y, burst.progress(), 9,
                        Color.web("#9bbf6a"), tileHeight() * 0.5);
                case PLANT_LOST -> drawParticles(gc, x, y, burst.progress(), 7,
                        Color.web("#6ec03a"), tileHeight() * 0.4);
                case PLANT_FOOD -> drawPlantFoodGlow(gc, x, y, burst.progress());
                case MOWER -> drawParticles(gc, x, y, burst.progress(), 6,
                        Color.web("#d8d8d8"), tileHeight() * 0.35);
                default -> { }
            }
        }
    }

    /**
     * A blast: a white flash that swells into an orange fireball and fades.
     */
    private void drawExplosion(GraphicsContext gc, double x, double y, double t) {
        double radius = tileWidth() * (0.35 + t * 1.15);
        gc.setGlobalAlpha(1 - t);
        gc.setFill(Color.web("#ff9020"));
        gc.fillOval(x - radius, y - radius * 0.8, radius * 2, radius * 1.6);
        gc.setGlobalAlpha(Math.max(0, 0.9 - t * 2));
        gc.setFill(Color.web("#fff3c4"));
        gc.fillOval(x - radius * 0.55, y - radius * 0.45, radius * 1.1, radius * 0.9);
        gc.setGlobalAlpha(1);
        drawParticles(gc, x, y, t, 10, Color.web("#ffb24a"), tileWidth() * 0.9);
    }

    /**
     * Debris thrown outwards on fixed spokes, so it reads as a burst without
     * needing any per-particle state to be kept between frames.
     */
    private void drawParticles(GraphicsContext gc, double x, double y, double t,
                               int count, Color colour, double reach) {
        gc.setGlobalAlpha(Math.max(0, 1 - t));
        gc.setFill(colour);
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count + i * 0.37;
            double distance = reach * t;
            double size = tileWidth() * 0.09 * (1 - t);
            double px = x + Math.cos(angle) * distance;
            double py = y + Math.sin(angle) * distance * 0.7 - reach * t * 0.35;
            gc.fillOval(px - size, py - size, size * 2, size * 2);
        }
        gc.setGlobalAlpha(1);
    }

    /**
     * The glow the document asks for behind a plant that was just fed.
     */
    private void drawPlantFoodGlow(GraphicsContext gc, double x, double y, double t) {
        double radius = tileHeight() * (0.35 + t * 0.5);
        gc.setGlobalAlpha(Math.max(0, 0.85 - t));
        gc.setFill(Color.web("#8ef04a"));
        gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);
        gc.setGlobalAlpha(1);
    }

    protected void drawBackground(GraphicsContext gc) {
        Image art = Assets.background(chapterId);
        if (art == null) {
            gc.setFill(Color.web("#3f7d34"));
            gc.fillRect(0, 0, getWidth(), getHeight());
            return;
        }
        gc.drawImage(art, 0, 0, getWidth(), getHeight());
    }

    /**
     * A headstone, in the kind that matches what it is hiding: the three Dark
     * Ages graves are a plain one, one holding sun and one holding plant food.
     */
    private void drawGrave(GraphicsContext gc, GameSession session, int x, int y) {
        String holds = session.graveContentAt(x, y);
        String kind = switch (holds) {
            case "sun" -> "sun";
            case "plant food" -> "plantfood";
            default -> "empty";
        };
        Image art = Assets.image("props/grave-" + kind);
        drawSprite(gc, art, tileX(x), tileY(y), tileHeight() * 0.85, Color.web("#4a4550"));
        gc.setFill(Color.web("#e8e2d0"));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font(12));
        gc.fillText(String.valueOf(session.graveHpAt(x, y)), tileX(x), tileY(y) + tileHeight() * 0.44);
    }

    /**
     * The line the sea reaches at high tide, which on Big Wave Beach tells the
     * player how far left the water can ever come.
     */
    private void drawTideLine(GraphicsContext gc, GameSession session) {
        int leftmostWater = Integer.MAX_VALUE;
        for (int row = 1; row <= GameSession.ROWS; row++) {
            for (int col = 1; col <= GameSession.COLS; col++) {
                TileTerrain terrain = session.terrainAt(col, row);
                if (terrain == TileTerrain.WATER || terrain == TileTerrain.LILY) {
                    leftmostWater = Math.min(leftmostWater, col);
                }
            }
        }
        if (leftmostWater == Integer.MAX_VALUE) {
            return;
        }
        double x = tileX(leftmostWater) - tileWidth() / 2;
        double top = tileY(1) - tileHeight() / 2;
        double height = tileHeight() * GameSession.ROWS;
        gc.setStroke(Color.color(0.55, 0.9, 1, 0.85));
        gc.setLineWidth(2.5);
        gc.setLineDashes(7, 7);
        gc.strokeLine(x, top, x, top + height);
        gc.setLineDashes();
    }

    /**
     * The markers that belong to a special level: the line zombies must not
     * cross, and the tiles whose plants have to survive.
     */
    private void drawSpecialMarkers(GraphicsContext gc, GameSession session) {
        SpecialRules rules = session.getSpecial().getRules();
        if (rules == null) {
            return;
        }
        if (rules.getType() == SpecialRules.Type.DEAD_LINE) {
            drawDeadLine(gc, rules.getDeadlineColumn());
        }
        if (rules.getType() == SpecialRules.Type.SAVE_OUR_SEEDS) {
            drawProtectedTiles(gc, session);
        }
    }

    /**
     * The dead line, drawn as the hazard stripe the game uses.
     */
    private void drawDeadLine(GraphicsContext gc, int column) {
        double x = tileX(column) + tileWidth() / 2;
        double top = tileY(1) - tileHeight() / 2;
        double height = tileHeight() * GameSession.ROWS;
        gc.setFill(Color.color(0.85, 0.1, 0.1, 0.8));
        gc.fillRect(x - 3, top, 6, height);
        gc.setStroke(Color.color(1, 0.85, 0.2, 0.9));
        gc.setLineWidth(2);
        gc.setLineDashes(12, 10);
        gc.strokeLine(x, top, x, top + height);
        gc.setLineDashes();
    }

    /**
     * A hazard border around every tile holding a plant that must not die.
     */
    private void drawProtectedTiles(GraphicsContext gc, GameSession session) {
        for (int row = 1; row <= GameSession.ROWS; row++) {
            for (int col = 1; col <= GameSession.COLS; col++) {
                Plant plant = session.plantAtTile(col, row);
                if (plant == null || !session.isProtectedPlant(plant)) {
                    continue;
                }
                double left = tileX(col) - tileWidth() / 2;
                double top = tileY(row) - tileHeight() / 2;
                gc.setStroke(Color.web("#ffd23f"));
                gc.setLineWidth(3);
                gc.setLineDashes(9, 7);
                gc.strokeRect(left + 2, top + 2, tileWidth() - 4, tileHeight() - 4);
                gc.setLineDashes();
            }
        }
    }

    /**
     * The white marker on the tile the cursor is over, drawn only while the
     * player is actually holding something to put there. The document asks for
     * the row and column of the target to be unmistakable, so the whole row and
     * column are tinted and the tile itself is outlined.
     */
    private void drawHoveredTile(GraphicsContext gc) {
        if (!hoverActive || hoverCol < 1 || hoverRow < 1) {
            return;
        }
        double left = tileX(hoverCol) - tileWidth() / 2;
        double top = tileY(hoverRow) - tileHeight() / 2;
        double rowTop = tileY(hoverRow) - tileHeight() / 2;
        double colLeft = tileX(hoverCol) - tileWidth() / 2;

        gc.setFill(Color.color(1, 1, 1, 0.16));
        gc.fillRect(tileX(1) - tileWidth() / 2, rowTop,
                tileWidth() * GameSession.COLS, tileHeight());
        gc.fillRect(colLeft, tileY(1) - tileHeight() / 2,
                tileWidth(), tileHeight() * GameSession.ROWS);

        gc.setFill(Color.color(1, 1, 1, 0.28));
        gc.fillRect(left, top, tileWidth(), tileHeight());
        gc.setStroke(Color.color(1, 1, 1, 0.95));
        gc.setLineWidth(2.5);
        gc.strokeRect(left, top, tileWidth(), tileHeight());
    }

    /**
     * The optional red lawn grid from the settings menu.
     */
    private void drawGrid(GraphicsContext gc) {
        gc.setStroke(Color.color(1, 0.2, 0.2, 0.75));
        gc.setLineWidth(1.5);
        for (int col = 0; col <= GameSession.COLS; col++) {
            double x = tileX(col + 1) - tileWidth() / 2;
            gc.strokeLine(x, tileY(1) - tileHeight() / 2, x, tileY(GameSession.ROWS) + tileHeight() / 2);
        }
        for (int row = 0; row <= GameSession.ROWS; row++) {
            double y = tileY(row + 1) - tileHeight() / 2;
            gc.strokeLine(tileX(1) - tileWidth() / 2, y, tileX(GameSession.COLS) + tileWidth() / 2, y);
        }
    }

    /**
     * Water, ice, graves and the other tile kinds phase 1 defines.
     */
    private void drawTerrain(GraphicsContext gc, GameSession session) {
        for (int y = 1; y <= GameSession.ROWS; y++) {
            for (int x = 1; x <= GameSession.COLS; x++) {
                TileTerrain terrain = session.terrainAt(x, y);
                if (terrain == TileTerrain.NORMAL) {
                    continue;
                }
                paintTile(gc, x, y, terrain, session);
            }
        }
    }

    private void paintTile(GraphicsContext gc, int x, int y, TileTerrain terrain, GameSession session) {
        double left = tileX(x) - tileWidth() / 2;
        double top = tileY(y) - tileHeight() / 2;
        gc.setFill(tileColour(terrain));
        gc.fillRect(left, top, tileWidth(), tileHeight());
        if (terrain == TileTerrain.GRAVE) {
            drawGrave(gc, session, x, y);
        }
    }

    private Color tileColour(TileTerrain terrain) {
        return switch (terrain) {
            case WATER -> Color.color(0.16, 0.42, 0.72, 0.55);
            case LILY -> Color.color(0.24, 0.62, 0.34, 0.45);
            case SLIPPERY_UP, SLIPPERY_DOWN -> Color.color(0.72, 0.90, 0.98, 0.45);
            case GRAVE -> Color.color(0.16, 0.13, 0.18, 0.70);
            case SPAWNER -> Color.color(0.55, 0.20, 0.55, 0.40);
            default -> Color.TRANSPARENT;
        };
    }

    private void drawMowers(GraphicsContext gc, GameSession session) {
        for (int row = 0; row < GameSession.ROWS; row++) {
            if (!session.isMowerAvailable(row)) {
                continue;
            }
            double x = tileX(1) - tileWidth() * 0.85;
            double y = tileY(row + 1);
            gc.setFill(Color.web("#c0392b"));
            gc.fillRoundRect(x - 16, y - 11, 32, 22, 8, 8);
            gc.setFill(Color.web("#2c3e50"));
            gc.fillOval(x - 14, y + 4, 10, 10);
            gc.fillOval(x + 4, y + 4, 10, 10);
        }
    }

    private void drawSuns(GraphicsContext gc, GameSession session) {
        Image art = Assets.ui("sun");
        for (Sun sun : session.groundSuns()) {
            // a special sun is worth four times as much and is drawn bigger to
            // match; the radioactive one keeps its purple glow
            double size = tileHeight() * SUN_SCALE
                    * (sun.getKind() == Sun.Kind.SPECIAL ? 1.35 : 1.0);
            Point2D at = sunCentre(sun);
            boolean radioactive = sun.getKind() == Sun.Kind.RADIOACTIVE;
            if (radioactive) {
                gc.setFill(Color.color(0.66, 0.3, 0.9, 0.55));
                gc.fillOval(at.getX() - size * 0.7, at.getY() - size * 0.7, size * 1.4, size * 1.4);
            }
            if (art == null) {
                gc.setFill(radioactive ? Color.web("#c05bff") : Color.web("#ffd733"));
                gc.fillOval(at.getX() - size / 2, at.getY() - size / 2, size, size);
                continue;
            }
            gc.save();
            if (radioactive) {
                gc.setEffect(new javafx.scene.effect.ColorAdjust(0.62, 0.5, 0, 0));
            }
            gc.drawImage(art, at.getX() - size / 2, at.getY() - size / 2, size, size);
            gc.restore();
        }
    }

    /**
     * Row by row so a plant in a lower row overlaps the one behind it, which
     * the project document calls out as the correct draw order.
     */
    private void drawRows(GraphicsContext gc, GameSession session, double seconds) {
        for (int row = 1; row <= GameSession.ROWS; row++) {
            for (int col = 1; col <= GameSession.COLS; col++) {
                Plant plant = session.plantAtTile(col, row);
                if (plant != null) {
                    drawPlant(gc, session, plant, col, row, seconds);
                }
            }
            for (Zombie zombie : session.getZombies()) {
                if (zombie.getRow() == row - 1) {
                    drawZombie(gc, zombie, seconds);
                }
            }
            for (Shot shot : session.getShots()) {
                if (shot.getRow() == row - 1) {
                    drawShot(gc, shot);
                }
            }
            for (MinigameProp prop : props(session)) {
                if ((int) Math.round(prop.row()) == row) {
                    drawProp(gc, prop);
                }
            }
        }
    }

    /**
     * Whatever the running minigame wants on the lawn: vases to smash, walnuts
     * rolling down their lane.
     */
    public static java.util.List<MinigameProp> props(GameSession session) {
        return session.getMinigame() == null
                ? java.util.List.of()
                : session.getMinigame().props();
    }

    private void drawProp(GraphicsContext gc, MinigameProp prop) {
        Image art = switch (prop.kind()) {
            case "nut", "packet" -> Assets.plant(prop.art());
            default -> Assets.image("props/" + prop.art() + "-" + prop.kind());
        };
        double centreX = tileX((int) Math.floor(prop.col()))
                + (prop.col() - Math.floor(prop.col())) * tileWidth();
        double centreY = tileY((int) Math.round(prop.row()));
        if (art == null) {
            gc.setFill(Color.web("#b07a45"));
            gc.fillOval(centreX - tileWidth() * 0.22, centreY - tileHeight() * 0.24,
                    tileWidth() * 0.44, tileHeight() * 0.48);
        } else {
            drawSprite(gc, art, centreX, centreY, tileHeight() * 0.78, null);
        }
        if (prop.label() != null) {
            gc.setFill(Color.web("#ffe9a8"));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFont(Font.font(11));
            gc.fillText(prop.label(), centreX, centreY + tileHeight() * 0.44);
        }
    }

    /**
     * A projectile on its way to the zombie it already damaged. Straight shots
     * run level with the plant's mouth; lobbed ones arc over anything between.
     */
    private void drawShot(GraphicsContext gc, Shot shot) {
        double x = tileX((int) Math.floor(shot.currentX()))
                + (shot.currentX() - Math.floor(shot.currentX())) * tileWidth();
        double y = tileY(shot.getRow() + 1) - tileHeight() * 0.18;
        if (shot.getFlight() == Shot.Flight.LOBBED) {
            double t = shot.progress();
            y -= Math.sin(t * Math.PI) * tileHeight() * 0.65;
        }
        gc.setFill(shotColour(shot.getKind()));
        if ("laser".equals(shot.getKind())) {
            double alpha = 1 - shot.progress();
            gc.setGlobalAlpha(alpha);
            gc.fillRect(x - tileWidth() * 3, y - 3, tileWidth() * 6, 6);
            gc.setGlobalAlpha(1);
            return;
        }
        boolean heavy = "lob".equals(shot.getKind()) || "aoe".equals(shot.getKind());
        double size = tileWidth() * (heavy ? 0.26 : 0.18);
        if ("aoe".equals(shot.getKind())) {
            // a splash ring so an area shot is obvious before it even lands
            gc.setGlobalAlpha(0.35);
            gc.fillOval(x - size, y - size * 0.7, size * 2, size * 1.4);
            gc.setGlobalAlpha(1);
        }
        gc.fillOval(x - size / 2, y - size / 2, size, size);
        gc.setStroke(Color.color(0, 0, 0, 0.35));
        gc.setLineWidth(1);
        gc.strokeOval(x - size / 2, y - size / 2, size, size);
    }

    private static Color shotColour(String kind) {
        return switch (kind) {
            case "ice" -> Color.web("#9fe4ff");
            case "fire" -> Color.web("#ff8b2e");
            case "lob" -> Color.web("#8fd14f");
            case "aoe" -> Color.web("#e0524a");
            case "laser" -> Color.web("#6ff0ff");
            default -> Color.web("#57c72b");
        };
    }

    private void drawPlant(GraphicsContext gc, GameSession session, Plant plant,
                           int col, int row, double seconds) {
        Image art = Assets.plant(plant.getSpec().getName());
        double height = tileHeight() * PLANT_SCALE;
        // a gentle idle sway, so every plant reads as alive without needing
        // the original per-plant animation data
        double bob = Math.sin(seconds * 2.4 + col * 0.7 + row) * height * 0.03;
        double centreX = tileX(col);
        double centreY = tileY(row) + bob;

        if (plant.isBoosted()) {
            gc.setFill(Color.color(1, 0.85, 0.25, 0.35));
            gc.fillOval(centreX - height * 0.5, centreY - height * 0.5, height, height);
        }
        int ice = session.iceLevelAt(col, row);
        if (ice > 0) {
            drawSprite(gc, Assets.image("ice/plant-behind"), centreX, centreY, height * 1.25, null);
        }
        drawSprite(gc, art, centreX, centreY, height, Color.web("#4caf50"));
        if (ice > 0) {
            // one third more frost per hit, so all three phase-1 steps read apart
            gc.setGlobalAlpha(Math.min(1, ice / 3.0));
            drawSprite(gc, Assets.image("ice/plant-front"), centreX, centreY, height * 1.25, null);
            gc.setGlobalAlpha(1);
        }

        double healthy = plant.getHp() / (double) plant.maxHp();
        if (healthy < 0.999) {
            drawHealthBar(gc, centreX, centreY - height * 0.62, height * 0.8, healthy,
                    Color.web("#7cd12a"));
        }
        if (session.isPlantDisabled(col, row) && ice == 0) {
            gc.setFill(Color.color(0.3, 0.3, 0.45, 0.45));
            gc.fillRect(tileX(col) - tileWidth() / 2, tileY(row) - tileHeight() / 2,
                    tileWidth(), tileHeight());
            // a pinned plant wears the octopus that pinned it
            if ("octopus".equals(session.disableCauseAt(col, row))) {
                drawSprite(gc, Assets.zombie("octopus"), centreX, centreY - height * 0.15,
                        height * 0.7, Color.web("#8e44ad"));
            }
        }
    }

    /**
     * The zombie's sprite, swapped for its bare version once every piece of
     * armour has been knocked off, so a cone or bucket stops being drawn the
     * moment it is gone.
     */
    private static Image zombieArt(Zombie zombie) {
        String name = zombie.getSpec().getName();
        if (!zombie.getSpec().getArmor().isEmpty() && zombie.getArmor().isEmpty()) {
            Image bare = Assets.zombie(name + "-bare");
            if (bare != null) {
                return bare;
            }
        }
        return Assets.zombie(name);
    }

    private void drawZombie(GraphicsContext gc, Zombie zombie, double seconds) {
        Image art = zombieArt(zombie);
        double height = tileHeight() * ZOMBIE_SCALE;
        // a biting zombie rocks forward sharply; a walking one just sways
        double lurch = zombie.isEating()
                ? Math.abs(Math.sin(seconds * 9)) * height * 0.05
                : Math.sin(seconds * 5 + zombie.getX()) * height * 0.02;
        double centreX = tileX((int) Math.floor(zombie.getX()))
                + (zombie.getX() - Math.floor(zombie.getX())) * tileWidth();
        double centreY = tileY(zombie.getRow() + 1) - height * 0.12 + lurch;
        if (zombie.isEating()) {
            centreX -= Math.abs(Math.sin(seconds * 9)) * tileWidth() * 0.08;
        }

        if (zombie.isFrozen()) {
            drawSprite(gc, Assets.image("ice/zombie-behind"), centreX, centreY, height * 1.2, null);
        }
        gc.save();
        applyZombieTint(gc, zombie);
        drawSprite(gc, art, centreX, centreY, height, Color.web("#8d9b6a"));
        gc.restore();
        if (zombie.isFrozen()) {
            drawSprite(gc, Assets.image("ice/zombie-front"), centreX, centreY, height * 1.2, null);
        }

        double healthy = zombie.totalRemainingHealth()
                / (double) Math.max(1, zombie.getSpec().getHp() + armourTotal(zombie));
        drawHealthBar(gc, centreX, centreY - height * 0.58, height * 0.55, healthy,
                Color.web("#e74c3c"));
    }

    /**
     * Frozen zombies go blue and slowed ones pale, the cheap version of the
     * status effects the document asks to make visible.
     */
    private void applyZombieTint(GraphicsContext gc, Zombie zombie) {
        if (zombie.isFrozen()) {
            gc.setGlobalAlpha(0.85);
            gc.setEffect(new javafx.scene.effect.ColorAdjust(0.55, 0.2, 0.1, 0));
        } else if (zombie.speedMultiplier() < 1) {
            gc.setEffect(new javafx.scene.effect.ColorAdjust(0.5, -0.1, 0.05, 0));
        } else if (zombie.isGlowing()) {
            gc.setEffect(new javafx.scene.effect.ColorAdjust(0, 0.4, 0.25, 0));
        }
    }

    private int armourTotal(Zombie zombie) {
        return zombie.getSpec().getArmor().values().stream().mapToInt(Integer::intValue).sum();
    }

    protected void drawSprite(GraphicsContext gc, Image art, double centreX, double centreY,
                            double height, Color fallback) {
        if (art == null) {
            gc.setFill(fallback);
            gc.fillOval(centreX - height * 0.3, centreY - height * 0.4, height * 0.6, height * 0.8);
            return;
        }
        double width = height * art.getWidth() / art.getHeight();
        gc.drawImage(art, centreX - width / 2, centreY - height / 2, width, height);
    }

    protected void drawHealthBar(GraphicsContext gc, double centreX, double y,
                               double width, double fraction, Color colour) {
        double clamped = Math.max(0, Math.min(1, fraction));
        gc.setFill(Color.color(0, 0, 0, 0.55));
        gc.fillRoundRect(centreX - width / 2, y, width, 5, 3, 3);
        gc.setFill(colour);
        gc.fillRoundRect(centreX - width / 2, y, width * clamped, 5, 3, 3);
    }

    // ===== geometry =====

    public double tileWidth() {
        return getWidth() * (GRID_RIGHT - GRID_LEFT) / GameSession.COLS;
    }

    public double tileHeight() {
        return getHeight() * (GRID_BOTTOM - GRID_TOP) / GameSession.ROWS;
    }

    /**
     * The pixel centre of a 1-based column.
     */
    public double tileX(int col) {
        return getWidth() * GRID_LEFT + (col - 0.5) * tileWidth();
    }

    /**
     * The pixel centre of a 1-based row.
     */
    public double tileY(int row) {
        return getHeight() * GRID_TOP + (row - 0.5) * tileHeight();
    }

    public Point2D sunCentre(Sun sun) {
        return new Point2D(tileX(sun.getCol() + 1), tileY(sun.getRow() + 1));
    }

    /**
     * Which 1-based tile a click landed on, or null when it missed the lawn.
     */
    public int[] tileAt(double pixelX, double pixelY) {
        int col = (int) Math.floor((pixelX - getWidth() * GRID_LEFT) / tileWidth()) + 1;
        int row = (int) Math.floor((pixelY - getHeight() * GRID_TOP) / tileHeight()) + 1;
        if (col < 1 || col > GameSession.COLS || row < 1 || row > GameSession.ROWS) {
            return null;
        }
        return new int[] {col, row};
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public double minWidth(double height) {
        return 0;
    }

    @Override
    public double minHeight(double width) {
        return 0;
    }

    @Override
    public double maxWidth(double height) {
        return Double.MAX_VALUE;
    }

    @Override
    public double maxHeight(double width) {
        return Double.MAX_VALUE;
    }
}
