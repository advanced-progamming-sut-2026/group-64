package ir.sharif.pvz.model.game;

/**
 * Putting a plant on the lawn and paying for it, lifted out of
 * {@link GameSession} so the engine class stays about running a level. It works
 * on the session's package-private state directly, the same way {@link Cheats}
 * and the board and wave systems do.
 */
final class Planting {

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
        if (spec.getName().equals("lily-pad")) {
            session.board.setTerrain(y - 1, x - 1, TileTerrain.LILY);
            return "Planted lily-pad at (" + x + ", " + y + "); the tile is now plantable.";
        }
        Plant plant = new Plant(spec, y - 1, x - 1, session.boostedPlants.remove(type));
        session.gridArray()[y - 1][x - 1] = plant;
        if (plant.isBoosted()) {
            plant.consumeBoost();
            session.applyPlantFoodEffect(plant);
        }
        if (session.minigame != null) {
            session.minigame.onPlanted(session, plant);
        }
        return "Planted " + type + " at (" + x + ", " + y + ").";
    }

    /**
     * Every reason this tile will not take this plant, or null when it will.
     */
    private String whyNot(String type, int x, int y) {
        if (!session.special.conveyorMode() && !freeHand() && !session.getSelectedPlants().contains(type)) {
            return "Error: plant '" + type + "' is not among your selected plants.";
        }
        if (!session.validTile(x, y)) {
            return "Error: (" + x + ", " + y + ") is not a valid tile.";
        }
        PlantSpec spec = GameCatalog.get().plant(type);
        if (spec == null) {
            return "Error: there is no plant named '" + type + "'.";
        }
        if (session.gridArray()[y - 1][x - 1] != null) {
            return "Error: tile (" + x + ", " + y + ") is already occupied.";
        }
        String terrainError = session.board.rejection(spec, y - 1, x - 1);
        if (terrainError != null) {
            return terrainError;
        }
        return session.minigame == null ? null : session.minigame.plantingRejection(x, y);
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
