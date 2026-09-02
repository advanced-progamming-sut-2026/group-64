package ir.sharif.pvz.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * One frame on the wire, used in both directions.
 *
 * <p>A request carries a {@code type} and an {@code id}; the reply comes back
 * with the same id so a caller can wait for its own answer while other traffic
 * flows past. A push from the server — a game invite, a state update — carries
 * a type and id 0, because nobody is waiting for it.
 */
public final class Message {

    private String type;
    private long id;
    private boolean ok = true;
    private String error;
    private JsonObject data = new JsonObject();

    public Message() {
    }

    private Message(String type, long id) {
        this.type = type;
        this.id = id;
    }

    /**
     * A request the client is waiting on an answer for.
     */
    public static Message request(String type, long id) {
        return new Message(type, id);
    }

    /**
     * A message the server sends unprompted.
     */
    public static Message push(String type) {
        return new Message(type, 0);
    }

    /**
     * A successful reply to the given request.
     */
    public static Message reply(Message request) {
        return new Message(request.type, request.id);
    }

    /**
     * A refusal, carrying the message the player should see.
     */
    public static Message failure(Message request, String reason) {
        Message message = new Message(request.type, request.id);
        message.ok = false;
        message.error = reason;
        return message;
    }

    public String getType() {
        return type;
    }

    public long getId() {
        return id;
    }

    public boolean isOk() {
        return ok;
    }

    public String getError() {
        return error;
    }

    public JsonObject getData() {
        return data == null ? new JsonObject() : data;
    }

    /**
     * Adds a field to the payload and returns this message, so calls chain.
     */
    public Message with(String key, String value) {
        getOrCreate().addProperty(key, value);
        return this;
    }

    public Message with(String key, Number value) {
        getOrCreate().addProperty(key, value);
        return this;
    }

    public Message with(String key, Boolean value) {
        getOrCreate().addProperty(key, value);
        return this;
    }

    public Message with(String key, JsonElement value) {
        getOrCreate().add(key, value);
        return this;
    }

    private JsonObject getOrCreate() {
        if (data == null) {
            data = new JsonObject();
        }
        return data;
    }

    /**
     * A payload field as text, or null when it is absent.
     */
    public String text(String key) {
        JsonElement element = getData().get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    public int number(String key, int fallback) {
        JsonElement element = getData().get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsInt();
    }

    public boolean flag(String key) {
        JsonElement element = getData().get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }
}
