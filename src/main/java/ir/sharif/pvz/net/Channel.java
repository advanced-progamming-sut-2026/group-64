package ir.sharif.pvz.net;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * One socket, carrying {@link Message}s as JSON with a newline after each.
 *
 * <p>Newline framing keeps the reader simple and the traffic readable, which
 * matters more here than squeezing bytes: a whole board state is a few hundred
 * of them.
 */
public final class Channel implements AutoCloseable {

    private static final Gson GSON = new Gson();

    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;

    public Channel(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Writes one message. Synchronised because the server pushes state from its
     * own thread while a reply may be going out on another.
     */
    public synchronized void send(Message message) throws IOException {
        out.write(GSON.toJson(message));
        out.write('\n');
        out.flush();
    }

    /**
     * Blocks for the next message, or returns null when the peer hangs up.
     * A line that will not parse is skipped rather than killing the link.
     */
    public Message receive() throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            try {
                Message message = GSON.fromJson(line, Message.class);
                if (message != null && message.getType() != null) {
                    return message;
                }
            } catch (JsonSyntaxException e) {
                // a garbled frame is not worth dropping the connection over
                continue;
            }
        }
        return null;
    }

    public String remoteName() {
        return String.valueOf(socket.getRemoteSocketAddress());
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            // closing a socket that is already gone is not a problem
        }
    }
}
