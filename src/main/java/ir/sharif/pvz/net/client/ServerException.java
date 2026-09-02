package ir.sharif.pvz.net.client;

/**
 * The server refused a request, or the link to it failed. The message is
 * already worded for the player.
 */
public class ServerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ServerException(String message) {
        super(message == null ? "The server refused that request." : message);
    }
}
