package activitystreamer.client;

import activitystreamer.util.ClientCommand;
import activitystreamer.util.Settings;
import activitystreamer.util.Response;
import org.json.simple.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** This class handles all of the logic for processing a message received from a server. */
public class ClientSessionHandler {

    /** Handles the logic for registering and/or logging in a client.
     * If the client is anonymous, then proceeds straight to login. If the client has a username but no secret,
     * generate a new secret for that client and proceed with registering the client.
     * If the client has a username and secret, attempt to login that client. */
    public ClientSessionHandler() {

        // No need to register if the username is "anonymous"
        if (Settings.getUsername().equals("anonymous")) {
            login();
        }
        else  {
            // In accordance with discussion board, a secret is only allowed if already registered
            if (Settings.getSecret() == null) {
                Settings.setSecret(Settings.nextSecret());
                register();
            }
            else {
                login();
            }
        }
    }

    /**
     * Receives data from server via ClientConnection's run(), validates the data received, and if successful,
     * processes the command appropriately.
     *
     * @param data The data received from the currently connected server.
     * @return false if the connection shouldn't terminate, else true.
     */
    public boolean process(String data) {

        JSONObject json = ClientMessageHandler.toJson(data);

        boolean valid = ClientMessageHandler.validateMessage(json);
        boolean executed = false;

        // It seems like the JSON is valid and is recognised! Execute the appropriate command.
        if (valid) {
            ClientResponseCommand responseCommand = new ClientResponseCommand();
            executed = responseCommand.executeResponse(json);
        }
        // If not executed, terminate the connection
        return !executed;
    }

    /**
     * Retrieves a constructed login message from a ClientMessageHandler, prints the username and secret to the
     * terminal, and sends the message to the connected server.
     */
    public static void login() {
        JSONObject login_msg = ClientMessageHandler.getLoginMsg();
        ClientMessageHandler.printLoginInfo(login_msg);
        ConnectionManager.getInstanceClientConnection().sendMessage(login_msg);
    }

    /**
     * Attempts to register the username & password from Settings with the server.
     */
    public static void register() {
        JSONObject register_msg = ClientMessageHandler.getRegistrationMsg();
        ConnectionManager.getInstanceClientConnection().sendMessage(register_msg);
    }


    /** Closes the connection upon receiving a failure message from the server (which has closed the connection on its
     * end). Prints an error message to the GUI, which is displayed for 5 seconds before closing the GUI.
     * @param conMan the connection manager
     * @param typeOfFailure the failure message to be printed to the GUI */
    public static void closeSession(ConnectionManager conMan, String typeOfFailure) {
        try {
            conMan.showMsgToUser(typeOfFailure + " Closing in 5 seconds.");
            Thread.sleep(Settings.getActivityInterval());
        }
        catch (InterruptedException e) {
            System.out.println("Interrupted, failed showing message to user via GUI");
            e.printStackTrace();
        }
        ConnectionManager.getInstanceClientConnection().disconnect();
        System.exit(0);
    }

    /**
     * Defines different actions for each ClientCommand. This is the logic of the Client.
     */
    static class ClientResponseCommand extends Response {

        static {
            final Map<String, ClientCommand> responses = new HashMap<>();

            // If registration is REGISTER_FAILED then close connection
            responses.put("REGISTER_FAILED", new ClientCommand() {
                @Override
                public void execute(JSONObject json) {
                    ConnectionManager conMan = ConnectionManager.getInstance();
                    conMan.displayMsg(json, false);
                    closeSession(conMan, "Registration failed.");
                }
            });
            // If registration is REGISTER_SUCCESS then (1) attempt to login and (2) change the boolean registered
            responses.put("REGISTER_SUCCESS", new ClientCommand() {
                @Override
                public void execute(JSONObject json) {
                    ConnectionManager conMan = ConnectionManager.getInstance();
                    conMan.displayMsg(json, false);
                    login();
                }
            });
            // Keep track of the knowledge that the client is registered & logged in.
            responses.put("LOGIN_SUCCESS", new ClientCommand() {
                @Override
                public void execute(JSONObject json) {
                    ConnectionManager.getInstance().displayMsg(json, false);
                    ClientConnection.setLoggedIn(true);
                }
            });
            // Login was unsuccessful, connection has been closed by server
            responses.put("LOGIN_FAILED", new ClientCommand() {
                @Override
                public void execute(JSONObject json) {
                    ConnectionManager conMan = ConnectionManager.getInstance();
                    conMan.displayMsg(json, false);
                    ConnectionManager.getInstanceClientConnection().disconnect();
                    closeSession(conMan, "Login failed.");
                }
            });
            // Received an activity broadcast - display activity to screen
            responses.put("ACTIVITY_BROADCAST", new ClientCommand() {
                @Override
                public void execute(JSONObject json) {
                    ConnectionManager.getInstance().displayMsg(json, false);
                }
            });
            // Redirect message received - update port and host name and attempt connection to new server
            responses.put("REDIRECT", new ClientCommand() {
                @Override
                public void execute(JSONObject json) {
                    ConnectionManager conMan = ConnectionManager.getInstance();
                    conMan.displayMsg(json, false);
                    Settings.setRemoteHostname(json.get("hostname").toString());
                    Settings.setRemotePort(((Long) json.get("port")).intValue());
                    conMan.restartConnection();
                }
            });
            // Invalid message received - close connection
            responses.put("INVALID_MESSAGE", new ClientCommand() {
                @Override
                public void execute(JSONObject json) {
                    ConnectionManager conMan = ConnectionManager.getInstance();
                    conMan.displayMsg(json, false);
                    closeSession(conMan, "Received an invalid message notification.");
                }
            });
            // Activity message received - display the message to the GUI
            responses.put("ACTIVITY_MESSAGE", new ClientCommand() {
                @Override
                public void execute(JSONObject json) {
                    ConnectionManager.getInstance().displayMsg(json, true);
                }
            });
            CLIENT_RESPONSES = Collections.unmodifiableMap(responses);
        }
    }
}