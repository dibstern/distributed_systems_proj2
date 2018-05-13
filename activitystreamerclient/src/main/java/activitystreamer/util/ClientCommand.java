package activitystreamer.util;

import org.json.simple.JSONObject;

/**
 * An interface for different possible client command responses.
 */
public interface ClientCommand {
    /**
     * All commands take some json object and execute a desired response from the client.
     * @param json The json object representing the server's message to the client.
     */
    public void execute(JSONObject json);
}