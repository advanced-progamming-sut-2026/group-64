package ir.sharif.pvz.view.fx.screen;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ir.sharif.pvz.net.Message;
import ir.sharif.pvz.net.Protocol;
import ir.sharif.pvz.net.client.ServerConnection;
import ir.sharif.pvz.net.client.ServerException;
import ir.sharif.pvz.view.fx.GameUi;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Where a versus match is arranged: challenge somebody by name, or wait in the
 * queue for whoever turns up next.
 */
public final class VersusLobbyScreen extends Screen {

    private static final Gson GSON = new Gson();

    private final Label status = new Label("Pick an opponent to get started.");

    private boolean queued;

    public VersusLobbyScreen(GameUi ui) {
        super(ui);
    }

    private ServerConnection link() {
        return ui.app().connection();
    }

    @Override
    public Parent build() {
        VBox column = new VBox(18, byName(), random(), couchPlay(), onlineList(), status);
        column.setAlignment(Pos.TOP_CENTER);
        column.setPadding(new Insets(24));
        column.setMaxWidth(560);
        status.getStyleClass().add("hint");
        status.setWrapText(true);

        VBox centred = new VBox(column);
        centred.setAlignment(Pos.TOP_CENTER);

        BorderPane layout = new BorderPane(centred);
        layout.setTop(Chrome.bar(ui, "I, Zombie — two players", () -> {
            if (queued) {
                safely(() -> link().ask(link().request(Protocol.QUEUE_LEAVE)));
            }
            ui.exitMenu();
        }));
        layout.getStyleClass().addAll("screen", "lobby-screen");
        return layout;
    }

    /**
     * Challenging a particular player, which the server turns into a pop-up on
     * their screen.
     */
    private VBox byName() {
        TextField name = new TextField();
        name.setPromptText("their username");

        Button invite = new Button("Send a challenge");
        invite.getStyleClass().add("primary-button");
        invite.setOnAction(event -> safely(() -> {
            link().ask(link().request(Protocol.INVITE).with("to", name.getText().trim()));
            status.setText("Waiting for " + name.getText().trim() + " to answer...");
        }));

        return Forms.panel(10,
                Forms.heading("Challenge someone"),
                Forms.hint("They have to be signed in right now."),
                new HBox(10, name, invite));
    }

    /**
     * The queue: pair with whoever is already waiting, or wait to be paired.
     */
    private VBox random() {
        Button join = new Button("Find any opponent");
        join.getStyleClass().add("primary-button");
        join.setOnAction(event -> safely(() -> {
            Message reply = link().ask(link().request(Protocol.QUEUE_JOIN));
            queued = reply.flag("waiting");
            status.setText(queued
                    ? "You are in the queue. The game starts as soon as somebody joins."
                    : "Opponent found — starting!");
        }));

        Button leave = new Button("Leave the queue");
        leave.getStyleClass().add("ghost-button");
        leave.setOnAction(event -> safely(() -> {
            link().ask(link().request(Protocol.QUEUE_LEAVE));
            queued = false;
            status.setText("You left the queue.");
        }));

        return Forms.panel(10,
                Forms.heading("Play a stranger"),
                new HBox(10, join, leave));
    }

    /**
     * The same game on one machine, with no server involved at all.
     */
    private VBox couchPlay() {
        Button start = new Button("Play on this device");
        start.getStyleClass().add("primary-button");
        start.setOnAction(event -> ui.show(new CouchPlayScreen(ui)));
        return Forms.panel(10,
                Forms.heading("Two players, one screen"),
                Forms.hint("Player 1 grows plants with the mouse; "
                        + "player 2 sends zombies with the arrow keys."),
                new HBox(10, start));
    }

    /**
     * Who else is signed in, so the player knows who they can challenge.
     */
    private VBox onlineList() {
        ListView<String> list = new ListView<>();
        list.setPrefHeight(180);
        safely(() -> {
            Message reply = link().ask(link().request(Protocol.ONLINE_USERS));
            List<String> names = GSON.fromJson(reply.getData().get("users"),
                    new TypeToken<List<String>>() { }.getType());
            list.getItems().setAll(names == null ? List.of() : names);
            if (list.getItems().isEmpty()) {
                status.setText("Nobody else is signed in yet.");
            }
        });
        list.setOnMouseClicked(event -> {
            String chosen = list.getSelectionModel().getSelectedItem();
            if (chosen != null) {
                safely(() -> {
                    link().ask(link().request(Protocol.INVITE).with("to", chosen));
                    status.setText("Waiting for " + chosen + " to answer...");
                });
            }
        });

        return Forms.panel(10, Forms.heading("Signed in right now"), list);
    }

    /**
     * Runs something that talks to the server, turning a refusal into a message
     * on screen rather than a stack trace.
     */
    private void safely(Runnable action) {
        if (link() == null) {
            status.setText("Not connected to a server; start one with --server.");
            return;
        }
        try {
            action.run();
        } catch (ServerException e) {
            status.setText(e.getMessage());
            ui.view().error(e.getMessage());
        }
    }
}
