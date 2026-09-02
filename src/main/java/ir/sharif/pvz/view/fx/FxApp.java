package ir.sharif.pvz.view.fx;

import ir.sharif.pvz.controller.GameApp;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * The JavaFX front-end. It builds the very same {@link GameApp} the console
 * front-end builds, only handing it a graphical view instead of a console one.
 */
public final class FxApp extends Application {

    /**
     * The link to the server, handed over before the toolkit starts because
     * JavaFX builds the Application itself.
     */
    private static ir.sharif.pvz.net.client.ServerConnection connection;

    public static void setConnection(ir.sharif.pvz.net.client.ServerConnection link) {
        connection = link;
    }

    private GameApp app;

    @Override
    public void start(Stage stage) {
        FxView view = new FxView();
        app = new GameApp(view, connection);

        GameUi ui = new GameUi(app, view);
        Scene scene = new Scene(ui.root(), GameUi.WIDTH, GameUi.HEIGHT);
        var stylesheet = FxApp.class.getResource("/style/app.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }

        stage.setTitle("Plants vs. Zombies 2");
        stage.setScene(scene);
        stage.setMinWidth(1024);
        stage.setMinHeight(700);
        stage.show();

        ui.refresh();
        app.greet();
    }

    @Override
    public void stop() {
        if (app != null) {
            app.save();
        }
    }
}
