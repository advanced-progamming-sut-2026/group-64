package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.controller.CollectionMenuController;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.game.GameCatalog;
import ir.sharif.pvz.model.game.PlantCategory;
import ir.sharif.pvz.model.game.PlantSpec;
import ir.sharif.pvz.model.game.ZombieSpec;
import ir.sharif.pvz.view.fx.Assets;
import ir.sharif.pvz.view.fx.GameUi;
import ir.sharif.pvz.view.fx.widget.ActorCard;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The almanac: a plants tab and a zombies tab, each a grid of cards with a
 * detail panel beside it. Zombies the player has never met stay blank frames.
 */
public final class CollectionScreen extends Screen {

    private static final double DETAIL_ART = 150;

    private boolean zombiesTab;
    private PlantCategory familyFilter;
    private boolean onlyUpgradable;
    private String selected;

    public CollectionScreen(GameUi ui) {
        super(ui);
    }

    /**
     * The collection opened straight onto one entry, which is how the almanac
     * page is reached from a link and how the snapshots photograph it.
     */
    public CollectionScreen(GameUi ui, String showing) {
        super(ui);
        this.selected = showing;
    }

    @Override
    public Parent build() {
        ScrollPane grid = new ScrollPane(zombiesTab ? zombieGrid() : plantGrid());
        grid.setFitToWidth(true);
        grid.getStyleClass().add("collection-scroll");
        HBox.setHgrow(grid, Priority.ALWAYS);

        VBox left = new VBox(12, tabs(), zombiesTab ? new HBox() : filters(), grid);
        if (ui.user().isDebugMode()) {
            left.getChildren().add(unlockEverything());
        }
        VBox.setVgrow(grid, Priority.ALWAYS);
        HBox.setHgrow(left, Priority.ALWAYS);

        HBox body = new HBox(18, left, detail());
        body.setPadding(new Insets(18));

        BorderPane layout = new BorderPane(body);
        layout.setTop(Chrome.bar(ui, "Collection"));
        layout.getStyleClass().addAll("screen", "collection-screen");
        return layout;
    }

