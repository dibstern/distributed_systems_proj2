package activitystreamer.server;


/** Handles the creation of all messages a server may send across the network.
 * Inspects every message received by a server to ensure it is valid, and has been sent by an authenticated server or
 * logged in client. Then tells the server what to do with the message.  */

import activitystreamer.util.ServerCommand;
import activitystreamer.util.Response;
import activitystreamer.util.Settings;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Responder {

    private static final boolean TESTING_DELAY = true;

    /**
     * @param json The JSON object received from the client
     * @param con The connection the object was received from
     */
    public boolean process(JSONObject json, Connection con) {
        ServerResponseCommand responseCommand = new ServerResponseCommand();
        return responseCommand.executeResponse(json, con);
    }

    /**
     * Defines different actions for each ServerCommand. This is the logic of the Server.
     */
    static class ServerResponseCommand extends Response {

        static {
            final Map<String, ServerCommand> responses = new HashMap<>();

            // ------------------------- FROM CLIENT -------------------------
            /* Login message received. Has already been checked message is valid and client logged in. */
            responses.put("LOGIN", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {

                    SessionManager sessionManager = SessionManager.getInstance();
                    String user = (String) json.get("username");
                    String username = user;
                    String secret;
                    Integer token;

                    if (user.equals("anonymous")) {
                        // Do not need to check secret against username as is anonymous - login client
                        username += "-" + Settings.nextSecret();
                        secret = Settings.nextSecret();

                        // Check if there is another server client should connect to, send getRedirectMsg message if so
                        boolean redirected = sessionManager.checkRedirectClient(con, username, secret, true);

                        // If we didn't redirect, then log this client in!
                        if (!redirected) {
                            // Add connection to clientConnections, create ConnectedClient, Add to ClientRegistry
                            token = sessionManager.loginAnonymousClient(con, username, secret);

                            // If login succeeded, broadcast LOGIN_BROADCAST message
                            if (!token.equals(Integer.MIN_VALUE)) {
                                sessionManager.serverBroadcast(MessageProcessor.getLoginBroadcast(username, secret, token));
                            }
                        }
                    }
                    else {
                        // Client logging in with username - check secret and username matches what is stored
                        secret = (String) json.get("secret");
                        token = sessionManager.loginClient(con, user, secret);
                        if (!token.equals(Integer.MIN_VALUE)) {
                            sessionManager.serverBroadcast(MessageProcessor.getLoginBroadcast(user, secret, token));
                        }
                        // Check if client should be redirected to another server
                        boolean redirected = sessionManager.checkRedirectClient(con, user, secret, false);

                        // If not redirected, send the user all of the messages that are waiting for them
                        if (!redirected) {
                            ArrayList<JSONObject> ackMsgs = sessionManager.getClientRegistry().messageFlush(con, user);

                            // Send the ACKs to all servers!
                            if (!ackMsgs.isEmpty()) {
                                ackMsgs.forEach((ackMessage) -> {
                                    if (ackMessage != null) {
                                        sessionManager.serverBroadcast(ackMessage.toString());
                                    }
                                });
                            }
                        }
                    }
                }
            });
            /* Logout message received from client by server.
             * Do not need to perform any additional checks as command checked upon calling function,
             * and no other fields are required.
             * Tells server to close connection. **/
            responses.put("LOGOUT", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {
                    SessionManager sessionManager = SessionManager.getInstance();
                    String closeConnectionContext = "Close Connection Context: Received LOGOUT (in Responder)";

                    ClientRegistry clientRegistry = sessionManager.getClientRegistry();
                    ConnectedClient client = sessionManager.getConnectedClient(con);
                    String username = client.getUsername();
                    String secret = client.getSecret();
                    String logoutContext = closeConnectionContext + ". Context: received LOGOUT request from " + username;

                    if (MessageProcessor.isAnonymous(username)) {

                        // Client was an anonymous user - remove record from ClientRegistry
                        clientRegistry.removeUser(username);
                        String anonLogoutBroadcastMsg = MessageProcessor.getAnonLogoutBroadcast(username, secret);
                        sessionManager.serverBroadcast(anonLogoutBroadcastMsg);
                    }
                    else {
                        Integer token = clientRegistry.logoutUser(username, secret, logoutContext, Integer.MIN_VALUE);
                        if (!token.equals(Integer.MIN_VALUE)) {
                            String logoutBroadcastMsg = MessageProcessor.getLogoutBroadcast(username, secret, token);
                            sessionManager.serverBroadcast(logoutBroadcastMsg);
                        }
                    }
                    // Close the connection for that given client
                    sessionManager.closeConnection(con, logoutContext);
                }
            });
            /* An activity message has been received from a client, and already checked to ensure it is valid, the client
             * has logged in and username and secret match that stored on server. Activity will be processed by server
             * so it can then be broadcast across the network (including back to the sending client, as per discussion
             * board post). **/
            responses.put("ACTIVITY_MESSAGE", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {

                    SessionManager sessionManager = SessionManager.getInstance();
                    ClientRegistry clientRegistry = sessionManager.getClientRegistry();

                    // Initial Setup
                    String user = json.get("username").toString();

                    if (MessageProcessor.isAnonymous(user)) {
                        ConnectedClient conClient = sessionManager.getConnectedClient(con);
                        user = conClient.getUsername();
                    }
                    
                    // `Process` the message
                    JSONObject clientMessage = MessageProcessor.processActivityMessage(json);

                    // Retrieve the logged in users (known to the clientRegistry at this time)
                    ArrayList<String> loggedInUsers = clientRegistry.getLoggedInUsers();
                    loggedInUsers.remove(user);     // Remove the sender

                    if (TESTING_DELAY) {
                        sessionManager.delayThread(3000);
                    }

                    // Add message and its expected recipients to ClientRegistry and retrieve the allocated token
                    Integer msgToken = clientRegistry.addMsgToRegistry(user, clientMessage, loggedInUsers);

                    // Add message token & recipients to ACTIVITY_BROADCAST message
                    String activityBroadcastMsg = MessageProcessor.getActivityBroadcastMsg(clientMessage, loggedInUsers,
                                                                                           msgToken);
                    // Send ACTIVITY_BROADCAST to other servers
                    sessionManager.serverBroadcast(activityBroadcastMsg);

                    // Retrieve client connections matching the usernames & passwords of loggedInUsers
                    HashMap<String, Connection> receiverConnections =
                            sessionManager.getClientConnections(clientRegistry.getClientCredentials(loggedInUsers));

                    // Send all msgs possible from this sender (all queued msgs first) to connected clients
                    JSONObject ackMessage = clientRegistry.messageFlush(receiverConnections, user);

                    // Send the ACKs to all servers!
                    if (ackMessage != null) {
                        sessionManager.serverBroadcast(ackMessage.toString());
                    }

                    // Send back an ACTIVITY_MESSAGE to the sender, so it can display it on its GUI
                    con.writeMsg(clientMessage.toString());
                }
            });
            /* Register message received by server from a client. Client wants to register username and password with
             * the server. Check if username already stored on this server, request locks, and determine if registration
             * has failed or succeeded depending on responses. **/
            responses.put("REGISTER", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {

                    String username = (String) json.get("username");
                    String secret = (String) json.get("secret");

                    // If server already knows of username then the registration fails
                    SessionManager sessionManager = SessionManager.getInstance();
                    if (sessionManager.getClientRegistry().userExists(username)) {
                        sessionManager.registrationFailed(username, secret, con);
                    }
                    // Username not known to this server - send out a lock request to all servers connected to
                    // and add username and secret to it's database
                    else {
                        sessionManager.getClientRegistry().addFreshClient(username, secret);
                        sessionManager.registerNewClient(con, username, secret);
                    }
                }
            });

            // ------------------------- FROM CLIENT & SERVER -------------------------
            /* Invalid Message received from Client OR Server. */
            responses.put("INVALID_MESSAGE", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {
                    String closeConnectionContext = "Close Connection Context: Received INVALID_MESSAGE (in Responder)";

                    SessionManager sessionManager = SessionManager.getInstance();
                    String logoutContext = closeConnectionContext + ". Logging out user that sent it.";
                    sessionManager.logoutClient(con, logoutContext);
                    sessionManager.closeConnection(con, closeConnectionContext);
                }
            });

            // ------------------------- FROM SERVER -------------------------
            /* Server has received an authenticate message from another server. Authenticate the server.
             * This is the one server message we do not check that sending server is authenticated first. **/
            responses.put("AUTHENTICATE", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {
                    // Authenticate the server
                    SessionManager.getInstance().authenticateIncomingSever((String) json.get("secret"), con);
                    JSONArray registry = (JSONArray) json.get("registry");
                    SessionManager.getInstance().getClientRegistry().updateRecords(registry);
                }
            });
            responses.put("AUTHENTICATION_SUCCESS", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {
                    JSONArray registry = (JSONArray) json.get("registry");
                    SessionManager.getInstance().getClientRegistry().updateRecords(registry);
                }
            });
            responses.put("ANON_CONFIRM", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {

                    // Add the ClientRecord to server's client Registry, and forward through network
                    JSONObject anonRecordTmp = (JSONObject) json.get("anon_record");
                    ClientRecord newAnonRecord = new ClientRecord(anonRecordTmp);
                    String username = newAnonRecord.getUsername();
                    SessionManager sessionManager = SessionManager.getInstance();

                    // Add the record to the registry and forward on the anon_confirm!
                    sessionManager.getClientRegistry().addRecord(username, newAnonRecord);
                    SessionManager.getInstance().forwardServerMsg(con, json.toString());
                }
            });
            responses.put("ANON_CHECK", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {

                    // Add the ClientRecord to server's client Registry, and forward through network
                    JSONObject anonRecordTmp = (JSONObject) json.get("anon_record");
                    ClientRecord newAnonRecord = new ClientRecord(anonRecordTmp);
                    SessionManager sessionManager = SessionManager.getInstance();
                    String username = newAnonRecord.getUsername();
                    String secret = newAnonRecord.getSecret();

                    // This server has a direct connection w/ the anon client. Broadcast ANON_CONFIRM msg.
                    if (sessionManager.clientLoggedInLocally(username, secret)) {
                        String msg = MessageProcessor.getAnonConfirm(anonRecordTmp);
                        sessionManager.serverBroadcast(msg);
                    }
                    // Not connected to anon client. Remove record from registry if it exists & forward ANON_CHECK.
                    else {
                        sessionManager.getClientRegistry().removeUser(username);
                        sessionManager.forwardServerMsg(con, json.toString());
                    }
                }
            });
            /* Received an activity broadcast message from a server. Forward message on to all other connections, except
             * to the sending server. **/
            responses.put("ACTIVITY_BROADCAST", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {

                    System.out.println("Received ACTIVITY_BROADCAST: " + json.toString());

                    // Forward message onto all other servers
                    SessionManager sessionManager = SessionManager.getInstance();
                    sessionManager.forwardServerMsg(con, json.toString());

                    // Add message to ClientRegistry
                    ClientRegistry clientRegistry = sessionManager.getClientRegistry();
                    String sender = json.get("username").toString();
                    Message received_message = new Message(json);
                    clientRegistry.addMessageToRegistry(received_message, sender);

                    ArrayList<String> remaining_recipients = received_message.getRemainingRecipients();

                    // Retrieve client connections matching the usernames & passwords of loggedInUsers
                    HashMap<String, Connection> receiverConnections = sessionManager.getClientConnections(
                            clientRegistry.getClientCredentials(remaining_recipients));

                    // Send all waiting messages, if appropriate
                    JSONObject ackMsg = clientRegistry.messageFlush(receiverConnections, sender);
                    if (ackMsg != null) {
                        sessionManager.serverBroadcast(ackMsg.toString());
                    }
                }
            });
            /* ... */
            responses.put("MSG_ACKS", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {
                    String sender = json.get("sender").toString();
                    JSONObject messageAcks = (JSONObject) json.get("messages");

                    // Parse the JSON to create a HashMap of Message ACKs and register them in the ClientRegistry
                    HashMap<Integer, ArrayList<String>> ackMap = MessageProcessor.acksToHashMap(messageAcks);
                    if (ackMap != null) {
                        SessionManager.getInstance().getClientRegistry().registerAcks(ackMap, sender);
                    }
                }
            });
            /* ... */
            responses.put("LOGIN_BROADCAST", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {
                    String user = json.get("username").toString();
                    String secret = json.get("secret").toString();

                    Integer loginRequestToken = ((Long) json.get("token")).intValue();
                    String loginContext = "Context: receiving LOGIN_BROADCAST (in Responder)";

                    ClientRegistry clientRegistry = SessionManager.getInstance().getClientRegistry();
                    clientRegistry.loginUser(user, secret, loginContext, loginRequestToken);
                }
            });
            /* ... */
            responses.put("LOGOUT_BROADCAST", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {
                    String user = json.get("username").toString();
                    String secret = json.get("secret").toString();
                    String loginContext = "Context: Receiving LOGOUT_BROADCAST (in Responder)";
                    Integer token = ((Long) json.get("token")).intValue();
                    SessionManager sessionManager = SessionManager.getInstance();
                    ClientRegistry clientRegistry = sessionManager.getClientRegistry();
                    Integer logoutToken = clientRegistry.logoutUser(user, secret, loginContext, token);
                    if (!logoutToken.equals(Integer.MIN_VALUE)) {
                        sessionManager.forwardServerMsg(con, json.toString());
                    }

                }
            });
            /* ... */
            responses.put("ANON_LOGOUT_BROADCAST", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {
                    String user = json.get("username").toString();
                    SessionManager sessionManager = SessionManager.getInstance();
                    sessionManager.getClientRegistry().removeUser(user);
                    sessionManager.forwardServerMsg(con, json.toString());
                }
            });
            /* Server announce message received from another server. Update information about this server, then forward
             * message on to all server connections. **/
            responses.put("SERVER_ANNOUNCE", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {

                    // Store message fields
                    String id = (String) json.get("id");
                    int load = ((Long) json.get("load")).intValue();
                    String hostname = (String) json.get("hostname");
                    int port = ((Long) json.get("port")).intValue();
                    JSONArray newClientRegistry = (JSONArray) json.get("registry");

                    // Update this server's information about the given server
                    SessionManager sessionManager = SessionManager.getInstance();
                    sessionManager.updateServerInfo(id, load, hostname, port);
                    sessionManager.getClientRegistry().updateRecords(newClientRegistry);

                    // Forward to all other servers that this server is connected to
                    sessionManager.forwardServerMsg(con, json.toString());
                }
            });
            /* A server on the network is trying to register a new user. Check if username exists on this server, and
             * send appropriate message back to that server. Forward the LOCK_REQUEST message to all other servers. */
            responses.put("LOCK_REQUEST", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {
                    String username = (String) json.get("username");
                    String secret = (String) json.get("secret");

                    SessionManager sessionManager = SessionManager.getInstance();

                    // Firstly, if username exists then broadcast a LOCK_DENIED message // TODO: Aaron said this on LMS
                    if (sessionManager.getClientRegistry().userExists(username)) {
                        String msg = MessageProcessor.getLockResponseMg("LOCK_DENIED", username, secret);
                        sessionManager.serverBroadcast(msg);
                    }
                    // Otherwise, broadcast Lock request and send LOCK_ALLOWED
                    else {
                        // Forward the LOCK_REQUEST message on to all other server connections
                        sessionManager.forwardServerMsg(con, json.toString());

                        // Add to username registry and send LOCK_ALLOWED message
                        sessionManager.getClientRegistry().addFreshClient(username, secret);
                        String msg = MessageProcessor.getLockResponseMg("LOCK_ALLOWED", username, secret);
                        sessionManager.serverBroadcast(msg);
                    }
                }
            });
            /* Another server has already registered a client with a particular username. Remove the given username
             * and secret from our own client registry, and forward the message through the network.
             */
            responses.put("LOCK_DENIED", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {
                    String username = (String) json.get("username");
                    String secret = (String) json.get("secret");

                    SessionManager sessionManager = SessionManager.getInstance();

                    // If the user/secret combination is in our registry, remove the combo from our local storage
                    if (sessionManager.getClientRegistry().secretCorrect(username, secret)) {
                        sessionManager.getClientRegistry().removeUser(username);
                    }
                    // If it's one of our connections, send REGISTRATION_FAILED message
                    sessionManager.registrationFailed(username, secret, con);

                    // Forward the LOCK_DENIED message to all other connections
                    sessionManager.broadcastMessage(con, json.toString());
                }
            });

            /* Another server does not already know about the username. If it's this server's connection, record it on
               the ConnectedServer's data. Otherwise, forward message on through the network to all other servers this\
               server is connected to.
             */
            responses.put("LOCK_ALLOWED", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {
                    String username = (String) json.get("username");
                    String secret = (String) json.get("secret");

                    SessionManager sessionManager = SessionManager.getInstance();

                    // If we're not the sender, then forward.
                    ConnectedClient client = sessionManager.getClientIfConnected(username, secret);

                    // We're connected to the client, so we're the sender. Update the ConnectedClient if it's not Anon!
                    if (client != null) {
                        if (!MessageProcessor.isAnonymous(username)) {
                            sessionManager.updateAndCompleteRegistration(client, username, secret);
                        }
                    }
                    // We're not connected to the client so we're not the sender; forward the LOCK_ALLOWED message!
                    else {
                        sessionManager.forwardServerMsg(con, json.toString());
                    }
                }
            });

            // Add responses to our unmodifiable final static hashmap!
            SERVER_RESPONSES = Collections.unmodifiableMap(responses);
        }
    }
}
