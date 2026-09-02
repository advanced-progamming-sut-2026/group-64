package ir.sharif.pvz.view.fx.screen;

import javafx.geometry.Pos;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Small helpers so every form on every screen is laid out the same way.
 */
public final class Forms {

    private Forms() {
    }

    /**
     * A field with its caption above it.
     */
    public static VBox field(String caption, Control control) {
        Label label = new Label(caption);
        label.getStyleClass().add("field-label");
        control.getStyleClass().add("field-input");
        VBox box = new VBox(4, label, control);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    /**
     * A heading for a group of related controls.
     */
    public static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-heading");
        return label;
    }

    /**
     * A muted explanatory line.
     */
    public static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hint");
        label.setWrapText(true);
        return label;
    }

    /**
     * A panel that groups controls on the tinted card background.
     */
    public static VBox panel(double spacing, javafx.scene.Node... children) {
        VBox panel = new VBox(spacing, children);
        panel.getStyleClass().add("panel");
        return panel;
    }
}
