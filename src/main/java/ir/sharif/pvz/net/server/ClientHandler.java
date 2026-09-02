package ir.sharif.pvz.net.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import ir.sharif.pvz.model.AuthException;
import ir.sharif.pvz.model.RegisterRequest;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.net.Channel;
import ir.sharif.pvz.net.Message;
import ir.sharif.pvz.net.Protocol;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Serves one connected client for as long as it stays connected: reads its
 * requests, answers them, and carries any pushes aimed at it.
 */
public final class ClientHandler implements Runnable {

    private static final Gson GSON = new Gson();

    private static final Message POISON = Message.push("shutdown");

    private final ServerState state;
    private final Channel channel;
    /**
     * Outbound frames. Everything the server sends this client goes through
     * here so that writing to a slow player never stalls the thread serving
     * somebody else — which is exactly what happens when one player leaves and
     * the server has to tell their opponent.
     */
    private final BlockingQueue<Message> outbox = new LinkedBlockingQueue<>();

    private volatile boolean live = true;
    private String username;

    ClientHandler(ServerState state, Socket socket) throws IOException {
        this.state = state;
        this.channel = new Channel(socket);
    }

    /**
     * The account this connection is signed in as, or null.
     */
    public String username() {
        return username;
    }

    /**
     * Sends a message to this client, ignoring a dead socket.
     */
    public void push(Message message) {
        if (live) {
            outbox.offer(message);
        }
    }

    /**
     * Ends this session, telling the client why.
     */
    public void disconnect(String reason) {
        push(Message.push("disconnected").with("reason", reason));
        live = false;
        outbox.offer(POISON);
    }

    @Override
    public void run() {
        Thread writer = new Thread(this::writeLoop, "pvz-writer");
        writer.setDaemon(true);
        writer.start();
        try (channel) {
            Message request;
            while ((request = channel.receive()) != null) {
                push(handle(request));
            }
        } catch (IOException e) {
            // client went away
        } finally {
            live = false;
            outbox.offer(POISON);
            state.signOut(username);
        }
    }