    /**
     * Opens the whole roster, for showing the game to somebody rather than
     * playing it: both pages only show what has been bought and what has been
     * met in a level, so most of each is shut on a fresh account. Offered only
     * while debug mode is on, like the other debug tools.
     */
    private HBox unlockEverything() {
        Button unlock = new Button("Unlock every plant and zombie");
        unlock.getStyleClass().add("ghost-button");
        unlock.setOnAction(event -> {
            ui.submit("cheat unlock-all");
            ui.refresh();
        });
        HBox row = new HBox(unlock);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private HBox tabs() {
        Button plants = new Button("Plants");
        Button zombies = new Button("Zombies");
        plants.getStyleClass().add(zombiesTab ? "tab-button" : "tab-button-active");
        zombies.getStyleClass().add(zombiesTab ? "tab-button-active" : "tab-button");
        plants.setOnAction(event -> switchTab(false));
        zombies.setOnAction(event -> switchTab(true));
        HBox bar = new HBox(10, plants, zombies);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void switchTab(boolean zombies) {
        zombiesTab = zombies;
        selected = null;
        ui.show(this);
    }

    /**
     * Filtering by family and by "can I upgrade this right now", as the
     * project document asks for.
     */
    private HBox filters() {
        ChoiceBox<String> family = new ChoiceBox<>();
        family.getItems().add("All families");
        for (PlantCategory category : PlantCategory.values()) {
            family.getItems().add(pretty(category.name()));
        }
        family.setValue(familyFilter == null ? "All families" : pretty(familyFilter.name()));
        family.setOnAction(event -> {
            int index = family.getSelectionModel().getSelectedIndex();
            familyFilter = index <= 0 ? null : PlantCategory.values()[index - 1];
            ui.show(this);
        });

        CheckBox upgradable = new CheckBox("Only upgradable");
        upgradable.setSelected(onlyUpgradable);
        upgradable.setOnAction(event -> {
            onlyUpgradable = upgradable.isSelected();
            ui.show(this);
        });

        HBox bar = new HBox(12, family, upgradable);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private FlowPane plantGrid() {
        User user = ui.user();
        FlowPane grid = new FlowPane(14, 14);
        for (PlantSpec spec : GameCatalog.get().allPlants()) {
            String name = spec.getName();
            boolean owned = user.getUnlockedPlants().contains(name);
            if (familyFilter != null && spec.getCategory() != familyFilter) {
                continue;
            }
            if (onlyUpgradable && !canUpgrade(user, name)) {
                continue;
            }
            int level = user.getPlantLevel(name);
            int packets = user.getSeedPackets().getOrDefault(name, 0);
            int needed = CollectionMenuController.UPGRADE_PACKETS_PER_LEVEL * (level + 1);

            ActorCard card = new ActorCard(Assets.plant(name), name)
                    .cost(spec.getSunCost())
                    .level(level)
                    .packets(packets, needed)
                    .locked(!owned)
                    .selected(name.equals(selected))
                    .onClick(() -> select(name));
            grid.getChildren().add(card);
        }
        return grid;
    }

    private FlowPane zombieGrid() {
        User user = ui.user();
        FlowPane grid = new FlowPane(14, 14);
        for (ZombieSpec spec : GameCatalog.get().allZombies()) {
            String name = spec.getName();
            boolean seen = user.getObservedZombies().contains(name);
            ActorCard card = new ActorCard(Assets.zombie(name), seen ? name : "???")
                    .undiscovered(!seen)
                    .selected(name.equals(selected))
                    .onClick(() -> select(seen ? name : null));
            grid.getChildren().add(card);
        }
        return grid;
    }

    private void select(String name) {
        selected = name;
        ui.show(this);
    }

    private boolean canUpgrade(User user, String name) {
        if (!user.getUnlockedPlants().contains(name)) {
            return false;
        }
        int next = user.getPlantLevel(name) + 1;
        return user.getCoins() >= CollectionMenuController.UPGRADE_COINS_PER_LEVEL * next
                && user.getSeedPackets().getOrDefault(name, 0)
                        >= CollectionMenuController.UPGRADE_PACKETS_PER_LEVEL * next;
    }

    private VBox detail() {
        VBox panel = Forms.panel(12);
        panel.setPrefWidth(340);
        panel.setMinWidth(340);
        if (selected == null) {
            panel.getChildren().add(Forms.hint(zombiesTab
                    ? "Pick a zombie you have met to read about it."
                    : "Pick a plant to see its stats."));
            return panel;
        }
        if (zombiesTab) {
            fillZombieDetail(panel, GameCatalog.get().zombie(selected));
        } else {
            fillPlantDetail(panel, GameCatalog.get().plant(selected));
        }
        return panel;
    }

    private void fillPlantDetail(VBox panel, PlantSpec spec) {
        User user = ui.user();
        boolean owned = user.getUnlockedPlants().contains(spec.getName());
        int level = user.getPlantLevel(spec.getName());
        PlantSpec.Almanac almanac = spec.getAlmanac();

        Map<String, String> numbers = new java.util.LinkedHashMap<>();
        numbers.put("Family", pretty(spec.getCategory().name()));
        numbers.put("Sun cost", String.valueOf(spec.getSunCost()));
        numbers.put("Recharge", spec.getRechargeSeconds() + "s");
        numbers.put("Health", String.valueOf(spec.getHp()));
        numbers.put("Damage", almanac.damage());
        numbers.put("Action interval",
                "-".equals(almanac.interval()) ? "-" : almanac.interval() + "s");
        numbers.put("Level", String.valueOf(level));

        panel.getChildren().addAll(
                Forms.heading(pretty(spec.getName())),
                Assets.view(Assets.plant(spec.getName()), DETAIL_ART),
                stats(numbers),
                Forms.hint("Tags: " + String.join(", ", spec.getTags())),
                paragraph("Ability", almanac.ability()),
                paragraph("Plant food", almanac.plantFood()),
                upgradeLadder(almanac, level));

        if (owned) {
            int next = level + 1;
            Button upgrade = new Button("Upgrade to level " + next + " ("
                    + CollectionMenuController.UPGRADE_COINS_PER_LEVEL * next + " coins, "
                    + CollectionMenuController.UPGRADE_PACKETS_PER_LEVEL * next + " packets)");
            upgrade.getStyleClass().add("primary-button");
            upgrade.setWrapText(true);
            upgrade.setOnAction(event ->
                    ui.submit("menu collection upgrade-plant -p " + spec.getName()));
            panel.getChildren().add(upgrade);
        } else {
            Button buy = new Button("Unlock for "
                    + CollectionMenuController.PURCHASE_COIN_COST + " coins");
            buy.getStyleClass().add("primary-button");
            buy.setOnAction(event -> Dialogs.confirm(ui, "Purchase confirmation",
                    "Unlock " + spec.getName() + " for "
                            + CollectionMenuController.PURCHASE_COIN_COST + " coins?",
                    () -> ui.submit("menu collection purchase-plant -p " + spec.getName())));
            panel.getChildren().add(buy);
        }
    }

    private void fillZombieDetail(VBox panel, ZombieSpec spec) {
        String armor = spec.getArmor().isEmpty() ? "none"
                : String.join(", ", spec.getArmor().keySet());
        panel.getChildren().addAll(
                Forms.heading(pretty(spec.getName())),
                Assets.view(Assets.zombie(spec.getName()), DETAIL_ART),
                stats(Map.of(
                        "Health", String.valueOf(spec.getHp()),
                        "Armor", armor,
                        "Speed", spec.getTilesPerSecond() + " tiles/s",
                        "Damage", spec.getDamagePerSecond() + "/s",
                        "Wave cost", String.valueOf(spec.getWaveCost()))),
                Forms.hint(spec.getDescription()));
    }

    /**
     * A stat block, ordered so the same keys always appear in the same place.
     */
    /**
     * A titled block of the sheet's own wording, which is a sentence rather
     * than a number and needs the room to wrap.
     */
    private VBox paragraph(String title, String body) {
        Label caption = new Label(title);
        caption.getStyleClass().add("stat-key");
        Label text = new Label(body);
        text.getStyleClass().add("stat-value");
        text.setWrapText(true);
        orientFor(text, body);
        return new VBox(2, caption, text);
    }

    /**
     * The sheet writes what a plant does in Persian, often with a run of Latin
     * in the middle of it. Laying such a line out left to right breaks it up;
     * telling the label the paragraph is right to left puts it back together.
     */
    private static void orientFor(Label label, String text) {
        if (text.codePoints().anyMatch(CollectionScreen::isPersian)) {
            label.setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        }
    }

    private static boolean isPersian(int codePoint) {
        return Character.UnicodeBlock.of(codePoint) == Character.UnicodeBlock.ARABIC
                || Character.UnicodeBlock.of(codePoint)
                        == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A;
    }

    /**
     * What levels two, three and four give, with the ones this player has
     * already paid for marked.
     */
    private VBox upgradeLadder(PlantSpec.Almanac almanac, int level) {
        VBox box = new VBox(2);
        Label caption = new Label("Upgrades");
        caption.getStyleClass().add("stat-key");
        box.getChildren().add(caption);
        for (int step = 0; step < almanac.upgrades().size(); step++) {
            int tier = step + 2;
            String stepText = almanac.upgrades().get(step);
            Label line = new Label("Lvl " + tier + ": " + stepText
                    + (level >= tier ? "  \u2713" : ""));
            line.getStyleClass().add(level >= tier ? "stat-value" : "hint");
            line.setWrapText(true);
            orientFor(line, stepText);
            box.getChildren().add(line);
        }
        return box;
    }

    private VBox stats(Map<String, String> values) {
        VBox box = new VBox(6);
        for (String key : values.keySet()) {
            Label caption = new Label(key);
            caption.getStyleClass().add("stat-key");
            Label value = new Label(values.get(key));
            value.getStyleClass().add("stat-value");
            HBox row = new HBox(10, caption, value);
            row.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().add(row);
        }
        return box;
    }

    /**
     * SUN_PRODUCER -> Sun producer, snow-pea -> Snow pea.
     */
    private static String pretty(String raw) {
        String spaced = raw.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
