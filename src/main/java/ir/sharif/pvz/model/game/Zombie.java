package ir.sharif.pvz.model.game;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A zombie instance walking across the board. Row is fixed; x is continuous
 * (measured in columns, 9 at the right edge, below 1 means it reached the house).
 */
public class Zombie {

    private final ZombieSpec spec;
    private int row;
    private final boolean glowing;
    private double x;
    private int hp;
    private final Map<String, Integer> armor;
    /** Armour knocked off by the most recent hit, for the view to drop. */
    private final java.util.List<String> armourJustLost = new java.util.ArrayList<>();
    private double chilledSeconds;
    private double frozenSeconds;
    private double eatingSeconds;
    private double poisonedSeconds;
    private int poisonPerSecond;
    private boolean hypnotized;
    /** Seconds left of being in the air, for a zombie that was thrown. */
    private double airborneSeconds;
    private double airborneTotal;
    private double thrownFrom;
    private double thrownTo;

    public Zombie(ZombieSpec spec, int row, double x, int hp, Map<String, Integer> armor, boolean glowing) {
        this.spec = spec;
        this.row = row;
        this.x = x;
        this.hp = hp;
        this.armor = new LinkedHashMap<>(armor);
        this.glowing = glowing;
    }

    public ZombieSpec getSpec() {
        return spec;
    }

    public int getRow() {
        return row;
    }

    /**
     * Slippery ice and the pianist can push a zombie into a neighbouring row.
     */
    public void setRow(int row) {
        this.row = row;
    }

    public double getX() {
        return x;
    }

    public void walk(double deltaColumns) {
        this.x -= deltaColumns;
    }

    public int getHp() {
        return hp;
    }