    /**
     * Drains the outbox onto the socket until the session ends.
     */
    private void writeLoop() {
        try {
            while (true) {
                Message message = outbox.take();
                if (message == POISON) {
                    break;
                }
                channel.send(message);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            channel.close();
        }
    }

    /**
     * Answers one request. Anything unexpected becomes a refusal rather than a
     * dropped connection.
     */
    private Message handle(Message request) {
        try {
            return dispatch(request);
        } catch (AuthException e) {
            return Message.failure(request, e.getMessage());
        } catch (RuntimeException e) {
            return Message.failure(request, "The server could not handle that request.");
        }
    }

    private Message dispatch(Message request) throws AuthException {
        return switch (request.getType()) {
            case Protocol.REGISTER -> register(request);
            case Protocol.LOGIN -> login(request);
            case Protocol.LOGOUT -> logout(request);
            case Protocol.USERNAME_TAKEN -> Message.reply(request)
                    .with("taken", state.users().usernameExists(request.text("username")));
            case Protocol.FIND_USER -> findUser(request);
            case Protocol.SAVE_USER -> saveUser(request);
            case Protocol.ALL_USERS -> allUsers(request);
            case Protocol.SUBMIT_SCORE -> submitScore(request);
            case Protocol.FORGET_PASSWORD -> forgetPassword(request);
            case Protocol.RESET_PASSWORD -> resetPassword(request);
            case Protocol.ONLINE_USERS -> Message.reply(request)
                    .with("users", GSON.toJsonTree(state.onlineExcept(username)));
            case Protocol.INVITE -> invite(request);
            case Protocol.INVITE_ANSWER -> inviteAnswer(request);
            case Protocol.QUEUE_JOIN -> queueJoin(request);
            case Protocol.QUEUE_LEAVE -> queueLeave(request);
            case Protocol.MATCH_LEAVE -> matchLeave(request);
            case Protocol.MATCH_ACTION -> matchAction(request);
            case Protocol.REACTION -> reaction(request);
            default -> Message.failure(request, "Unknown request: " + request.getType());
        };
    }

    // ===== lobby =====

    private Message requireSignedIn(Message request) {
        return username == null ? Message.failure(request, "Sign in first.") : null;
    }

    private Message invite(Message request) {
        Message refusal = requireSignedIn(request);
        if (refusal != null) {
            return refusal;
        }
        String problem = state.matchmaker().invite(username, request.text("to"));
        return problem == null
                ? Message.reply(request).with("waiting", true)
                : Message.failure(request, problem);
    }

    private Message inviteAnswer(Message request) {
        Message refusal = requireSignedIn(request);
        if (refusal != null) {
            return refusal;
        }
        state.matchmaker().answerInvite(username, request.flag("accepted"));
        return Message.reply(request);
    }

    private Message queueJoin(Message request) {
        Message refusal = requireSignedIn(request);
        if (refusal != null) {
            return refusal;
        }
        Match match = state.matchmaker().joinQueue(username);
        return Message.reply(request).with("waiting", match == null);
    }

    private Message queueLeave(Message request) {
        state.matchmaker().leaveQueue(username);
        return Message.reply(request);
    }

    /**
     * Something the player did on their own lawn. It is queued on the match and
     * applied on the match's own thread, so the two players' actions never land
     * in the middle of a tick.
     */
    private Message matchAction(Message request) {
        Match match = state.matchmaker().matchOf(username);
        if (match == null || match.isOver()) {
            return Message.failure(request, "You are not in a game.");
        }
        boolean plants = "plants".equals(match.roleOf(this));
        String type = request.text("plant");
        int col = request.number("col", 0);
        int row = request.number("row", 0);
        match.submit(() -> {
            if (plants) {
                match.session().plant(type, col, row);
            } else {
                match.session().placeZombie(type, col, row);
            }
        });
        return Message.reply(request);
    }

    /**
     * A canned message, emoji or sticker, passed straight to the opponent.
     */
    private Message reaction(Message request) {
        Match match = state.matchmaker().matchOf(username);
        if (match == null) {
            return Message.failure(request, "You are not in a game.");
        }
        match.opponentOf(this).push(Message.push(Protocol.REACTION)
                .with("kind", request.text("kind"))
                .with("value", request.text("value"))
                .with("from", username));
        return Message.reply(request);
    }

    private Message matchLeave(Message request) {
        state.matchmaker().leave(username, "Your opponent left the game.");
        return Message.reply(request);
    }

    // ===== accounts =====

    private Message register(Message request) {
        RegisterRequest form = new RegisterRequest(
                request.text("username"), request.text("password"), request.text("password"),
                request.text("nickname"), request.text("email"), request.text("gender"));
        List<String> problems = state.auth().validateRegistration(form);
        if (!problems.isEmpty()) {
            return Message.failure(request, String.join("\n", problems));
        }
        User created = state.auth().register(form, request.number("question", 1),
                request.text("answer"));
        return Message.reply(request).with("user", GSON.toJsonTree(created));
    }

    private Message login(Message request) throws AuthException {
        User user = state.auth().login(request.text("username"), request.text("password"));
        username = user.getUsername();
        state.signIn(username, this);
        return Message.reply(request).with("user", GSON.toJsonTree(user));
    }

    private Message logout(Message request) {
        state.signOut(username);
        username = null;
        return Message.reply(request);
    }

    private Message findUser(Message request) {
        User user = state.users().findByUsername(request.text("username"));
        Message reply = Message.reply(request);
        return user == null ? reply : reply.with("user", GSON.toJsonTree(user));
    }

    private Message saveUser(Message request) {
        User updated = GSON.fromJson(request.getData().get("user"), User.class);
        if (updated == null || updated.getUsername() == null) {
            return Message.failure(request, "There was no account in that request.");
        }
        state.store(updated, request.text("previous"));
        if (username != null && !username.equals(updated.getUsername())) {
            username = updated.getUsername();
        }
        return Message.reply(request);
    }

    /**
     * Records a round of the online score game. The server keeps the better of
     * the old and new figures, so a client cannot post a worse one over its own
     * record or make one up for somebody else.
     */
    private Message submitScore(Message request) {
        if (username == null) {
            return Message.failure(request, "Sign in first.");
        }
        User user = state.users().findByUsername(username);
        if (user == null) {
            return Message.failure(request, "That account is gone.");
        }
        user.submitNetworkPoints(Math.max(0, request.number("points", 0)));
        state.store(user, username);
        return Message.reply(request).with("points", user.getNetworkPoints());
    }

    private Message allUsers(Message request) {
        JsonArray array = new JsonArray();
        for (User user : state.allUsers()) {
            array.add(GSON.toJsonTree(user));
        }
        return Message.reply(request).with("users", array);
    }

    private Message forgetPassword(Message request) throws AuthException {
        User user = state.auth().startForgetPassword(request.text("username"), request.text("email"));
        return Message.reply(request).with("question", user.getSecurityQuestionNumber());
    }

    private Message resetPassword(Message request) throws AuthException {
        User user = state.auth().startForgetPassword(request.text("username"), request.text("email"));
        if (!state.auth().checkSecurityAnswer(user, request.text("answer"))) {
            return Message.failure(request, "That is not the right answer.");
        }
        state.auth().resetPassword(user, request.text("password"));
        return Message.reply(request).with("user", GSON.toJsonTree(user));
    }
}
