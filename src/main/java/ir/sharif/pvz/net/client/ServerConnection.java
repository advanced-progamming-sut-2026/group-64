package ir.sharif.pvz.net.client;

import ir.sharif.pvz.net.Channel;
import ir.sharif.pvz.net.Message;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The client's end of the link.
 *
 * <p>A single reader thread pulls everything off the socket. Replies are handed
 * to whichever caller is waiting on that request id; anything else — an invite,
 * a board update — goes to the listener for its type. That way a blocking
 * {@code ask} never swallows a push meant for the game.
 */
public final class ServerConnection implements AutoCloseable {

    private static final long TIMEOUT_SECONDS = 10;

    private final Channel channel;
    private final AtomicLong nextId = new AtomicLong(1);
    /**
     * One pigeonhole per request in flight, keyed by its id. It has to be a
     * queue that can hold the reply rather than one that hands it straight
     * over: the reader thread can get the answer back before the caller has
     * reached its {@code poll}, and anything without room drops it there.
     */
    private final Map<Long, BlockingQueue<Message>> waiting = new ConcurrentHashMap<>();
    private final Map<String, Consumer<Message>> listeners = new ConcurrentHashMap<>();

    private volatile boolean open = true;
    private volatile Runnable onDisconnect;

    public ServerConnection(String host, int port) throws IOException {
        this.channel = new Channel(new Socket(host, port));
        Thread reader = new Thread(this::readLoop, "pvz-client-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /**
     * Registers a handler for one kind of unprompted message.
     */
    public void on(String type, Consumer<Message> listener) {
        listeners.put(type, listener);
    }

    /**
     * Stops handling one kind of push, for a screen that has been left.
     */
    public void off(String type) {
        listeners.remove(type);
    }

    /**
     * Called when the link drops, so the UI can say so.
     */
    public void onDisconnect(Runnable action) {
        this.onDisconnect = action;
    }

    public boolean isOpen() {
        return open;
    }

    /**
     * Sends a request and blocks until its reply arrives.
     *
     * @throws ServerException when the server refuses, or the link is gone
     */
    public Message ask(Message request) {
        if (!open) {
            throw new ServerException("Not connected to the server.");
        }
        BlockingQueue<Message> slot = new ArrayBlockingQueue<>(1);
        waiting.put(request.getId(), slot);
        try {
            channel.send(request);
            Message reply = slot.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (reply == null) {
                throw new ServerException("The server did not answer in time.");
            }
            if (!reply.isOk()) {
                throw new ServerException(reply.getError());
            }
            return reply;
        } catch (IOException e) {
            throw new ServerException("Lost the connection to the server.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServerException("Interrupted while waiting for the server.");
        } finally {
            waiting.remove(request.getId());
        }
    }

    /**
     * Starts a request of the given type with a fresh id.
     */
    public Message request(String type) {
        return Message.request(type, nextId.getAndIncrement());
    }

    /**
     * Sends without waiting, for in-game actions that need no answer.
     */
    public void tell(Message message) {
        try {
            channel.send(message);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void readLoop() {
        try {
            Message message;
            while ((message = channel.receive()) != null) {
                deliver(message);
            }
        } catch (IOException e) {
            // falls through to the shutdown below
        } finally {
            open = false;
            Runnable action = onDisconnect;
            if (action != null) {
                action.run();
            }
        }
    }

    private void deliver(Message message) {
        BlockingQueue<Message> slot = message.getId() == 0 ? null : waiting.get(message.getId());
        if (slot != null) {
            slot.offer(message);
            return;
        }
        Consumer<Message> listener = listeners.get(message.getType());
        if (listener != null) {
            listener.accept(message);
        }
    }

    @Override
    public void close() {
        open = false;
        channel.close();
    }
}