    public int totalRemainingHealth() {
        return hp + armor.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Map<String, Integer> getArmor() {
        return new LinkedHashMap<>(armor);
    }

    public boolean isGlowing() {
        return glowing;
    }

    /**
     * Applies damage, consuming armor first.
     * Returns true when the zombie dies from this hit.
     */
    public boolean damage(int amount) {
        armourJustLost.clear();
        int remaining = amount;
        for (Map.Entry<String, Integer> piece : armor.entrySet()) {
            if (remaining <= 0) {
                break;
            }
            int absorbed = Math.min(piece.getValue(), remaining);
            piece.setValue(piece.getValue() - absorbed);
            remaining -= absorbed;
        }
        armor.forEach((name, left) -> {
            if (left <= 0) {
                armourJustLost.add(name);
            }
        });
        armor.values().removeIf(v -> v <= 0);
        hp -= remaining;
        return hp <= 0;
    }

    /**
     * The armour this zombie lost to the last hit it took, so the piece can be
     * sent tumbling off rather than just vanishing.
     */
    public java.util.List<String> armourJustLost() {
        return java.util.List.copyOf(armourJustLost);
    }

    /**
     * Goo-peashooter damage: it seeps past armour straight into the zombie.
     */
    public boolean damageIgnoringArmor(int amount) {
        hp -= amount;
        return hp <= 0;
    }

    /**
     * Poisons the zombie for a while; the damage lands once per second and
     * ignores armour, the way the document describes the goo pea.
     */
    public void poison(double seconds, int perSecond) {
        poisonedSeconds = Math.max(poisonedSeconds, seconds);
        poisonPerSecond = Math.max(poisonPerSecond, perSecond);
    }

    public boolean isPoisoned() {
        return poisonedSeconds > 0;
    }

    public int getPoisonPerSecond() {
        return poisonPerSecond;
    }

    /**
     * Magnet-shroom pulls every metal piece off the zombie's head.
     */
    public Map<String, Integer> stripArmor() {
        Map<String, Integer> taken = new LinkedHashMap<>(armor);
        armor.clear();
        return taken;
    }

    /**
     * Hypno-shroom (and Caulipower) turn a zombie around: it now walks back
     * toward the graveyard and attacks the zombies it meets instead of plants.
     */
    public void hypnotize() {
        hypnotized = true;
    }

    public boolean isHypnotized() {
        return hypnotized;
    }

    /**
     * Puts a restored zombie back under whatever was affecting it.
     */
    void restoreEffects(double chilled, double frozen, double poisoned, int poisonPerSecond,
                        boolean charmed) {
        this.chilledSeconds = chilled;
        this.frozenSeconds = frozen;
        this.poisonedSeconds = poisoned;
        this.poisonPerSecond = poisonPerSecond;
        this.hypnotized = charmed;
    }

    /** How long this zombie stays chilled, for a save. */
    public double chilledSeconds() {
        return chilledSeconds;
    }

    /** How long this zombie stays frozen, for a save. */
    public double frozenSeconds() {
        return frozenSeconds;
    }

    /** How long the poison on this zombie lasts, for a save. */
    public double poisonedSeconds() {
        return poisonedSeconds;
    }

    public void chill(double seconds) {
        chilledSeconds = Math.max(chilledSeconds, seconds);
    }

    public void freeze(double seconds) {
        frozenSeconds = Math.max(frozenSeconds, seconds);
    }

    /**
     * True while this zombie is chewing on a plant, which the view shows with a
     * biting motion rather than the walking one.
     */
    public boolean isEating() {
        return eatingSeconds > 0;
    }

    /**
     * Marks the zombie as biting; the flag lapses on its own if it stops.
     */
    void startEating() {
        eatingSeconds = 0.4;
    }

    /**
     * Throws this zombie to a tile further up the lawn. It travels in an arc
     * and does nothing on the way: it cannot walk, eat, or be shot until it
     * lands. The gargantuar's imp used to be put down at its landing spot with
     * no flight at all, which made the throw invisible.
     */
    void throwTo(double landing, double seconds) {
        this.thrownFrom = x;
        this.thrownTo = landing;
        this.airborneSeconds = seconds;
        this.airborneTotal = seconds;
    }

    /** True while it is still in the air and out of the game. */
    public boolean isAirborne() {
        return airborneSeconds > 0;
    }

    /**
     * How high above its lane it is, in lane heights; zero on the ground.
     */
    public double getLift() {
        if (!isAirborne()) {
            return 0;
        }
        return Math.sin(flightProgress() * Math.PI) * 1.1;
    }

    /** How far it has turned while tumbling through the air, in degrees. */
    public double getTumble() {
        return isAirborne() ? flightProgress() * 300 : 0;
    }

    private double flightProgress() {
        return airborneTotal <= 0 ? 1 : 1 - (airborneSeconds / airborneTotal);
    }

    public boolean isFrozen() {
        return frozenSeconds > 0;
    }

    public double speedMultiplier() {
        if (frozenSeconds > 0) {
            return 0;
        }
        return chilledSeconds > 0 ? 0.5 : 1;
    }

    public void passSeconds(double seconds) {
        chilledSeconds = Math.max(0, chilledSeconds - seconds);
        frozenSeconds = Math.max(0, frozenSeconds - seconds);
        eatingSeconds = Math.max(0, eatingSeconds - seconds);
        poisonedSeconds = Math.max(0, poisonedSeconds - seconds);
        if (poisonedSeconds <= 0) {
            poisonPerSecond = 0;
        }
        if (airborneSeconds > 0) {
            airborneSeconds = Math.max(0, airborneSeconds - seconds);
            // it is carried along its arc by the clock, and put down exactly
            // on its landing tile
            x = airborneSeconds > 0
                    ? thrownFrom + (thrownTo - thrownFrom) * flightProgress()
                    : thrownTo;
        }
    }

    public Map<String, Double> activeEffects() {
        Map<String, Double> effects = new LinkedHashMap<>();
        if (chilledSeconds > 0) {
            effects.put("chilled", chilledSeconds);
        }
        if (frozenSeconds > 0) {
            effects.put("frozen", frozenSeconds);
        }
        if (poisonedSeconds > 0) {
            effects.put("poisoned", poisonedSeconds);
        }
        return effects;
    }
}
