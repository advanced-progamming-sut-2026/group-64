package ir.sharif.pvz.model.net;

import com.google.gson.Gson;
import ir.sharif.pvz.model.AuthException;
import ir.sharif.pvz.model.AuthService;
import ir.sharif.pvz.model.RegisterRequest;
import ir.sharif.pvz.model.User;
import ir.sharif.pvz.model.UserValidator;
import ir.sharif.pvz.net.Message;
import ir.sharif.pvz.net.Protocol;
import ir.sharif.pvz.net.client.ServerConnection;
import ir.sharif.pvz.net.client.ServerException;
import java.util.ArrayList;
import java.util.List;

/**
 * Signing up and signing in against the server.
 *
 * <p>The password never leaves as a hash the client made up: the server does
 * the hashing and the checking, so a tampered client cannot talk its way in.
 * The shape of the class matches the offline one exactly, which is why none of
 * the phase-1 menus had to change.
 */
public final class RemoteAuthService extends AuthService {

    private static final Gson GSON = new Gson();

    private final ServerConnection connection;
    private final RemoteUserRepository users;

    public RemoteAuthService(ServerConnection connection, RemoteUserRepository users) {
        super(users);
        this.connection = connection;
        this.users = users;
    }

    /**
     * Validates locally first so the player gets every complaint at once, then
     * lets the server have the final say on whether the name is free.
     */
    @Override
    public List<String> validateRegistration(RegisterRequest request) {
        List<String> errors = new ArrayList<>(UserValidator.validateUsername(request.username()));
        if (errors.isEmpty() && nameTaken(request.username())) {
            errors.add("Username '" + request.username() + "' already exists.");
        }
        errors.addAll(UserValidator.validatePassword(request.password()));
        errors.addAll(UserValidator.validateNickname(request.nickname()));
        errors.addAll(UserValidator.validateEmail(request.email()));
        errors.addAll(UserValidator.validateGender(request.gender()));
        return errors;
    }

    private boolean nameTaken(String username) {
        try {
            return users.usernameExists(username);
        } catch (ServerException e) {
            // let the server reject it properly when the account is created
            return false;
        }
    }

    @Override
    public User register(RegisterRequest request, int questionNumber, String answer) {
        Message reply = connection.ask(connection.request(Protocol.REGISTER)
                .with("username", request.username())
                .with("password", request.password())
                .with("nickname", request.nickname())
                .with("email", request.email())
                .with("gender", request.gender())
                .with("question", questionNumber)
                .with("answer", answer));
        User created = GSON.fromJson(reply.getData().get("user"), User.class);
        users.track(created);
        return created;
    }

    @Override
    public User login(String username, String password) throws AuthException {
        try {
            Message reply = connection.ask(connection.request(Protocol.LOGIN)
                    .with("username", username)
                    .with("password", password));
            User user = GSON.fromJson(reply.getData().get("user"), User.class);
            users.track(user);
            return user;
        } catch (ServerException e) {
            throw new AuthException(e.getMessage());
        }
    }

    @Override
    public User startForgetPassword(String username, String email) throws AuthException {
        try {
            connection.ask(connection.request(Protocol.FORGET_PASSWORD)
                    .with("username", username)
                    .with("email", email));
            User user = users.findByUsername(username);
            if (user == null) {
                throw new AuthException("Username '" + username + "' does not exist.");
            }
            return user;
        } catch (ServerException e) {
            throw new AuthException(e.getMessage());
        }
    }

    /**
     * Sets a new password on the server, which re-checks the security answer
     * rather than trusting that this client already did.
     */
    @Override
    public void resetPassword(User user, String newPassword) {
        connection.ask(connection.request(Protocol.RESET_PASSWORD)
                .with("username", user.getUsername())
                .with("email", user.getEmail())
                .with("answer", pendingAnswer)
                .with("password", newPassword));
    }

    /** The answer the player just gave, kept so the reset can prove it. */
    private String pendingAnswer = "";

    /**
     * Remembers the answer and reports whether the server accepts it.
     */
    @Override
    public boolean checkSecurityAnswer(User user, String answer) {
        pendingAnswer = answer;
        return ir.sharif.pvz.util.PasswordHasher.matches(answer, user.getSecurityAnswerHash());
    }
}
