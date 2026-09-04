package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.controller.GameMenuController;
import ir.sharif.pvz.controller.MenuType;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.game.GameCatalog;
import ir.sharif.pvz.model.game.LevelSpec;
import ir.sharif.pvz.model.game.Levels;
import ir.sharif.pvz.model.game.PlantSpec;
import ir.sharif.pvz.model.game.SpecialRules;
import ir.sharif.pvz.view.fx.Assets;
import ir.sharif.pvz.view.fx.GameUi;
import ir.sharif.pvz.view.fx.widget.ActorCard;
import java.util.List;
import java.util.Set;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Choosing the lawn before a level: the objectives on the left, every plant the
 * player owns in the middle, and the picked line-up down the side.
 *
 * <p>Cards are the same {@link ActorCard} the collection uses, so cost, level
 * and boost state read identically in both places.
 */
public final class PlantPickerScreen extends Screen {

    private static final int MAX_SELECTED = 8;
    private static final int BOOST_DIAMOND_COST = 2;

    private final MenuType menu;

    public PlantPickerScreen(GameUi ui, MenuType menu) {
        super(ui);
        this.menu = menu;
    }

    @Override
    public Parent build() {
        GameMenuController controller = (GameMenuController) ui.app().currentController();
        Set<String> picked = controller.getSelectedPlants();
        Set<String> boosted = controller.getBoostedPlants();
        LevelSpec level = upcomingLevel();

        HBox body = new HBox(18, briefing(level, picked), catalogue(picked, boosted), lineUp(picked, level));
        body.setPadding(new Insets(18));

        BorderPane layout = new BorderPane(body);
        // an adventure level was chosen on the chapter map, so back goes there;
        // the daily challenge is reached from the main menu, so back goes there
        layout.setTop(Chrome.bar(ui, "Choose your plants",
                menu == MenuType.GAME ? ui::exitToAdventure : ui::exitMenu));
        layout.getStyleClass().addAll("screen", "picker-screen");
        String chapter = AdventureScreen.chapterId(level.getChapter());
        layout.setStyle("-fx-background-image: url('" + Assets.url("backgrounds/" + chapter) + "');"
                + "-fx-background-size: cover;");
        return layout;
    }

    /**
     * The level this menu is about to start; the score game plays its own.
     */
    private LevelSpec upcomingLevel() {
        return menu == MenuType.SCORE_GAME
                ? Levels.scoreGame()
                : Levels.byProgress(ui.user().getLevelsPassed());
    }

    /**
     * The objectives panel the project document asks to show before a level.
     */
    private VBox briefing(LevelSpec level, Set<String> picked) {
        VBox panel = Forms.panel(10,
                Forms.heading(level.title() + (level.isNight() ? " (night)" : "")),
                objective("Do not let a zombie reach your house"),
                objective(level.isBoss()
                        ? "Knock out all three parts of Zomboss"
                        : "Survive all " + level.getTotalWaves() + " waves"));
        panel.getChildren().add(characterLine(level));

        SpecialRules special = level.getSpecial();
        if (special != null) {
            panel.getChildren().add(objective(describe(special)));
            if (special.getType() == SpecialRules.Type.CONVEYOR_BELT) {
                panel.getChildren().add(Forms.hint(
                        "This level feeds you plants on a belt, so there is nothing to pick."));
            }
            if (!special.getLockedPlants().isEmpty()) {
                panel.getChildren().add(Forms.hint(
                        "Locked here: " + String.join(", ", special.getLockedPlants())));
            }
        }
        panel.getChildren().add(Forms.hint(picked.size() + " of " + MAX_SELECTED + " slots used."));
        panel.setPrefWidth(300);
        panel.setMinWidth(300);
        return panel;
    }

    /**
     * The couple of words a character says before the level, which the document
     * asks for and leaves entirely up to us.
     */
    private VBox characterLine(LevelSpec level) {
        Label who = new Label(Dialogue.speaker(level) + ":");
        who.getStyleClass().add("dialogue-speaker");
        Label line = new Label(Dialogue.opening(level));
        line.getStyleClass().add("dialogue-line");
        line.setWrapText(true);
        VBox box = new VBox(2, who, line);
        box.getStyleClass().add("dialogue");
        return box;
    }

    private Label objective(String text) {
        Label label = new Label("◦  " + text);
        label.getStyleClass().add("objective");
        label.setWrapText(true);
        return label;
    }

