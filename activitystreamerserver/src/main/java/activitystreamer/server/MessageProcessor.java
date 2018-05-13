package activitystreamer.server;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/** This class is responsible for generating all of the messages to be sent by the server across the network.
 * It also checks that each message is valid/non-corrupt, and came from an authenticated server or a client that
 * has logged in. Class also handles the conversion of a string to a JSON object. **/

public class MessageProcessor {

    /**
     * Validates incoming messages (ensures they have the correct fields)
     * @param json The JSONObject to be validated as containing the correct fields.
     * @return Msg if the JSONObject does not contain a known command and the required fields for that command, otherwise
     * null.
     */
    public static String hasValidCommandAndFields(JSONObject json) {

        String typeErrorMsg = "Type Error! Some of the values in the json message have the incorrect type";

        if (!json.containsKey("command")) {
            return "the received message did not contain a command";
        }

        String typeIssue = ensureTypesCorrect(json);
        if (typeIssue != null) {
            return typeErrorMsg;
        }

        // Get the required information from the JSONObject
        boolean containsSecret = json.containsKey("secret");
        boolean containsLoginInfo = json.containsKey("username") &&
                (json.get("username").toString().equals("anonymous") || containsSecret);
        boolean containsActivity = json.containsKey("activity");

        // Error message to be sent if JSONObject is missing required field
        String command = (String) json.get("command");
        String missingFieldMsg = "the received " + command + " was missing fields";

        switch (command) {
            case "AUTHENTICATE":
                return (containsSecret ? null : missingFieldMsg);
            case "LOGIN":
            case "REGISTER":
            case "LOCK_REQUEST":
            case "LOCK_DENIED":
            case "LOCK_ALLOWED":
                return (containsLoginInfo ? null : missingFieldMsg);
            case "ACTIVITY_MESSAGE":
                return (containsLoginInfo && containsActivity ? null : missingFieldMsg);
            case "ACTIVITY_BROADCAST":
                return (containsActivity ? null : missingFieldMsg);
            case "SERVER_ANNOUNCE":
                return ((json.containsKey("id") && json.containsKey("load") && json.containsKey("hostname") &&
                        json.containsKey("port")) ? null : missingFieldMsg);
            case "INVALID_MESSAGE":
                return (json.containsKey("info") ? null : missingFieldMsg);
            case "LOGOUT":
                return null;
            // Unrecognised command -> Return false, triggering an INVALID_MESSAGE
            default:
                return "the received message contained an unrecognised command: " + command;
        }
    }

    /**
     * Ensures the types of the values in the JSON message fields are of the correct type.
     * @param json The message
     * @return null if no errors are detected, else a string describing which field has an incorrect type
     */
    public static String ensureTypesCorrect(JSONObject json) {

        Object o;

        if (json.containsKey("command")) {
            o = json.get("command");
            if (!(o instanceof String)) {
                return "Command field is not a string";
            }
        }
        if (json.containsKey("username")) {
            o = json.get("username");
            if (!(o instanceof String)) {
                return "username field is not a String";
            }
            else {
                if (!((String) o).equals("anonymous")) {
                    if (json.containsKey("secret")) {
                        o = json.get("secret");
                        if (!(o instanceof String)) {
                            return "secret field is not a String";
                        }
                    }
                }
            }
        }
        if (json.containsKey("id")) {
            o = json.get("id");
            if (!(o instanceof String)) {
                return "id field is not a String";
            }
        }
        if (json.containsKey("hostname")) {
            o = json.get("hostname");
            if (!(o instanceof String)) {
                return "hostname field is not a String";
            }
        }
        if (json.containsKey("port")) {
            o = json.get("port");
            if (!(o instanceof Number)) {
                return "port field is not a number";
            }
        }
        if (json.containsKey("load")) {
            o = json.get("load");
            if (!(o instanceof Number)) {
                return "load field is not a number";
            }
        }
        return null;
    }

