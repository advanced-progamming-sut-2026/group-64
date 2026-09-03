package ir.sharif.pvz.model.game;

/**
 * Pluggable behaviour of one minigame inside a session. Defaults are no-ops
 * so every game only overrides the hooks it needs.
 */
public interface MinigameLogic {

    default void init(GameSession session) {
    }

    default void tick(GameSession session, double seconds) {
    }

    /** Non-null return rejects planting on that tile (e.g. right of the red line). */
    default String plantingRejection(int x, int y) {
        return null;
    }

    /** When true, planting consumes from the hand instead of sun and cooldowns. */
    default boolean freePlantMode() {
        return false;
    }

    default String takeFromHand(String type) {
        return "Error: there is no plant at hand.";
    }

    default java.util.List<String> handContents() {
        return java.util.List.of();
    }

    /** Called right after a plant lands on the board. */
    default void onPlanted(GameSession session, Plant plant) {
    }

    /** Returns true when the minigame handled a zombie entering the house. */
    default boolean onHouseReached(GameSession session, Zombie zombie) {
        return false;
    }

    default String breakVase(GameSession session, int x, int y) {
        return "Error: there is no vase to break in this game.";
    }

    default String takePacket(GameSession session, int x, int y) {
        return "Error: there is no seed packet to take in this game.";
    }

    default java.util.List<String> vasesInfo() {
        return java.util.List.of("There is no vase in this game.");
    }

    default String placeZombie(GameSession session, String type, int x, int y) {
        return "Error: you cannot place zombies in this game.";
    }

    /**
     * Everything this minigame wants drawn on the lawn on top of the ordinary
     * plants and zombies, such as vases or rolling walnuts.
     */
    default java.util.List<MinigameProp> props() {
        return java.util.List.of();
    }

    /**
     * Trades two neighbouring plants, for the one game that rearranges the
     * lawn rather than planting on it.
     */
    default String swap(GameSession session, int x1, int y1, int x2, int y2) {
        return "Error: you cannot swap plants in this game.";
    }

    /**
     * The line this game wants on the objective bar, or null when the ordinary
     * level objectives belong there instead.
     */
    default String objective(GameSession session) {
        return null;
    }

    /**
     * Plants currently moving between tiles, for the view to draw part-way.
     * Only Beghouled rearranges the lawn, so everything else leaves this empty.
     */
    default java.util.List<MinigameSlide> slides() {
        return java.util.List.of();
    }

    /**
     * What the card bar should offer instead of the chosen plants. I, Zombie
     * hands the player zombies; the others leave this empty and keep the
     * normal seed packets.
     */
    default java.util.Map<String, Integer> cardsInsteadOfPlants() {
        return java.util.Map.of();
    }

    /**
     * The column of the line the player may not build across, or 0 when the
     * game has no such line.
     */
    default int restrictedColumn() {
        return 0;
    }
}
