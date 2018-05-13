package activitystreamer.client;

import activitystreamer.util.Settings;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Validates received messages for Client, Converts from Strings to JSON objects & creates messages to send to server.
 */
public class ClientMessageHandler {

    /**
     * Converts a String to a JSONObject. Returns a specific JSON object if there's a parsing error.
     * @param data The string, hopefully formatted as a JSON object, to be parsed.
     * @return A JSONObject containing the data included in the string, or a specific error response.
     */
    public static JSONObject toJson(String data) {

        JSONParser parser = new JSONParser();
        JSONObject json;

        try {
            return (JSONObject) parser.parse(data);
        }
        catch (ParseException e) {
            e.printStackTrace();
            json = new JSONObject();
            json.put("status", "failure");
            return json;
        }
    }

    /**
     * Constructs the login message, to be sent to the server.
     * @return A correctly formatted JSONObject to be sent to a server, to request that the client log in.
     */
    public static JSONObject getLoginMsg() {
        JSONObject login_msg = new JSONObject();
        login_msg.put("command", "LOGIN");
        String username = Settings.getUsername();
        login_msg.put("username", username);

        if (!username.equals("anonymous")) {
            login_msg.put("secret", Settings.getSecret());
        }
        return login_msg;
    }

    /**
     * Constructs the logout message, to be sent to the server.
     * @return A correctly formatted JSONObject that indicates that the client is closing the connection
     */
    public static JSONObject getLogoutMsg() {
        JSONObject logout_msg = new JSONObject();
        logout_msg.put("command", "LOGOUT");
        return logout_msg;
    }

    /**
     * Constructs the registration message, to be sent to the server.
     * @return A correctly formatted JSONObject to be sent to a server, to request that the client be registered
     */
    public static JSONObject getRegistrationMsg() {
        JSONObject register_msg = new JSONObject();
        register_msg.put("command", "REGISTER");
        register_msg.put("username", Settings.getUsername());
        register_msg.put("secret", Settings.getSecret());
        return register_msg;
    }

    /**
     * Constructs an INVALID_MESSAGE message
     * @param info Information to be included, provided to tell recipient the reason the message is invalid.
     * @return An INVALID_MESSAGE JSONObject, to be sent to the sender of an invalid message.
     */
    public static JSONObject getInvalidMsg(String info) {
        JSONObject invalid_msg = new JSONObject();
        invalid_msg.put("command", "INVALID_MESSAGE");
        invalid_msg.put("info", info);
        return invalid_msg;
    }

    /**
     * Constructs the Activity Object message, to be sent to the server.
     * @return A correctly formatted Activity Object to be sent to a server.
     */
    public static JSONObject getActivityObjectMsg(JSONObject activity_obj) {
        JSONObject msg = new JSONObject();
        msg.put("command", "ACTIVITY_MESSAGE");
        msg.put("username", Settings.getUsername());
        msg.put("secret", Settings.getSecret());
        msg.put("activity", activity_obj);
        return msg;
    }

    /**
     * Validates incoming messages (ensures they have the correct fields)
     * @param json The JSONObject to be validated as containing the correct fields.
     * @return true if the JSONObject is a known command and contains the required fields for that command, else false
     */
    public static boolean validFieldsInMessage(JSONObject json) {

        switch (json.get("command").toString()) {

            case "REGISTER_SUCCESS":
            case "REGISTER_FAILED":
            case "LOGIN_SUCCESS":
            case "INVALID_MESSAGE":
            case "LOGIN_FAILED":
                return json.containsKey("info");

            case "REDIRECT":
                return validateRedirectMsg(json);

            case "ACTIVITY_BROADCAST":
            case "ACTIVITY_MESSAGE":
                boolean hasActivity = json.containsKey("activity");
                JSONObject activity = (JSONObject) json.get("activity");
                return activity.containsKey("authenticated_user");

            // Unrecognised command -> Return false, triggering an INVALID_MESSAGE
            default:
                return false;
        }
    }

    /**
     * Validates incoming messages (ensures that the JSON can be parsed, contains a command and the correct fields)
     * @param json The JSONObject to be validated as containing the correct fields, a command and can be parsed.
     * @return true if the JSONObject has the correct fields, can be parsed and contains a command, else false
     */
    public static boolean validateMessage(JSONObject json) {

        boolean valid = false;

        // Handle if the JSON couldn't be parsed
        if (json.containsKey("status") && json.get("status").equals("failure")) {
            JSONObject invalid_msg = ClientMessageHandler.getInvalidMsg("JSON parse error while parsing message");
            ConnectionManager.getInstanceClientConnection().sendMessage(invalid_msg);
            return valid;
        }

        // Handle if there's no "command" field in the json
        if (!json.containsKey("command")) {
            JSONObject invalid_msg = ClientMessageHandler.getInvalidMsg(
                    "the received message did not contain a command");
            ConnectionManager.getInstanceClientConnection().sendMessage(invalid_msg);
            return valid;
        }

        // Handle if the JSON didn't contain the correct fields
        if (!ClientMessageHandler.validFieldsInMessage(json)) {
            JSONObject invalid_msg = ClientMessageHandler.getInvalidMsg(
                    "the " + json.get("command").toString() +
                            " message was an unrecognised message or it did not contain the correct fields.");
            ConnectionManager.getInstanceClientConnection().sendMessage(invalid_msg);
            return valid;
        }
        return true;
    }

    /**
     * Validates REDIRECT messages.
     * @param json The REDIRECT message to validate.
     * @return true if the REDIRECT message contains the correct fields, false otherwise.
     */
    private static boolean validateRedirectMsg(JSONObject json) {
        if (json.containsKey("hostname") && json.containsKey("port")) {
            String hostname = (String) json.get("hostname").toString();
            int port = -1;
            port = ((Long) json.get("port")).intValue();
            return !((hostname == null) || (port == -1));
        }
        return false;
    }

    /**
     * Used to print login info to terminal, for future logins.
     *
     * @param login_msg A JSONObject containing the constructed login information
     */
    public static void printLoginInfo(JSONObject login_msg) {

        String username = (String) login_msg.get("username");

        // Print login info for client
        if (username.equals("anonymous")) {
            ConnectionManager.getInstance().getTextFrame().updateLoginInfo(username, null);
        }
        else {
            String secret = (String) login_msg.get("secret");
            ConnectionManager.getInstance().getTextFrame().updateLoginInfo(username, secret);
        }
    }


}

