package ir.sharif.pvz.model;

/**
 * Thrown when an authentication operation fails with a user-facing message.
 */
public class AuthException extends Exception {

    public AuthException(String message) {
        super(message);
    }
}
