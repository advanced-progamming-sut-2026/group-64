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

    @Override
    public Parent build() {
        ScrollPane grid = new ScrollPane(zombiesTab ? zombieGrid() : plantGrid());
        grid.setFitToWidth(true);
        grid.getStyleClass().add("collection-scroll");
        HBox.setHgrow(grid, Priority.ALWAYS);

        VBox left = new VBox(12, tabs(), zombiesTab ? new HBox() : filters(), grid);
        VBox.setVgrow(grid, Priority.ALWAYS);
        HBox.setHgrow(left, Priority.ALWAYS);

        HBox body = new HBox(18, left, detail());
        body.setPadding(new Insets(18));

        BorderPane layout = new BorderPane(body);
        layout.setTop(Chrome.bar(ui, "Collection"));
        layout.getStyleClass().addAll("screen", "collection-screen");
        return layout;
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

        panel.getChildren().addAll(
                Forms.heading(pretty(spec.getName())),
                Assets.view(Assets.plant(spec.getName()), DETAIL_ART),
                stats(Map.of(
                        "Family", pretty(spec.getCategory().name()),
                        "Sun cost", String.valueOf(spec.getSunCost()),
                        "Recharge", spec.getRechargeSeconds() + "s",
                        "Health", String.valueOf(spec.getHp()),
                        "Damage", String.valueOf(spec.getDamage()),
                        "Level", String.valueOf(level))),
                Forms.hint("Tags: " + String.join(", ", spec.getTags())));

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
    private VBox stats(Map<String, String> values) {
        VBox box = new VBox(6);
        List<String> order = List.of("Family", "Sun cost", "Recharge", "Health",
                "Armor", "Damage", "Speed", "Wave cost", "Level");
        for (String key : order) {
            if (!values.containsKey(key)) {
                continue;
            }
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
