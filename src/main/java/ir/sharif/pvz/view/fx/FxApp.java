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

    private GameApp app;

    @Override
    public void start(Stage stage) {
        FxView view = new FxView();
        app = new GameApp(view);

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
