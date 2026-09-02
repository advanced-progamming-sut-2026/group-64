package ir.sharif.pvz.view.fx.widget;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * The three animated stickers a player can throw at their opponent.
 *
 * <p>They are built out of transitions rather than sprite sheets: the asset
 * dump has no animated art, and a spin or a bounce reads just as clearly while
 * staying a few lines of code.
 */
public final class Sticker {

    /** The stickers on offer, in the order the buttons show them. */
    public enum Kind {
        SPINNING_SUN("☀", "Spinning sun"),
        BOUNCING_BRAIN("🧠", "Bouncing brain"),
        SHAKING_ZOMBIE("🧟", "Shaking zombie");

        private final String glyph;
        private final String title;

        Kind(String glyph, String title) {
            this.glyph = glyph;
            this.title = title;
        }

        public String glyph() {
            return glyph;
        }

        public String title() {
            return title;
        }

        /**
         * The sticker with this name, or null when the text is not one of them.
         */
        public static Kind byName(String name) {
            for (Kind kind : values()) {
                if (kind.name().equals(name)) {
                    return kind;
                }
            }
            return null;
        }
    }

    private Sticker() {
    }

    /**
     * A large label playing this sticker's animation, ready to drop onto a
     * screen. It stops on its own after a few seconds.
     */
    public static Label play(Kind kind) {
        Label label = new Label(kind.glyph());
        label.getStyleClass().add("sticker");
        label.setStyle("-fx-font-size: 76px;");
        animationFor(kind, label).play();
        return label;
    }

    private static Animation animationFor(Kind kind, Label label) {
        return switch (kind) {
            case SPINNING_SUN -> spin(label);
            case BOUNCING_BRAIN -> bounce(label);
            case SHAKING_ZOMBIE -> shake(label);
        };
    }

    private static Animation spin(Label label) {
        RotateTransition spin = new RotateTransition(Duration.seconds(1.1), label);
        spin.setByAngle(360);
        spin.setCycleCount(3);
        spin.setInterpolator(Interpolator.LINEAR);
        ScaleTransition pulse = new ScaleTransition(Duration.seconds(0.55), label);
        pulse.setFromX(0.8);
        pulse.setFromY(0.8);
        pulse.setToX(1.15);
        pulse.setToY(1.15);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(6);
        return withFade(label, new ParallelTransition(spin, pulse));
    }

    private static Animation bounce(Label label) {
        TranslateTransition hop = new TranslateTransition(Duration.seconds(0.32), label);
        hop.setByY(-34);
        hop.setAutoReverse(true);
        hop.setCycleCount(10);
        hop.setInterpolator(Interpolator.EASE_BOTH);
        return withFade(label, hop);
    }

    private static Animation shake(Label label) {
        TranslateTransition wobble = new TranslateTransition(Duration.seconds(0.09), label);
        wobble.setByX(14);
        wobble.setAutoReverse(true);
        wobble.setCycleCount(34);
        RotateTransition tilt = new RotateTransition(Duration.seconds(0.18), label);
        tilt.setByAngle(12);
        tilt.setAutoReverse(true);
        tilt.setCycleCount(17);
        return withFade(label, new ParallelTransition(wobble, tilt));
    }

    /**
     * Fades whatever it is given out at the end, so the sticker clears itself.
     */
    private static Animation withFade(Label label, Animation body) {
        FadeTransition out = new FadeTransition(Duration.seconds(0.5), label);
        out.setFromValue(1);
        out.setToValue(0);
        out.setDelay(Duration.seconds(2.6));
        return new ParallelTransition(body, out);
    }
}