    /**
     * Returns an error message if the sender is not authenticated / logged in / registered, otherwise null
     * @param json The JSON object received by the server
     * @param con The connection the JSON object was sent on
     * @return
     */
    public static String validSender(JSONObject json, Connection con) {

        // Extract necessary fields
        String command = json.get("command").toString();
        String username = null;
        String secret = null;

        if (json.containsKey("username")) {
            username = (String) json.get("username").toString();
            if (!username.equals("anonymous") && json.containsKey("secret")) {
                secret = (String) json.get("secret").toString();
            }
        }

        // Check that the server is authenticated OR client logged in, depending on connection type
        boolean serverAuthenticated = Control.getInstance().checkServerAuthenticated(con);
        boolean clientLoggedIn = Control.getInstance().checkClientLoggedIn(con);
        // Check client has logged in, and if username is NOT anonymous, username and secret match that stored locally
        boolean validClient = (clientLoggedIn && (username.equals("anonymous") || (username != null &&
                secret != null && Control.getInstance().secretIsCorrect(username, secret))));

        switch(command) {

            // Client messages that require a client is NOT logged in
            case "REGISTER":
                if (clientLoggedIn) {
                    return "Register message received from a client already logged in";
                }
                return null;

            // Client messages that require the user IS logged in, and if not anonymous, supplied username and secret
            // match that stored locally
            case "ACTIVITY_MESSAGE":
                if (!validClient) {
                    return "Message received from a client that has not logged in or that has an incorrect secret";
                }
                return null;

            // Server messages whereby sending server must be authenticated
            case "SERVER_ANNOUNCE":
            case "ACTIVITY_BROADCAST":
            case "LOCK_REQUEST":
            case "LOCK_DENIED":
            case "LOCK_ALLOWED":
                if (!serverAuthenticated) {
                   return "Message received from an unauthenticated server";
                }
                return null;

            // Is a LOGIN message - not authenticated/logged in yet so okay
            case "LOGIN":
                return null;

            // Server message whereby sending server must be unauthenticated
            case "AUTHENTICATE":
                if (serverAuthenticated) {
                    return "Server already authenticated, thus message is invalid. Now disconnecting.";
                }
                return null;

            default:
                return "the received message contained an unrecognised command: " + command;
        }
    }

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

    /** Creates a LOGIN_SUCCESS message to be sent back to a client.
     * @param username The username a client logged in with
     * @return Msg the message to be sent back to the client */
    public static String getLoginSuccessMsg(String username) {
        JSONObject msg = new JSONObject();

        msg.put("command", "LOGIN_SUCCESS");
        msg.put("info", "logged in as user " + username);

        return msg.toString();
    }

    /** Creates an INVALID_MESSAGE message to be sent back to the original sender.
     * @param errorLog The error message to be used
     * @return msg the message to be sent back to the sender */
    public static String getInvalidMessage(String errorLog) {
        JSONObject msg = new JSONObject();

        msg.put("command", "INVALID_MESSAGE");
        msg.put("info", errorLog);

        return msg.toString();
    }

    /** Creates an AUTHENTICATE message to be sent by a server to its parent server.
     * @param secret The secret a server is trying to authenticate with
     * @return msg the message to be sent to the parent server */
    public static String getAuthenticateMsg(String secret) {
        JSONObject msg = new JSONObject();
        msg.put("command", "AUTHENTICATE");
        msg.put("secret", secret);
        return msg.toString();
    }

    /** Creates an AUTHENTICATION_FAIL message to be sent back to a server.
     * @param secret The secret a server attempted to authenticate with
     * @return Msg the message to be sent back to the server */
    public static String getAuthenticationFailedMsg(String secret) {
        JSONObject msg = new JSONObject();

        msg.put("command", "AUTHENTICATION_FAIL");
        msg.put("info", "the supplied secret is incorrect: " + secret);

        return msg.toString();
    }

