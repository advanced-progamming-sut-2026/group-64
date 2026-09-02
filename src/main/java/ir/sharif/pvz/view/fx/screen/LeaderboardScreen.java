package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.model.LeaderboardService;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.view.fx.GameUi;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The score table, sortable by the same four columns the phase-1 command
 * offers. The current player's row is highlighted.
 */
public final class LeaderboardScreen extends Screen {

    private LeaderboardService.Column sortBy = LeaderboardService.Column.LEVEL;
    private boolean ascending;

    public LeaderboardScreen(GameUi ui) {
        super(ui);
    }

    @Override
    public Parent build() {
        VBox panel = Forms.panel(14, controls(), table());
        panel.setPadding(new Insets(18));
        panel.setMaxWidth(940);

        VBox column = new VBox(panel);
        column.setAlignment(Pos.TOP_CENTER);
        column.setPadding(new Insets(24));

        BorderPane layout = new BorderPane(column);
        layout.setTop(Chrome.bar(ui, "Leaderboard"));
        layout.getStyleClass().addAll("screen", "leaderboard-screen");
        return layout;
    }

    private HBox controls() {
        ChoiceBox<LeaderboardService.Column> picker = new ChoiceBox<>(
                FXCollections.observableArrayList(LeaderboardService.Column.values()));
        picker.setValue(sortBy);
        picker.setOnAction(event -> {
            sortBy = picker.getValue();
            ui.show(this);
        });

        ToggleButton order = new ToggleButton(ascending ? "Ascending" : "Descending");
        order.setSelected(ascending);
        order.getStyleClass().add("tab-button");
        order.setOnAction(event -> {
            ascending = order.isSelected();
            ui.show(this);
        });

        Label caption = new Label("Sort by");
        caption.getStyleClass().add("field-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(12, caption, picker, order, spacer);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private TableView<User> table() {
        LeaderboardService service = new LeaderboardService(ui.app().getContext().getUserRepository());
        TableView<User> view = new TableView<>(
                FXCollections.observableArrayList(service.ranking(sortBy, ascending)));
        view.setPlaceholder(Forms.hint("No player has registered yet."));
        view.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        view.setPrefHeight(520);

        view.getColumns().add(column("Player", User::getUsername));
        view.getColumns().add(column("Last level passed", service::lastPassed));
        view.getColumns().add(column("Levels", user -> String.valueOf(user.getLevelsPassed())));
        view.getColumns().add(column("Minigames", user -> String.valueOf(user.getMinigamesCompleted())));
        view.getColumns().add(column("Quests", user -> String.valueOf(user.getQuestsCompleted())));
        view.getColumns().add(column("Mow points", user -> String.valueOf(user.getMaxMewPoints())));

        highlightCurrentPlayer(view);
        return view;
    }

    private TableColumn<User, String> column(String title, Function<User, String> value) {
        TableColumn<User, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    /**
     * Marks the signed-in player's row so it stands out in a long table.
     */
    private void highlightCurrentPlayer(TableView<User> view) {
        String me = ui.user() == null ? null : ui.user().getUsername();
        view.setRowFactory(table -> new javafx.scene.control.TableRow<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                boolean mine = !empty && item != null && item.getUsername().equals(me);
                pseudoClassStateChanged(
                        javafx.css.PseudoClass.getPseudoClass("current-player"), mine);
            }
        });
    }
}
