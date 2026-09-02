package ir.sharif.pvz.view.fx;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

/**
 * The background music: one looping track at a time, swapped when the player
 * moves between the menus and a chapter.
 *
 * <p>Kept separate from {@link Sfx} because the pause menu controls the two
 * independently, the way the project document's screenshot shows.
 */
public final class Music {

    private static final String ROOT = "/audio/music/";

    private static MediaPlayer player;
    private static String playing;
    private static double volume = 0.35;
    private static boolean enabled = true;

    private Music() {
    }

    /**
     * Plays the menu theme.
     */
    public static void menu() {
        play("menu");
    }

    /**
     * Plays the theme for a chapter, falling back to the menu music when that
     * chapter has no track of its own.
     */
    public static void chapter(String chapterId) {
        if (Music.class.getResource(ROOT + chapterId + ".mp3") == null) {
            play("menu");
        } else {
            play(chapterId);
        }
    }

    /**
     * Starts a track on a loop, doing nothing if it is already the one playing.
     */
    public static void play(String track) {
        if (!enabled || track.equals(playing)) {
            return;
        }
        var url = Music.class.getResource(ROOT + track + ".mp3");
        if (url == null) {
            return;
        }
        stop();
        try {
            player = new MediaPlayer(new Media(url.toExternalForm()));
            player.setCycleCount(MediaPlayer.INDEFINITE);
            player.setVolume(volume);
            player.play();
            playing = track;
        } catch (RuntimeException e) {
            // no audio device, or a codec the platform will not open; the game
            // is perfectly playable without music
            enabled = false;
            player = null;
            playing = null;
        }
    }

    public static void stop() {
        if (player != null) {
            player.dispose();
            player = null;
        }
        playing = null;
    }

    public static void setVolume(double level) {
        volume = Math.max(0, Math.min(1, level));
        if (player != null) {
            player.setVolume(volume);
        }
    }

    public static double getVolume() {
        return volume;
    }

    /**
     * Turns the music on or off, resuming the last track when switched back on.
     */
    public static void setEnabled(boolean on) {
        enabled = on;
        if (!on) {
            String was = playing;
            stop();
            playing = was;
        } else if (playing != null) {
            String resume = playing;
            playing = null;
            play(resume);
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }
}
