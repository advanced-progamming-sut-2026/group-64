package ir.sharif.pvz.net.server;

import ir.sharif.pvz.model.AuthService;
import ir.sharif.pvz.model.ProfileService;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.UserRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything the server knows: the accounts on disk, who is signed in right
 * now, and who is waiting for a random opponent.
 *
 * <p>Connections each run on their own thread, so every method here is either
 * synchronised or backed by a concurrent collection.
 */
public final class ServerState {

    private final UserRepository users;
    private final AuthService auth;
    private final ProfileService profiles;

    /** username -> the connection they are signed in on. */
    private final Map<String, ClientHandler> online = new ConcurrentHashMap<>();

    /** usernames waiting to be paired with anyone. */
    private final Deque<String> queue = new ArrayDeque<>();

    private final Matchmaker matchmaker = new Matchmaker(this);

    public ServerState(UserRepository users) {
        this.users = users;
        this.auth = new AuthService(users);
        this.profiles = new ProfileService(users);
    }

    public Matchmaker matchmaker() {
        return matchmaker;
    }

    public UserRepository users() {
        return users;
    }

    public AuthService auth() {
        return auth;
    }

    public ProfileService profiles() {
        return profiles;
    }

    /**
     * Marks a connection as signed in, replacing any older session for the same
     * account so a second login elsewhere takes over cleanly.
     */
    public void signIn(String username, ClientHandler handler) {
        ClientHandler previous = online.put(username, handler);
        if (previous != null && previous != handler) {
            previous.disconnect("Signed in from somewhere else.");
        }
    }

    public void signOut(String username) {
        if (username != null) {
            matchmaker.leave(username, "Your opponent left the game.");
            online.remove(username);
            dropFromQueue(username);
        }
    }

    /**
     * Winds the server down: every game in progress stops and every client is
     * told why, so no loop thread is left ticking behind a closed server.
     */
    public void shutdown() {
        matchmaker.endEveryMatch();
        for (ClientHandler handler : online.values()) {
            handler.disconnect("The server is shutting down.");
        }
        online.clear();
        clearQueue();
    }

    private synchronized void clearQueue() {
        queue.clear();
    }

    public boolean isOnline(String username) {
        return online.containsKey(username);
    }

    public ClientHandler connectionOf(String username) {
        return online.get(username);
    }

    /**
     * Everyone signed in apart from the caller.
     */
    public List<String> onlineExcept(String username) {
        List<String> names = new ArrayList<>(online.keySet());
        names.remove(username);
        names.sort(String::compareTo);
        return names;
    }

    /**
     * Puts a player in the random-match queue, or pairs them with whoever was
     * already waiting. Returns the opponent, or null when they now wait.
     */
    public synchronized String pairOrEnqueue(String username) {
        while (!queue.isEmpty()) {
            String waiting = queue.pollFirst();
            if (!waiting.equals(username) && isOnline(waiting)) {
                return waiting;
            }
        }
        if (!queue.contains(username)) {
            queue.addLast(username);
        }
        return null;
    }

    public synchronized void dropFromQueue(String username) {
        queue.remove(username);
    }

    /**
     * Persists whatever a client sent up for its own account.
     */
    public synchronized void store(User updated, String previousUsername) {
        boolean renamed = previousUsername != null
                && !previousUsername.equals(updated.getUsername());
        if (renamed) {
            users.rename(previousUsername, updated);
            moveOnlineEntry(previousUsername, updated.getUsername());
        } else if (users.findByUsername(updated.getUsername()) == null) {
            users.add(updated);
            return;
        } else {
            users.replace(updated);
        }
        users.save();
    }

    /**
     * Follows a rename in the signed-in registry so pushes still reach them.
     */
    private void moveOnlineEntry(String from, String to) {
        ClientHandler handler = online.remove(from);
        if (handler != null) {
            online.put(to, handler);
        }
    }

    public synchronized List<User> allUsers() {
        return users.all();
    }
}
