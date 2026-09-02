package ir.sharif.pvz.view.fx.widget;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * The single card used everywhere a plant or a zombie is listed: the collection
 * tabs, the plant picker before a level, and the seed bar during one.
 *
 * <p>The project document asks for exactly this — one reusable actor rather
 * than three near-identical lists — so every extra (cost, level, seed packet
 * progress, lock, boost, cooldown) is an opt-in decoration on the same card.
 */
public final class ActorCard extends StackPane {

    private static final double ART_HEIGHT = 62;
    private static final double CARD_WIDTH = 104;

    private final VBox body = new VBox(4);
    private final HBox badges = new HBox(6);
    private final Rectangle cooldownVeil = new Rectangle(CARD_WIDTH, 0);
    private final ImageView art;

    /**
     * @param art   the sprite to show, or null to draw an empty frame
     * @param title the label under the art
     */
    public ActorCard(Image art, String title) {
        this.art = new ImageView(art);
        this.art.setPreserveRatio(true);
        this.art.setSmooth(true);
        this.art.setFitHeight(ART_HEIGHT);

        StackPane frame = new StackPane(this.art);
        frame.setMinHeight(ART_HEIGHT);
        frame.setPrefHeight(ART_HEIGHT);
        frame.getStyleClass().add("actor-art");

        Label name = new Label(title);
        name.getStyleClass().add("actor-name");
        name.setWrapText(true);
        name.setMaxWidth(CARD_WIDTH - 10);
        name.setAlignment(Pos.CENTER);

        badges.setAlignment(Pos.CENTER);
        body.setAlignment(Pos.TOP_CENTER);
        body.setPadding(new Insets(6));
        body.getChildren().addAll(frame, name, badges);

        cooldownVeil.getStyleClass().add("actor-cooldown");
        StackPane.setAlignment(cooldownVeil, Pos.TOP_CENTER);

        setPrefWidth(CARD_WIDTH);
        setMinWidth(CARD_WIDTH);
        getStyleClass().add("actor-card");
        getChildren().addAll(body, cooldownVeil);
    }

    /**
     * The sun price, shown as the game itself does.
     */
    public ActorCard cost(int sun) {
        return badge(String.valueOf(sun), "badge-sun");
    }

    public ActorCard level(int level) {
        return badge("LVL " + level, "badge-level");
    }

    /**
     * Seed packet progress: how many are collected and how many the next
     * upgrade needs.
     */
    public ActorCard packets(int collected, int needed) {
        ProgressBar bar = new ProgressBar(needed <= 0 ? 1 : Math.min(1.0, collected / (double) needed));
        bar.getStyleClass().add("packet-bar");
        bar.setPrefWidth(CARD_WIDTH - 18);
        Label text = new Label(collected + " / " + needed);
        text.getStyleClass().add("packet-text");
        VBox progress = new VBox(2, bar, text);
        progress.setAlignment(Pos.CENTER);
        body.getChildren().add(progress);
        return this;
    }

    public ActorCard badge(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("actor-badge", styleClass);
        badges.getChildren().add(label);
        return this;
    }

    /**
     * Dims the card and stamps a lock on it.
     */
    public ActorCard locked(boolean locked) {
        toggle("actor-locked", locked);
        if (locked) {
            Label lock = new Label("🔒");
            lock.getStyleClass().add("actor-lock");
            StackPane.setAlignment(lock, Pos.TOP_RIGHT);
            getChildren().add(lock);
        }
        return this;
    }

    /**
     * The golden background the game uses for boosted plants.
     */
    public ActorCard boosted(boolean boosted) {
        return toggle("actor-boosted", boosted);
    }

    public ActorCard selected(boolean selected) {
        return toggle("actor-selected", selected);
    }

    /**
     * Hides the art behind a blank frame, for a zombie the player has never met.
     */
    public ActorCard undiscovered(boolean undiscovered) {
        art.setVisible(!undiscovered);
        return toggle("actor-undiscovered", undiscovered);
    }

    /**
     * Greys the card out when the player cannot afford it right now.
     */
    public ActorCard affordable(boolean affordable) {
        return toggle("actor-unaffordable", !affordable);
    }

    /**
     * Fills the card from the top with the recharge veil; 0 is ready, 1 is a
     * card that just got planted.
     */
    public ActorCard cooldown(double remainingFraction) {
        double clamped = Math.max(0, Math.min(1, remainingFraction));
        cooldownVeil.setHeight(clamped * (ART_HEIGHT + 52));
        return this;
    }

    public ActorCard onClick(Runnable action) {
        setOnMouseClicked(event -> action.run());
        getStyleClass().add("actor-clickable");
        return this;
    }

    /**
     * Any extra node the caller wants under the badges.
     */
    public ActorCard extra(Region node) {
        body.getChildren().add(node);
        return this;
    }

    private ActorCard toggle(String styleClass, boolean on) {
        if (on) {
            getStyleClass().add(styleClass);
        } else {
            getStyleClass().remove(styleClass);
        }
        return this;
    }
}
