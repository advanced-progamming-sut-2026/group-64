package ir.sharif.pvz.model.game;

/**
 * Pluggable behaviour of one minigame inside a session. Defaults are no-ops
 * so every game only overrides the hooks it needs.
 */
interface MinigameLogic {

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
}
