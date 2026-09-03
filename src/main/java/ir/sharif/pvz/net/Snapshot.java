package ir.sharif.pvz.net;

import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.Plant;
import ir.sharif.pvz.model.game.Shot;
import ir.sharif.pvz.model.game.Sun;
import ir.sharif.pvz.model.game.VersusGame;
import ir.sharif.pvz.model.game.Zombie;
import java.util.ArrayList;
import java.util.List;

/**
 * The whole board at one instant, as it travels from the server to both
 * clients.
 *
 * <p>The document asks for the lawn, the clock and every entity on it to stay
 * in step across the two screens. The simplest way to guarantee that is to send
 * the lot: at nine by five tiles a full picture is a few hundred bytes, so
 * there is nothing to gain from sending differences and a great deal of
 * complexity to lose.
 */
public record Snapshot(
        double time,
        double roundSeconds,
        boolean over,
        boolean plantsWon,
        int plantSun,
        int zombieSun,
        List<Boolean> brains,
        List<PlantView> plants,
        List<ZombieView> zombies,
        List<SunView> suns,
        List<ShotView> shots) {

    /** A plant, at the tile it occupies. */
    public record PlantView(String type, int col, int row, int hp, int maxHp, boolean disabled) {
    }

    /** A zombie, at its exact position along the lane. */
    public record ZombieView(String type, double x, int row, int hp, int armor,
                             boolean eating, boolean frozen, boolean chilled) {
    }

    /** A sun waiting to be collected. */
    public record SunView(int col, int row, String kind) {
    }

    /** A projectile in flight. */
    public record ShotView(String kind, double col, int row, double progress, boolean lobbed) {
    }

    /**
     * Reads the current state of a running versus match.
     */
    public static Snapshot of(GameSession session, VersusGame rules) {
        List<PlantView> plants = new ArrayList<>();
        for (Plant plant : session.plantedPlants()) {
            plants.add(new PlantView(plant.getSpec().getName(), plant.getCol() + 1,
                    plant.getRow() + 1, plant.getHp(), plant.maxHp(),
                    session.isPlantDisabled(plant.getCol() + 1, plant.getRow() + 1)));
        }
        List<ZombieView> zombies = new ArrayList<>();
        for (Zombie zombie : session.getZombies()) {
            zombies.add(new ZombieView(zombie.getSpec().getName(), zombie.getX(), zombie.getRow(),
                    zombie.getHp(), armourOf(zombie), zombie.isEating(), zombie.isFrozen(),
                    zombie.activeEffects().containsKey("chilled")));
        }
        List<SunView> suns = new ArrayList<>();
        for (Sun sun : session.groundSuns()) {
            suns.add(new SunView(sun.getCol(), sun.getRow(), sun.getKind().name()));
        }
        List<ShotView> shots = new ArrayList<>();
        for (Shot shot : session.getShots()) {
            shots.add(new ShotView(shot.getKind(), shot.currentX(), shot.getRow(),
                    shot.progress(), shot.getFlight() == Shot.Flight.LOBBED));
        }
        List<Boolean> brains = new ArrayList<>();
        for (boolean brain : rules.brains()) {
            brains.add(brain);
        }
        return new Snapshot(session.getElapsedSeconds(), VersusGame.ROUND_SECONDS,
                session.isOver(), session.isWon(), session.getSunAmount(),
                rules.getZombieSun(), brains, plants, zombies, suns, shots);
    }

    private static int armourOf(Zombie zombie) {
        return zombie.getArmor().values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * How long the plant side still has to hold out.
     */
    public double secondsLeft() {
        return Math.max(0, roundSeconds - time);
    }
}
