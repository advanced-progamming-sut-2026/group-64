package ir.sharif.pvz.net.server;

import ir.sharif.pvz.model.UserRepository;
import ir.sharif.pvz.net.Protocol;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The game server: holds every account and pairs players up.
 *
 * <p>Started with {@code ./gradlew run --args="--server"}. One thread per
 * connection is plenty for a class-sized player base and keeps the code plain.
 */
public final class PvzServer implements AutoCloseable {

    private final ServerState state;
    private final ServerSocket socket;
    private final ExecutorService clients = Executors.newCachedThreadPool();

    private volatile boolean running = true;

    public PvzServer(int port, Path usersFile) throws IOException {
        this.state = new ServerState(new UserRepository(usersFile));
        this.socket = new ServerSocket(port);
    }

    /**
     * The port actually bound, which matters when 0 was asked for.
     */
    public int port() {
        return socket.getLocalPort();
    }

    public ServerState state() {
        return state;
    }

    /**
     * Accepts connections until closed.
     */
    public void serve() {
        while (running) {
            try {
                Socket connection = socket.accept();
                clients.execute(new ClientHandler(state, connection));
            } catch (IOException e) {
                if (running) {
                    System.out.println("Dropped a connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Runs the server in the background and returns once it is listening.
     */
    public Thread serveInBackground() {
        Thread thread = new Thread(this::serve, "pvz-server");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    @Override
    public void close() {
        running = false;
        state.shutdown();
        clients.shutdownNow();
        try {
            socket.close();
        } catch (IOException e) {
            // already closed
        }
    }

    /**
     * Entry point for {@code --server}.
     */
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Protocol.DEFAULT_PORT;
        try (PvzServer server = new PvzServer(port, Path.of("data", "users.json"))) {
            System.out.println("Plants vs. Zombies server listening on port " + server.port());
            server.serve();
        }
    }
}
