package ir.sharif.pvz.model.game;

/**
 * A plant instance placed on the board.
 */
public class Plant {

    private final PlantSpec spec;
    private final int row;
    private final int col;
    private int hp;
    private double attackCooldownSeconds;
    private double armSeconds;
    private boolean boosted;

    public Plant(PlantSpec spec, int row, int col, boolean boosted) {
        this.spec = spec;
        this.row = row;
        this.col = col;
        this.hp = spec.getHp();
        this.boosted = boosted;
        this.armSeconds = spec.getCategory() == PlantCategory.TRAP ? 15 : 0;
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
        this.hp = spec.getHp();
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

    public void resetAttackCooldown() {
        attackCooldownSeconds = spec.getAttackPeriodSeconds();
    }

    public boolean isArmed() {
        return armSeconds <= 0;
    }

    public void passSeconds(double seconds) {
        attackCooldownSeconds = Math.max(0, attackCooldownSeconds - seconds);
        armSeconds = Math.max(0, armSeconds - seconds);
    }

    public boolean isBoosted() {
        return boosted;
    }

    public void consumeBoost() {
        boosted = false;
    }
}
