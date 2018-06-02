package activitystreamer.server;

import activitystreamer.util.Settings;
import com.google.gson.Gson;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.ArrayList;
import java.util.HashMap;

/** This class is responsible for generating all of the messages to be sent by the server across the network.
 * It also checks that each message is valid/non-corrupt, and came from an authenticated server or a client that
 * has logged in. Class also handles the conversion of a string to a JSON object. **/

public class MessageProcessor {

    private static Gson gson = null;
    private static JSONParser jsonParser = null;

    /**
     * Validates incoming messages (ensures they have the correct fields)
     * @param json The JSONObject to be validated as containing the correct fields.
     * @return Msg if the JSONObject does not contain a known command and the required fields for that command, otherwise
     * null.
     */
    public static String hasValidCommandAndFields(JSONObject json) {

        String typeErrorMsg = "Type Error! Some of the values in the json message have the incorrect type: ";

        if (!json.containsKey("command")) {
            return "the received message did not contain a command";
        }

        String typeIssue = ensureTypesCorrect(json);
        if (typeIssue != null) {
            return typeErrorMsg + typeIssue;
        }

        // Get the required information from the JSONObject
        boolean containsSecret = json.containsKey("secret");
        boolean containsLoginInfo = json.containsKey("username") &&
                (isAnonymous(json.get("username").toString()) || containsSecret);
        boolean containsActivity = json.containsKey("activity");
        boolean containsAMBroadcastInfo = (json.containsKey("recipients") && json.containsKey("token"));
        boolean isValidServerAuthMsg = false;
        if (json.containsKey("registry")) {
            Object registryObj = json.get("registry");

            if (registryObj instanceof JSONArray) {
                isValidServerAuthMsg = true;
            }
        }

        // Error message to be sent if JSONObject is missing required field
        String command = (String) json.get("command");
        String missingFieldMsg = "the received " + command + " was missing fields";

        switch (command) {
            // TODO: Check that this is correct
            case "ANON_CONFIRM":
            case "ANON_CHECK":
                return (json.containsKey("anon_record") ? null : missingFieldMsg);
            case "MSG_ACKS":
                return (json.containsKey("sender") && json.containsKey("messages") ? null : missingFieldMsg);

            case "GRANDPARENT_UPDATE":
                return (json.containsKey("new_grandparent") ? null : missingFieldMsg);
            case "SIBLING_UPDATE":
                return (json.containsKey("new_sibling") ? null : missingFieldMsg);

            // TODO: CONFIRM FIELDS OF AUTHENTICATION_SUCCESS
            case "AUTHENTICATION_SUCCESS":
                return (isValidServerAuthMsg && json.containsKey("id") && json.containsKey("hostname") &&
                        json.containsKey("port") ? null : missingFieldMsg);
            case "AUTHENTICATE":
                return (containsSecret && isValidServerAuthMsg ? null : missingFieldMsg);
            case "SERVER_ANNOUNCE":
                return ((json.containsKey("id") && json.containsKey("load") && json.containsKey("hostname") &&
                        json.containsKey("port") && isValidServerAuthMsg) ? null : missingFieldMsg);
            case "LOGIN":
            case "REGISTER":
            case "LOCK_REQUEST":
            case "LOCK_DENIED":
            case "LOCK_ALLOWED":
                return (containsLoginInfo ? null : missingFieldMsg);
            case "LOGIN_BROADCAST":
            case "LOGOUT_BROADCAST":
                return (containsLoginInfo && json.containsKey("token") ? null : missingFieldMsg);
            case "ACTIVITY_MESSAGE":
                return (containsLoginInfo && containsActivity ? null : missingFieldMsg);
            case "ACTIVITY_BROADCAST":
                return (containsActivity && containsLoginInfo && containsAMBroadcastInfo? null : missingFieldMsg);
            case "AUTHENTICATION_FAIL":
            case "INVALID_MESSAGE":
                return (json.containsKey("info") ? null : missingFieldMsg);
            case "SERVER_SHUTDOWN":                                                                                     // TODO: CONFIRM FIELDS OF SERVER_SHUTDOWN
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
                if (!(isAnonymous(o.toString()))) {
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
        if (json.containsKey("registry")) {
            o = json.get("registry");
            if (!(o instanceof JSONArray)) {
                return "the registry field is not a JSONArray";
            }
        }
        return null;
    }

    /**
     * Returns an error message if the sender is not authenticated / logged in / registered, otherwise null
     * @param json The JSON object received by the server
     * @param con The connection the JSON object was sent on
     * @return ...
     */
    public static String validSender(JSONObject json, Connection con) {

        // Extract necessary fields
        String command = json.get("command").toString();
        String username = null;
        String secret = null;

        if (json.containsKey("username")) {
            username = json.get("username").toString();
            if (!isAnonymous(username) && json.containsKey("secret")) {
                secret = json.get("secret").toString();
            }
        }

        SessionManager sessionManager = SessionManager.getInstance();
        ClientRegistry clientRegistry = sessionManager.getClientRegistry();

        // Check that the server is authenticated OR client logged in, depending on connection type
        boolean serverAuthenticated = sessionManager.checkServerAuthenticated(con);
        boolean clientLoggedIn = sessionManager.checkClientLoggedIn(con);

        // Check client has logged in, and if username is NOT anonymous, username and secret match that stored locally
        boolean validClient = (clientLoggedIn &&
                ((username != null && secret != null && clientRegistry.secretCorrect(username, secret)) ||
                        (username != null && isAnonymous(username))));

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
            case "GRANDPARENT_UPDATE":
            case "SIBLING_UPDATE":
            case "ANON_CONFIRM":
            case "ANON_CHECK":
            case "MSG_ACKS":
            case "AUTHENTICATION_SUCCESS":
            case "SERVER_ANNOUNCE":
            case "ACTIVITY_BROADCAST":
            case "LOCK_REQUEST":
            case "LOCK_DENIED":
            case "LOCK_ALLOWED":
            case "LOGIN_BROADCAST":
            case "LOGOUT_BROADCAST":
                if (!serverAuthenticated) {
                   return "Message received from an unauthenticated server";
                }
                return null;

            // Is a LOGIN message - not authenticated/logged in yet so okay
            case "LOGIN":
                return null;

            // Can be sent from an unauthenticated server, or an authenticated server
            case "SERVER_SHUTDOWN":
                return null;

            // Server messages whereby sending server must be unauthenticated
            case "AUTHENTICATE_SUCCESS":
            case "AUTHENTICATE":
                if (serverAuthenticated) {
                    return "Server already authenticated, thus message is invalid. Now disconnecting.";
                }
                return null;

            default:
                return "the received message contained an unrecognised command: " + command;
        }
    }

    public static Gson getGson() {
        if (gson == null) {
            gson = new Gson();
        }
        return gson;
    }

    public static JSONParser getJsonParser() {
        if (jsonParser == null) {
            jsonParser = new JSONParser();
        }
        return jsonParser;
    }

    /**
     * Converts a String to a JSONObject. Returns a specific JSON object if there's a parsing error.
     * @param data The string, hopefully formatted as a JSON object, to be parsed.
     * @return A JSONObject containing the data included in the string, or a specific error response.
     */
    public synchronized static JSONObject toJson(String data, boolean dataIsArray, String keyString) {

        JSONObject json;

        try {
            if (dataIsArray) {
                JSONArray jsonData = (JSONArray) getJsonParser().parse(data);
                json = new JSONObject();
                json.put(keyString, jsonData);
                return json;
            }
            // System.out.println("If Error, was parsing: " + data);
            return (JSONObject) getJsonParser().parse(data);
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
        if (isAnonymous(username)) {
            msg.put("info", "logged in as an anonymous user.");
        }
        else {
            msg.put("info", "logged in as user " + username);
        }
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

    /** Creates an AUTHENTICATE message to be sent by a server to its parent server.
     * @param secret The secret a server is trying to authenticate with
     * @param clientRecordsJson The ClientRegistry as a JSONArray in a JSON object -> {"registry" : JSONArray[...]}
     * @return msg the message to be sent to the parent server */
    public static String getAuthenticateMsg(String secret, JSONObject clientRecordsJson, String id, String hostname, Integer port) {
        JSONObject msg = new JSONObject();
        msg.put("command", "AUTHENTICATE");
        msg.put("secret", secret);
        msg.put("id", id);
        msg.put("hostname", hostname);
        msg.put("port", port);
        // Adds all mappings in clientRecordsJson to msg (only "registry" is mapped to a value)
        msg.putAll(clientRecordsJson);
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

    public static String getGrandparentUpdateMsg(JSONObject grandparentRecordJson) {
        JSONObject msg = new JSONObject();
        msg.put("command", "GRANDPARENT_UPDATE");
        msg.put("new_grandparent", grandparentRecordJson);
        return msg.toString();
    }

    public static String getSiblingUpdateMsg(JSONObject siblingRecordJson) {
        JSONObject msg = new JSONObject();
        msg.put("command", "SIBLING_UPDATE");
        msg.put("new_sibling", siblingRecordJson);
        return msg.toString();
    }

    public static String getSiblingCrashed(JSONObject siblingCrashed) {
        JSONObject msg = new JSONObject();
        msg.put("command", "SIBLING_CRASHED");
        msg.putAll(siblingCrashed);
        return msg.toString();
    }

    public static String getAuthenticationSuccessMsg(JSONObject clientRecordsJson, JSONObject serverRegistryJson,
                                                     String hostname, int port, String id, JSONObject grandparent,
                                                     JSONObject siblingList) {
        JSONObject msg = new JSONObject();
        msg.put("command", "AUTHENTICATION_SUCCESS");
        msg.put("hostname", hostname);
        msg.put("port", port);
        msg.put("id", id);

        if (serverRegistryJson != null)
        {
            msg.putAll(serverRegistryJson);
        }
        if (grandparent != null) {
            msg.putAll(grandparent);
        }
        if (siblingList != null) {
            msg.putAll(siblingList);
        }

        // Adds all mappings in clientRecordsJson to msg (only "registry" is mapped to a value)
        msg.putAll(clientRecordsJson);
        return msg.toString();
    }


    /** Creates a SERVER_ANNOUNCE message to be sent to all servers in the network.
     * @param id The sending server's id
     * @param load The number of client connections a server currently has
     * @param hostName The sending server's host name
     * @param portNum The sending server's port number
     * @param clientRecordsJson The ClientRegistry as a JSONArray in a JSON object -> {"registry" : JSONArray[...]}
     * @return Msg the message to be sent to all servers on the network */
    public static String getServerAnnounceMsg(String id, int load, String hostName, int portNum,
                                              JSONObject clientRecordsJson) {
        JSONObject msg = new JSONObject();
        msg.put("command", "SERVER_ANNOUNCE");
        msg.put("id", id);
        msg.put("load", load);
        msg.put("hostname", hostName);
        msg.put("port", portNum);
        msg.putAll(clientRecordsJson);
        return msg.toString();
    }

    public static String getLoginBroadcast(String user, String secret, Integer token) {
        JSONObject msg = new JSONObject();
        msg.put("command", "LOGIN_BROADCAST");
        msg.put("username", user);
        msg.put("secret", secret);
        msg.put("token", token);
        return msg.toString();
    }

    public static String getLogoutBroadcast(String user, String secret, Integer token) {
        JSONObject msg = new JSONObject();
        msg.put("command", "LOGOUT_BROADCAST");
        msg.put("username", user);
        msg.put("secret", secret);
        msg.put("token", token);
        return msg.toString();
    }

    public static String getAnonLogoutBroadcast(String user, String secret) {
        JSONObject msg = new JSONObject();
        msg.put("command", "ANON_LOGOUT_BROADCAST");
        msg.put("username", user);
        msg.put("secret", secret);
        return msg.toString();
    }

    public static String getAnonConfirm(JSONObject anonClientRecord) {
        JSONObject msg = new JSONObject();
        msg.put("command", "ANON_CONFIRM");
        msg.put("anon_record", anonClientRecord);
        return msg.toString();
    }

    public static String getAnonCheck(JSONObject anonClientRecord) {
        JSONObject msg = new JSONObject();
        msg.put("command", "ANON_CHECK");
        msg.put("anon_record", anonClientRecord);
        return msg.toString();
    }

    /** Creates an ACTIVITY_BROADCAST message to be sent across the network.
     * @param json The activity message
     * @return Msg the message to be sent across the network*/
    public static String getActivityBroadcastMsg(JSONObject json, ArrayList<String> loggedInUsers, Integer msgToken) {

        // Add all the Activity_Message fields and values (command, username, secret, activity)
        JSONObject msg = new JSONObject();
        msg.putAll(json);

        // Rename the command
        msg.put("command", "ACTIVITY_BROADCAST");

        // Add the token and the recipients
        msg.put("token", msgToken);
        JSONObject recipientsJson = toJson(getGson().toJson(loggedInUsers), true, "recipients");
        msg.putAll(recipientsJson);
        System.out.println("MADE ACTIVITY_BROADCAST message: " + msg.toString());
        return msg.toString();
    }

    public static JSONObject processActivityMessage(JSONObject activityMsg, String user, String secret) {
        String command = activityMsg.get("command").toString();
        JSONObject activityMessage = (JSONObject) activityMsg.get("activity");

        if (isAnonymous(user)) {
            activityMessage.put("authenticated_user", "anonymous");
        }
        else {
            activityMessage.put("authenticated_user", user);
        }
        // Create new processed message
        JSONObject processedMsg = new JSONObject();
        processedMsg.put("username", user);
        processedMsg.put("secret", secret);
        processedMsg.put("command", command);
        processedMsg.put("activity", activityMessage);

        return processedMsg;
    }

    public static HashMap<Integer, ArrayList<String>> acksToHashMap(Object msgAcksObj) {
        HashMap<Integer, ArrayList<String>> ackMap = new HashMap<Integer, ArrayList<String>>();
        JSONObject msgAcksJson = (JSONObject) msgAcksObj;

        // Add each list of tokens and receivers to the HashMap
        msgAcksJson.forEach((tokenObj, receivedListObj) -> {
            ArrayList<String> msgs = new ArrayList<String>();
            Integer token = Integer.parseInt(tokenObj.toString());
            JSONArray receivedList = (JSONArray) receivedListObj;
            receivedList.forEach((receiver) -> msgs.add(receiver.toString()));
            ackMap.put(token, msgs);
        });
        if (!ackMap.isEmpty()) {
            return ackMap;
        }
        return null;
    }

    public static JSONObject getStartAckMsg(String sender) {
        JSONObject ackMessage = new JSONObject();
        ackMessage.put("command", "MSG_ACKS");
        ackMessage.put("sender", sender);
        return ackMessage;
    }

    public static JSONObject serverToClientJson(JSONObject serverJsonMessage) {
        JSONObject clientJsonMsg = new JSONObject();
        clientJsonMsg.put("command", serverJsonMessage.get("command"));
        clientJsonMsg.put("activity", serverJsonMessage.get("activity"));
        clientJsonMsg.put("username", serverJsonMessage.get("username"));
        return clientJsonMsg;
    }

    public static JSONObject cleanClientMessage(JSONObject json) {
        JSONObject cleanedMessage = new JSONObject();
        cleanedMessage.put("command", "ACTIVITY_BROADCAST");
        cleanedMessage.put("activity", json.get("activity"));
        return cleanedMessage;
    }

    public static JSONObject cleanActivityMessage(JSONObject json) {
        JSONObject cleanedActMsg = new JSONObject();
        cleanedActMsg.put("command", "ACTIVITY_MESSAGE");
        cleanedActMsg.put("activity", json.get("activity"));
        return cleanedActMsg;
    }

    public static String getShutdownMessage(String thisServerId) {
        JSONObject shutdownMessage = new JSONObject();

        shutdownMessage.put("command", "SERVER_SHUTDOWN");
        shutdownMessage.put("id", thisServerId);
        return shutdownMessage.toString();
    }

    public static boolean isAnonymous(String username) {
        return (username.length() >= 9 && username.substring(0, 9).equals("anonymous"));

    }

}
