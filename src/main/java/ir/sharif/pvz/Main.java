package ir.sharif.pvz;

import ir.sharif.pvz.controller.GameApp;
import ir.sharif.pvz.view.fx.FxApp;
import javafx.application.Application;

/**
 * Entry point. The graphical interface is the default; passing --cli runs the
 * phase-1 terminal interface instead. Both drive the same controllers.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        boolean console = args.length > 0 && "--cli".equals(args[0]);
        if (console) {
            new GameApp(new ir.sharif.pvz.view.ConsoleView()).run();
        } else {
            Application.launch(FxApp.class, args);
        }
    }
}
