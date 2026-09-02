package ir.sharif.pvz.model.game;

/**
 * Something a minigame puts on the lawn that is neither a plant nor a zombie —
 * a vase to smash, a walnut rolling down its lane.
 *
 * <p>The engine keeps these in whatever shape suits its own logic; this is the
 * flat description the view needs to draw one, in the usual 1-based board
 * coordinates (which may be fractional while a prop is moving).
 *
 * @param art    sprite name, resolved by the view against its asset folders
 * @param kind   what the prop is, so the view can pick a folder and a style
 * @param col    column on the board, 1-based and possibly fractional
 * @param row    row on the board, 1-based
 * @param label  short caption to draw over it, or null for none
 */
public record MinigameProp(String art, String kind, double col, double row, String label) {

    /** A vase the player can click to smash open. */
    public static MinigameProp vase(int col, int row, String kind) {
        return new MinigameProp("vase", kind, col, row, kind.equals("normal") ? null : kind);
    }

    /** A seed packet that fell out of a vase and is waiting to be picked up. */
    public static MinigameProp packet(String plantType, int col, int row) {
        return new MinigameProp(plantType, "packet", col, row, "take");
    }

    /** A walnut rolling down a bowling lane. */
    public static MinigameProp nut(String plantType, double col, int row) {
        return new MinigameProp(plantType, "nut", col, row, null);
    }
}
