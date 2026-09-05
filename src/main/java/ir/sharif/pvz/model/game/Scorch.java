package ir.sharif.pvz.model.game;

/**
 * Ground the Dark Ages boss set alight.
 *
 * <p>Its wide move is called torching two whole rows, and all it did was
 * destroy what was standing there — so a player could plant again on the same
 * tile the moment it finished, and nothing said the fire had been there. The
 * rows keep burning for a few seconds now, and anything put down on them goes
 * up too.
 */
final class Scorch {

    /** When each 0-based row stops burning, in elapsed seconds. */
    private final double[] until = new double[GameSession.ROWS];

    /** Sets a row alight until the given moment. */
    void light(int row, double untilSeconds) {
        until[row] = Math.max(until[row], untilSeconds);
    }

    /** How much longer this row burns; zero when it is not alight. */
    double leftOn(int row, double now) {
        return Math.max(0, until[row] - now);
    }

    /** Burns away anything standing on ground that is still alight. */
    void burn(GameSession session) {
        double now = session.getElapsedSeconds();
        for (int row = 0; row < GameSession.ROWS; row++) {
            if (leftOn(row, now) <= 0) {
                continue;
            }
            for (int col = 1; col <= GameSession.COLS; col++) {
                Plant standing = session.plantAtTile(col, row + 1);
                if (standing != null) {
                    session.eventLog().add(standing.getSpec().getName()
                            + " burned up on the scorched ground.");
                    session.recordBurst(Burst.Kind.PLANT_LOST, col, row + 1.0);
                    session.destroyPlantSilently(standing);
                }
            }
        }
    }
}
