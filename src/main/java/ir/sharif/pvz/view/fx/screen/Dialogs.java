package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.view.fx.GameUi;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The floating panels the project document asks for: a purchase confirmation,
 * a level briefing, and the win/lose report.
 */
public final class Dialogs {

    private static final double PANEL_WIDTH = 460;

    private Dialogs() {
    }

    /**
     * Asks before doing something that spends the player's money.
     */
    public static void confirm(GameUi ui, String title, String question, Runnable onYes) {
        Button yes = new Button("Confirm");
        yes.getStyleClass().add("primary-button");
        yes.setOnAction(event -> {
            ui.closeModal();
            onYes.run();
        });
        Button no = new Button("Cancel");
        no.getStyleClass().add("link-button");
        no.setOnAction(event -> ui.closeModal());

        HBox buttons = new HBox(12, yes, no);
        buttons.setAlignment(Pos.CENTER);
        ui.showModal(panel(title, question, buttons));
    }

    /**
     * A panel with a title, a body and whatever buttons the caller supplies.
     */
    public static VBox panel(String title, String body, HBox buttons) {
        Label heading = new Label(title);
        heading.getStyleClass().add("modal-title");
        Label text = new Label(body);
        text.getStyleClass().add("modal-body");
        text.setWrapText(true);

        VBox panel = new VBox(16, heading, text, buttons);
        panel.getStyleClass().add("modal-panel");
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(26));
        panel.setMaxWidth(PANEL_WIDTH);
        panel.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return panel;
    }

    /**
     * A panel whose body is a node rather than a line of text.
     */
    public static VBox panel(String title, javafx.scene.Node body, HBox buttons) {
        Label heading = new Label(title);
        heading.getStyleClass().add("modal-title");

        VBox panel = new VBox(16, heading, body, buttons);
        panel.getStyleClass().add("modal-panel");
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(26));
        panel.setMaxWidth(PANEL_WIDTH);
        panel.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return panel;
    }
}
