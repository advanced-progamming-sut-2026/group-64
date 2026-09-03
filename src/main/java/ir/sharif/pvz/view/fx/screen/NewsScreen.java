package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.model.NewsItem;
import ir.sharif.pvz.view.fx.GameUi;
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
 * News and updates. Unread items are marked, and opening the list is what
 * marks them read — the same rule the phase-1 controller applies.
 */
public final class NewsScreen extends Screen {

    /**
     * The menu commands this screen drives the controller with. They are the
     * full commands the news menu answers to, not the bare switch names — the
     * screen used to send those and the menu quietly rejected every one, so
     * nothing was ever marked read.
     */
    public static final String SHOW_UNREAD = "menu news show-unread";
    public static final String SHOW_ALL = "menu news show-all";

    private static final double PANEL_WIDTH = 720;

    private boolean showAll;

    public NewsScreen(GameUi ui) {
        super(ui);
    }

    @Override
    public Parent build() {
        List<NewsItem> items = ui.user().getNews();
        // what is unread has to be read off before the controller marks it read
        List<NewsItem> shown = showAll ? items : items.stream().filter(item -> !item.isRead()).toList();
        List<NewsItem> freshlyRead = items.stream().filter(item -> !item.isRead()).toList();

        VBox list = new VBox(10);
        if (shown.isEmpty()) {
            list.getChildren().add(Forms.hint(showAll
                    ? "There is no news yet. Play a level to make some."
                    : "You are all caught up. Switch to \"All\" to reread older news."));
        } else {
            shown.forEach(item -> list.getChildren().add(entry(item)));
        }

        ScrollPane scroller = new ScrollPane(list);
        scroller.setFitToWidth(true);
        scroller.getStyleClass().add("news-scroll");
        VBox.setVgrow(scroller, Priority.ALWAYS);

        VBox panel = Forms.panel(14, filterBar(), scroller);
        panel.setMaxWidth(PANEL_WIDTH);
        panel.setPadding(new Insets(18));

        VBox column = new VBox(panel);
        column.setAlignment(Pos.TOP_CENTER);
        column.setPadding(new Insets(24));
        VBox.setVgrow(panel, Priority.ALWAYS);

        BorderPane layout = new BorderPane(column);
        layout.setTop(Chrome.bar(ui, "News and updates"));
        layout.getStyleClass().addAll("screen", "news-screen");
        // opening the page is what reads it, so the NEW tags clear next time
        markRead(freshlyRead);
        return layout;
    }

    /**
     * Hands the marking to the controller, which is what saves it, and keeps
     * its console-shaped output out of the notification stack.
     */
    private void markRead(List<NewsItem> unread) {
        boolean onThisMenu = ui.app().getContext().getCurrentMenu()
                == ir.sharif.pvz.controller.MenuType.NEWS;
        if (!unread.isEmpty() && onThisMenu) {
            ui.view().capture(() -> ui.app().submit(SHOW_UNREAD));
        }
    }

    private HBox filterBar() {
        Button unread = new Button("Unread");
        Button all = new Button("All");
        unread.getStyleClass().add(showAll ? "tab-button" : "tab-button-active");
        all.getStyleClass().add(showAll ? "tab-button-active" : "tab-button");
        unread.setOnAction(event -> switchTo(false));
        all.setOnAction(event -> switchTo(true));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(10, unread, all, spacer);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void switchTo(boolean all) {
        showAll = all;
        ui.show(this);
    }

    private VBox entry(NewsItem item) {
        Label text = new Label(item.getText());
        text.getStyleClass().add("news-text");
        text.setWrapText(true);

        VBox box = new VBox(4);
        box.getStyleClass().add("news-item");
        if (!item.isRead()) {
            Label tag = new Label("NEW");
            tag.getStyleClass().add("news-tag");
            box.getChildren().add(tag);
        }
        box.getChildren().add(text);
        return box;
    }
}
