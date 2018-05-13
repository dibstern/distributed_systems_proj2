package activitystreamer.util;
import org.json.simple.JSONObject;

import java.util.Map;


/**
 * An abstract class containing the functions to execute client and server behaviour for a given message.
 */

public abstract class Response {

    public static Map<String, ClientCommand> CLIENT_RESPONSES;

    /**
     * Takes a received JSON message, retrieves and executes the appropriate behaviour for that message.
     * @param json The JSONObject message sent to the client.
     * @return true if response was executed, else indicates an
     */
    public boolean executeResponse(JSONObject json) {
        String responseType = json.get("command").toString();
        ClientCommand command = CLIENT_RESPONSES.get(responseType);

        // Check that behaviour is defined for given response message
        if (command == null) {
            System.out.println("Error: ClientCommand validated but behaviour is undefined.");
            return false;
        }
        command.execute(json);
        return true;
    }
}
