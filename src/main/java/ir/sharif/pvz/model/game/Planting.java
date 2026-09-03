package ir.sharif.pvz.model.game;

/**
 * Putting a plant on the lawn and paying for it, lifted out of
 * {@link GameSession} so the engine class stays about running a level. It works
 * on the session's package-private state directly, the same way {@link Cheats}
 * and the board and wave systems do.
 */
final class Planting {

    /** The last plant type the player put down, for the imitater to copy. */
    private String lastPlanted;

    private final GameSession session;

    Planting(GameSession session) {
        this.session = session;
    }

    /**
     * Validates the tile, charges for the plant and puts it down, returning the
     * message the player sees either way.
     */
    String plant(String type, int x, int y) {
        String rejection = whyNot(type, x, y);
        if (rejection != null) {
            return rejection;
        }
        PlantSpec spec = GameCatalog.get().plant(type);
        String paymentError = payFor(type, spec);
        if (paymentError != null) {
            return paymentError;
        }
        Plant standing = session.gridArray()[y - 1][x - 1];
        if (standing != null) {
            return stackOnto(standing, spec, x, y);
        }
        if (spec.getName().equals("imitater")) {
            spec = GameCatalog.get().plant(imitationTarget());
            type = spec.getName();
        }
        if (spec.getName().equals("lily-pad")) {
            session.board.setTerrain(y - 1, x - 1, TileTerrain.LILY);
            return "Planted lily-pad at (" + x + ", " + y + "); the tile is now plantable.";
        }
        Plant plant = new Plant(spec, y - 1, x - 1, session.boostedPlants.remove(type),
                session.plantLevel(type));
        session.gridArray()[y - 1][x - 1] = plant;
        session.plantAbilities().onPlanted(plant);
        if (plant.isBoosted()) {
            plant.consumeBoost();
            session.applyPlantFoodEffect(plant);
        }
        if (session.minigame != null) {
            session.minigame.onPlanted(session, plant);
        }
        if (!type.equals("imitater")) {
            lastPlanted = type;
        }
        return "Planted " + type + " at (" + x + ", " + y + ").";
    }

    /**
     * The two plants that go on top of another one: a second pea pod adds a
     * head to the one already there, and a pumpkin wraps itself around it.
     */
    private String stackOnto(Plant standing, PlantSpec spec, int x, int y) {
        String tile = "(" + x + ", " + y + ")";
        if (spec.hasTag("shield")) {
            if (session.shieldOn(standing) != null) {
                return "Error: " + standing.getSpec().getName() + " at " + tile
                        + " already has a pumpkin around it.";
            }
            session.plantAbilities().putShield(standing, new Plant(spec, y - 1, x - 1, false,
                    session.plantLevel(spec.getName())));
            return "Planted a pumpkin around " + standing.getSpec().getName() + " at " + tile + ".";
        }
        if (!standing.addToStack()) {
            return "Error: the pea pod at " + tile + " already has all five heads.";
        }
        return "The pea pod at " + tile + " grew a head; it now has "
                + standing.getStack() + ".";
    }

    /**
     * Digs a plant back up, unless the level says it has to stay standing.
     */
    String pluck(int x, int y) {
        String tile = "(" + x + ", " + y + ")";
        if (!session.validTile(x, y)) {
            return "Error: " + tile + " is not a valid tile.";
        }
        Plant plant = session.gridArray()[y - 1][x - 1];
        if (plant == null) {
            return "Error: there is no plant at " + tile + ".";
        }
        if (session.isProtectedPlant(plant)) {
            return "Error: the " + plant.getSpec().getName() + " at " + tile
                    + " must be protected, not plucked!";
        }
        session.destroyPlantSilently(plant);
        return "Plucked " + plant.getSpec().getName() + " from " + tile + ".";
    }

    /**
     * Spends one plant food on the plant at this tile; it also thaws a plant
     * that a zombie ability had frozen or trapped.
     */
    String feedPlant(int x, int y) {
        String tile = "(" + x + ", " + y + ")";
        if (!session.validTile(x, y)) {
            return "Error: " + tile + " is not a valid tile.";
        }
        Plant plant = session.gridArray()[y - 1][x - 1];
        if (plant == null) {
            return "Error: there is no plant at " + tile + ".";
        }
        if (session.getPlantFood() <= 0) {
            return "Error: you have no plant food.";
        }
        String cured = session.spendPlantFoodOn(plant);
        return "Plant food used on " + plant.getSpec().getName() + " at " + tile + "."
                + (cured == null ? "" : " It broke free of the " + cured + "!");
    }

