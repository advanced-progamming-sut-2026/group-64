package ir.sharif.pvz.view.fx.screen;

import ir.sharif.pvz.model.ShopService;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.view.fx.Assets;
import ir.sharif.pvz.view.fx.GameUi;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/**
 * The store. Every item shows its price in its own currency and asks for
 * confirmation before spending anything, as the project document requires.
 */
public final class ShopScreen extends Screen {

    private static final double ART = 76;

    public ShopScreen(GameUi ui) {
        super(ui);
    }

    @Override
    public Parent build() {
        User user = ui.user();

        FlowPane items = new FlowPane(18, 18,
                offer("Greenhouse pot", Assets.ui("coin"),
                        ShopService.POT_COIN_PRICE + " coins",
                        "Unlocks one more greenhouse pot (20 at most).",
                        () -> buy("pot", 1)),
                offer("Plant food", Assets.plant("peppermint"),
                        ShopService.PLANT_FOOD_DIAMOND_PRICE + " diamonds",
                        "Start your next level with one extra plant food.",
                        () -> buy("plant-food", 1)),
                offer("Surprise seed packets", Assets.plant("sunflower"),
                        ShopService.RANDOM_BUNDLE_COIN_PRICE + " coins",
                        ShopService.RANDOM_BUNDLE_PACKETS + " packets of a random unlocked plant.",
                        () -> buy("random-packets", 1)),
                choiceOffer(user),
                offer("Coin exchange", Assets.ui("gem"),
                        ShopService.EXCHANGE_DIAMOND_PRICE + " diamonds",
                        "Trade diamonds for " + ShopService.EXCHANGE_COINS + " coins.",
                        () -> buy("exchange", 1)),
                dailyOffer(user));
        items.setAlignment(Pos.TOP_CENTER);

        ScrollPane scroller = new ScrollPane(items);
        scroller.setFitToWidth(true);
        scroller.getStyleClass().add("shop-scroll");
        scroller.setPadding(new Insets(24));

        BorderPane layout = new BorderPane(scroller);
        layout.setTop(Chrome.bar(ui, "Shop"));
        layout.getStyleClass().addAll("screen", "shop-screen");
        return layout;
    }

    /**
     * The seed packet bundle that lets the player name the plant, which the
     * document calls out as needing a way to choose a type.
     */
    private VBox choiceOffer(User user) {
        List<String> unlocked = List.copyOf(user.getUnlockedPlants());
        ChoiceBox<String> picker = new ChoiceBox<>();
        picker.getItems().addAll(unlocked);
        if (!unlocked.isEmpty()) {
            picker.setValue(unlocked.get(0));
        }
        VBox card = offer("Seed packets of your choice", Assets.plant("sunflower"),
                ShopService.CHOICE_BUNDLE_DIAMOND_PRICE + " diamonds",
                ShopService.CHOICE_BUNDLE_PACKETS + " packets of the plant you pick.",
                () -> buy("choice-packets", 1, picker.getValue()));
        card.getChildren().add(card.getChildren().size() - 1, picker);
        return card;
    }

    private VBox dailyOffer(User user) {
        ShopService service = new ShopService(ui.app().getContext().getUserRepository(),
                System::currentTimeMillis, new java.util.Random());
        String headline = service.describeDaily(user).get(0);
        return offer("Today's deal", Assets.plant("cherry-bomb"),
                ShopService.DAILY_BUNDLE_COIN_PRICE + " coins", headline,
                () -> buy("daily", 1));
    }

    private VBox offer(String title, Image art, String price, String description, Runnable onBuy) {
        Label name = new Label(title);
        name.getStyleClass().add("shop-title");
        name.setWrapText(true);

        Label body = new Label(description);
        body.getStyleClass().add("shop-body");
        body.setWrapText(true);

        Label tag = new Label(price);
        tag.getStyleClass().add("shop-price");

        Button buy = new Button("Buy");
        buy.getStyleClass().add("primary-button");
        buy.setOnAction(event -> Dialogs.confirm(ui, "Purchase confirmation",
                "Would you like to purchase " + title + " for " + price + "?", onBuy));

        VBox card = new VBox(8, name, Assets.view(art, ART), body, tag, buy);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(240);
        card.getStyleClass().add("shop-card");
        return card;
    }

    private void buy(String itemId, int count) {
        buy(itemId, count, null);
    }

    private void buy(String itemId, int count, String plantType) {
        ui.submit("shop buy -i " + itemId + " -n " + count
                + (plantType == null ? "" : " -t " + plantType));
    }
}
