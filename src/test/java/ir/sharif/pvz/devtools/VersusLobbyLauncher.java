package ir.sharif.pvz.devtools;

import javafx.application.Application;

/**
 * Starts {@link VersusLobbyCheck}, for the same reason
 * {@link SnapshotLauncher} exists: JavaFX will not boot from a main class that
 * extends Application when the toolkit is on the classpath.
 */
public final class VersusLobbyLauncher {

    private VersusLobbyLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(VersusLobbyCheck.class, args);
    }
}
