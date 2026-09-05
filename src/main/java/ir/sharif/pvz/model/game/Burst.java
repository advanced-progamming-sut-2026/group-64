package ir.sharif.pvz.model.game;

/**
 * A moment worth showing on the lawn: an explosion, a zombie coming apart, a
 * plant being eaten.
 *
 * <p>Like {@link Shot}, a burst carries no rules — the damage it stands for was
 * already applied. It exists so the view has something to animate, and it ages
 * out on its own.
 */
public final class Burst {

    /** What happened, which decides how the view draws it. */
    public enum Kind {
        /** A cherry bomb, potato mine or other blast. */
        EXPLOSION,
        /** A zombie died normally. */
        ZOMBIE_DOWN,
        /** A plant was destroyed. */
        PLANT_LOST,
        /** A mower ran down a lane. */
        MOWER,
        /** A plant food was spent on a plant. */
        PLANT_FOOD,
        /** A zombie used a trick we draw nothing special for. */
        ABILITY,
        /** The sun-stealer took a sun off the lawn. */
        SUN_STOLEN,
        /** The grave-raiser threw its bones. */
        BONES,
        /** The hunter threw its ice. */
        ICE_THROW,
        /** The octopus-thrower let one go. */
        OCTOPUS_THROW,
        /** The all-star kicked the plant in front of it. */
        KICK,
        /** The gargantuar brought its hammer down. */
        SMASH,
        /** A snorkel went under, or came back up. */
        DIVE
    }

    private static final double LIFETIME_SECONDS = 0.7;

    private final Kind kind;
    private final double col;
    private final double row;

    private double age;

    Burst(Kind kind, double col, double row) {
        this.kind = kind;
        this.col = col;
        this.row = row;
    }

    void passSeconds(double seconds) {
        age += seconds;
    }

    public Kind getKind() {
        return kind;
    }

    public double getCol() {
        return col;
    }

    public double getRow() {
        return row;
    }

    /**
     * How far through its life the burst is, 0 at the flash and 1 when spent.
     */
    public double progress() {
        return Math.min(1, age / LIFETIME_SECONDS);
    }

    public boolean isDone() {
        return progress() >= 1;
    }

    /**
     * How hard the camera should shake for this burst, 0 when it should not.
     */
    public double shake() {
        return kind == Kind.EXPLOSION ? (1 - progress()) * 7 : 0;
    }
}
