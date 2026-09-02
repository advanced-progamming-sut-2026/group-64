package ir.sharif.pvz.net;

/**
 * The names of every message the client and server exchange, and the defaults
 * they connect on.
 */
public final class Protocol {

    public static final int DEFAULT_PORT = 5252;
    public static final String DEFAULT_HOST = "localhost";

    // ----- account -----
    public static final String REGISTER = "register";
    public static final String LOGIN = "login";
    public static final String LOGOUT = "logout";
    public static final String USERNAME_TAKEN = "username-taken";
    public static final String FIND_USER = "find-user";
    public static final String SAVE_USER = "save-user";
    public static final String ALL_USERS = "all-users";
    public static final String SUBMIT_SCORE = "submit-score";
    public static final String FORGET_PASSWORD = "forget-password";
    public static final String RESET_PASSWORD = "reset-password";

    // ----- lobby -----
    public static final String ONLINE_USERS = "online-users";
    public static final String INVITE = "invite";
    public static final String INVITE_ANSWER = "invite-answer";
    public static final String QUEUE_JOIN = "queue-join";
    public static final String QUEUE_LEAVE = "queue-leave";

    // ----- match -----
    public static final String MATCH_FOUND = "match-found";
    public static final String MATCH_ACTION = "match-action";
    public static final String MATCH_STATE = "match-state";
    public static final String MATCH_OVER = "match-over";
    public static final String MATCH_LEAVE = "match-leave";
    public static final String REACTION = "reaction";

    // ----- pushes -----
    public static final String INVITED = "invited";
    public static final String INVITE_DECLINED = "invite-declined";

    private Protocol() {
    }
}
