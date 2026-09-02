package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.controller.MenuType;
import ir.sharif.pvz.model.game.Chapter;
import ir.sharif.pvz.model.game.LevelSpec;
import ir.sharif.pvz.model.game.Levels;
import ir.sharif.pvz.view.fx.Assets;
import ir.sharif.pvz.view.fx.GameUi;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The adventure map: one row per chapter, one node per level, showing how far
 * the player has come and which level is unlocked next.
 *
 * <p>Phase 1 walks the adventure in a straight line, so the playable node is
 * always the first one that has not been passed yet.
 */
public final class AdventureScreen extends Screen {

    private static final double NODE = 74;

    public AdventureScreen(GameUi ui) {
        super(ui);
    }

    @Override
    public Parent build() {
        List<LevelSpec> levels = Levels.adventure();
        int passed = ui.user().getLevelsPassed();

        VBox chapters = new VBox(20);
        chapters.setAlignment(Pos.TOP_CENTER);
        for (Chapter chapter : Chapter.values()) {
            chapters.getChildren().add(chapterRow(chapter, levels, passed));
        }

        ScrollPane scroller = new ScrollPane(chapters);
        scroller.setFitToWidth(true);
        scroller.getStyleClass().add("adventure-scroll");
        scroller.setPadding(new Insets(22));

        BorderPane layout = new BorderPane(scroller);
        layout.setTop(Chrome.bar(ui, "Adventure", () -> ui.refresh()));
        layout.getStyleClass().addAll("screen", "adventure-screen");
        return layout;
    }

    private VBox chapterRow(Chapter chapter, List<LevelSpec> levels, int passed) {
        List<LevelSpec> own = levels.stream()
                .filter(level -> level.getChapter() == chapter).toList();
        int done = 0;
        for (LevelSpec level : own) {
            if (levels.indexOf(level) < passed) {
                done++;
            }
        }

        Label title = new Label(chapter.displayName());
        title.getStyleClass().add("chapter-title");
        Label progress = new Label(done + " / " + own.size() + " levels");
        progress.getStyleClass().add("chapter-progress");

        HBox nodes = new HBox(12);
        nodes.setAlignment(Pos.CENTER_LEFT);
        for (LevelSpec level : own) {
            nodes.getChildren().add(levelNode(level, levels.indexOf(level), passed));
        }

        VBox row = new VBox(8, new HBox(14, title, progress), nodes);
        row.getStyleClass().add("chapter-row");
        row.setPadding(new Insets(16));
        row.setMaxWidth(1000);
        row.setStyle("-fx-background-image: url('"
                + backgroundUrl(chapter) + "');");
        return row;
    }

    private StackPane levelNode(LevelSpec level, int index, int passed) {
        boolean cleared = index < passed;
        boolean playable = index == passed;

        Label day = new Label("Day " + level.getDay());
        day.getStyleClass().add("level-day");
        Label state = new Label(cleared ? "✓" : playable ? "▶" : "🔒");
        state.getStyleClass().add("level-state");

        VBox body = new VBox(2, state, day);
        body.setAlignment(Pos.CENTER);
        body.setPrefSize(NODE, NODE);

        StackPane node = new StackPane(body);
        node.getStyleClass().add("level-node");
        if (cleared) {
            node.getStyleClass().add("level-cleared");
        }
        if (playable) {
            node.getStyleClass().add("level-playable");
            node.setOnMouseClicked(event -> ui.enter(MenuType.GAME));
        } else {
            node.getStyleClass().add("level-locked");
        }
        javafx.scene.control.Tooltip.install(node,
                new javafx.scene.control.Tooltip(level.title()
                        + (level.isNight() ? " (night)" : "")
                        + "\nWaves: " + level.getTotalWaves()));
        return node;
    }

    /**
     * The chapter's own lawn art, used as a faint strip behind its levels.
     */
    private String backgroundUrl(Chapter chapter) {
        return Assets.url("backgrounds/" + chapterId(chapter));
    }

    /**
     * ANCIENT_EGYPT -> ancient-egypt, matching the extracted sprite names.
     */
    public static String chapterId(Chapter chapter) {
        return chapter.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
