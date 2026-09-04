package ir.sharif.pvz.model.game;

/**
 * How each plant category fights during a tick: straight shots (blocked by
 * graves, reflected by jesters), lobbed shots (deflected by parasols), lasers,
 * homing shots, bites, mines and mint blasts.
 */
class PlantCombat {

    /** How many shots a plant food barrage puts in the air to be seen. */
    private static final int BARRAGE_SHOTS = 6;

    private final GameSession session;

    PlantCombat(GameSession session) {
        this.session = session;
    }

    /**
     * The plant-food (and boost) effect of each category.
     */
    void applyPlantFood(Plant plant) {
        if (session.plantAbilities.plantFood(plant)) {
            return;
        }
        switch (plant.getSpec().getCategory()) {
            case SUN_PRODUCER -> session.setSunAmount(session.getSunAmount() + 150);
            case WALL -> plant.heal();
            case EXPLOSIVE, TRAP -> session.explodePlant(plant, 1);
            case MELEE -> {
                Zombie front = session.frontmost(plant.getRow(), plant.getCol() + 1.0);
                if (front != null) {
                    session.slayZombie(front);
                }
            }
            case MODIFIER, MINT -> explodeAround(plant, 150);
            default -> barrage(plant, plant.getRow(), 300);
        }
    }

    /**
     * A shooter's plant food rakes a lane. The shots are recorded as well as
     * applied so the view has a volley to draw rather than damage appearing
     * from nowhere.
     */
    void barrage(Plant plant, int row, int damage) {
        for (int i = 0; i < BARRAGE_SHOTS; i++) {
            session.recordShot(plant, GameSession.COLS + 1.0, Shot.Flight.STRAIGHT);
        }
        session.damageRowFrom(row, plant.getCol() + 1.0, damage);
    }

    private void explodeAround(Plant plant, int damage) {
        for (Zombie zombie : session.getZombies()) {
            if (Math.abs(zombie.getRow() - plant.getRow()) <= 1
                    && Math.abs(zombie.getX() - (plant.getCol() + 1)) <= 1.5) {
                session.hitZombie(zombie, damage);
            }
        }
    }

    /**
     * The blast of a radioactive sun collected mid-air: it hurts zombies in a
     * 5x5 square and plants in a 3x3 square around the tile.
     */
    void radioactiveBlast(int row, int col) {
        for (Zombie zombie : session.getZombies()) {
            if (Math.abs(zombie.getRow() - row) <= 2 && Math.abs(zombie.getX() - (col + 1)) <= 2.5) {
                session.hitZombie(zombie, 150);
            }
        }
        for (int r = Math.max(0, row - 1); r <= Math.min(GameSession.ROWS - 1, row + 1); r++) {
            for (int c = Math.max(0, col - 1); c <= Math.min(GameSession.COLS - 1, col + 1); c++) {
                Plant plant = session.gridArray()[r][c];
                if (plant != null) {
                    session.plantHit(plant, 80);
                }
            }
        }
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
        session.log.creditTo(plant.getSpec().getName());
        try {
            attack(plant);
        } finally {
            session.log.creditTo(null);
        }
    }

    private void attack(Plant plant) {
        if (session.plantAbilities.act(plant)) {
            return;
        }
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
            session.damageRowFrom(plant.getRow(), 0, plant.getDamage());
            session.destroyPlantSilently(plant);
            return;
        }
        if (!plant.isReadyToAttack() || plant.getDamage() == 0) {
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
            // with the lane clear, a shooter facing the boss hits the boss
            shootZomboss(plant, lobbed);
            return;
        }
        if (!lobbed) {
            int grave = session.board.graveColumnBetween(plant.getRow(),
                    plant.getCol() + 1, target.getX());
            if (grave >= 0) {
                session.damageGrave(plant.getRow(), grave, plant.getDamage());
                plant.resetAttackCooldown();
                return;
            }
            if (target.getSpec().getName().equals("jester")) {
                session.eventLog().add("The jester reflected " + plant.getSpec().getName()
                        + "'s shot back at it!");
                session.plantHit(plant, plant.getDamage());
                plant.resetAttackCooldown();
                return;
            }
        }
        int damage = session.plantAbilities.scaledDamage(plant) + torchwoodBonus(plant, target);
        if (plant.getSpec().hasTag("ice")) {
            target.chill(5);
            session.abilities.onIceHit(target);
        }
        session.recordShot(plant, target.getX(),
                lobbed ? Shot.Flight.LOBBED : Shot.Flight.STRAIGHT);
        session.plantAbilities.onShotLanded(plant, target);
        session.hitZombie(target, damage);
        plant.resetAttackCooldown();
    }

    /**
     * Fires at Zomboss when this plant is in one of the rows it covers.
     */
    private void shootZomboss(Plant plant, boolean lobbed) {
        Zomboss boss = session.getZomboss();
        if (boss == null || boss.isDefeated() || !boss.covers(plant.getRow())) {
            return;
        }
        session.recordShot(plant, boss.getColumn(),
                lobbed ? Shot.Flight.LOBBED : Shot.Flight.STRAIGHT);
        session.zomboss.hit(plant.getDamage());
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
        Zomboss boss = session.getZomboss();
        boolean facingBoss = boss != null && !boss.isDefeated() && boss.covers(plant.getRow());
        if (anyZombie || facingBoss) {
            session.recordShot(plant, GameSession.COLS + 1.0, Shot.Flight.STRAIGHT);
            session.damageRowFrom(plant.getRow(), plant.getCol() + 1.0,
                    session.plantAbilities.scaledDamage(plant));
            if (facingBoss) {
                session.zomboss.hit(plant.getDamage());
            }
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
            session.recordShot(plant, target.getX(), Shot.Flight.LOBBED);
            if (plant.getSpec().hasTag("hypno")) {
                target.hypnotize();
                session.eventLog().add("Caulipower charmed a " + target.getSpec().getName() + ".");
            } else {
                session.hitZombie(target, plant.getDamage());
            }
            plant.resetAttackCooldown();
        }
    }

    private void biteAdjacent(Plant plant) {
        Zombie target = session.frontmost(plant.getRow(), plant.getCol() + 1.0);
        if (target != null && target.getX() <= plant.getCol() + 2.2) {
            session.hitZombie(target, plant.getDamage());
            plant.resetAttackCooldown();
        }
    }

    private void triggerTrapIfTouched(Plant plant) {
        for (Zombie zombie : new java.util.ArrayList<>(session.zombieList())) {
            if (zombie.getRow() == plant.getRow() && Math.abs(zombie.getX() - (plant.getCol() + 1)) < 0.5) {
                if (plant.getSpec().hasTag("ice")) {
                    zombie.freeze(5);
                } else if (plant.getSpec().hasTag("insta-kill")) {
                    session.slayZombie(zombie);
                } else {
                    session.hitZombie(zombie, plant.getDamage());
                }
                session.destroyPlantSilently(plant);
                return;
            }
        }
    }
}
