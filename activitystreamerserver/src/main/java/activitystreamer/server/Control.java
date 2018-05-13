package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;

import org.json.simple.JSONObject;

/**
 * This class is responsible for coordinating the receival of messages on a network, having the server process the
 * messages and acting on each message. The class also maintains the server's local storage.
 * Control uses a Listener and Connection(s). Seems like server logic is implemented here.
 */
public class Control extends Thread {
    private static final Logger log = LogManager.getLogger();
    private static ArrayList<Connection> connections;
    private static ArrayList<Connection> serverConnections;
    private static HashMap<Connection, ConnectedClient> clientConnections;
    private static HashMap<String, String> clientRegistry;
    private static HashMap<String, ConnectedServer> serverInfo;
    private static boolean term = false;
    private static Listener listener;
    private static String serverId;
    private static ServerSessionHandler sessionHandler;

    protected static Control control = null;

    public static Control getInstance() {
        if (control == null) {
            control = new Control();
        }
        return control;
    }

    /**
     * Initialise all required data structures and components of a server
     */
    public Control() {

        // To store unauthenticated server connections & not yet logged in client connections
        connections = new ArrayList<Connection>();

        // To store connected Servers & Clients.
        serverConnections = new ArrayList<Connection>();
        clientConnections = new HashMap<Connection, ConnectedClient>();

        // To store information about all know servers in a system
        serverInfo = new HashMap<String, ConnectedServer>();

        // Store Username -> Password pairs, to check passwords in O(1)
        clientRegistry = new HashMap<String, String>();

        // Set server ID by randomly generating a string
        serverId = Settings.nextSecret();

        sessionHandler = new ServerSessionHandler();

        // start a listener - keeps listening until ...?
        try {
            listener = new Listener();
        }
        catch (IOException e1) {
            log.fatal("failed to startup a listening thread: " + e1);
            System.exit(-1);
        }
        // Initiate a connection with a remote server, if remote hostname is provided
        initiateConnection();
        start();
    }


    /**
     * Initiates an outgoing connection with another server, and authenticates itself with that server once the
     * connection has been established.
     */
    public void initiateConnection() {
        // Make a connection to another server if remote hostname is supplied
        if (Settings.getRemoteHostname() != null) {
            try {
                Connection con = outgoingConnection(new Socket(Settings.getRemoteHostname(), Settings.getRemotePort()));
                authenticate(con);
                log.info("connected to server on port number " + Settings.getRemotePort());
            }
            catch (IOException e) {
                log.error("failed to make connection to " + Settings.getRemoteHostname() + ":" + Settings.getRemotePort() + " :" + e);
                System.exit(-1);
            }
        }
    }

    /**
     * Processing incoming messages from a given connection.
     * @param con The connection a message was received on
     * @param msg The message sent by a client or server on the network
     * @return If the message was successfully processed
     */
    public synchronized boolean process(Connection con, String msg) {
        JSONObject json = MessageProcessor.toJson(msg);

        // If we couldn't parse the message, notify the sender and disconnect
        if (json.containsKey("status") && ((String) json.get("status")).equals("failure")) {
            return messageInvalid(con, "Incorrect Message. Json parse error.");
        }

        log.info("Received Message: " + msg);

        // Check that a message contains a valid command, and that it has the required fields
        String invalidMsgStructureMsg = MessageProcessor.hasValidCommandAndFields(json);
        if (invalidMsgStructureMsg != null) {
            return messageInvalid(con, invalidMsgStructureMsg);
        }

        // If the message is an INVALID_MESSAGE or LOGOUT message, close the connection.
        if (json.get("command").equals("INVALID_MESSAGE") || json.get("command").equals("LOGOUT")) {
            con.closeCon();
            return true;        // true because we want terminate = true;
        }

        // Check Authentication/Validation status - unless is an AUTHENTICATE MESSAGE or LOGIN message
         String invalidSender = MessageProcessor.validSender(json, con);
         if (invalidSender != null) {
             return messageInvalid(con, invalidSender);
         }

         // Process the message
        return sessionHandler.process(MessageProcessor.toJson(msg), con);
    }

