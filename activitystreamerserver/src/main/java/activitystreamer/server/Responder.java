package activitystreamer.server;


/** Handles the creation of all messages a server may send across the network.
 * Inspects every message received by a server to ensure it is valid, and has been sent by an authenticated server or
 * logged in client. Then tells the server what to do with the message.  */

import activitystreamer.util.ServerCommand;
import activitystreamer.util.Response;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Responder {

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

            // FROM CLIENT
            /* Login message received. Has already been checked message is valid and client logged in. */
            responses.put("LOGIN", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {

                    // TODO: Register Login status with ClientRegistry

                    String username = (String) json.get("username");

                    if (username.equals("anonymous")) {
                        // Do not need to check secret against username as is anonymous - login client
                        SessionManager.getInstance().loginAnonymousClient(con, username);
                    }
                    else {
                        // Client logging in with username - check secret and username matches what is stored
                        String secret = (String) json.get("secret");
                        SessionManager.getInstance().loginClient(con, username, secret);
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
                    sessionManager.closeConnection(con);
                }
            });
            /* An activity message has been received from a client, and already checked to ensure it is valid, the client
             * has logged in and username and secret match that stored on server. Activity will be processed by server
             * so it can then be broadcast across the network (including back to the sending client, as per discussion
             * board post). **/
            responses.put("ACTIVITY_MESSAGE", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {

                    // `Process` the message
                    String user = json.get("username").toString();
                    JSONObject activityMessage = (JSONObject) json.get("activity");
                    activityMessage.put("authenticated_user", user);

                    SessionManager sessionManager = SessionManager.getInstance();
                    ClientRegistry clientRegistry = sessionManager.getClientRegistry();

                    // Retrieve the logged in users (known to the clientRegistry at this time)
                    ArrayList<String> loggedInUsers = clientRegistry.getLoggedInUsers();

                    // Add message and its expected recipients to ClientRegistry and retrieve the allocated token
                    Integer msgToken = clientRegistry.addMessageToRegistry(user, activityMessage, loggedInUsers);

                    // Add message token & recipients to ACTIVITY_BROADCAST message
                    String activityBroadcastMsg = MessageProcessor.getActivityBroadcastMsg(activityMessage,
                                                                                           loggedInUsers, msgToken);
                    // Send ACTIVITY_BROADCAST to other servers
                    sessionManager.serverBroadcast(activityBroadcastMsg);

                    // Retrieve client connections matching the usernames & passwords of loggedInUsers
                    HashMap<String, Connection> receiverConnections =
                            sessionManager.getClientConnections(clientRegistry.getClientCredentials(loggedInUsers));

                    // Send all msgs possible from this sender (all queued msgs first) to connected clients
                    JSONObject ackMessage = clientRegistry.messageFlush(receiverConnections, user);

                    // Send the ACKs to all servers!
                    sessionManager.serverBroadcast(ackMessage.toString());

                    // Send back an ACTIVITY_MESSAGE to the sender, so it can display it on its GUI
                    String processedActivityMsg = MessageProcessor.getActivityMessage(activityMessage);
                    con.writeMsg(processedActivityMsg);
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

            // FROM CLIENT & SERVER
            /* Invalid Message received from Client OR Server. Do not bother checking if the structure of the
             * INVALID_MESSAGE is itself valid, given the connection will be closed either way. **/
            responses.put("INVALID_MESSAGE", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {
                    SessionManager.getInstance().closeConnection(con);
                }
            });

            // FROM SERVER
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


            /* Received an activity broadcast message from a server. Forward message on to all other connections, except
             * to the sending server. **/
            responses.put("ACTIVITY_BROADCAST", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {

                    // TODO: 1. Send only if tokens are matching and msg is unsent,
                    // TODO: 2. Update ClientRegistry's ClientRecords.expected_tokens,
                    // TODO: 3. Send all waiting msgs if appropriate
                    // TODO: 4. Create and send ACK messages after sending to clients
                    // TODO: 5. Create Ability to process ACK messages and update ClientRegistry based on these ACKs.
                    SessionManager.getInstance().broadcastMessage(con, json.toString());

                }
            });
            /* Logout message received from client by server.
             * Do not need to perform any additional checks as command checked upon calling function,
             * and no other fields are required.
             * Tells server to close connection. **/
            responses.put("MSG_ACKS", new ServerCommand() {
                @Override
                public void execute(JSONObject json, Connection con) {
                    // TODO: Parse & Update Messages in ClientRegistry


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

                    // Update if we're the sender of the request, else forward LOCK_ALLOWED to all servers except sender
                    if (!sessionManager.updateIfSender(username, secret)) {
                        sessionManager.forwardServerMsg(con, json.toString());
                    }
                }
            });

            // Add responses to our unmodifiable final static hashmap!
            SERVER_RESPONSES = Collections.unmodifiableMap(responses);
        }
    }
}
