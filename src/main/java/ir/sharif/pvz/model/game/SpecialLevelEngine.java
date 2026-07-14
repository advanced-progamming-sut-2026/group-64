package ir.sharif.pvz.model.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Runtime behaviour of the special-level rules inside one session: the
 * conveyor belt, the war timer, the dead line and the loss counters.
 */
class SpecialLevelEngine {

    private static final double BELT_PERIOD_SECONDS = 12;

    private final GameSession session;
    private final SpecialRules rules;
    private final Random random;
    private final List<String> belt = new ArrayList<>();
    private double nextBeltAtSeconds;
    private int kills;
    private int plantLosses;
    private boolean wavesReleased;

    SpecialLevelEngine(GameSession session, SpecialRules rules, Random random) {
        this.session = session;
        this.rules = rules;
        this.random = random;
    }

    boolean is(SpecialRules.Type type) {
        return rules != null && rules.getType() == type;
    }

    /**
     * Applies the start-of-level effects: the announcement, protected plants,
     * the first belt plant, and Plant-What-You-Get's frozen waves.
     */
    void init() {
        if (rules == null) {
            return;
        }
        session.eventLog().add(rules.describe());
        rules.getProtectedPlants().forEach((key, type) ->
                session.placeProtectedPlant(key / GameSession.COLS, key % GameSession.COLS, type));
        if (is(SpecialRules.Type.CONVEYOR_BELT)) {
            deliverBeltPlant();
        }
        if (is(SpecialRules.Type.PLANT_WHAT_YOU_GET)) {
            session.setWavesEnabled(false);
            session.setCooldownsSuspended(true);
            session.setSunAmount(rules.getInitialSun());
        }
    }

    void tick(double seconds) {
        if (rules == null) {
            return;
        }
        if (is(SpecialRules.Type.CONVEYOR_BELT) && seconds >= nextBeltAtSeconds) {
            deliverBeltPlant();
        }
        if (is(SpecialRules.Type.TIMED_WAR) && !session.isOver()) {
            if (kills >= rules.getTargetKills()) {
                session.winNow("You killed " + kills + " zombies in time!");
            } else if (seconds >= rules.getTimerSeconds()) {
                session.loseNow("Time is up; only " + kills + " of "
                        + rules.getTargetKills() + " zombies were killed.");
            }
        }
    }

    private void deliverBeltPlant() {
        List<String> pool = rules.getConveyorPlants();
        String plant = pool.get(random.nextInt(pool.size()));
        belt.add(plant);
        nextBeltAtSeconds += BELT_PERIOD_SECONDS;
        session.eventLog().add("The conveyor belt delivered a " + plant + ".");
    }

    void onZombieKilled() {
        kills++;
    }

    void onPlantDestroyed(Plant plant) {
        if (rules == null) {
            return;
        }
        if (session.isProtectedPlant(plant)) {
            session.loseNow("A protected plant was destroyed!");
            return;
        }
        plantLosses++;
        if (is(SpecialRules.Type.LOVE_YOUR_PLANTS) && plantLosses >= rules.getMaxPlantLosses()) {
            session.loseNow("You lost " + plantLosses + " plants; the level is failed.");
        }
    }

    void onZombieMoved(Zombie zombie) {
        if (rules != null && is(SpecialRules.Type.DEAD_LINE) && !session.isOver()
                && zombie.getX() < rules.getDeadlineColumn()) {
            session.loseNow("Zombie " + zombie.getSpec().getName()
                    + " crossed the dead line at column " + rules.getDeadlineColumn() + "!");
        }
    }

    boolean conveyorMode() {
        return is(SpecialRules.Type.CONVEYOR_BELT);
    }

    List<String> beltContents() {
        return new ArrayList<>(belt);
    }

    /**
     * Belt planting is free: no sun, no cooldown, but only what the belt holds.
     */
    String takeFromBelt(String type) {
        if (!belt.remove(type)) {
            return "Error: the belt does not hold a " + type + " right now.";
        }
        return null;
    }

    String startZombieWaves() {
        if (!is(SpecialRules.Type.PLANT_WHAT_YOU_GET)) {
            return "Error: this level's waves start on their own.";
        }
        if (wavesReleased) {
            return "Error: the waves are already coming.";
        }
        wavesReleased = true;
        session.setWavesEnabled(true);
        session.setCooldownsSuspended(false);
        return "The zombie waves are coming!";
    }
}
