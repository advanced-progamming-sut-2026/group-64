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
    private double chilledSeconds;
    private double frozenSeconds;

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
     * Applies damage, consuming armor first (poison would bypass armor; not needed yet).
     * Returns true when the zombie dies from this hit.
     */
    public boolean damage(int amount) {
        int remaining = amount;
        for (Map.Entry<String, Integer> piece : armor.entrySet()) {
            if (remaining <= 0) {
                break;
            }
            int absorbed = Math.min(piece.getValue(), remaining);
            piece.setValue(piece.getValue() - absorbed);
            remaining -= absorbed;
        }
        armor.values().removeIf(v -> v <= 0);
        hp -= remaining;
        return hp <= 0;
    }

    public void chill(double seconds) {
        chilledSeconds = Math.max(chilledSeconds, seconds);
    }

    public void freeze(double seconds) {
        frozenSeconds = Math.max(frozenSeconds, seconds);
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
    }

    public Map<String, Double> activeEffects() {
        Map<String, Double> effects = new LinkedHashMap<>();
        if (chilledSeconds > 0) {
            effects.put("chilled", chilledSeconds);
        }
        if (frozenSeconds > 0) {
            effects.put("frozen", frozenSeconds);
        }
        return effects;
    }
}
