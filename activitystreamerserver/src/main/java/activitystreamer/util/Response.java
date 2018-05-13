package activitystreamer.util;

import activitystreamer.server.Connection;
import org.json.simple.JSONObject;

import java.util.Map;


/**
 * An abstract class containing the functions to execute client and server behaviour for a given message.
 */

public abstract class Response {

    public static Map<String, ServerCommand> SERVER_RESPONSES;

    /**
     * Takes a received JSON message, retrieves and executes the appropriate behaviour for that message.
     * @param json The JSONObject message sent to the server.
     * @param con The connection a message was received on
     * @return true if response was executed, else indicates an
     */
    public boolean executeResponse(JSONObject json, Connection con) {
        String responseType = json.get("command").toString();
        ServerCommand command = SERVER_RESPONSES.get(responseType);

        // Check that behaviour is defined for given response message
        if (command == null) {
            System.out.println("Error: ServerCommand validated but behaviour is undefined.");
            return true;
        }
        command.execute(json, con);
        return false;
    }

}
