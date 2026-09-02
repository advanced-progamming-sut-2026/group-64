package ir.sharif.pvz.net.server;

import com.google.gson.Gson;
import ir.sharif.pvz.model.game.GameSession;
import ir.sharif.pvz.model.game.Minigames;
import ir.sharif.pvz.model.game.VersusGame;
import ir.sharif.pvz.net.Message;
import ir.sharif.pvz.net.Protocol;
import ir.sharif.pvz.net.Snapshot;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Two players paired up for a versus game of "I, Zombie".
 *
 * <p>One side grows plants, the other side sends zombies; which is which is
 * decided here and told to both, so neither client has to negotiate it.
 *
 * <p>The match owns the only real copy of the game. Both clients send what
 * their player did and draw whatever comes back, which is what keeps the two
 * screens showing the same board, and means neither client can cheat by
 * editing its own copy.
 */
public class Match {

    private static final AtomicLong IDS = new AtomicLong(1);

    private final String id = "m" + IDS.getAndIncrement();
    private final ClientHandler plantSide;
    private final ClientHandler zombieSide;

    private static final Gson GSON = new Gson();
    /** How often the board is sent out; ten a second matches the engine's tick. */
    private static final long BROADCAST_MILLIS = 100;

    private final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();

    private volatile boolean over;
    private GameSession session;
    private VersusGame rules;
    private Thread loop;

    Match(ClientHandler plantSide, ClientHandler zombieSide) {
        this.plantSide = plantSide;
        this.zombieSide = zombieSide;
    }

    public String id() {
        return id;
    }

    public ClientHandler plantSide() {
        return plantSide;
    }

    public ClientHandler zombieSide() {
        return zombieSide;
    }

    /**
     * The side this connection is playing, "plants" or "zombies".
     */
    public String roleOf(ClientHandler handler) {
        return handler == plantSide ? "plants" : "zombies";
    }

    public ClientHandler opponentOf(ClientHandler handler) {
        return handler == plantSide ? zombieSide : plantSide;
    }

    public boolean isOver() {
        return over;
    }

    void markOver() {
        over = true;
    }

    /**
     * Builds the game and starts ticking it. Both players are told their side
     * before the first board goes out.
     */
    void start() {
        rules = new VersusGame();
        session = Minigames.versus(rules, new Random());
        loop = new Thread(this::run, "pvz-match-" + id);
        loop.setDaemon(true);
        loop.start();
    }

    /**
     * Queues something a player did, to be applied on the next tick so that the
     * simulation only ever changes on its own thread.
     */
    void submit(Runnable action) {
        pending.add(action);
    }

    /**
     * The live game, for the handler that needs to read prices off it.
     */
    GameSession session() {
        return session;
    }

    VersusGame rules() {
        return rules;
    }

    private void run() {
        long previous = System.currentTimeMillis();
        while (!over) {
            long now = System.currentTimeMillis();
            int ticks = (int) ((now - previous) / BROADCAST_MILLIS);
            if (ticks <= 0) {
                sleepABit();
                continue;
            }
            previous += ticks * BROADCAST_MILLIS;
            applyPending();
            synchronized (this) {
                session.advance(ticks);
            }
            broadcast(Message.push(Protocol.MATCH_STATE)
                    .with("state", GSON.toJsonTree(Snapshot.of(session, rules))));
            if (session.isOver()) {
                finish();
            }
        }
    }

    private void applyPending() {
        Runnable action;
        while ((action = pending.poll()) != null) {
            synchronized (this) {
                action.run();
            }
        }
    }

    /**
     * Declares the winner. The plant side wins by outlasting the clock or
     * saving a brain; the zombie side wins by eating them all.
     */
    private void finish() {
        if (over) {
            return;
        }
        markOver();
        boolean plantsWon = session.isWon();
        String winner = (plantsWon ? plantSide : zombieSide).username();
        broadcast(Message.push(Protocol.MATCH_OVER)
                .with("winner", winner)
                .with("reason", plantsWon
                        ? "The plants held the line."
                        : "The zombies ate every brain."));
    }

    private void sleepABit() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markOver();
        }
    }

    /**
     * Sends a message to both players at once.
     */
    public void broadcast(Message message) {
        plantSide.push(message);
        zombieSide.push(message);
    }
}
