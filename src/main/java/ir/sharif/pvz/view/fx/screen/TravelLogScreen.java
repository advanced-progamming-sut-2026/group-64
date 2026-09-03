package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.controller.MenuType;
import ir.sharif.pvz.model.Quest;
import ir.sharif.pvz.model.QuestCatalog;
import ir.sharif.pvz.model.QuestService;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.view.fx.GameUi;
import java.util.Comparator;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The travel log: every quest with its reward and progress, ordered by
 * priority, plus the way through to the minigames.
 */
public final class TravelLogScreen extends Screen {

    private String page;

    public TravelLogScreen(GameUi ui) {
        super(ui);
    }

    @Override
    public Parent build() {
        VBox list = new VBox(12);
        QuestService service = new QuestService(
                ui.app().getContext().getUserRepository(), System::currentTimeMillis);
        List<Quest> quests = QuestCatalog.all().stream()
                .filter(quest -> page == null || quest.getPage().equals(page))
                .sorted(Comparator.comparing(Quest::getPriority))
                .toList();
        for (Quest quest : quests) {
            list.getChildren().add(row(quest, service));
        }

        ScrollPane scroller = new ScrollPane(list);
        scroller.setFitToWidth(true);
        scroller.getStyleClass().add("quest-scroll");
        VBox.setVgrow(scroller, Priority.ALWAYS);

        VBox panel = Forms.panel(14, pageTabs(), scroller, minigameLink());
        panel.setPadding(new Insets(18));
        panel.setMaxWidth(880);
        VBox.setVgrow(scroller, Priority.ALWAYS);

        VBox column = new VBox(panel);
        column.setAlignment(Pos.TOP_CENTER);
        column.setPadding(new Insets(22));
        VBox.setVgrow(panel, Priority.ALWAYS);

        BorderPane layout = new BorderPane(column);
        layout.setTop(Chrome.bar(ui, "Quests"));
        layout.getStyleClass().addAll("screen", "quest-screen");
        return layout;
    }

    private HBox pageTabs() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getChildren().add(tab("All", null));
        QuestCatalog.all().stream().map(Quest::getPage).distinct()
                .forEach(name -> bar.getChildren().add(tab(capitalise(name), name)));
        return bar;
    }

    private Button tab(String caption, String target) {
        Button button = new Button(caption);
        button.getStyleClass().add(
                java.util.Objects.equals(page, target) ? "tab-button-active" : "tab-button");
        button.setOnAction(event -> {
            page = target;
            ui.show(this);
        });
        return button;
    }

    /**
     * A bar filled to match a "34 / 50" readout.
     */
    private javafx.scene.control.ProgressBar progressBar(String measured) {
        javafx.scene.control.ProgressBar bar = new javafx.scene.control.ProgressBar(0);
        bar.setPrefWidth(140);
        String[] parts = measured.split("/");
        try {
            double done = Double.parseDouble(parts[0].trim());
            double target = Double.parseDouble(parts[1].trim());
            bar.setProgress(target <= 0 ? 0 : Math.min(1, done / target));
        } catch (RuntimeException e) {
            // a readout that is not two numbers just leaves the bar empty
            bar.setProgress(0);
        }
        return bar;
    }

    private HBox row(Quest quest, QuestService service) {
        User user = ui.user();
        String status = service.status(user, quest);
        boolean ready = "ready to claim".equals(status);

        Label title = new Label(quest.getTitle());
        title.getStyleClass().add("quest-title");
        Label reward = new Label("Reward: " + quest.getRewardDescription());
        reward.getStyleClass().add("quest-reward");
        Label priority = new Label(quest.getPriority().name());
        priority.getStyleClass().addAll("quest-priority",
                "priority-" + quest.getPriority().name().toLowerCase(java.util.Locale.ROOT));

        VBox text = new VBox(4, new HBox(10, title, priority), reward);
        // the counting quests say how far along they are, with a bar to match
        String measured = quest.progress(user, java.time.LocalDate.now().toString());
        if (measured != null) {
            Label done = new Label(measured);
            done.getStyleClass().add("quest-reward");
            text.getChildren().add(new HBox(8, progressBar(measured), done));
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button claim = new Button(ready ? "Claim" : status);
        claim.getStyleClass().add(ready ? "primary-button" : "ghost-button");
        claim.setDisable(!ready);
        claim.setOnAction(event -> ui.submit("travel log claim -q " + quest.getId()));

        HBox row = new HBox(14, text, spacer, claim);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12));
        row.getStyleClass().add("quest-row");
        return row;
    }

    private HBox minigameLink() {
        Button minigames = new Button("Minigames");
        minigames.getStyleClass().add("primary-button");
        minigames.setOnAction(event -> ui.enter(MenuType.MINIGAME));
        HBox bar = new HBox(minigames);
        bar.setAlignment(Pos.CENTER);
        return bar;
    }

    private static String capitalise(String value) {
        return value.isEmpty() ? value
                : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
