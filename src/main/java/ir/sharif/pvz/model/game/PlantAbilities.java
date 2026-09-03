package ir.sharif.pvz.model.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The plant-specific behaviour that the plain category rules in
 * {@link PlantCombat} do not cover: the peashooters that fire into more than
 * one lane, the nuts that shove zombies around, the shrooms that charm or
 * disarm them, and the one-shot explosives.
 *
 * <p>Everything here is chosen by the plant's tags rather than by its name
 * wherever it can be, so a new plant is usually a new line in
 * {@code data/plants.csv} and nothing more. The handful of plants whose effect
 * is genuinely unique (imitater, grave buster, hot potato) are matched by name.
 */
class PlantAbilities {

    /** How long the two short-lived shrooms stay on the lawn. */
    private static final double SHROOM_LIFESPAN_SECONDS = 60;

    /** Kernel-pult's chance, in percent, of lobbing butter instead of corn. */
    private static final int BUTTER_CHANCE = 25;

    /** Pumpkins, keyed by the plant each one is wrapped around. */
    private final java.util.Map<Plant, Plant> shields = new java.util.HashMap<>();

    private final GameSession session;

    PlantAbilities(GameSession session) {
        this.session = session;
    }

    // ===== the pumpkin shell =====

    /**
     * The pumpkin wrapped around this plant, if any.
     */
    Plant shieldOn(Plant plant) {
        return shields.get(plant);
    }

    void putShield(Plant plant, Plant pumpkin) {
        shields.put(plant, pumpkin);
    }

    void dropShield(Plant plant) {
        shields.remove(plant);
    }

    /**
     * Sends a bite into the pumpkin first; true when the plant inside was
     * spared because the shell took the damage.
     */
    boolean absorbedByShield(Plant plant, int damage) {
        Plant shell = shields.get(plant);
        if (shell == null) {
            return false;
        }
        if (shell.damage(damage)) {
            shields.remove(plant);
            session.eventLog().add("The pumpkin around " + plant.getSpec().getName() + " at ("
                    + (plant.getCol() + 1) + ", " + (plant.getRow() + 1) + ") broke.");
        }
        return true;
    }

    // ===== once-per-tick upkeep =====

    /**
     * The effects that run on their own rather than as an attack: the
     * short-lived shrooms wilting, the instant plants going off the moment they
     * land, and poison burning down the zombies it is on.
     */
    void tick(double dt) {
        for (Plant plant : session.plantedPlants()) {
            if (plant.getSpec().hasTag("lifespan")
                    && plant.getAgeSeconds() >= SHROOM_LIFESPAN_SECONDS) {
                session.eventLog().add("Plant " + plant.getSpec().getName() + " at ("
                        + (plant.getCol() + 1) + ", " + (plant.getRow() + 1) + ") wilted away.");
                session.destroyPlantSilently(plant);
            }
        }
        for (Zombie zombie : new ArrayList<>(session.zombieList())) {
            if (zombie.isPoisoned() && zombie.damageIgnoringArmor(
                    (int) Math.round(zombie.getPoisonPerSecond() * dt))) {
                session.killZombie(zombie);
            }
        }
    }

    // ===== attacks =====

    /**
     * Runs this plant's own attack, or returns false to let the category rules
     * in {@link PlantCombat} handle it.
     */
    boolean act(Plant plant) {
        PlantSpec spec = plant.getSpec();
        if (spec.hasTag("instant")) {
            return instant(plant);
        }
        if (!handlesAttack(spec)) {
            return false;
        }
        if (!plant.isReadyToAttack()) {
            return true;
        }
        if (spec.hasTag("magnet")) {
            return magnet(plant);
        }
        if (spec.hasTag("three-lane")) {
            return shootLanes(plant, List.of(plant.getRow() - 1, plant.getRow(), plant.getRow() + 1));
        }
        if (spec.hasTag("split")) {
            return split(plant);
        }
        if (spec.hasTag("star")) {
            return star(plant);
        }
        if (spec.hasTag("diagonal")) {
            return diagonal(plant);
        }
        if (spec.getCategory() == PlantCategory.MELEE) {
            return melee(plant);
        }
        return false;
    }

