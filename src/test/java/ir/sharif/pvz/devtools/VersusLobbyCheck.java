package ir.sharif.pvz.devtools;

import ir.sharif.pvz.controller.GameApp;
import ir.sharif.pvz.model.RegisterRequest;
import ir.sharif.pvz.model.net.RemoteAuthService;
import ir.sharif.pvz.model.net.RemoteUserRepository;
import ir.sharif.pvz.net.Protocol;
import ir.sharif.pvz.net.client.ServerConnection;
import ir.sharif.pvz.net.server.PvzServer;
import ir.sharif.pvz.view.fx.FxView;
import ir.sharif.pvz.view.fx.GameUi;
import ir.sharif.pvz.view.fx.screen.VersusLobbyScreen;
import ir.sharif.pvz.view.fx.screen.VersusScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * A development aid, not a test: drives the real versus lobby — the screen
 * itself, its real buttons, against a real server — and prints what each click
 * actually did. Run it with {@code ./gradlew versusCheck}.
 *
 * <p>The lobby is the one screen that cannot be judged from a snapshot: every
 * panel on it needs a second player on the other end of a socket, so a picture
 * of it only ever shows the empty case. This stands two signed-in players up
 * and clicks through both routes into a match, the challenge and the queue.
 */
public final class VersusLobbyCheck extends Application {

    private static final long TIMEOUT_MS = 8000;

    private final List<String> results = new ArrayList<>();

    private PvzServer server;
    private GameUi hostUi;
    private GameUi guestUi;

    @Override
    public void start(Stage stage) throws Exception {
        Path store = Files.createTempDirectory("pvz-lobby").resolve("users.json");
        server = new PvzServer(0, store);
        server.serveInBackground();

        hostUi = signedIn("hostie", stage);
        guestUi = signedIn("guestie", null);

        Thread driver = new Thread(this::check, "lobby-check");
        driver.setDaemon(true);
        driver.start();
    }

