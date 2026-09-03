package ir.sharif.pvz.model.game;

import java.util.ArrayList;
import java.util.List;

/**
 * How the zombies advance each tick: walking, being blocked into eating, and
 * what happens when one gets past the last plant.
 *
 * <p>It sits beside {@link GameSession} rather than inside it because that
 * class had grown past what the project's linter allows, and this is the one
 * part of a tick that is entirely about the zombie side.
 */
final class ZombieMovement {

    private final GameSession session;

    ZombieMovement(GameSession session) {
        this.session = session;
    }

    void tick() {
        double dt = 1.0 / GameSession.TICKS_PER_SECOND;
        driveMowers(dt);
        session.abilities.tick(dt);
        for (Zombie zombie : new ArrayList<>(session.zombies)) {
            if (!session.zombies.contains(zombie)) {
                continue;
            }
            if (zombie.isHypnotized()) {
                session.plantAbilities.walkBackAndFight(zombie, dt);
                continue;
            }
            Plant blocking = plantInFrontOf(zombie);
            if (blocking != null) {
                eat(zombie, blocking, dt);
            } else {
                walk(zombie, dt);
            }
        }
    }

    private void walk(Zombie zombie, double dt) {
        zombie.walk(speedOf(zombie) * dt);
        session.board.slideIfOnIce(zombie);
        session.special.onZombieMoved(zombie);
        if (!session.isLost() && zombie.getX() < 1) {
            reachHouse(zombie);
        }
    }

    /**
     * A newspaper zombie that has lost its paper storms forward at three times
     * the pace.
     */
    private double speedOf(Zombie zombie) {
        double speed = zombie.getSpec().getTilesPerSecond()
                * session.difficultyUp * zombie.speedMultiplier();
        if (zombie.getSpec().getName().equals("newspaper") && zombie.getArmor().isEmpty()) {
            speed *= 3;
        }
        return speed;
    }

    /**
     * The plant this zombie has walked into, or null when the way is clear.
     * The dodo rider glides over anything that is not a wall.
     */
    private Plant plantInFrontOf(Zombie zombie) {
        int col = (int) Math.round(zombie.getX()) - 1;
        if (col < 0 || col >= GameSession.COLS) {
            return null;
        }
        Plant plant = session.gridArray()[zombie.getRow()][col];
        if (plant != null && zombie.getSpec().getName().equals("dodo-rider")
                && plant.getSpec().getCategory() != PlantCategory.WALL) {
            return null;
        }
        return plant;
    }

    private void eat(Zombie zombie, Plant plant, double dt) {
        if (zombie.isFrozen()) {
            return;
        }
        zombie.startEating();
        double progress = session.eatProgress.merge(zombie, dt, Double::sum);
        if (progress >= 1) {
            session.eatProgress.put(zombie, progress - 1);
            session.plantAbilities.onEaten(plant, zombie);
            if (session.gridArray()[plant.getRow()][plant.getCol()] == plant) {
                session.plantHit(plant, (int) Math.round(
                        zombie.getSpec().getDamagePerSecond() * session.difficultyUp));
            }
        }
    }

    /**
     * A zombie reaching the house: the lane's mower takes it and everything
     * else in that lane, and without one the level is lost.
     */
    private void reachHouse(Zombie zombie) {
        if (session.minigame != null && session.minigame.onHouseReached(session, zombie)) {
            return;
        }
        int row = zombie.getRow();
        if (!session.mowers[row]) {
            session.loseNow("The zombie ate your brain; LOSER!!!");
            return;
        }
        session.mowers[row] = false;
        session.recordBurst(Burst.Kind.MOWER, 1, row + 1.0);
        session.rolling.add(new Mower(row));
        session.eventLog().add("The lawn mower in the row " + (row + 1) + " is triggered.");
    }

    /**
     * Rolls the mowers already on their way, taking whatever they catch up
     * with, until they leave the far edge.
     */
    private void driveMowers(double dt) {
        session.log.creditTo(LevelLog.MOWER);
        for (Mower mower : new ArrayList<>(session.rolling)) {
            mower.advance(dt);
            for (Zombie victim : new ArrayList<>(session.zombies)) {
                if (mower.catches(victim)) {
                    session.eventLog().add("The mower ran over a "
                            + victim.getSpec().getName() + ".");
                    session.killZombie(victim);
                }
            }
            if (mower.isGone()) {
                session.rolling.remove(mower);
            }
        }
        session.log.creditTo(null);
    }
}
