package ir.sharif.pvz.model.game;

import java.util.List;

/**
 * Static definition of a plant type, loaded from data/plants.csv.
 */
public class PlantSpec {

    private final String name;
    private final PlantCategory category;
    private final int sunCost;
    private final double rechargeSeconds;
    private final int hp;
    private final int damage;
    private final double attackPeriodSeconds;
    private final List<String> tags;

    public PlantSpec(String name, PlantCategory category, int sunCost, double rechargeSeconds,
                     int hp, int damage, double attackPeriodSeconds, List<String> tags) {
        this.name = name;
        this.category = category;
        this.sunCost = sunCost;
        this.rechargeSeconds = rechargeSeconds;
        this.hp = hp;
        this.damage = damage;
        this.attackPeriodSeconds = attackPeriodSeconds;
        this.tags = List.copyOf(tags);
    }

    public String getName() {
        return name;
    }

    public PlantCategory getCategory() {
        return category;
    }

    public int getSunCost() {
        return sunCost;
    }

    public double getRechargeSeconds() {
        return rechargeSeconds;
    }

    public int getHp() {
        return hp;
    }

    public int getDamage() {
        return damage;
    }

    public double getAttackPeriodSeconds() {
        return attackPeriodSeconds;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