    /**
     * Whether this plant's attack lives here rather than in the category rules.
     */
    private boolean handlesAttack(PlantSpec spec) {
        return spec.getCategory() == PlantCategory.MELEE || spec.hasTag("magnet")
                || spec.hasTag("three-lane") || spec.hasTag("split")
                || spec.hasTag("star") || spec.hasTag("diagonal");
    }

    /**
     * The plants that do their one thing the moment they are planted and then
     * disappear: the sun burst, the lane of fire, the map-wide blasts, and the
     * two utility plants that clear a tile.
     */
    private boolean instant(Plant plant) {
        switch (plant.getSpec().getName()) {
            case "gold-bloom" -> goldBloom(plant);
            case "grave-buster" -> graveBuster(plant);
            case "hot-potato" -> melt(plant);
            case "jalapeno" -> jalapeno(plant);
            case "doom-shroom" -> doomShroom(plant);
            case "ice-shroom" -> iceShroom(plant);
            case "grapeshot" -> {
                session.explodePlant(plant, 1);
                bounceInto(plant, plant.getDamage() / 4);
                return true;
            }
            default -> {
                return plant.getSpec().getCategory() == PlantCategory.MINT && mint(plant);
            }
        }
        session.destroyPlantSilently(plant);
        return true;
    }

    private void goldBloom(Plant plant) {
        int worth = SunSystem.yieldOf(plant);
        session.setSunAmount(session.getSunAmount() + worth);
        session.eventLog().add("Gold bloom burst into " + worth + " sun.");
    }

    private void graveBuster(Plant plant) {
        int row = plant.getRow();
        int col = plant.getCol();
        if (session.board.terrainAt(row, col) == TileTerrain.GRAVE) {
            session.board.setTerrain(row, col, TileTerrain.NORMAL);
            session.eventLog().add("The grave at (" + (col + 1) + ", " + (row + 1)
                    + ") was busted.");
            return;
        }
        session.eventLog().add("Grave buster found no grave at (" + (col + 1) + ", "
                + (row + 1) + ") and crumbled.");
    }

    private void jalapeno(Plant plant) {
        int row = plant.getRow();
        session.recordBurst(Burst.Kind.EXPLOSION, plant.getCol() + 1.0, row + 1.0);
        for (Zombie zombie : new ArrayList<>(session.zombieList())) {
            if (zombie.getRow() == row) {
                session.hitZombie(zombie, plant.getDamage());
            }
        }
        thawRow(row);
        session.eventLog().add("Jalapeno set lane " + (row + 1) + " on fire.");
    }

    private void doomShroom(Plant plant) {
        wholeMap(plant, plant.getDamage());
        session.board.setTerrain(plant.getRow(), plant.getCol(), TileTerrain.CRATER);
        session.eventLog().add("The doom-shroom left a crater at ("
                + (plant.getCol() + 1) + ", " + (plant.getRow() + 1) + ").");
    }

    private void iceShroom(Plant plant) {
        for (Zombie zombie : session.zombieList()) {
            zombie.freeze(10);
        }
        session.recordBurst(Burst.Kind.EXPLOSION, plant.getCol() + 1.0, plant.getRow() + 1.0);
        session.eventLog().add("The ice-shroom froze every zombie on the map.");
    }

    /**
     * A mint hands its whole family a plant food effect and is used up doing it.
     */
    private boolean mint(Plant mint) {
        String family = null;
        for (String tag : mint.getSpec().getTags()) {
            if (tag.startsWith("family:")) {
                family = tag.substring("family:".length());
            }
        }
        if (family == null) {
            return false;
        }
        PlantCategory target = PlantCategory.valueOf(
                family.toUpperCase(Locale.ROOT).replace('-', '_'));
        int fed = 0;
        for (Plant plant : session.plantedPlants()) {
            if (plant != mint && plant.getSpec().getCategory() == target) {
                session.applyPlantFoodEffect(plant);
                fed++;
            }
        }
        for (PlantSpec spec : GameCatalog.get().allPlants()) {
            if (spec.getCategory() == target) {
                session.plantCooldowns.remove(spec.getName());
            }
        }
        session.eventLog().add(mint.getSpec().getName() + " fed " + fed + " "
                + family + " plants and reset their cooldowns.");
        session.destroyPlantSilently(mint);
        return true;
    }