    /**
     * Runs something on the JavaFX thread and waits for it, so the checking
     * thread can touch the scene graph without racing the toolkit.
     */
    private void onFx(Runnable action) {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                done.countDown();
            }
        });
        try {
            done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private <T> T askFx(java.util.function.Supplier<T> question) {
        List<T> answer = new ArrayList<>(1);
        onFx(() -> answer.add(question.get()));
        return answer.isEmpty() ? null : answer.get(0);
    }

    /**
     * Builds a whole client — connection, app and window chrome — signed in as
     * one player, the way the running game does it.
     */
    private GameUi signedIn(String name, Stage stage) throws Exception {
        ServerConnection link = new ServerConnection("localhost", server.port());
        RemoteUserRepository users = new RemoteUserRepository(link);
        RemoteAuthService auth = new RemoteAuthService(link, users);
        auth.register(new RegisterRequest(name, "Aa1!aaaa", "Aa1!aaaa",
                "N" + name, name + "@example.com", "female"), 1, "green");
        auth.login(name, "Aa1!aaaa");

        FxView view = new FxView();
        GameApp app = new GameApp(view, link);
        app.getContext().setCurrentUser(users.findByUsername(name));
        GameUi ui = new GameUi(app, view);
        if (stage != null) {
            stage.setScene(new Scene(ui.root(), GameUi.WIDTH, GameUi.HEIGHT));
            stage.show();
        }
        return ui;
    }

    private void check() {
        openLobbies();

        say("the banner says", Boolean.TRUE.equals(
                askFx(() -> label(hostUi, "Connected") != null))
                ? "Connected. Challenge somebody or join the queue." : "NOT CONNECTED");
        say("the other player is listed as signed in",
                Boolean.TRUE.equals(askFx(() -> listed(hostUi, "guestie"))));
        say("the challenge field is usable",
                Boolean.TRUE.equals(askFx(() -> !field(hostUi).isDisable())));

        latecomerRoute();
        challengeRoute();
        queueRoute();

        results.forEach(System.out::println);
        Platform.exit();
    }

    private void openLobbies() {
        onFx(() -> {
            hostUi.show(new VersusLobbyScreen(hostUi));
            guestUi.show(new VersusLobbyScreen(guestUi));
        });
    }

    /**
     * Somebody signing in after the lobby was opened. The list used to be
     * asked for once and never again, so a player who arrived a moment late
     * stayed invisible until the lobby was left and reopened.
     */
    private void latecomerRoute() {
        say("a latecomer is not listed before they sign in",
                !Boolean.TRUE.equals(askFx(() -> listed(hostUi, "latey"))));
        try {
            signedIn("latey", null);
        } catch (Exception e) {
            say("the latecomer could not sign in: " + e.getMessage(), false);
            return;
        }
        say("the latecomer turns up in the list on their own",
                waitFor(() -> Boolean.TRUE.equals(askFx(() -> listed(hostUi, "latey")))));
    }

    /**
     * Player one types a name and challenges; player two gets the pop-up and
     * accepts; both should end up in the match.
     */
    private void challengeRoute() {
        onFx(() -> field(hostUi).setText("guestie"));
        onFx(() -> click(hostUi, "Send a challenge"));
        say("the challenged player is shown the pop-up",
                waitFor(() -> Boolean.TRUE.equals(askFx(guestUi::hasModal))));
        onFx(() -> click(guestUi, "Accept"));
        say("the challenger reaches the match", waitForMatch(hostUi));
        say("the challenged player reaches the match", waitForMatch(guestUi));
        openLobbies();
    }

    /** Both players join the queue and should be paired with each other. */
    private void queueRoute() {
        say("the first to queue is told to wait",
                clickAndRead(hostUi, "Find any opponent").startsWith("You are in the queue"));
        say("the second to queue is paired at once",
                clickAndRead(guestUi, "Find any opponent").startsWith("Opponent found"));
        say("the queued player one reaches the match", waitForMatch(hostUi));
        say("the queued player two reaches the match", waitForMatch(guestUi));

        onFx(() -> hostUi.show(new VersusLobbyScreen(hostUi)));
        onFx(() -> click(hostUi, "Find any opponent"));
        onFx(() -> click(hostUi, "Leave the queue"));
        say("leaving the queue is confirmed",
                "You left the queue.".equals(askFx(() -> status(hostUi))));
    }

    /**
     * Clicks and reads the status line in the same pulse. Read in a later one
     * and the match has already replaced the lobby, so the label that comes
     * back belongs to another screen.
     */
    private String clickAndRead(GameUi ui, String caption) {
        List<String> text = new ArrayList<>(1);
        onFx(() -> {
            click(ui, caption);
            text.add(status(ui));
        });
        return text.isEmpty() ? "" : text.get(0);
    }

    private boolean waitForMatch(GameUi ui) {
        return waitFor(() -> Boolean.TRUE.equals(askFx(() -> inMatch(ui))));
    }

    // ===== poking at the real scene graph =====

    private boolean inMatch(GameUi ui) {
        return ui.current() instanceof VersusScreen;
    }

    private void click(GameUi ui, String caption) {
        Node node = find(root(ui), candidate -> candidate instanceof Button button
                && caption.equals(button.getText()));
        if (node == null) {
            say("could not find the '" + caption + "' button", false);
            return;
        }
        ((Button) node).fire();
    }

    private TextField field(GameUi ui) {
        return (TextField) find(root(ui), node -> node instanceof TextField);
    }

    private Label label(GameUi ui, String startsWith) {
        return (Label) find(root(ui), node -> node instanceof Label text
                && text.getText() != null && text.getText().startsWith(startsWith));
    }

    /** The lobby's status line is the last label its column holds. */
    private String status(GameUi ui) {
        Label last = null;
        for (Node node : all(root(ui))) {
            if (node instanceof Label text && text.getStyleClass().contains("hint")) {
                last = text;
            }
        }
        return last == null || last.getText() == null ? "" : last.getText();
    }

    @SuppressWarnings("unchecked")
    private boolean listed(GameUi ui, String name) {
        Node node = find(root(ui), candidate -> candidate instanceof ListView);
        return node != null && ((ListView<String>) node).getItems().contains(name);
    }

    private Parent root(GameUi ui) {
        return ui.root();
    }

    private Node find(Parent parent, java.util.function.Predicate<Node> test) {
        for (Node node : all(parent)) {
            if (test.test(node)) {
                return node;
            }
        }
        return null;
    }

    private List<Node> all(Parent parent) {
        List<Node> found = new ArrayList<>();
        for (Node child : parent.getChildrenUnmodifiable()) {
            found.add(child);
            if (child instanceof Parent nested) {
                found.addAll(all(nested));
            }
        }
        return found;
    }

    /**
     * Pumps the JavaFX thread until the condition holds. The server answers on
     * another thread and hands its message back through Platform.runLater, so
     * the pending runnables have to be given a chance to run.
     */
    private boolean waitFor(java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    private void say(String what, boolean ok) {
        results.add((ok ? "  ok   " : "  FAIL ") + what);
    }

    private void say(String what, String value) {
        results.add("       " + what + ": " + value);
    }

    @Override
    public void stop() {
        if (server != null) {
            server.close();
        }
    }

    public static void main(String[] args) {
        Application.launch(VersusLobbyCheck.class, args);
    }
}
