package ir.sharif.pvz.model.game;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static definition of a zombie type, loaded from data/zombies.csv.
 */
public class ZombieSpec {

    private final String name;
    private final int hp;
    private final Map<String, Integer> armor;
    private final double tilesPerSecond;
    private final int damagePerSecond;
    private final int waveCost;
    private final String description;

    public ZombieSpec(String name, int hp, Map<String, Integer> armor, double tilesPerSecond,
                      int damagePerSecond, int waveCost, String description) {
        this.name = name;
        this.hp = hp;
        this.armor = new LinkedHashMap<>(armor);
        this.tilesPerSecond = tilesPerSecond;
        this.damagePerSecond = damagePerSecond;
        this.waveCost = waveCost;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public int getHp() {
        return hp;
    }

    /**
     * Armor piece name to hit points; must be destroyed before the zombie itself takes damage.
     */
    public Map<String, Integer> getArmor() {
        return new LinkedHashMap<>(armor);
    }

    public double getTilesPerSecond() {
        return tilesPerSecond;
    }

    public int getDamagePerSecond() {
        return damagePerSecond;
    }

    public int getWaveCost() {
        return waveCost;
    }

    public String getDescription() {
        return description;
    }
}
