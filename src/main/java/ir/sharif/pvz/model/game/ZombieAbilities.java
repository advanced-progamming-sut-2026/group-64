package ir.sharif.pvz.model.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Periodic special behaviours of the chapter zombies. Each ability fires on
 * its own timer per zombie; death hooks undo lasting effects (stolen suns,
 * sheeped plants).
 */
class ZombieAbilities {

    private static final Map<String, Double> PERIODS = Map.of(
            "ra", 2.0,
            "explorer", 1.0,
            "tombraiser", 4.0,
            "hunter", 5.0,
            "octopus", 6.0,
            "fisherman", 5.0,
            "wizard", 6.0,
            "king", 8.0);

    private final GameSession session;
    private final Random random;
    private final Map<Zombie, Double> timers = new HashMap<>();
    private final Map<Zombie, Integer> stolenSuns = new HashMap<>();
    private final Map<Zombie, List<Plant>> sheepedPlants = new HashMap<>();
    private final Map<Plant, Integer> iceHits = new HashMap<>();
    private final Set<Zombie> torchOut = new HashSet<>();

    ZombieAbilities(GameSession session, Random random) {
        this.session = session;
        this.random = random;
    }

    void tick(double dt) {
        for (Zombie zombie : new ArrayList<>(session.zombieList())) {
            Double period = PERIODS.get(zombie.getSpec().getName());
            if (period == null || zombie.isFrozen()) {
                continue;
            }
            double elapsed = timers.merge(zombie, dt, Double::sum);
            if (elapsed >= period) {
                timers.put(zombie, elapsed - period);
                act(zombie);
            }
        }
    }

    /** Icy shots put out the explorer's torch. */
    void onIceHit(Zombie zombie) {
        if (zombie.getSpec().getName().equals("explorer") && torchOut.add(zombie)) {
            session.eventLog().add("The explorer's torch is put out.");
        }
    }

    void onDeath(Zombie zombie) {
        timers.remove(zombie);
        torchOut.remove(zombie);
        returnStolenSuns(zombie);
        freeSheepedPlants(zombie);
    }

    private void act(Zombie zombie) {
        switch (zombie.getSpec().getName()) {
            case "ra" -> stealSun(zombie);
            case "explorer" -> burnAhead(zombie);
            case "tombraiser" -> throwBones(zombie);
            case "hunter" -> throwIce(zombie);
            case "octopus" -> throwOctopus(zombie);
            case "fisherman" -> reelPlant(zombie);
            case "wizard" -> sheepPlant(zombie);
            case "king" -> knightZombie(zombie);
            default -> { }
        }
    }

    private void stealSun(Zombie ra) {
        List<Sun> ground = session.sunList().stream().filter(Sun::isOnGround).toList();
        if (ground.isEmpty()) {
            return;
        }
        Sun stolen = ground.get(0);
        session.sunList().remove(stolen);
        stolenSuns.merge(ra, stolen.value(), Integer::sum);
        session.eventLog().add("Ra stole a sun worth " + stolen.value() + "!");
    }

    private void returnStolenSuns(Zombie zombie) {
        Integer amount = stolenSuns.remove(zombie);
        if (amount == null) {
            return;
        }
        int col = Math.min(GameSession.COLS - 1, Math.max(0, (int) Math.round(zombie.getX()) - 1));
        for (int value = amount; value > 0; value -= 25) {
            session.sunList().add(new Sun(Sun.Kind.NORMAL, zombie.getRow(), col, 0));
        }
        session.eventLog().add("Ra dropped the stolen suns (" + amount + ") at ("
                + (col + 1) + ", " + (zombie.getRow() + 1) + ").");
    }

    private void burnAhead(Zombie explorer) {
        if (torchOut.contains(explorer)) {
            return;
        }
        int col = (int) Math.round(explorer.getX() - 1) - 1;
        if (col < 0 || col >= GameSession.COLS) {
            return;
        }
        Plant plant = session.gridArray()[explorer.getRow()][col];
        if (plant != null) {
            session.eventLog().add("The explorer's torch burned " + plant.getSpec().getName() + "!");
            session.plantHit(plant, plant.getHp());
        }
    }

