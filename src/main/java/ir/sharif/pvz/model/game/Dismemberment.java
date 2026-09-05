package ir.sharif.pvz.model.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The bits that come off a zombie: the armour a shot finally knocks loose, and
 * the head and arm that leave when the zombie itself does.
 *
 * <p>It owns the pieces in flight so that {@link GameSession} only has to tick
 * it and hand it the two moments it cares about.
 */
final class Dismemberment {

    /** As many pieces as can be in the air before old ones are dropped. */
    private static final int MAX_PIECES = 60;

    /**
     * The armour we have a sprite for. A piece that is not here — a knight's
     * crown, a newspaper — is left undrawn rather than dropped as something it
     * is not.
     */
    private static final List<String> ARMOUR_ART = List.of("cone", "bucket", "block");

    private final List<Debris> pieces = new ArrayList<>();
    private final Random random;

    Dismemberment(Random random) {
        this.random = random;
    }

    List<Debris> pieces() {
        return pieces;
    }

    void tick(double seconds) {
        for (Debris piece : pieces) {
            piece.passSeconds(seconds);
        }
        pieces.removeIf(Debris::isGone);
    }

    /**
     * Sends the armour this zombie just lost tumbling off its head.
     */
    void onArmourLost(Zombie zombie) {
        for (String piece : zombie.armourJustLost()) {
            String art = artFor(piece);
            if (art != null) {
                add(new Debris(Debris.Kind.ARMOUR, art, zombie.getRow(), zombie.getX(),
                        backwards(1.2), upwards(2.4), spin()));
            }
        }
    }

    /**
     * A zombie going down leaves its head and one arm behind.
     */
    void onDeath(Zombie zombie) {
        // the body keels over where it stood: no throw, no spin, and it holds
        // its place while the head and arm are flung off it
        add(new Debris(Debris.Kind.BODY, zombie.getSpec().getName(), zombie.getRow(),
                zombie.getX(), 0, 0, 0));
        add(new Debris(Debris.Kind.HEAD, "head", zombie.getRow(), zombie.getX(),
                backwards(0.9), upwards(3.0), spin()));
        add(new Debris(Debris.Kind.ARM, "arm", zombie.getRow(), zombie.getX(),
                backwards(1.6), upwards(2.2), spin()));
        for (String piece : zombie.getArmor().keySet()) {
            String art = artFor(piece);
            if (art != null) {
                add(new Debris(Debris.Kind.ARMOUR, art, zombie.getRow(), zombie.getX(),
                        backwards(1.4), upwards(2.6), spin()));
            }
        }
    }

    /**
     * The sprite for an armour piece, or null when we have none for it.
     */
    private static String artFor(String piece) {
        String name = piece.toLowerCase(Locale.ROOT);
        return ARMOUR_ART.contains(name) ? name : null;
    }

    /**
     * Pieces fly back the way the zombie came, with some spread.
     */
    private double backwards(double base) {
        return base + random.nextDouble() * 0.8;
    }

    private double upwards(double base) {
        return base + random.nextDouble() * 0.8;
    }

    private double spin() {
        return (random.nextDouble() - 0.5) * 12;
    }

    private void add(Debris piece) {
        if (pieces.size() >= MAX_PIECES) {
            pieces.remove(0);
        }
        pieces.add(piece);
    }
}