    /** Creates a LOGIN_FAILED message to be sent back to a client.
     * @param failureMessage The username a client logged in with
     * @return Msg the message to be sent back to the client */
    public static String getClientLoginFailedMsg(String failureMessage) {
        JSONObject msg = new JSONObject();

        msg.put("command", "LOGIN_FAILED");
        msg.put("info", failureMessage);

        return msg.toString();
    }


    /** Creates a REDIRECT message to be sent back to a client.
     * @param hostName The host name a client should connect to
     * @param portNum The port a client should connect to
     * @return Msg the message to be sent back to the client */
    public static String getRedirectMsg(String hostName, int portNum) {
        JSONObject msg = new JSONObject();

        msg.put("command", "REDIRECT");
        msg.put("hostname", hostName);
        msg.put("port", portNum);

        return msg.toString();
    }

    /** Creates a SERVER_ANNOUNCE message to be sent to all servers in the network.
     * @param id The sending server's id
     * @param load The number of client connections a server currently has
     * @param hostName The sending server's host name
     * @param portnum The sending server's port number
     * @return Msg the message to be sent to all servers on the network */
    public static String getServerAnnounceMsg(String id, int load, String hostName, int portnum) {
        JSONObject msg = new JSONObject();

        msg.put("command", "SERVER_ANNOUNCE");
        msg.put("id", id);
        msg.put("load", load);
        msg.put("hostname", hostName);
        msg.put("port", portnum);

        return msg.toString();
    }

    /** Creates an ACTIVITY_BROADCAST message to be sent across the network.
     * @param json The activity message
     * @return Msg the message to be sent across the network*/
    public static String getActivityBroadcastMsg(JSONObject json) {
        JSONObject msg = new JSONObject();

        msg.put("command", "ACTIVITY_BROADCAST");
        msg.put("activity", json);

        return msg.toString();
    }

    /** Creates an ACTIVITY_MESSAGE message to be sent across the network.
     * @param json The activity message
     * @return Msg the message to be sent across the network*/
    public static String getActivityMessage(JSONObject json) {
        JSONObject msg = new JSONObject();
        msg.put("command", "ACTIVITY_MESSAGE");
        msg.put("activity", json);
        return msg.toString();
    }

    /** Creates a REGISTER_SUCCESS message to be sent to a registering client.
     * @param username The username a client is attempting to register with
     * @return Msg the message to be sent back to the client */
    public static String getRegisterSuccessMsg(String username) {
        JSONObject msg = new JSONObject();

        msg.put("command", "REGISTER_SUCCESS");
        msg.put("info", "register success for " + username);

        return msg.toString();
    }

    /** Creates a REGISTER_FAILED message to be sent to a registering client.
     * @param username The username a client is attempting to register with
     * @return Msg the message to be sent back to the client */
    public static String getRegisterFailedMsg(String username) {
        JSONObject msg = new JSONObject();

        msg.put("command", "REGISTER_FAILED");
        msg.put("info", username + " is already registered with the system");

        return msg.toString();
    }

    /** Creates a LOCK_REQUEST message to be sent to all servers on the network.
     * @param username The username a client is attempting to register with
     * @param secret The secret a client is attempting to register with
     * @return Msg the message to be sent to all servers on the network */
    public static String getLockRequestMsg(String username, String secret) {
        JSONObject msg = new JSONObject();

        msg.put("command", "LOCK_REQUEST");
        msg.put("username", username);
        msg.put("secret", secret);

        return msg.toString();
    }

    /** Creates either a LOCK_ALLOWED or LOCK_DENIED message to be sent to all servers on the network.
     * @param lockType The type of message to be created
     * @param username The username a client is attempting to register with
     * @param secret The secret a client is attempting to register with
     * @return Msg the message to be sent to all servers on the network */
    public static String getLockResponseMg(String lockType, String username, String secret) {
        JSONObject msg = new JSONObject();

        msg.put("command", lockType);
        msg.put("username", username);
        msg.put("secret", secret);

        return msg.toString();
    }
}