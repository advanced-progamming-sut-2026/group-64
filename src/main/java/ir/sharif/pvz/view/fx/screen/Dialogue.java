package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.model.game.Chapter;
import ir.sharif.pvz.model.game.LevelSpec;

/**
 * The couple of lines a character says before a level starts and after it ends.
 *
 * <p>The project document lets these come from whatever characters we like, so
 * rather than model Crazy Dave and Penny the game uses a nameless gardener and
 * the neighbourhood's least helpful sunflower.
 */
public final class Dialogue {

    private Dialogue() {
    }

    /**
     * Who is talking before this level.
     */
    public static String speaker(LevelSpec level) {
        return level.isBoss() ? "Penny" : "Crazy Dave";
    }

    /**
     * The line shown on the level briefing.
     */
    public static String opening(LevelSpec level) {
        if (level.isBoss()) {
            return "User Dave, my sensors read one very large Zomboss ahead. "
                    + "Its armour comes off in three pieces — keep hitting it.";
        }
        if (level.getSpecial() != null) {
            return "WABBY WABBO! This one plays by its own rules. Read them twice!";
        }
        return switch (level.getChapter()) {
            case ANCIENT_EGYPT -> "I buried my taco somewhere in this desert. Guard it!";
            case FROSTBITE_CAVES -> "Brrr! Keep your plants moving or they will freeze solid.";
            case BIG_WAVE_BEACH -> "Watch the water line! Only water plants swim, you know.";
            case DARK_AGES -> "It is dark, it is spooky, and the graves are full. Perfect!";
        };
    }

    /**
     * The line shown once the level is decided.
     */
    public static String closing(boolean won, Chapter chapter) {
        if (won) {
            return chapter == Chapter.DARK_AGES
                    ? "You did it! Let us never speak of the wizard again."
                    : "BRAINS! I mean... great job. That was all me, obviously.";
        }
        return "They got through. Plant a few more sunflowers next time, trust me.";
    }
}