    /**
     * Threepeater: the same shot into this lane and the two beside it.
     */
    private boolean shootLanes(Plant plant, List<Integer> rows) {
        boolean fired = false;
        for (int row : rows) {
            if (row < 0 || row >= GameSession.ROWS) {
                continue;
            }
            Zombie target = session.frontmost(row, plant.getCol() + 1.0);
            if (target != null) {
                session.recordShot(plant, target.getX(), Shot.Flight.STRAIGHT);
                session.hitZombie(target, plant.getDamage());
                fired = true;
            }
        }
        if (fired) {
            plant.resetAttackCooldown();
        }
        return true;
    }

    /**
     * Split pea: one pea forward, two back over its own shoulder.
     */
    private boolean split(Plant plant) {
        boolean fired = false;
        Zombie ahead = session.frontmost(plant.getRow(), plant.getCol() + 1.0);
        if (ahead != null) {
            session.recordShot(plant, ahead.getX(), Shot.Flight.STRAIGHT);
            session.hitZombie(ahead, plant.getDamage());
            fired = true;
        }
        Zombie behind = rearmost(plant.getRow(), plant.getCol() + 1.0);
        if (behind != null) {
            session.recordShot(plant, behind.getX(), Shot.Flight.STRAIGHT);
            session.hitZombie(behind, plant.getDamage() * 2);
            fired = true;
        }
        if (fired) {
            plant.resetAttackCooldown();
        }
        return true;
    }

    /**
     * Starfruit: five points, so both ways down its lane and into the lanes
     * above and below it.
     */
    private boolean star(Plant plant) {
        boolean fired = false;
        Zombie behind = rearmost(plant.getRow(), plant.getCol() + 1.0);
        if (behind != null) {
            session.recordShot(plant, behind.getX(), Shot.Flight.STRAIGHT);
            session.hitZombie(behind, plant.getDamage());
            fired = true;
        }
        for (int row : List.of(plant.getRow() - 1, plant.getRow(), plant.getRow() + 1)) {
            if (row < 0 || row >= GameSession.ROWS) {
                continue;
            }
            Zombie target = session.frontmost(row, plant.getCol() + 1.0);
            if (target != null) {
                session.recordShot(plant, target.getX(), Shot.Flight.STRAIGHT);
                session.hitZombie(target, plant.getDamage());
                fired = true;
            }
        }
        if (fired) {
            plant.resetAttackCooldown();
        }
        return true;
    }

    /**
     * Rotobaga: the four diagonals, so the lane above and below in both
     * directions but never straight ahead.
     */
    private boolean diagonal(Plant plant) {
        boolean fired = false;
        for (int row : List.of(plant.getRow() - 1, plant.getRow() + 1)) {
            if (row < 0 || row >= GameSession.ROWS) {
                continue;
            }
            Zombie ahead = session.frontmost(row, plant.getCol() + 1.0);
            if (ahead != null) {
                session.recordShot(plant, ahead.getX(), Shot.Flight.STRAIGHT);
                session.hitZombie(ahead, plant.getDamage());
                fired = true;
            }
            Zombie behind = rearmost(row, plant.getCol() + 1.0);
            if (behind != null) {
                session.recordShot(plant, behind.getX(), Shot.Flight.STRAIGHT);
                session.hitZombie(behind, plant.getDamage());
                fired = true;
            }
        }
        if (fired) {
            plant.resetAttackCooldown();
        }
        return true;
    }

    /**
     * Magnet-shroom: rips the metal off the nearest armoured zombie.
     */
    private boolean magnet(Plant plant) {
        for (Zombie zombie : session.zombieList()) {
            if (zombie.getArmor().isEmpty() || Math.abs(zombie.getRow() - plant.getRow()) > 1
                    || Math.abs(zombie.getX() - (plant.getCol() + 1)) > 3.5) {
                continue;
            }
            String taken = String.join(", ", zombie.stripArmor().keySet());
            session.eventLog().add("Magnet-shroom pulled the " + taken + " off a "
                    + zombie.getSpec().getName() + ".");
            plant.resetAttackCooldown();
            return true;
        }
        return true;
    }

