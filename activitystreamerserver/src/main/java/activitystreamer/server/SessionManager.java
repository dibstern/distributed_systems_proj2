package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;

import org.json.simple.JSONObject;

/**
 * This class is responsible for coordinating the receival of messages on a network, having the server process the
 * messages and acting on each message. The class also maintains the server's local storage.
 * SessionManager uses a Listener and Connection(s). Seems like server logic is implemented here.
 */
public class SessionManager extends Thread {
    private static final Logger log = LogManager.getLogger();
    private static ArrayList<Connection> connections;
    private static HashMap<Connection, ConnectedClient> clientConnections;
    private static ServerRegistry serverRegistry;
    private static boolean term = false;
    private static Listener listener;
    private static String serverId;
    private static Responder responder;
    private static ClientRegistry clientRegistry;
    private final static int REDIRECT_DELAY = 2000; // milliseconds (= 2 seconds)
    private static ConcurrentLinkedQueue<String> deliveries;
    private static boolean reconnecting;

    protected static SessionManager sessionManager = null;

    public static SessionManager getInstance() {
        if (sessionManager == null) {
            sessionManager = new SessionManager();
        }
        return sessionManager;
    }

    /**
     * Initialise all required data structures and components of a server
     */
    public SessionManager() {

        // To store unauthenticated server connections & not yet logged in client connections
        connections = new ArrayList<Connection>();

        // Set server ID by randomly generating a string
        serverId = Settings.nextSecret();

        // To store connected Servers & Clients.
        clientConnections = new HashMap<Connection, ConnectedClient>();
        deliveries = new ConcurrentLinkedQueue<String>();
        serverRegistry = new ServerRegistry(serverId, Settings.getLocalPort(), Settings.getLocalHostname());

        // Store information about all known clients in a system
        clientRegistry = new ClientRegistry();

        responder = new Responder();

        // start a listener - keeps listening until ...?
        try {
            listener = new Listener();
        }
        catch (IOException e1) {
            log.fatal("failed to startup a listening thread: " + e1);
            System.exit(-1);
        }

        this.reconnecting = false;

        // Initiate a connection with a remote server, if remote hostname is provided
        initiateConnection();

        // Start the server
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
                log.error("failed to make connection to " + Settings.getRemoteHostname() + ":" +
                        Settings.getRemotePort() + " :" + e);
                System.exit(-1);
            }
        }
    }

    /**
     * Initiates an outgoing connection with another server, and authenticates itself with that server once the
     * connection has been established.
     */
    public synchronized boolean initiateConnection(ConnectedServer conToTry) {

        String hostname = conToTry.getHostname();
        Integer port = conToTry.getPort();

        // Make a connection to another server if remote hostname is supplied
        try {
            System.out.println("Trying to connect to: " + hostname + ":" + port);
            Connection con = outgoingConnection(new Socket(hostname, port));
            authenticate(con);
            log.info("connected to server on port number " + port);
            serverRegistry.setConnectedParent(conToTry.getId(), hostname, port, con);
            return true;
        }
        catch (IOException e) {
            log.error("failed to make connection to " + hostname + ":" + port + " :" + e);
            return false;
        }
    }


    /**
     * Processing incoming messages from a given connection.
     * @param con The connection a message was received on
     * @param msg The message sent by a client or server on the network
     * @return If the message was successfully processed
     */
    public synchronized boolean process(Connection con, String msg) {
        JSONObject json = MessageProcessor.toJson(msg, false, "status");

        // If we couldn't parse the message, notify the sender and disconnect
        if (json.containsKey("status") && (json.get("status").toString()).equals("failure")) {
            return messageInvalid(con, "Incorrect Message. Json parse error, parsing: " + msg);
        }

        // log.info("Received Message: " + msg);

        // Check that a message contains a valid command, and that it has the required fields
        String invalidMsgStructureMsg = MessageProcessor.hasValidCommandAndFields(json);
        if (invalidMsgStructureMsg != null) {
            return messageInvalid(con, invalidMsgStructureMsg);
        }
        String command = json.get("command").toString();

        // If the message is an INVALID_MESSAGE or LOGOUT message, close the connection.
        if (command.equals("INVALID_MESSAGE") || command.equals("LOGOUT") || command.equals("AUTHENTICATION_FAIL")) {

            if (serverRegistry.isServerCon(con)) {
                con.writeMsg(MessageProcessor.getShutdownMessage(serverId));
            }
            con.closeCon();
            return true;        // true because we want terminate = true; makes con delete itself from SessionManager
        }

        // Check Authentication/Validation status - unless is an AUTHENTICATE MESSAGE or LOGIN message
         String invalidSender = MessageProcessor.validSender(json, con);
         if (invalidSender != null) {
             return messageInvalid(con, invalidSender);
         }

         // Process the message
        return responder.process(json, con);
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
        serverRegistry.addServerCon(c);
        return c;
    }

    /**
     * Runs the server SessionManager. Sends regular SERVER_ANNOUNCE messages to all servers on the network at a given
     * time interval.
     */
    @Override
    public void run() {
        log.info("using activity interval of " + Settings.getActivityInterval() + " milliseconds");
        Integer secondsPassed;
        while (!term) {
            try {
                secondsPassed = 0;
                while (secondsPassed < 5) {

                    // Deliver queued messages every second
                    Thread.sleep(Settings.getActivityInterval() / 5);
                    makeDeliveries();
                    eventualRedirect(null);
                    secondsPassed += 1;
                }
            }
            catch (InterruptedException e) {
                log.info("received an interrupt, system is shutting down");
                break;
            }
            // Make a serverAnnounce every 5 seconds
            serverAnnounce();
        }
        log.info("closing " + connections.size() + " connections");
        // clean up
        closeAllConnections();
        listener.setTerm(true);
    }

    /**
     * We know the parent has been disconnected. Try to reconnect to a different server.
     */
    public synchronized void reconnectParentIfDisconnected() {
        reconnecting = true;
        boolean reconnected = false;
        ConcurrentLinkedQueue<ConnectedServer> consToTry = serverRegistry.getConsToTry();

        // Try to reconnect to grandparent
        ConnectedServer grandparent = serverRegistry.getGrandparent();
        if (grandparent != null) {
            log.info("Should be connecting to grandparent here.");
            reconnected = initiateConnection(grandparent);
            if (reconnected) {
                serverRegistry.setNoGrandparent();
            }
        }
        boolean rootSibling = serverRegistry.amRootSibling();
        ConnectedServer conToTry;
        // If the reconnection didn't work
        if (!reconnected) {

            // If we're not the root sibling, try to connect to other servers
            if (!rootSibling) {
                while (!reconnected && !consToTry.isEmpty()) {
                    conToTry = consToTry.poll();
                    reconnected = initiateConnection(conToTry);
                }
            }
        }
        // Check if we are the new root server of the network
        if (!reconnected && rootSibling) {
            log.info("This server is the new parent server, allowing other servers to connect to this one.");
            String msg =  MessageProcessor.getGrandparentUpdateMsg(null);
            forwardToChildren(msg);
        }
        else if (!reconnected) {
            log.info("Unable to reconnect to any new servers. Unrepairable partition.");
        }
        // Must have reconnected!
        else {
            ConnectedServer newParent = serverRegistry.getParentInfo();
            Connection newParentCon = serverRegistry.getParentConnection();
            if (newParent != null) {
                log.info("Succeeded in repairing network partition due to server failure.");

                // Send a "GRANDPARENT_UPDATE" message to children.
                String msg = MessageProcessor.getGrandparentUpdateMsg(newParent.toJson());
                forwardToChildren(msg);
            }
        }
        reconnecting = false;
    }

    /** Checks if server is in the process of reconnecting with the rest of the network/fixing the network
     * partition. */
    public static boolean isReconnecting() {
        return reconnecting;
    }

    /** Closes all client and server connections a server has. */
    public void closeAllConnections() {
        for (Connection c : connections) {
            sessionManager.closeConnection(c, "Context: Closing all connections");
        }
        connections.clear();
        serverRegistry.closeServerCons();
        clientConnections.keySet().forEach((c) -> {
            sessionManager.closeConnection(c, "Context: Closing all connections");
        });
        clientConnections.clear();
    }

    /** Received an invalid message from some connection. Generate an invalid message response
     * and send to the source of the invalid message, then close the connection.
     * @param c The connection a message was received on
     * @param errorLog the error message to be included
     * @return Returns true if message successfully handled. **/
    public boolean messageInvalid(Connection c, String errorLog) {
        String msg = MessageProcessor.getInvalidMessage(errorLog);
        c.writeMsg(msg);
        String closeContext = "Close Connection Context: Received invalid message (in messageInvalid, in SessionMangr)";
        if (clientConnections.containsKey(c)) {
            String loginContext = closeContext + ". Logout Context: Received an invalid message from " +
                    getConnectedClient(c).getUsername() + ".";
            logoutClient(c, loginContext, true, true, null);
        }
        return true;
    }

    /**
     * Retrieves the connection for a particular client, given its username
     * @param username The username of the client for which we are searching.
     * @param secret The secret of the client for which we are searching
     * @return The connection for that given username. Null if the connection is not in our connections.
     */
    public Connection getConnectionForClient(String username, String secret) {
        for (Connection con : clientConnections.keySet()) {
            ConnectedClient client = clientConnections.get(con);
            if (client.isClient(username, secret)) {
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
        System.out.println("Running Server Announce!");

        int load = clientConnections.size();
        int port = Settings.getLocalPort();
        String hostname = Settings.getLocalHostname();

        String msg = MessageProcessor.getServerAnnounceMsg(serverId, load, hostname, port,
                                                           clientRegistry.getRecordsJson());
        serverBroadcast(msg);
    }




    //
    // SERVER AUTHENTICATION
    //

    /** Needs to authenticates itself with server following outgoing connection being established.
     * Sends an AUTHENTICATE message to that server with its secret.
     * @param c The connection the authenticate message will be send on **/
    public void authenticate(Connection c) {
        String msg = MessageProcessor.getAuthenticateMsg(Settings.getSecret(), clientRegistry.getRecordsJson(), serverId,
                                                         Settings.getLocalHostname(), Settings.getLocalPort());
        c.writeMsg(msg);
    }

    /** Authenticates a new server from incoming connection
     * @param incomingSecret The secret supplied by the authenticating server
     * @param c The connection a server is trying to authenticate on **/
    public boolean authenticateIncomingSever(String incomingSecret, Connection c, String id, String hostname,
                                             Integer port) {

        // Check if secret matches the secret of this server
        if (!Settings.getSecret().equals(incomingSecret)) {
            serverAuthenticateFailed(c, incomingSecret);
            return false;
        }
        else {
            // Server supplied correct secret, remove from generic "holding" connections array and add to server
            // connections array
            // Also add this server to our list of child servers
            ConnectedServer newChild;
            connections.remove(c);
            if (serverRegistry.hasRootChild()) {
                newChild = serverRegistry.addConnectedChild(c, id, hostname, port);
            }
            else {
                // This server is the new root child of the system
                newChild = serverRegistry.addRootChild(c, id, hostname, port);
            }
            // Send AUTHENTICATE_SUCCESS message
            serverAuthenticateSuccess(c, newChild);
            return true;
        }
    }

    /** Called by Responder to determine if a given server is authenticated.
     * Checks server connections array list for the connection. If in this array list, server is authenticated and
     * returns true, otherwise not authenticated and returns falls.
     * @param c The connection we are checking for authentication**/
    public boolean checkServerAuthenticated(Connection c) {
        return serverRegistry.isServerCon(c);
    }

    /**
     * The server cannot be authenticated - send an authentication failed message and close the connection
     * @param con The connection to send the message on
     * @param secret The secret a server attempted to authenticate with
     */
    public void serverAuthenticateFailed(Connection con, String secret) {
        String msg = MessageProcessor.getAuthenticationFailedMsg(secret);
        con.writeMsg(msg);
        String closeContext = "Close Connection Context: Authenticate Failed (in serverAuthenticateFailed, " +
                "in SessionManager)";
        closeConnection(con, closeContext);
        deleteClosedConnection(con);
    }

    /**
     * Incoming server has successfully authenticated - send an authentication success message
     * @param con the server connection to send the message to
     * @param newChild the record of the new server connection
     */
    public void serverAuthenticateSuccess(Connection con, ConnectedServer newChild) {
        // Generate AUTHENTICATION_SUCCESS message
        String msg = MessageProcessor.getAuthenticationSuccessMsg(clientRegistry.getRecordsJson(),
                                                                  serverRegistry.toJson(),
                                                                  Settings.getLocalHostname(),
                                                                  Settings.getLocalPort(), serverId,
                                                                  serverRegistry.getParentJson(),
                                                                  serverRegistry.childListToJson());
        con.writeMsg(msg);

        // Update other child servers with their new sibling!
        msg = MessageProcessor.getSiblingUpdateMsg(newChild.toJson());
        forwardToChildren(msg);
    }




    //
    // USER LOGIN / AUTHENTICATION (User & Pass checking)
    //

    /** Checks if a client is logged in. Returns true if logged in, false otherwise.
     * @param c The connection we are checking
     * @returns true if the connection belongs to a client logged into this server, false otherwise.
     */
    public boolean checkClientLoggedIn(Connection c) {
        if (c == null) {
            return false;
        }
        return clientConnections.containsKey(c);
    }


    /** Checks if a client is logged in. Returns true if logged in, false otherwise.
     * @param user The username of the client we are checking
     * @param secret The secret of the client we are checking
     * @returns true if the connection belongs to a client logged into this server, false otherwise.
     */
    public boolean clientLoggedInLocally(String user, String secret) {
        Connection con = getConnectionForClient(user, secret);
        return checkClientLoggedIn(con);
    }

    /**
     * Client failed to login successfully - send a failure message back to the client
     * @param con The connection to send the message on
     * @param failureMessage The failure message to be sent
     */
    public void clientLoginFailed(Connection con, String failureMessage) {
        String msg = MessageProcessor.getClientLoginFailedMsg(failureMessage);
        con.writeMsg(msg);
        String closeContext = "Close Connection Context: Client failed to login (in clientLoginFailed, " +
                "in SessionManager)";
        closeConnection(con, closeContext);
        deleteClosedConnection(con);
    }

    /** Log in a client that is not anonymous, by checking username and password combination match that
     * stored locally in server. Remove from generic connections array, and add to client-specific connections
     * array.
     * @param c The connection a client is using
     * @param username The username supplied by the client
     * @param secret The password supplied by the client */
    public Integer loginClient(Connection c, String username, String secret) {

        boolean logged_in = false;
        String failure_message = null;

        // If the username & secret combination matches the known combination
        if (clientRegistry.secretCorrect(username, secret)) {

            // Is the connected client still registering with this server?
            if (clientConnections.containsKey(c)) {

                // Is the client registered?
                if (clientConnections.get(c).isRegistered()) {
                    logged_in = true;
                }
                // If not registered yet, client cannot yet log in!
                else {
                    failure_message = "Client registration ongoing, cannot yet log in";
                }
            }
            // Did they instead just connect to this server?
            else {
                // Send login success message, add to client connections HashMap & remove from generic connections
                // "holding" ArrayList
                clientConnections.put(c, new ConnectedClient(username, secret));
                connections.remove(c);
                logged_in = true;
            }
        }
        else {
            failure_message = "attempted to login with an invalid username & secret combination";
        }
        // Send login success message (Registered & Correct combo) & check for redirection
        if (logged_in) {
            String loginContext = "Context: Received LOGIN, now in loginClient (in SessionManager)";
            Integer token = clientRegistry.logUser(true, username, secret, loginContext, Integer.MIN_VALUE);

            String msg = MessageProcessor.getLoginSuccessMsg(username);
            c.writeMsg(msg);
            return token;
        }
        // username & secret either not stored locally or client registration is incomplete. Send login failed message.
        else {
            clientLoginFailed(c, failure_message);
            return Integer.MIN_VALUE;
        }
    }

    /** Log in an anonymous client
     * @param c The connection a client is using
     * @param username The username supplied by the client
     * @param secret The secret assigned to the client **/
    public Integer loginAnonymousClient(Connection c, String username, String secret) {

        // Add client to the clientConnections list and remove from generic holding list
        System.out.println("LOGGING IN ANONYMOUS CLIENT LOCALLY ->       username: " + username);
        clientConnections.put(c, new ConnectedClient(username, secret));
        connections.remove(c);

        // Add the client record to our local store and login the client
        clientRegistry.addFreshClient(username, secret);
        String loginContext = "Context: Received LOGIN from Anon user, now in loginAnonymousClient (in SessionManger).";
        Integer token = clientRegistry.logUser(true, username, secret, loginContext, Integer.MIN_VALUE);

        // Broadcast the LOCK_REQUEST
        String msg = MessageProcessor.getLockRequestMsg(username, secret);
        serverBroadcast(msg);

        // Send LOGIN_SUCCESS Message - delay so that we have time to send around the LOCK_REQUEST
        delayThread(1500);
        msg = MessageProcessor.getLoginSuccessMsg(username);
        c.writeMsg(msg);
        return token;
    }

    /** Checks if the server knows of another server that has at least two less connections than it. If such a server
     * exists, sends a REDIRECT message with that server's hostname and port number.
     * This has been implemented to return the FIRST server that has two or less connections.
     * @param c The connection to send the message on
     * @param anonClient True if the client is an anonymous client, false if registered */
    public boolean checkRedirectClient(Connection c, boolean anonClient) {
        int load = clientConnections.size() + connections.size();
        ConnectedServer server = getLowLoadServer(load);
        if (server != null) {
            return redirect(c, server, anonClient);
        }
        else {
            return false;
        }
    }

    public ConnectedServer getLowLoadServer(Integer load) {
        ArrayList<ConnectedServer> lowLoadServers = new ArrayList<ConnectedServer>();
        for (ConnectedServer server : serverRegistry.getAllServers()) {
            Integer serverLoad = server.getLoad();
            if (server.isConnected() && serverLoad != null && serverLoad <= load - 2 && !server.isTimedOut()) {
                return server;
            }
        }
        return null;
    }

    public boolean eventualRedirect(Connection con) {
        boolean anonClient;
        String username;
        int load = clientConnections.size() + connections.size();
        ConnectedServer lowLoadServer = getLowLoadServer(load);
        if (lowLoadServer == null) {
            // log.debug("Redirect failed; logoutClient failed.");
            return false;
        }
        // Get a connection to redirect to the server
        if (con == null) {
            if (clientConnections.isEmpty()) {
                return false;
            }
            HashMap.Entry<Connection, ConnectedClient> entry = clientConnections.entrySet().iterator().next();
            con = entry.getKey();
            ConnectedClient conClient = entry.getValue();
            username = conClient.getUsername();
            anonClient = MessageProcessor.isAnonymous(username);
        }
        else {
            anonClient = MessageProcessor.isAnonymous(clientConnections.get(con).getUsername());
        }
        return redirect(con, lowLoadServer, anonClient);
    }

    public boolean redirect(Connection con, ConnectedServer destServer, boolean anonClient) {
        boolean logoutSuccess = false;
        boolean disconnect = false;
        boolean redirect = false;

        String msg = MessageProcessor.getRedirectMsg(destServer.getHostname(), destServer.getPort());;
        String logoutContext = "Context: Redirecting, now in checkRedirect (in SessionManager)";

        // If client is anonymous, we need to remove the record from our registry so we do not create conflicts
        if (!anonClient) {
            boolean doDisconnect = false;
            logoutSuccess = logoutClient(con, logoutContext, doDisconnect, true, null);
            if (logoutSuccess) { disconnect = true; }
        }
        // Client is a registered user - merely set as logout and redirect
        else { disconnect = true; }
        if (disconnect) {
            log.info("about to call redirect message, waiting 2 secs\n");
            delayDisconnect();

            // LOGOUT_BROADCAST should have been sent. Will now disconnect user and log them out.
            con.writeMsg(msg);
            closeConnection(con, "Close " + logoutContext);
            deleteClosedConnection(con);
            return true;
        }
        if (redirect && !logoutSuccess) { log.debug("Completely Failed Redirection; logoutClient failed."); }
        return false;
    }



    /** Delays the disconnection to account for synchronisation and communication delays*/
    public void delayDisconnect() {
        delayThread(REDIRECT_DELAY);
    }


    /** Delays the thread for a period of time to avoid issues causes by communication delays
     * @param delay The period of time to put a thread to sleep */
    public void delayThread(Integer delay) {
        try {
            Thread.sleep(delay);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
            String errorMsg = "Error: Redirect Delay Interrupted (InterruptedException). " +
                    "In checkRedirectClient in SessionManager.";
            log.error(errorMsg);
            System.exit(-1);
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
        return serverRegistry.numServersInNetwork(this.serverId);
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
        String closeContext = "Close Connection Context: Registration Failed (in registrationFailed, in SessionManager)";
        closeConnection(con, closeContext);
        deleteClosedConnection(con);
    }

    /**
     * Update the ConnectedClient, send a registration success message if all LOCK_ALLOWED messages have arrived.
     * @param client
     * @param username
     * @param secret
     */
    public void updateAndCompleteRegistration(ConnectedClient client, String username, String secret) {
        boolean registrationComplete = client.receivedLockAllowed();
        if (registrationComplete) {
            Connection clientCon = getConnectionForClient(username, secret);
            registrationSuccess(username, secret, clientCon);
        }
    }


    public ConnectedClient getClientIfConnected(String username, String secret) {
        for (HashMap.Entry<Connection, ConnectedClient> client : clientConnections.entrySet()) {
            ConnectedClient clientInfo = client.getValue();
            if (clientInfo.isClient(username, secret)) {
                return clientInfo;
            }
        }
        return null;
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
        for (Connection curr: serverRegistry.getServerConnections().keySet()) {
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
        System.out.println("Broadcasting!");
        for (Connection c: serverRegistry.getServerConnections().keySet()) {
            c.writeMsg(msg);
        }
    }

    /** Sends a message to all of the servers a given server has a direct connection to, except the server it received
     * the message from.
     * @param c The connection that should NOT have the message sent to
     * @param msg The message to be sent **/
    public void forwardServerMsg(Connection c, String msg) {
        for (Connection con: serverRegistry.getServerConnections().keySet()) {
            if (con != c) {
                con.writeMsg(msg);
            }
        }
    }

    public void forwardToChildren(String msg) {
        ConcurrentHashMap<ConnectedServer, Connection> children = serverRegistry.getConnectedChildConnections();
        for (Connection con : children.values()) {
            con.writeMsg(msg);
        }
    }

    public void forwardToParent(String msg) {
        Connection con = serverRegistry.getParentConnection();
        if (con != null) {
            con.writeMsg(msg);
        }
    }





    //
    // CONNECTION CLOSURE
    //
    // TODO: Ensure Disconnected Clients that are no longer in the registry don't cause a bug
    public void ensureLogoutDisconnectedClient(Connection c) {
        ConnectedClient client = getConnectedClient(c);
        if (client != null) {
            String username = client.getUsername();
            String secret = client.getSecret();
            ClientRecord record = getClientRegistry().getClientRecord(username);
            if (record.loggedIn()) {
                String logoutContext = "Updating Client Registry w/ logout out of disconnected Client";
                clientRegistry.logUser(false, username, secret, logoutContext, Integer.MIN_VALUE);
            }
        }
    }

    /** Initiate closure of a given connection and remove from the appropriate array
     * @param c The connection to be closed  */
    public void closeConnection(Connection c, String closeConnectionContext) {
        System.out.println("Closed Connection: " + closeConnectionContext);
        c.closeCon();
    }

    /**
     * The connection has been closed by the other party.
     * Removes the connection from the appropriate data structure, depending on who the connection is with.
     * @param con The connection to be closed
     */
    public synchronized void deleteClosedConnection(Connection con) {

        if (clientConnections.containsKey(con)) {
            // Close connection to another client
            // Generate appropriate logout broadcast, depending on if client was registered or anonymous
            ConnectedClient client = getConnectedClient(con);

            if (client.getUsername().contains("anonymous")) {
                // Client connection anonymous
                String msg = MessageProcessor.getAnonLogoutBroadcast(client.getUsername(), client.getSecret());
                serverBroadcast(msg);
            }
            else {
                // Client was registered -- need to get client token
                String msg = MessageProcessor.getLogoutBroadcast(client.getUsername(), client.getSecret(),
                                                    clientRegistry.getClientToken(client));
                serverBroadcast(msg);
            }
            System.out.println("REMOVING CONNECTION FROM clientConnections     -> username = " + client.getUsername());
            clientConnections.remove(con);
        }
        else {
            // Closing the connection to an unauthenticated server/client not logged in
            connections.remove(con);
        }
    }

    public boolean logoutClient(Connection con, String logoutContext, boolean disconnect, boolean bcast, Integer optionalToken) {
        if (conIsClient(con)) {
            ConnectedClient client = getConnectedClient(con);
            String username = client.getUsername();
            String secret = client.getSecret();
            if (MessageProcessor.isAnonymous(username)) {
                logoutAnonClient(con, logoutContext, username, secret, bcast);
                return true;
            }
            else {
                Integer tokenUsed;
                if (optionalToken == null) {
                    tokenUsed = Integer.MIN_VALUE;
                }
                else {
                    tokenUsed = optionalToken;
                }
                Integer logoutToken = clientRegistry.logUser(false, username, secret, logoutContext, tokenUsed);
                if (!logoutToken.equals(Integer.MIN_VALUE) && bcast) {
                    String logoutBroadcastMsg = MessageProcessor.getLogoutBroadcast(username, secret, logoutToken);
                    serverBroadcast(logoutBroadcastMsg);
                }
            }
            if (disconnect) {
                closeConnection(con, logoutContext);
                deleteClosedConnection(con);
            }
            return true;
        }
        return false;
    }

    public void logoutAnonClient(Connection con, String logoutContext, String username, String secret, boolean bcast) {
        clientRegistry.removeUser(username);
        clientRegistry.clearRecipientFromAllMsgs(username);
        if (bcast) {
            String anonLogoutBroadcastMsg = MessageProcessor.getAnonLogoutBroadcast(username, secret);
            sessionManager.serverBroadcast(anonLogoutBroadcastMsg);
        }
    }

    /**
     * Only use in response to an ANON_LOGOUT_BROADCAST
     * @param user
     */
    public void logoutAnonClient(String user) {
        clientRegistry.removeUser(user);
        clientRegistry.clearRecipientFromAllMsgs(user);
    }

    /**
     * Only use in response to a LOGOUT_BROADCAST
     * @param user
     * @param secret
     * @param logoutContext
     * @param optionalToken
     */
    public void logoutRegisteredClient(String user, String secret, String logoutContext, Integer optionalToken) {
        clientRegistry.logUser(false, user, secret, logoutContext, optionalToken);
    }


    public boolean conIsClient(Connection con) {
        return clientConnections.containsKey(con);
    }

    public ConnectedClient getConnectedClient(Connection con) {
        return clientConnections.get(con);
    }

    public boolean clientStillConnected(Connection con) {
        return clientConnections.containsKey(con);
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

    //
    // Getters and Setters
    //

    /**
     * Getter that retrieves the ClientRegistry
     * @return the clientRegistry of this SessionManager
     */
    public ClientRegistry getClientRegistry() {
        return this.clientRegistry;
    }

    public ServerRegistry getServerRegistry() {
        return this.serverRegistry;
    }

    /**
     * Getter for clientConnections, given a HashMap of
     * @param receivingUsers A HashMap of Usernames and Secrets
     * @return The clientConnections HashMap that contains the connections and information about the associated clients.
     */
    public HashMap<String, Connection> getClientConnections(HashMap<String, String> receivingUsers) {
        HashMap<String, Connection> connectedClients = new HashMap<String, Connection>();
        clientConnections.forEach((con, client) -> {
            String conUsername = client.getUsername();
            String conSecret = client.getSecret();
            if (receivingUsers.containsKey(conUsername) && receivingUsers.get(conUsername).equals(conSecret)) {
                connectedClients.put(conUsername, con);
            }
        });
        return connectedClients;
    }

    public HashMap<String, Connection> getClientConnections() {
        HashMap<String, Connection> connectedClients = new HashMap<String, Connection>();
        clientConnections.forEach((con, client) -> {
            String conUsername = client.getUsername();
            connectedClients.put(conUsername, con);
        });
        return connectedClients;
    }

    public static void logDebug(String msg) {
        log.debug(msg);
    }

    public static void logInfo(String msg) {
        log.info(msg);
    }

    public void scheduleDelivery(String sender) {
        deliveries.add(sender);
    }

    public void makeDeliveries() {
        while (!deliveries.isEmpty()) {
            JSONObject ackMsg = clientRegistry.messageFlush(getClientConnections(), deliveries.poll());
            if (ackMsg != null) {
                serverBroadcast(ackMsg.toString());
            }
        }
    }

    public static String getServerId() {
        return serverId;
    }


}