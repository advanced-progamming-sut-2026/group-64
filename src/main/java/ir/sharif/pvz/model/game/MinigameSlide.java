package ir.sharif.pvz.model.game;

/**
 * A plant on its way from one tile to another, so the view can draw it
 * part-way instead of having it jump.
 *
 * <p>Beghouled is the one game that moves plants around: two trade places on a
 * swap, and after a line clears the plants above it fall into the gap while
 * fresh ones slide in from off the top of the board.
 *
 * @param plant    the plant type, so the view knows which sprite to draw
 * @param fromCol  starting column, 1-based (may sit off the board)
 * @param fromRow  starting row, 1-based (may sit off the board)
 * @param toCol    destination column, 1-based
 * @param toRow    destination row, 1-based
 * @param progress how far along it is, 0 at the start and 1 on arrival
 */
public record MinigameSlide(String plant, double fromCol, double fromRow,
                            int toCol, int toRow, double progress) {

    /**
     * Where to draw it right now, easing out so it settles rather than stops
     * dead.
     */
    public double col() {
        return fromCol + (toCol - fromCol) * eased();
    }

    public double row() {
        return fromRow + (toRow - fromRow) * eased();
    }

    private double eased() {
        double clamped = Math.max(0, Math.min(1, progress));
        return 1 - (1 - clamped) * (1 - clamped);
    }
}