    /**
     * The melee plants: the two whips hit the tile in front and the one behind,
     * the beet and the kiwi thump everything around them, and the chomper
     * swallows its target whole.
     */
    private boolean melee(Plant plant) {
        PlantSpec spec = plant.getSpec();
        if (spec.hasTag("insta-kill")) {
            Zombie target = session.frontmost(plant.getRow(), plant.getCol() + 1.0);
            if (target != null && target.getX() <= plant.getCol() + 2.2) {
                session.eventLog().add("Chomper swallowed a " + target.getSpec().getName()
                        + " whole and is chewing.");
                session.slayZombie(target);
                plant.resetAttackCooldown();
            }
            return true;
        }
        if (spec.hasTag("aoe")) {
            int damage = plant.getDamage() * plant.getStage();
            boolean hit = false;
            for (Zombie zombie : new ArrayList<>(session.zombieList())) {
                if (Math.abs(zombie.getRow() - plant.getRow()) <= 1
                        && Math.abs(zombie.getX() - (plant.getCol() + 1)) <= 1.5) {
                    session.hitZombie(zombie, damage);
                    hit = true;
                }
            }
            if (hit) {
                session.recordBurst(Burst.Kind.EXPLOSION, plant.getCol() + 1.0, plant.getRow() + 1.0);
                plant.resetAttackCooldown();
            }
            return true;
        }
        if (spec.hasTag("melee-back")) {
            boolean hit = false;
            for (Zombie zombie : new ArrayList<>(session.zombieList())) {
                if (zombie.getRow() == plant.getRow()
                        && Math.abs(zombie.getX() - (plant.getCol() + 1)) <= 1.2) {
                    session.hitZombie(zombie, plant.getDamage());
                    if (spec.hasTag("fire")) {
                        zombie.chill(0);
                    }
                    hit = true;
                }
            }
            if (hit) {
                plant.resetAttackCooldown();
            }
            return true;
        }
        return false;
    }

    // ===== plant food =====

    /**
     * The plant-food effect of the plants whose burst is their own rather than
     * their category's; returns false to fall back to the category effect in
     * {@link PlantCombat}.
     */
    boolean plantFood(Plant plant) {
        PlantSpec spec = plant.getSpec();
        if (spec.getCategory() == PlantCategory.SUN_PRODUCER) {
            int worth = SunSystem.yieldOf(plant) * 3;
            session.setSunAmount(session.getSunAmount() + worth);
            session.eventLog().add(spec.getName() + " burst into " + worth + " sun.");
            return true;
        }
        if (spec.getCategory() == PlantCategory.WALL) {
            plant.heal();
            putShield(plant, new Plant(spec, plant.getRow(), plant.getCol(), false));
            session.eventLog().add(spec.getName() + " put on a suit of armour.");
            return true;
        }
        if (spec.hasTag("water") && spec.hasTag("stack")) {
            spreadLilyPads(plant);
            return true;
        }
        return charmingPlantFood(plant) || shootingPlantFood(plant);
    }

    /**
     * The plant foods that work on the zombies themselves rather than by
     * shooting at them.
     */
    private boolean charmingPlantFood(Plant plant) {
        PlantSpec spec = plant.getSpec();
        if (spec.hasTag("magnet")) {
            int stripped = 0;
            for (Zombie zombie : session.zombieList()) {
                if (!zombie.getArmor().isEmpty()) {
                    zombie.stripArmor();
                    stripped++;
                }
            }
            session.eventLog().add("Magnet-shroom tore the metal off " + stripped + " zombies.");
            return true;
        }
        if (spec.hasTag("hypno")) {
            for (Zombie zombie : session.zombieList()) {
                if (zombie.getRow() == plant.getRow()) {
                    zombie.hypnotize();
                }
            }
            session.eventLog().add("Every zombie in lane " + (plant.getRow() + 1)
                    + " now fights for you.");
            return true;
        }
        if (spec.hasTag("insta-kill") && spec.getCategory() == PlantCategory.MELEE) {
            int eaten = 0;
            for (Zombie zombie : new ArrayList<>(session.zombieList())) {
                if (eaten == 3) {
                    break;
                }
                session.slayZombie(zombie);
                eaten++;
            }
            session.eventLog().add("The chomper swallowed " + eaten + " zombies at once.");
            return true;
        }
        return false;
    }

