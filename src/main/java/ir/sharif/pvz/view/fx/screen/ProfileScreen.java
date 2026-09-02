package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.model.User;
import ir.sharif.pvz.view.fx.GameUi;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The player's statistics on the left, the edit forms on the right. Changing
 * the password asks for the old one, exactly as the phase-1 controller does.
 */
public final class ProfileScreen extends Screen {

    private static final double COLUMN_WIDTH = 420;

    public ProfileScreen(GameUi ui) {
        super(ui);
    }

    @Override
    public Parent build() {
        HBox columns = new HBox(24, stats(ui.user()), edits());
        columns.setPadding(new Insets(24));
        columns.setAlignment(Pos.TOP_CENTER);

        BorderPane layout = new BorderPane(columns);
        layout.setTop(Chrome.bar(ui, "Profile"));
        layout.getStyleClass().addAll("screen", "profile-screen");
        return layout;
    }

    private VBox stats(User user) {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(10);
        int row = 0;
        row = stat(grid, row, "Username", user.getUsername());
        row = stat(grid, row, "Nickname", user.getNickname());
        row = stat(grid, row, "Email", user.getEmail());
        row = stat(grid, row, "Games played", String.valueOf(user.getGamesPlayed()));
        row = stat(grid, row, "Levels passed", String.valueOf(user.getLevelsPassed()));
        row = stat(grid, row, "Best mew points", String.valueOf(user.getMaxMewPoints()));
        row = stat(grid, row, "Coins", String.valueOf(user.getCoins()));
        stat(grid, row, "Diamonds", String.valueOf(user.getDiamonds()));

        VBox panel = Forms.panel(14, Forms.heading("Your progress"), grid);
        panel.setPrefWidth(COLUMN_WIDTH);
        return panel;
    }

    private int stat(GridPane grid, int row, String caption, String value) {
        Label key = new Label(caption);
        key.getStyleClass().add("stat-key");
        Label val = new Label(value);
        val.getStyleClass().add("stat-value");
        grid.add(key, 0, row);
        grid.add(val, 1, row);
        return row + 1;
    }

    private VBox edits() {
        VBox panel = Forms.panel(14, Forms.heading("Change your details"));
        panel.setPrefWidth(COLUMN_WIDTH);
        panel.getChildren().addAll(
                renameRow("Username", "change username -u "),
                renameRow("Nickname", "change nickname -n "),
                renameRow("Email", "change email -e "),
                passwordRow());
        return panel;
    }

    private VBox renameRow(String caption, String commandPrefix) {
        TextField field = new TextField();
        field.getStyleClass().add("field-input");
        Button apply = new Button("Save");
        apply.getStyleClass().add("primary-button");
        apply.setOnAction(event ->
                ui.submit(commandPrefix + SignupScreen.blankToDash(field.getText())));

        Label label = new Label(caption);
        label.getStyleClass().add("field-label");
        HBox row = new HBox(10, field, apply);
        row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(4, label, row);
    }

    private VBox passwordRow() {
        PasswordField oldPassword = new PasswordField();
        PasswordField newPassword = new PasswordField();
        Button apply = new Button("Change password");
        apply.getStyleClass().add("primary-button");
        apply.setOnAction(event -> ui.submit("change password -p "
                + SignupScreen.blankToDash(newPassword.getText())
                + " -o " + SignupScreen.blankToDash(oldPassword.getText())));
        return Forms.panel(8,
                Forms.field("Current password", oldPassword),
                Forms.field("New password", newPassword),
                apply);
    }
}
