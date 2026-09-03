package ir.sharif.pvz.net.server;

import ir.sharif.pvz.net.Message;
import ir.sharif.pvz.net.Protocol;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pairs players up, either because one invited the other by name or because
 * both asked for a random opponent, and keeps track of the games in progress.
 */
public final class Matchmaker {

    private final ServerState state;
    private final Random random = new Random();

    /** username -> the match they are in. */
    private final Map<String, Match> byPlayer = new ConcurrentHashMap<>();

    /** invited username -> who invited them. */
    private final Map<String, String> pendingInvites = new ConcurrentHashMap<>();

    Matchmaker(ServerState state) {
        this.state = state;
    }

    public Match matchOf(String username) {
        return username == null ? null : byPlayer.get(username);
    }

    /**
     * Asks a named player to a game. The reply tells the caller to wait; the
     * invitation itself arrives at the other end as a push.
     *
     * @return the problem to report, or null when the invite went out
     */
    public String invite(String from, String to) {
        if (to == null || to.isBlank()) {
            return "Type the username of the player you want to challenge.";
        }
        if (to.equals(from)) {
            return "You cannot challenge yourself.";
        }
        if (state.users().findByUsername(to) == null) {
            return "There is no player called '" + to + "'.";
        }
        if (!state.isOnline(to)) {
            return "'" + to + "' is not online right now.";
        }
        if (byPlayer.containsKey(to)) {
            return "'" + to + "' is already in a game.";
        }
        pendingInvites.put(to, from);
        state.connectionOf(to).push(Message.push(Protocol.INVITED).with("from", from));
        return null;
    }

    /**
     * The invited player's answer. Returns the new match when they accepted.
     */
    public Match answerInvite(String invited, boolean accepted) {
        String inviter = pendingInvites.remove(invited);
        if (inviter == null || !state.isOnline(inviter)) {
            return null;
        }
        if (!accepted) {
            state.connectionOf(inviter).push(
                    Message.push(Protocol.INVITE_DECLINED).with("from", invited));
            return null;
        }
        return start(inviter, invited);
    }

    /**
     * Puts a player in the random queue, pairing them with whoever waits.
     *
     * @return the match that just started, or null while they wait
     */
    public Match joinQueue(String username) {
        String opponent = state.pairOrEnqueue(username);
        return opponent == null ? null : start(opponent, username);
    }

    public void leaveQueue(String username) {
        state.dropFromQueue(username);
    }

    /**
     * Creates the match and tells both sides which role they drew.
     */
    private Match start(String first, String second) {
        ClientHandler one = state.connectionOf(first);
        ClientHandler two = state.connectionOf(second);
        if (one == null || two == null) {
            return null;
        }
        boolean firstGrows = random.nextBoolean();
        Match match = new Match(firstGrows ? one : two, firstGrows ? two : one);
        byPlayer.put(first, match);
        byPlayer.put(second, match);

        announce(match, one, first, second);
        announce(match, two, second, first);
        // both sides know their role before the first board arrives
        match.start();
        return match;
    }

    private void announce(Match match, ClientHandler side, String self, String other) {
        side.push(Message.push(Protocol.MATCH_FOUND)
                .with("match", match.id())
                .with("role", match.roleOf(side))
                .with("you", self)
                .with("opponent", other));
    }

    /**
     * Ends every game in progress, for a server that is shutting down. Without
     * this each match's loop thread outlives the server and goes on ticking a
     * game nobody is watching.
     */
    void endEveryMatch() {
        for (Match match : java.util.Set.copyOf(byPlayer.values())) {
            match.markOver();
        }
        byPlayer.clear();
        pendingInvites.clear();
    }

    /**
     * Takes a player out of whatever they were in, telling the other side.
     */
    public void leave(String username, String reason) {
        Match match = byPlayer.remove(username);
        pendingInvites.remove(username);
        pendingInvites.values().remove(username);
        state.dropFromQueue(username);
        if (match == null || match.isOver()) {
            return;
        }
        match.markOver();
        ClientHandler self = state.connectionOf(username);
        ClientHandler other = self == null ? null : match.opponentOf(self);
        if (other != null) {
            byPlayer.remove(other.username());
            other.push(Message.push(Protocol.MATCH_OVER)
                    .with("winner", other.username())
                    .with("reason", reason));
        }
    }
}