    /**
     * The shooters' plant foods: the fan of shots over every lane, the frozen
     * lane, and the poisoned one.
     */
    private boolean shootingPlantFood(Plant plant) {
        PlantSpec spec = plant.getSpec();
        if (fansOutOverLanes(spec)) {
            for (int row = 0; row < GameSession.ROWS; row++) {
                session.damageRowFrom(row, plant.getCol() + 1.0, plant.getDamage() * 5);
            }
            session.eventLog().add(spec.getName() + " sprayed every lane.");
            return true;
        }
        if (spec.hasTag("ice")) {
            for (Zombie zombie : session.zombieList()) {
                if (zombie.getRow() == plant.getRow()) {
                    zombie.freeze(8);
                }
            }
            session.damageRowFrom(plant.getRow(), plant.getCol() + 1.0, plant.getDamage() * 5);
            return true;
        }
        if (spec.hasTag("poison")) {
            for (Zombie zombie : session.zombieList()) {
                if (zombie.getRow() == plant.getRow()) {
                    zombie.poison(10, plant.getDamage());
                }
            }
            return true;
        }
        return false;
    }

    /**
     * The lily pad's plant food: a pad on every free water tile.
     */
    private void spreadLilyPads(Plant pad) {
        int added = 0;
        for (int row = 0; row < GameSession.ROWS; row++) {
            for (int col = 0; col < GameSession.COLS; col++) {
                if (session.board.terrainAt(row, col) == TileTerrain.WATER
                        && session.gridArray()[row][col] == null) {
                    session.board.setTerrain(row, col, TileTerrain.LILY);
                    added++;
                }
            }
        }
        session.eventLog().add("The lily pad at (" + (pad.getCol() + 1) + ", "
                + (pad.getRow() + 1) + ") spread " + added + " more pads over the water.");
    }

    private boolean fansOutOverLanes(PlantSpec spec) {
        return spec.hasTag("three-lane") || spec.hasTag("star") || spec.hasTag("diagonal");
    }

    // ===== hooks the rest of the engine calls into =====

    /**
     * Extra work when a straight or lobbed shot lands: the goo pea's poison,
     * the kernel-pult's butter, and the bowling bulb's bounce.
     */
    void onShotLanded(Plant plant, Zombie target) {
        PlantSpec spec = plant.getSpec();
        if (spec.hasTag("poison")) {
            target.poison(5, Math.max(1, plant.getDamage() / 4));
        }
        if (spec.hasTag("butter") && session.roll(100) < BUTTER_CHANCE) {
            target.freeze(3);
            session.eventLog().add("The kernel-pult buttered a " + target.getSpec().getName() + ".");
        }
        if (spec.hasTag("bounce")) {
            bounceInto(plant, plant.getDamage() / 2);
        }
    }

    /**
     * The bouncing shots carry on into the lane above and below.
     */
    private void bounceInto(Plant plant, int damage) {
        if (damage <= 0) {
            return;
        }
        for (int row : List.of(plant.getRow() - 1, plant.getRow() + 1)) {
            if (row < 0 || row >= GameSession.ROWS) {
                continue;
            }
            Zombie next = session.frontmost(row, plant.getCol() + 1.0);
            if (next != null) {
                session.hitZombie(next, damage);
            }
        }
    }

    /**
     * A shooter's damage after the modifiers that scale it: the pea pod counts
     * its heads, the ramp-up plants count their stage.
     */
    int scaledDamage(Plant plant) {
        return plant.getDamage() * plant.getStack() * plant.getStage();
    }

    /**
     * What happens when a zombie takes a bite out of this plant: the endurian
     * spikes it back, the sun bean pays out, the garlic and sweet potato move
     * it to another lane, and the hypno-shroom turns it around.
     */
    void onEaten(Plant plant, Zombie eater) {
        PlantSpec spec = plant.getSpec();
        if (spec.hasTag("reflect")) {
            session.hitZombie(eater, plant.getDamage());
        }
        if (spec.hasTag("sun-on-hit")) {
            session.setSunAmount(session.getSunAmount() + 5);
        }
        if (spec.hasTag("hypno")) {
            eater.hypnotize();
            session.eventLog().add("The hypno-shroom charmed a " + eater.getSpec().getName()
                    + "; it now fights for you.");
            session.destroyPlantSilently(plant);
        }
        if (spec.hasTag("push")) {
            shove(eater, plant, false);
            session.destroyPlantSilently(plant);
        }
    }