    private String describe(SpecialRules special) {
        return switch (special.getType()) {
            case CONVEYOR_BELT -> "Plants arrive on a conveyor belt";
            case LOCKED_PLANTS -> "Some plants are locked on this level";
            case SAVE_OUR_SEEDS -> "Protect the marked plants";
            case TIMED_WAR -> "Kill " + special.getTargetKills() + " zombies in "
                    + special.getTimerSeconds() + " seconds";
            case NIGHT_OPS -> "A night level; sun falls slower";
            case DEAD_LINE -> "Do not let zombies pass column " + special.getDeadlineColumn();
            case LOVE_YOUR_PLANTS -> "Lose at most "
                    + special.getMaxPlantLosses() + " plant(s)";
            case PLANT_WHAT_YOU_GET -> "Plant what you are given, then start the waves";
        };
    }

    /**
     * The way back into a level the player walked away from. It only appears
     * when they actually have one waiting.
     */
    private Button resumeButton() {
        var saved = ui.app().getContext().getSavedGames().of(ui.user().getUsername());
        Button resume = new Button();
        resume.setManaged(saved != null);
        resume.setVisible(saved != null);
        if (saved == null) {
            return resume;
        }
        resume.setText("Continue " + ir.sharif.pvz.model.game.SaveState.describe(saved));
        resume.getStyleClass().add("primary-button");
        resume.setMaxWidth(Double.MAX_VALUE);
        resume.setOnAction(event -> ui.submit("resume game"));
        return resume;
    }

    private VBox catalogue(Set<String> picked, Set<String> boosted) {
        User user = ui.user();
        FlowPane grid = new FlowPane(12, 12);
        List<PlantSpec> owned = GameCatalog.get().allPlants().stream()
                .filter(spec -> user.getUnlockedPlants().contains(spec.getName()))
                .toList();

        for (PlantSpec spec : owned) {
            String name = spec.getName();
            boolean chosen = picked.contains(name);
            ActorCard card = new ActorCard(Assets.plant(name), name)
                    .cost(spec.getSunCost())
                    .level(user.getPlantLevel(name))
                    .selected(chosen)
                    .boosted(boosted.contains(name))
                    .affordable(true)
                    .onClick(() -> toggle(name, chosen));
            if (chosen) {
                card.extra(boostButton(name, boosted.contains(name)));
            }
            grid.getChildren().add(card);
        }

        ScrollPane scroller = new ScrollPane(grid);
        scroller.setFitToWidth(true);
        scroller.getStyleClass().add("picker-scroll");

        VBox panel = Forms.panel(10, Forms.heading("Your plants"), scroller);
        VBox.setVgrow(scroller, Priority.ALWAYS);
        HBox.setHgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private Button boostButton(String name, boolean alreadyBoosted) {
        Button boost = new Button(alreadyBoosted ? "Boosted" : "💎 " + BOOST_DIAMOND_COST);
        boost.getStyleClass().add("boost-button");
        boost.setDisable(alreadyBoosted);
        boost.setOnAction(event -> {
            event.consume();
            ui.submit("boost plant -t " + name);
        });
        return boost;
    }

    private void toggle(String name, boolean chosen) {
        ui.submit((chosen ? "remove plant -t " : "add plant -t ") + name);
    }

    private VBox lineUp(Set<String> picked, LevelSpec level) {
        VBox slots = new VBox(8);
        for (String name : picked) {
            slots.getChildren().add(new ActorCard(Assets.plant(name), name)
                    .cost(GameCatalog.get().plant(name).getSunCost())
                    .onClick(() -> ui.submit("remove plant -t " + name)));
        }
        for (int i = picked.size(); i < MAX_SELECTED; i++) {
            Region empty = new Region();
            empty.setPrefSize(104, 40);
            empty.getStyleClass().add("empty-slot");
            slots.getChildren().add(empty);
        }

        Button start = new Button("LET'S ROCK!");
        start.getStyleClass().add("rock-button");
        start.setMaxWidth(Double.MAX_VALUE);
        start.setOnAction(event -> ui.submit("start game"));
        start.setDisable(picked.isEmpty() && level.getSpecial() == null);

        ScrollPane scroller = new ScrollPane(slots);
        scroller.setFitToWidth(true);
        scroller.getStyleClass().add("picker-scroll");
        VBox panel = Forms.panel(10, Forms.heading("Your line-up"), scroller,
                resumeButton(), start);
        VBox.setVgrow(scroller, Priority.ALWAYS);
        panel.setPrefWidth(240);
        panel.setMinWidth(240);
        panel.setAlignment(Pos.TOP_CENTER);
        return panel;
    }
}
