package ir.sharif.pvz.model.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Wallnut Bowling: nuts from the conveyor belt roll toward the zombies.
 * Planting is only allowed left of the red line (column 3). A rolling nut
 * damages the zombie it hits and bounces to a neighbouring lane; the red
 * Explode O' Nut blows up on first contact and the giant nut crushes through.
 */
class BowlingGame implements MinigameLogic {

    private static final int RED_LINE_COLUMN = 3;
    private static final double NUT_SPEED = 2;
    private static final int NUT_DAMAGE = 200;
    private static final int EXPLOSION_DAMAGE = 1800;

    private static final class Nut {
        private final String type;
        private int row;
        private double x;

        Nut(String type, int row, double x) {
            this.type = type;
            this.row = row;
            this.x = x;
        }
    }

    private final List<Nut> nuts = new ArrayList<>();
    private final Random random;

    BowlingGame(Random random) {
        this.random = random;
    }

    @Override
    public void init(GameSession session) {
        session.eventLog().add("Bowl your walnuts! You may only plant left of column "
                + RED_LINE_COLUMN + ".");
    }

    @Override
    public String plantingRejection(int x, int y) {
        return x > RED_LINE_COLUMN
                ? "Error: you may only plant left of the red line (column " + RED_LINE_COLUMN + ")."
                : null;
    }

    @Override
    public void onPlanted(GameSession session, Plant plant) {
        session.clearTile(plant.getRow(), plant.getCol());
        nuts.add(new Nut(plant.getSpec().getName(), plant.getRow(), plant.getCol() + 1));
        session.eventLog().add("The " + plant.getSpec().getName() + " starts rolling down lane "
                + (plant.getRow() + 1) + "!");
    }

    @Override
    public void tick(GameSession session, double seconds) {
        double dt = 1.0 / GameSession.TICKS_PER_SECOND;
        for (Nut nut : new ArrayList<>(nuts)) {
            nut.x += NUT_SPEED * dt;
            if (nut.x > GameSession.COLS + 0.5) {
                nuts.remove(nut);
                continue;
            }
            Zombie victim = session.getZombies().stream()
                    .filter(z -> z.getRow() == nut.row && Math.abs(z.getX() - nut.x) < 0.5)
                    .findFirst().orElse(null);
            if (victim != null) {
                smash(session, nut, victim);
            }
        }
    }

    private void smash(GameSession session, Nut nut, Zombie victim) {
        switch (nut.type) {
            case "explode-o-nut" -> {
                session.eventLog().add("The Explode O' Nut blew up in lane " + (nut.row + 1) + "!");
                for (Zombie zombie : session.getZombies()) {
                    if (Math.abs(zombie.getRow() - nut.row) <= 1 && Math.abs(zombie.getX() - nut.x) <= 1.5) {
                        session.hitZombie(zombie, EXPLOSION_DAMAGE);
                    }
                }
                nuts.remove(nut);
            }
            case "giant-wallnut" -> {
                session.eventLog().add("The giant walnut crushed " + victim.getSpec().getName() + "!");
                session.hitZombie(victim, Integer.MAX_VALUE / 2);
            }
            default -> {
                session.hitZombie(victim, NUT_DAMAGE);
                bounce(session, nut);
            }
        }
    }

    private void bounce(GameSession session, Nut nut) {
        int direction = nut.row == 0 ? 1 : nut.row == GameSession.ROWS - 1 ? -1
                : random.nextBoolean() ? 1 : -1;
        nut.row += direction;
        session.eventLog().add("The walnut bounced into lane " + (nut.row + 1) + ".");
    }
}
