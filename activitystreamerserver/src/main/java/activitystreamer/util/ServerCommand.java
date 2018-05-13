package activitystreamer.util;

import activitystreamer.server.Connection;
import org.json.simple.JSONObject;

/**
 * An interface for different possible server command responses.
 */
public interface ServerCommand {

    /**
     * All commands take some json object and execute a desired response from the server.
     * @param json The json object representing the client's message to the server.
     * @param con The connection a given message was received from
     */
    public void execute(JSONObject json, Connection con);
}