    /**
     * Sweet potato pulls the zombies from the lanes beside it into its own;
     * garlic shoves whoever bites it out of the lane instead.
     */
    void onPlanted(Plant plant) {
        if (plant.getSpec().hasTag("pull")) {
            for (Zombie zombie : session.zombieList()) {
                if (Math.abs(zombie.getRow() - plant.getRow()) == 1) {
                    zombie.setRow(plant.getRow());
                }
            }
            session.eventLog().add("The sweet potato drew the neighbouring zombies into lane "
                    + (plant.getRow() + 1) + ".");
        }
    }

    /**
     * Moves a zombie one lane away from the plant it just bit.
     */
    private void shove(Zombie zombie, Plant plant, boolean towards) {
        int up = plant.getRow() - 1;
        int down = plant.getRow() + 1;
        int target = up >= 0 ? up : down;
        if (towards) {
            target = plant.getRow();
        } else if (down < GameSession.ROWS && session.roll(2) == 0) {
            target = down;
        }
        if (target >= 0 && target < GameSession.ROWS) {
            zombie.setRow(target);
            session.eventLog().add("Garlic pushed a " + zombie.getSpec().getName()
                    + " into lane " + (target + 1) + ".");
        }
    }

    /**
     * Hot potato thaws the ice on its own tile and frees the plant frozen in it.
     */
    private void melt(Plant plant) {
        Plant frozen = session.plantAtTile(plant.getCol() + 1, plant.getRow() + 1);
        if (frozen != null && frozen != plant) {
            session.enablePlant(frozen);
        }
        thawTile(plant.getRow(), plant.getCol());
        session.eventLog().add("Hot potato melted the ice at ("
                + (plant.getCol() + 1) + ", " + (plant.getRow() + 1) + ").");
    }

    private void thawRow(int row) {
        for (Zombie zombie : session.zombieList()) {
            if (zombie.getRow() == row) {
                zombie.freeze(0);
                zombie.chill(0);
            }
        }
    }

    /**
     * A charmed zombie marches back toward the graveyard, mauling the first
     * zombie it meets on the way; it leaves the board at the right edge.
     */
    void walkBackAndFight(Zombie zombie, double dt) {
        Zombie victim = null;
        for (Zombie other : session.zombieList()) {
            if (other == zombie || other.isHypnotized() || other.getRow() != zombie.getRow()
                    || other.getX() < zombie.getX() - 0.2 || other.getX() > zombie.getX() + 1.2) {
                continue;
            }
            if (victim == null || other.getX() < victim.getX()) {
                victim = other;
            }
        }
        if (victim != null) {
            zombie.startEating();
            session.hitZombie(victim,
                    (int) Math.round(zombie.getSpec().getDamagePerSecond() * dt));
            return;
        }
        double speed = zombie.getSpec().getTilesPerSecond()
                * session.difficultyScale() * zombie.speedMultiplier();
        zombie.walk(-speed * dt);
        if (zombie.getX() > GameSession.COLS + 1) {
            session.removeZombieQuietly(zombie);
            session.eventLog().add("The charmed " + zombie.getSpec().getName()
                    + " wandered off the lawn.");
        }
    }

    /**
     * The zombie furthest back in a lane, for the plants that shoot behind them.
     */
    private Zombie rearmost(int row, double fromX) {
        Zombie back = null;
        for (Zombie zombie : session.zombieList()) {
            if (zombie.getRow() == row && zombie.getX() < fromX
                    && (back == null || zombie.getX() > back.getX())) {
                back = zombie;
            }
        }
        return back;
    }

    /**
     * Hot potato thaws the ice on its own tile and frees the plant frozen in it.
     */
    private void thawTile(int row, int col) {
        TileTerrain kind = session.board.terrainAt(row, col);
        if (kind == TileTerrain.SLIPPERY_UP || kind == TileTerrain.SLIPPERY_DOWN) {
            session.board.setTerrain(row, col, TileTerrain.NORMAL);
        }
    }

    private void wholeMap(Plant plant, int damage) {
        session.recordBurst(Burst.Kind.EXPLOSION, plant.getCol() + 1.0, plant.getRow() + 1.0);
        for (Zombie zombie : new ArrayList<>(session.zombieList())) {
            session.hitZombie(zombie, damage);
        }
    }
}
