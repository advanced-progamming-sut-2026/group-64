package ir.sharif.pvz;

import ir.sharif.pvz.controller.GameApp;
import ir.sharif.pvz.net.Protocol;
import ir.sharif.pvz.net.client.ServerConnection;
import ir.sharif.pvz.net.server.PvzServer;
import ir.sharif.pvz.view.fx.FxApp;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javafx.application.Application;

/**
 * Entry point for all three ways the project runs.
 *
 * <pre>
 *   (no arguments)   the graphical client, which joins the server
 *   --server [port]  the game server
 *   --cli            the phase-1 terminal client
 *   --offline        keep accounts on this machine instead of the server
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        List<String> arguments = List.of(args);
        if (arguments.contains("--server")) {
            runServer(args);
            return;
        }
        if (arguments.contains("--cli")) {
            new GameApp(new ir.sharif.pvz.view.ConsoleView(), connect(arguments)).run();
            return;
        }
        FxApp.setConnection(connect(arguments));
        Application.launch(FxApp.class, args);
    }

    private static void runServer(String[] args) throws IOException {
        int port = portFrom(args);
        try (PvzServer server = new PvzServer(port, Path.of("data", "users.json"))) {
            System.out.println("Plants vs. Zombies server listening on port " + server.port());
            server.serve();
        }
    }

    private static int portFrom(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--server".equals(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    break;
                }
            }
        }
        return Protocol.DEFAULT_PORT;
    }

    /**
     * Opens the link to the server, or returns null to play offline — either
     * because it was asked for, or because nothing is listening.
     */
    private static ServerConnection connect(List<String> arguments) {
        if (arguments.contains("--offline")) {
            return null;
        }
        String host = valueAfter(arguments, "--host", Protocol.DEFAULT_HOST);
        int port = Integer.parseInt(valueAfter(arguments, "--port",
                String.valueOf(Protocol.DEFAULT_PORT)));
        try {
            return new ServerConnection(host, port);
        } catch (IOException e) {
            System.out.println("No server at " + host + ":" + port
                    + "; starting offline. Run with --server to host one.");
            return null;
        }
    }

    private static String valueAfter(List<String> arguments, String flag, String fallback) {
        int index = arguments.indexOf(flag);
        return index >= 0 && index + 1 < arguments.size() ? arguments.get(index + 1) : fallback;
    }
}
