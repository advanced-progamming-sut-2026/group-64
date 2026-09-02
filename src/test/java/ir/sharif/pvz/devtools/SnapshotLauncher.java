package ir.sharif.pvz.devtools;

import javafx.application.Application;

/**
 * Starts {@link ScreenSnapshots}. JavaFX refuses to boot when the main class
 * itself extends Application and the toolkit sits on the classpath rather than
 * the module path, so the entry point lives in this separate class.
 */
public final class SnapshotLauncher {

    private SnapshotLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(ScreenSnapshots.class, args);
    }
}