    /**
     * Picks up the sun on this tile. A radioactive one that is still falling
     * goes off in the player's face instead.
     */
    String collectSun(int x, int y) {
        String tile = "(" + x + ", " + y + ")";
        if (!session.validTile(x, y)) {
            return "Error: " + tile + " is not a valid tile.";
        }
        Sun falling = session.sunSystem().fallingRadioactiveAt(y - 1, x - 1);
        if (falling != null) {
            session.sunSystem().remove(falling);
            session.combat().radioactiveBlast(y - 1, x - 1);
            return "The radioactive sun exploded!";
        }
        Sun sun = session.sunSystem().groundAt(y - 1, x - 1);
        if (sun == null) {
            return "Error: there is no sun at " + tile + ".";
        }
        session.sunSystem().remove(sun);
        session.setSunAmount(session.getSunAmount() + sun.value());
        return "Collected " + sun.value() + " sun; you now have "
                + session.getSunAmount() + " sun.";
    }

    /**
     * Every reason this tile will not take this plant, or null when it will.
     */
    private String whyNot(String type, int x, int y) {
        String minigameRejection = session.minigame == null ? null : session.minigame.plantingRejection(x, y);
        if (minigameRejection != null) {
            return minigameRejection;
        }
        if (!session.special.conveyorMode() && !freeHand() && !session.getSelectedPlants().contains(type)) {
            return "Error: plant '" + type + "' is not among your selected plants.";
        }
        PlantSpec spec = GameCatalog.get().plant(type);
        if (spec == null) {
            return "Error: there is no plant named '" + type + "'.";
        }
        if (!session.validTile(x, y)) {
            return "Error: (" + x + ", " + y + ") is not a valid tile.";
        }
        Plant standing = session.gridArray()[y - 1][x - 1];
        if (standing != null && !canStackOnto(standing, spec)) {
            return "Error: tile (" + x + ", " + y + ") is already occupied.";
        }
        return session.board.rejection(spec, y - 1, x - 1);
    }

    /**
     * What the imitater turns into: whatever the player planted last, or the
     * first real plant they brought to the level.
     */
    private String imitationTarget() {
        if (lastPlanted != null) {
            return lastPlanted;
        }
        return session.getSelectedPlants().stream()
                .filter(name -> !name.equals("imitater"))
                .findFirst().orElse("peashooter");
    }

    /**
     * A pumpkin goes around anything; a pea pod only joins another pea pod.
     */
    private boolean canStackOnto(Plant standing, PlantSpec spec) {
        if (spec.hasTag("shield")) {
            return true;
        }
        return spec.hasTag("stack") && !spec.hasTag("water")
                && standing.getSpec().getName().equals(spec.getName());
    }

    /**
     * Charges for a plant: the conveyor belt and the vasebreaker hand are free;
     * anything else costs sun and starts the recharge timer.
     */
    private String payFor(String type, PlantSpec spec) {
        if (session.special.conveyorMode()) {
            return session.special.takeFromBelt(type);
        }
        if (freeHand()) {
            return session.minigame.takeFromHand(type);
        }
        boolean recharging = !session.cooldownsDisabled && !session.cooldownsSuspended
                && session.plantCooldowns.getOrDefault(type, 0.0) > 0;
        if (recharging) {
            return "Error: " + type + " is recharging; wait "
                    + GameSession.trim(session.plantCooldowns.get(type)) + "s.";
        }
        if (session.sunAmount < spec.getSunCost()) {
            return "Error: not enough sun; " + type + " costs " + spec.getSunCost() + ".";
        }
        session.sunAmount -= spec.getSunCost();
        session.plantCooldowns.put(type, spec.getRechargeSeconds());
        return null;
    }

    private boolean freeHand() {
        return session.minigame != null && session.minigame.freePlantMode();
    }
}
