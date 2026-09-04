package ir.sharif.pvz.model.game;

/**
 * A plant instance placed on the board.
 */
public class Plant {

    /** What each level above the first adds to the plant's damage and health. */
    public static final double LEVEL_BONUS = 0.25;

    private final PlantSpec spec;
    private final int row;
    private final int col;
    private final int level;
    private int hp;
    private double attackCooldownSeconds;
    private double armSeconds;
    private double ageSeconds;
    private int stack = 1;
    private boolean boosted;

    public Plant(PlantSpec spec, int row, int col, boolean boosted) {
        this(spec, row, col, boosted, 1);
    }

    public Plant(PlantSpec spec, int row, int col, boolean boosted, int level) {
        this.spec = spec;
        this.row = row;
        this.col = col;
        this.level = Math.max(1, level);
        this.hp = maxHp();
        this.boosted = boosted;
        this.armSeconds = spec.getCategory() == PlantCategory.TRAP
                && spec.hasTag("charge") ? 15 : 0;
    }

    /**
     * The level the player upgraded this plant to in the collection menu.
     */
    public int getLevel() {
        return level;
    }

    /**
     * Damage after the collection upgrades, which is what the combat uses.
     */
    public int getDamage() {
        return (int) Math.round(spec.getDamage() * levelMultiplier());
    }

    /**
     * Health after the collection upgrades; a fresh plant starts here.
     */
    public int maxHp() {
        return (int) Math.round(spec.getHp() * levelMultiplier());
    }

    private double levelMultiplier() {
        return 1 + LEVEL_BONUS * (level - 1);
    }

    public PlantSpec getSpec() {
        return spec;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public int getHp() {
        return hp;
    }

    public void heal() {
        this.hp = maxHp();
    }

    /**
     * Puts a restored plant back at the health, size and age it was saved with.
     */
    void restoreTo(int hp, int stack, double ageSeconds) {
        this.hp = hp;
        this.stack = Math.max(1, stack);
        this.ageSeconds = ageSeconds;
        this.armSeconds = 0;
    }

    /**
     * Applies damage and reports whether the plant was destroyed by it.
     */
    public boolean damage(int amount) {
        hp -= amount;
        return hp <= 0;
    }

    public boolean isReadyToAttack() {
        return attackCooldownSeconds <= 0;
    }

    /**
     * How long since this plant last acted, as a fraction of its own period: 0
     * the instant it fires and 1 once it is ready again. The view animates the
     * recoil off this, so a fast shooter kicks often and a chomper's long
     * chew reads as one slow swallow.
     */
    public double sinceItActed() {
        double period = spec.getAttackPeriodSeconds();
        if (period <= 0) {
            return 1;
        }
        return Math.max(0, Math.min(1, 1 - attackCooldownSeconds / period));
    }

    public void resetAttackCooldown() {
        attackCooldownSeconds = spec.getAttackPeriodSeconds();
    }

    public boolean isArmed() {
        return armSeconds <= 0;
    }

    /**
     * How long this plant has been on the lawn. The short-lived shrooms wilt on
     * it and the ramp-up plants (sun-shroom, kiwibeast) grow on it.
     */
    public double getAgeSeconds() {
        return ageSeconds;
    }

    /**
     * How many heads a stacking plant has (pea pod); one for everything else.
     */
    public int getStack() {
        return stack;
    }

    /**
     * Adds a head to a stacking plant, up to the five the document allows.
     */
    public boolean addToStack() {
        if (stack >= 5) {
            return false;
        }
        stack++;
        return true;
    }

    /**
     * The stage a ramp-up plant has grown into: 1 for the first 24 seconds,
     * 2 up to 72, then 3.
     */
    public int getStage() {
        if (!spec.hasTag("wramp-up")) {
            return 1;
        }
        if (ageSeconds >= 72) {
            return 3;
        }
        return ageSeconds >= 24 ? 2 : 1;
    }

    public void passSeconds(double seconds) {
        attackCooldownSeconds = Math.max(0, attackCooldownSeconds - seconds);
        armSeconds = Math.max(0, armSeconds - seconds);
        ageSeconds += seconds;
    }

    public boolean isBoosted() {
        return boosted;
    }

    public void consumeBoost() {
        boosted = false;
    }
}
