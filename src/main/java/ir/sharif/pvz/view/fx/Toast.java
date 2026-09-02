package ir.sharif.pvz.view.fx;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * The stack of temporary notifications the project document asks for: every
 * message a controller produces slides in at the top of the screen and fades
 * out on its own, so no menu needs a dedicated error area.
 */
public final class Toast {

    private static final Duration VISIBLE = Duration.seconds(2.6);
    private static final Duration FADE = Duration.millis(400);
    private static final int MAX_STACKED = 4;

    private final VBox stack = new VBox(8);

    public Toast() {
        stack.setAlignment(Pos.TOP_RIGHT);
        stack.setPickOnBounds(false);
        stack.setMouseTransparent(true);
        StackPane.setAlignment(stack, Pos.TOP_RIGHT);
        stack.getStyleClass().add("toast-stack");
    }

    /**
     * The node to overlay on top of a screen; it never blocks mouse input.
     */
    public VBox node() {
        return stack;
    }

    public void info(String message) {
        show(message, "toast-info");
    }

    public void error(String message) {
        show(message, "toast-error");
    }

    private void show(String message, String styleClass) {
        Label label = new Label(message);
        label.getStyleClass().addAll("toast", styleClass);
        label.setWrapText(true);
        label.setMaxWidth(360);
        stack.getChildren().add(label);
        while (stack.getChildren().size() > MAX_STACKED) {
            stack.getChildren().remove(0);
        }

        FadeTransition in = new FadeTransition(FADE, label);
        in.setFromValue(0);
        in.setToValue(1);
        FadeTransition out = new FadeTransition(FADE, label);
        out.setFromValue(1);
        out.setToValue(0);
        SequentialTransition life =
                new SequentialTransition(in, new PauseTransition(VISIBLE), out);
        life.setOnFinished(event -> stack.getChildren().remove(label));
        life.play();
    }
}
