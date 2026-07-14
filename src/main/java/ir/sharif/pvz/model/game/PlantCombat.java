package ir.sharif.pvz.model.game;

/**
 * How each plant category fights during a tick: straight shots (blocked by
 * graves, reflected by jesters), lobbed shots (deflected by parasols), lasers,
 * homing shots, bites, mines and mint blasts.
 */
class PlantCombat {

    private final GameSession session;

    PlantCombat(GameSession session) {
        this.session = session;
    }

    void tick() {
        for (Plant plant : session.plantedPlants()) {
            if (session.gridArray()[plant.getRow()][plant.getCol()] == plant
                    && !session.isDisabled(plant)) {
                act(plant);
            }
        }
    }

    private void act(Plant plant) {
        PlantCategory category = plant.getSpec().getCategory();
        if (category == PlantCategory.EXPLOSIVE && plant.isArmed()) {
            session.explodePlant(plant, 1);
            return;
        }
        if (category == PlantCategory.TRAP && plant.isArmed()) {
            triggerTrapIfTouched(plant);
            return;
        }
        if (category == PlantCategory.MINT && plant.isArmed()) {
            session.damageRowFrom(plant.getRow(), 0, plant.getSpec().getDamage());
            session.destroyPlantSilently(plant);
            return;
        }
        if (!plant.isReadyToAttack() || plant.getSpec().getDamage() == 0) {
            return;
        }
        switch (category) {
            case SHOOTER, LOBBER -> shootFrontmost(plant);
            case STRIKE_THROUGH -> damageRowByPlant(plant);
            case HOMING -> shootNearestAnywhere(plant);
            case MELEE -> biteAdjacent(plant);
            default -> { }
        }
    }

    private void shootFrontmost(Plant plant) {
        boolean lobbed = plant.getSpec().getCategory() == PlantCategory.LOBBER;
        Zombie target = targetFor(plant, lobbed);
        if (target == null) {
            return;
        }
        if (!lobbed) {
            int grave = session.graveColumnBetween(plant.getRow(), plant.getCol() + 1, target.getX());
            if (grave >= 0) {
                session.damageGraveAt(plant.getRow(), grave, plant.getSpec().getDamage());
                plant.resetAttackCooldown();
                return;
            }
            if (target.getSpec().getName().equals("jester")) {
                session.eventLog().add("The jester reflected " + plant.getSpec().getName()
                        + "'s shot back at it!");
                session.plantHit(plant, plant.getSpec().getDamage());
                plant.resetAttackCooldown();
                return;
            }
        }
        int damage = plant.getSpec().getDamage() + torchwoodBonus(plant, target);
        if (plant.getSpec().hasTag("ice")) {
            target.chill(5);
            session.abilitiesRef().onIceHit(target);
        }
        session.hitZombie(target, damage);
        plant.resetAttackCooldown();
    }

    /**
     * The frontmost zombie this plant can actually damage: straight shots miss
     * submerged snorkels, lobbed shots bounce off parasols.
     */
    private Zombie targetFor(Plant plant, boolean lobbed) {
        Zombie front = null;
        for (Zombie zombie : session.zombieList()) {
            if (zombie.getRow() != plant.getRow() || zombie.getX() < plant.getCol() + 1.0) {
                continue;
            }
            if (!lobbed && isSubmerged(zombie) || lobbed && zombie.getSpec().getName().equals("parasol")) {
                continue;
            }
            if (front == null || zombie.getX() < front.getX()) {
                front = zombie;
            }
        }
        return front;
    }

    private boolean isSubmerged(Zombie zombie) {
        int col = (int) Math.round(zombie.getX()) - 1;
        if (!zombie.getSpec().getName().equals("snorkel") || col < 0 || col >= GameSession.COLS) {
            return false;
        }
        TileTerrain kind = session.terrainAt(col + 1, zombie.getRow() + 1);
        return kind == TileTerrain.WATER || kind == TileTerrain.LILY;
    }

    private int torchwoodBonus(Plant shooter, Zombie target) {
        if (!shooter.getSpec().hasTag("pea") || target.getSpec().getName().equals("imp-dragon")) {
            return 0;
        }
        for (int col = shooter.getCol() + 1; col < GameSession.COLS; col++) {
            Plant plant = session.gridArray()[shooter.getRow()][col];
            if (plant != null && plant.getSpec().getCategory() == PlantCategory.MODIFIER
                    && plant.getSpec().hasTag("fire")) {
                return 20;
            }
        }
        return 0;
    }

    private void damageRowByPlant(Plant plant) {
        boolean anyZombie = session.zombieList().stream().anyMatch(z -> z.getRow() == plant.getRow());
        if (anyZombie) {
            session.damageRowFrom(plant.getRow(), plant.getCol() + 1.0, plant.getSpec().getDamage());
            plant.resetAttackCooldown();
        }
    }

    private void shootNearestAnywhere(Plant plant) {
        Zombie target = null;
        double best = Double.MAX_VALUE;
        for (Zombie zombie : session.zombieList()) {
            double distance = Math.abs(zombie.getRow() - plant.getRow())
                    + Math.abs(zombie.getX() - plant.getCol());
            if (distance < best) {
                best = distance;
                target = zombie;
            }
        }
        if (target != null) {
            session.hitZombie(target, plant.getSpec().getDamage());
            plant.resetAttackCooldown();
        }
    }

    private void biteAdjacent(Plant plant) {
        Zombie target = session.frontmost(plant.getRow(), plant.getCol() + 1.0);
        if (target != null && target.getX() <= plant.getCol() + 2.2) {
            session.hitZombie(target, plant.getSpec().getDamage());
            plant.resetAttackCooldown();
        }
    }

    private void triggerTrapIfTouched(Plant plant) {
        for (Zombie zombie : new java.util.ArrayList<>(session.zombieList())) {
            if (zombie.getRow() == plant.getRow() && Math.abs(zombie.getX() - (plant.getCol() + 1)) < 0.5) {
                if (plant.getSpec().hasTag("ice")) {
                    zombie.freeze(5);
                } else {
                    session.hitZombie(zombie, plant.getSpec().getDamage());
                }
                session.destroyPlantSilently(plant);
                return;
            }
        }
    }
}
