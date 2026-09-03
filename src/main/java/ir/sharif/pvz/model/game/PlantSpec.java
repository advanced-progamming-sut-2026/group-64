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
    private Almanac almanac = Almanac.UNKNOWN;

    /**
     * What the project sheet says a plant does, as opposed to what the engine
     * needs to make it fight. The damage and interval are the sheet's own
     * wording — "20x2", "Insta-kill", "20/40/60/80/100" — because a single
     * number cannot say what those mean.
     *
     * @param damage    damage as the sheet writes it
     * @param interval  how often it acts, as the sheet writes it
     * @param ability   what the plant does
     * @param plantFood what a plant food does to it
     * @param upgrades  what levels two, three and four give, in order
     */
    public record Almanac(String damage, String interval, String ability,
                          String plantFood, List<String> upgrades) {

        /** Stands in for a plant the sheet has no row for. */
        public static final Almanac UNKNOWN =
                new Almanac("-", "-", "-", "-", List.of("-", "-", "-"));

        public Almanac {
            upgrades = List.copyOf(upgrades);
        }
    }

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

    /**
     * What the sheet says about this plant, for the collection menu.
     */
    public Almanac getAlmanac() {
        return almanac;
    }

    void setAlmanac(Almanac almanac) {
        this.almanac = almanac;
    }
}