    /**
     * A new incoming client connection has been established, and a reference is returned to it.
     * @param s The socket for the incoming connection to use
     * @return c The connection reference for a particular socket
     * @throws IOException
     */
    public synchronized Connection incomingConnection(Socket s) throws IOException {
        log.debug("incoming connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        // Add connection to the "holding" array until it has either logged in or authenticated
        connections.add(c);
        return c;
    }

    /**
     * A new outgoing connection has been established, and a reference is returned to it
     * @param s The socket for an outgoing connection to use
     * @return c The connection reference for a particular socket
     * @throws IOException
     */
    public synchronized Connection outgoingConnection(Socket s) throws IOException {
        log.debug("outgoing connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        // Add connections straight to server array, as parent server is already authenticated
        serverConnections.add(c);
        return c;
    }

    /**
     * Runs the server Control. Sends regular SERVER_ANNOUNCE messages to all servers on the network at a given time
     * interval.
     */
    @Override
    public void run() {
        log.info("using activity interval of " + Settings.getActivityInterval() + " milliseconds");
        while (!term) {
            // do something with 5 second intervals in between
            try {
                Thread.sleep(Settings.getActivityInterval());
            }
            catch (InterruptedException e) {
                log.info("received an interrupt, system is shutting down");
                break;
            }
            serverAnnounce();
        }
        log.info("closing " + connections.size() + " connections");
        // clean up
        for (Connection connection : connections) {
            connection.closeCon();
        }
        listener.setTerm(true);
    }


    /** Received an invalid message from some connection. Generate an invalid message response
     * and send to the source of the invalid message, then close the connection.
     * @param c The connection a message was received on
     * @param errorLog the error message to be included
     * @return Returns true if message successfully handled. **/
    public boolean messageInvalid(Connection c, String errorLog) {
        String msg = MessageProcessor.getInvalidMessage(errorLog);
        c.writeMsg(msg);
        closeConnection(c);
        return true;
    }

    /**
     * Lookups the connection for a particular client, given its username
     * @param username The username of the client we are searching for
     * @return The connection for that given username
     */
    private Connection getConnectionForClient(String username, String secret) {
        for (Connection con : clientConnections.keySet()) {
            ConnectedClient curr = clientConnections.get(con);
            if (curr.getUsername().equals(username) && curr.getSecret().equals(secret)) {
                return con;
            }
        }
        return null;
    }




    //
    // SERVER_ANNOUNCE MANAGEMENT
    //

    /** Create and sends a server announce message to all servers it is connected to.
     * If the server is the first server in the network (therefore the secret sever), then remote port and remote
     * hostname is null. Send message to all servers it has direct connection to. **/
    public void serverAnnounce() {

        int load = clientConnections.size();
        int port = Settings.getLocalPort();
        String hostname = Settings.getLocalHostname();

        String msg = MessageProcessor.getServerAnnounceMsg(serverId, load, hostname, port);
        serverBroadcast(msg);
    }

    /** SERVER_ANNOUNCE message received. Update information about that server, then forward to all other servers
     * given server is directly connected to.
     * @param id The sending server's ID
     * @param load The number of client connections the server has
     * @param hostname The host name of the server
     * @param port The port number of the server
     * */
    public void updateServerInfo(String id, int load, String hostname, int port) {

        // This is the first server broadcast we have received from this given server - initialise all fields and
        // set the load of the server
        if (!serverInfo.containsKey(id + hostname + Integer.toString(port))) {
            serverInfo.put(id + hostname + Integer.toString(port), new ConnectedServer(id, hostname, port));
        }
        // Skips to here if already have basic info stored about the server (have received activity message from them
        // previously. Update the load of that server.
        serverInfo.get(id + hostname + Integer.toString(port)).setLoad(load);
    }





    //
    // SERVER AUTHENTICATION
    //

    /** Needs to authenticates itself with server following outgoing connection being established.
     * Sends an AUTHENTICATE message to that server with its secret.
     * @param c The connection the authenticate message will be send on **/
    public void authenticate(Connection c) {
        String msg = MessageProcessor.getAuthenticateMsg(Settings.getSecret());
        c.writeMsg(msg);
    }

    /** Authenticates a new server from incoming connection
     * @param incomingSecret The secret supplied by the authenticating server
     * @param c The connection a server is trying to authenticate on **/
    public void authenticateIncomingSever(String incomingSecret, Connection c) {

        // Check if secret matches the secret of this server
        String secret = Settings.getSecret();
        if (!secret.equals(incomingSecret)) {
            serverAuthenticateFailed(c, incomingSecret);
        }
        else {
            // Server supplied correct secret, remove from generic "holding" connections array and add to server
            // connections array
            connections.remove(c);
            serverConnections.add(c);

        }
    }

    /** Called by ServerSessionHandler to determine if a given server is authenticated.
     * Checks server connections array list for the connection. If in this array list, server is authenticated and
     * returns true, otherwise not authenticated and returns falls.
     * @param c The connection we are checking for authentication**/
    public boolean checkServerAuthenticated(Connection c) {
        return serverConnections.contains(c);
    }

    /**
     * The server cannot be authenticated - send an authentication failed message and close the connection
     * @param con The connection to send the message on
     * @param secret The secret a server attempted to authenticate with
     */
    public void serverAuthenticateFailed(Connection con, String secret) {
        String msg = MessageProcessor.getAuthenticationFailedMsg(secret);
        con.writeMsg(msg);
        closeConnection(con);
    }




    //
    // USER LOGIN / AUTHENTICATION (User & Pass checking)
    //

    /** Checks if a client is logged in. Returns true if logged in, false otherwise.
     * @param c The connection we are checking **/
    public boolean checkClientLoggedIn(Connection c) {
        return clientConnections.containsKey(c);
    }

    /**
     * Checks to see if a supplied username and secret match that which is stored locally. Returns true if matches,
     * false otherwise.
     * @param username The supplied username
     * @param password The supplied secret to be checked against the given username
     * @return True if secret and password match what is on storage, false otherwise
     */
    public boolean secretIsCorrect(String username, String password) {
        // Retrieve password stored for user from local storage
        if (clientRegistry.containsKey(username)) {
            String storedPassword = clientRegistry.get(username);
            return storedPassword.equals(password);
        }
        return false;
    }

    /**
     * Client failed to login successfully - send a failure message back to the client
     * @param con The connection to send the message on
     * @param failureMessage The failure message to be sent
     */
    public void clientLoginFailed(Connection con, String failureMessage) {
        String msg = MessageProcessor.getClientLoginFailedMsg(failureMessage);
        con.writeMsg(msg);
        closeConnection(con);
    }

    /** Log in a client that is not anonymous, by checking username and password combination match that
     * stored locally in server. Remove from generic connections array, and add to client-specific connections
     * array.
     * @param c The connection a client is using
     * @param username The username supplied by the client
     * @param password The password supplied by the client */
    public void loginClient(Connection c, String username, String password) {

        boolean logged_in = false;
        String failure_message = null;

        // If the username & secret combination matches the known combination
        if (secretIsCorrect(username, password)) {

            // Is the connected client registering with this server?
            if (clientConnections.containsKey(c)) {
                ConnectedClient client = clientConnections.get(c);

                // Is the client registered?
                if (client.isRegistered()) {
                    client.setLoggedIn(true);
                    logged_in = true;
                }
                // If not registered yet, client cannot yet log in!
                else {
                    failure_message = "Client registration ongoing, cannot yet log in";
                }
            }
            else {
                // Send login success message, add to client connections hashmap and remove from generic connections
                // "holding" ArrayList
                clientConnections.put(c, new ConnectedClient(username, password));
                connections.remove(c);
                logged_in = true;
            }
        }
        else {
            failure_message = "attempted to login with an invalid username & secret combination";
        }
        // Send login success message (Registered & Correct combo) & check for redirection
        if (logged_in) {
            String msg = MessageProcessor.getLoginSuccessMsg(username);
            c.writeMsg(msg);

            // Check if client should be redirected to another server
            checkRedirectClient(c);

        }
        // username & secret either not stored locally or client registration is incomplete. Send login failed message.
        else {
            clientLoginFailed(c, failure_message);
        }
    }

    /** Log in a client that is anonymous, and therefore does not require a secret.
     * This client is not stored in the clientRegistry hash map as we never need to check
     * the username against the password. Instead just add connection to client connections hash map
     * @param c The connection a client is using
     * @param username The username supplied by the client **/
    public void loginAnonymousClient(Connection c, String username) {
        clientConnections.put(c, new ConnectedClient(username, null));
        connections.remove(c);
        String msg = MessageProcessor.getLoginSuccessMsg(username);
        c.writeMsg(msg);

        // Check if there is another server client should connect to, and send getRedirectMsg message if so
        checkRedirectClient(c);
    }

    /** Checks if the server knows of another server that has at least two less connections than it. If such a server
     * exists, sends a REDIRECT message with that server's hostname and port number.
     * This has been implemented to return the FIRST server that has two or less connections.
     * @param c The connection to send the message onn**/
    public void checkRedirectClient(Connection c) {

        int load = clientConnections.size();
        for (ConnectedServer server : serverInfo.values()) {
            if (server.getLoad() <= load - 2) {
                String msg = MessageProcessor.getRedirectMsg(server.getHostname(), server.getPort());
                c.writeMsg(msg);
                clientConnections.remove(c);
                break;
            }
        }
    }







    //
    // REGISTRATION MANAGEMENT
    //

    /**
     * Retrieves the known number of servers in the network, at the time of the method call.
     * @return An integer representing the number of known servers in the network.
     */
    private int numKnownServersInNetwork() {
        return serverInfo.size();
    }

    /** Stores the client connection for later reference. Sends a LOCK_REQUEST message and forwards this to all other
     * servers in the system
     * @param con The connection a client is using
     * @param username The username supplied by the client to register with
     * @param secret The secret supplied by the client to register with **/
    public void registerNewClient(Connection con, String username, String secret) {
        // Save client as a client connection
        int numKnownServers = numKnownServersInNetwork();
        clientConnections.put(con, new ConnectedClient(username, secret, numKnownServers));
        connections.remove(con);
        // No need to send lock request if no servers connected
        if (numKnownServers == 0) {
            registrationSuccess(username, secret, con);
        }
        // Create the LOCK_REQUEST MESSAGE and send to all connected servers
        else {
            String msg = MessageProcessor.getLockRequestMsg(username, secret);
            serverBroadcast(msg);
        }
    }

    /**
     * Client has selected a unique username, so registration has succeeded. Send REGISTRATION_SUCCESS message to
     * client
     * @param con The connection a client is using
     * @param username The username supplied by the client */
    public void registrationSuccess(String username, String secret, Connection con) {
        if (con == null) {
            con = getConnectionForClient(username, secret);
        }
        String msg = MessageProcessor.getRegisterSuccessMsg(username);
        con.writeMsg(msg);
    }

    /** The username already exists within the system. Send a REGISTER_FAILED message to the client and close the
     * connection. Reset the server so it is no longer the sender of the LOCK_REQUEST message. And clear the response
     * counter.
     * @param username The username a client attempted to register with */
    public void registrationFailed(String username, String secret, Connection con) {

        if (con == null) {
            con = getConnectionForClient(username, secret);
        }
        String msg = MessageProcessor.getRegisterFailedMsg(username);
        con.writeMsg(msg);
        closeConnection(con);
    }

    /** Register a new client by saving username and password locally
     * @param username The username a client is registering with
     * @param secret The secret a client is registering with **/
    public void registerUserSecret(String username, String secret) {
        clientRegistry.put(username, secret);
    }

    /** Checks all of the username's registered with the client to see if it already exists.
     * Returns true if username already registered, false otherwise.
     * @param username The username to be looked up
     * @return True if username already exists in local server storage, false otherwise **/
    public boolean usernameExists(String username) {
        return clientRegistry.containsKey(username);
    }

    /** Removes a username from the client registry
     * @param username The username to be removed. **/
    public void removeUser(String username) {
        clientRegistry.remove(username);
    }

    /**
     * Checks to see if a client has registered
     * @param username The username of the sending client
     * @param secret The corresponding secret for that client
     * @return True if has registered, false otherwise.
     */
    public boolean isRegistered(String username, String secret) {
        if (clientRegistry.containsKey(username) && clientRegistry.get(username).equals(secret)) {
            return true;
        }
        return false;
    }
    /**
     * If the Client with the same username and password is stored in this server's connections, it will tell it that
     * it has received a LOCK_ALLOWED message (setting that server as "registered" once all the required no. of
     * LOCK_ALLOWED messages have been received) and return true,
     *
     * @param username The username a client is attempting to register with
     * @param secret The corresponding secret
     * @return True if this server is the sender of the original LOCK_REQUEST message, false otherwise
     */
    public boolean updateIfSender(String username, String secret) {
        for (HashMap.Entry<Connection, ConnectedClient> client : clientConnections.entrySet()) {
            ConnectedClient clientInfo = client.getValue();
            if (clientInfo.isClient(username, secret)) {
                Connection clientConnection = client.getKey();
                clientInfo.receivedLockAllowed();
                registrationSuccess(username, secret, clientConnection);
                return true;
            }
        }
        return false;
    }

    /**
     * A given username does not already exist in the server storage. Tell sending server that the lock is allowed.
     * @param lockType The type of message to be sent
     * @param username The username attempting to be registered
     * @param secret The corresponding secret */
    public void broadcastLockMsg(String lockType, String username, String secret) {
        // Create the lock success message to be sent across the network with given username and secret
        String msg = MessageProcessor.getLockResponseMg(lockType, username, secret);
        // Send message to each server this server is connected to
        serverBroadcast(msg);
    }







    //
    // MESSAGE BROADCASTING
    //

    /** Sends an activity message to all of a server's connections (both clients and servers), except for the connection
     * it received the message from (to prevent duplicate messages).
     * @param c The connection to be excluded from the broadcast
     * @param msg The message to be sent across the network **/
    public void broadcastMessage(Connection c, String msg) {
        // Broadcast the message to all servers
        for (Connection curr: serverConnections) {
            if (curr != c) {
                curr.writeMsg(msg);
            }
        }
        // Broadcast the message to all clients
        for (Connection con : clientConnections.keySet()) {
            if (con != c) {
                con.writeMsg(msg);
            }
        }
    }

    /** Sends a message to all of the servers a given server has a direct connection to.
     * @param msg The message to be sent **/
    public void serverBroadcast(String msg) {
        for (Connection c: serverConnections) {
            c.writeMsg(msg);
        }
    }

    /** Sends a message to all of the servers a given server has a direct connection to, except the server it received
     * the message from.
     * @param c The connection that should NOT have the message sent to
     * @param msg The message to be sent **/
    public void forwardServerMsg(Connection c, String msg) {
        for (Connection con: serverConnections) {
            if (con != c) {
                con.writeMsg(msg);
            }
        }
    }






    //
    // CONNECTION CLOSURE
    //

    /** Initiate closure of a given connection and remove from the appropriate array
     * @param c The connection to be closed  **/
    public void closeConnection(Connection c) {
        c.closeCon();
        deleteClosedConnection(c);
    }

    /**
     * The connection has been closed by the other party.
     * Removes the connection from the appropriate data structure, depending on who the connection is with.
     * @param con The connection to be closed
     */
    public synchronized void deleteClosedConnection(Connection con) {

        if (serverConnections.contains(con)) {
            // Close connection to another server
            serverConnections.remove(con);
        }
        else if (clientConnections.containsKey(con)) {
            // Close connection to another client
            clientConnections.remove(con);
        }
        else {
            // Closing the connection to an unauthenticated server/client not logged in
            connections.remove(con);
        }
    }


    //
    // MISC
    //

    /**
     * Used to tell the server to terminate.
     * @param t If true, sets 'term' to true, communicating that the server should terminate, in run(). Otherwise
     *          sets to false and ensures the server keeps running (via run()).
     */
    public final void setTerm(boolean t) {
        term = t;
    }
}