    private void throwBones(Zombie tombraiser) {
        for (int i = 0; i < 2; i++) {
            session.raiseGrave(random.nextInt(GameSession.ROWS), random.nextInt(GameSession.COLS), null);
        }
    }

    private void throwIce(Zombie hunter) {
        Plant target = nearestPlantInRow(hunter);
        if (target == null) {
            return;
        }
        int hits = iceHits.merge(target, 1, Integer::sum);
        if (hits >= 3) {
            session.disablePlant(target, "ice");
            session.eventLog().add("The hunter froze " + target.getSpec().getName() + " solid!");
        } else {
            session.eventLog().add("The hunter threw ice at " + target.getSpec().getName()
                    + " (" + hits + "/3).");
        }
    }

    private void throwOctopus(Zombie octopus) {
        Plant target = nearestPlantInRow(octopus);
        if (target != null) {
            session.disablePlant(target, "octopus");
            session.eventLog().add("An octopus pinned down " + target.getSpec().getName() + "!");
        }
    }

    /**
     * The fisherman drags the nearest plant of his row one tile toward him;
     * a plant that cannot move (occupied tile) is torn out instead.
     */
    private void reelPlant(Zombie fisherman) {
        Plant target = nearestPlantInRow(fisherman);
        if (target == null) {
            return;
        }
        Plant[][] grid = session.gridArray();
        int destination = target.getCol() + 1;
        if (destination >= GameSession.COLS || grid[target.getRow()][destination] != null
                || destination + 1 >= Math.round(fisherman.getX())) {
            session.eventLog().add("The fisherman tore " + target.getSpec().getName() + " out!");
            session.plantHit(target, target.getHp());
            return;
        }
        grid[target.getRow()][target.getCol()] = null;
        Plant moved = new Plant(target.getSpec(), target.getRow(), destination, false);
        grid[target.getRow()][destination] = moved;
        session.eventLog().add("The fisherman reeled " + target.getSpec().getName() + " to ("
                + (destination + 1) + ", " + (target.getRow() + 1) + ").");
    }

    private void sheepPlant(Zombie wizard) {
        List<Plant> candidates = session.plantedPlants().stream()
                .filter(plant -> !session.isPlantDisabled(plant.getCol() + 1, plant.getRow() + 1))
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        Plant target = candidates.get(random.nextInt(candidates.size()));
        session.disablePlant(target, "spell");
        sheepedPlants.computeIfAbsent(wizard, key -> new ArrayList<>()).add(target);
        session.eventLog().add("The wizard turned " + target.getSpec().getName() + " into a sheep!");
    }

    private void freeSheepedPlants(Zombie wizard) {
        List<Plant> sheeped = sheepedPlants.remove(wizard);
        if (sheeped == null) {
            return;
        }
        for (Plant plant : sheeped) {
            if (session.gridArray()[plant.getRow()][plant.getCol()] == plant) {
                session.enablePlant(plant);
                session.eventLog().add(plant.getSpec().getName() + " is no longer a sheep.");
            }
        }
    }

    private void knightZombie(Zombie king) {
        Zombie nearest = null;
        double best = Double.MAX_VALUE;
        for (Zombie zombie : session.zombieList()) {
            if (!zombie.getSpec().getName().equals("normal")) {
                continue;
            }
            double distance = Math.abs(zombie.getRow() - king.getRow()) + Math.abs(zombie.getX() - king.getX());
            if (distance < best) {
                best = distance;
                nearest = zombie;
            }
        }
        if (nearest != null) {
            session.convertToKnight(nearest);
            session.eventLog().add("The king knighted a zombie in lane " + (nearest.getRow() + 1) + "!");
        }
    }

    private Plant nearestPlantInRow(Zombie zombie) {
        Plant nearest = null;
        double best = Double.MAX_VALUE;
        for (Plant plant : session.plantedPlants()) {
            if (plant.getRow() != zombie.getRow()) {
                continue;
            }
            double distance = Math.abs(zombie.getX() - (plant.getCol() + 1));
            if (distance < best) {
                best = distance;
                nearest = plant;
            }
        }
        return nearest;
    }
}
