package activitystreamer.server;

import activitystreamer.util.LoginException;
import activitystreamer.util.RecordAccessException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;


/** This class stores and handles all ClientRecords a given server knows about. */
public class ClientRegistry {

    private static final Integer UPDATE_FAILED = -2;

    private ConcurrentHashMap<String, ClientRecord> clientRecords;

    // Client Records can either start empty, or they can be provided
    public ClientRegistry() {
        this.clientRecords = new ConcurrentHashMap<String, ClientRecord>();
    }

    public ClientRegistry(ConcurrentHashMap<String, ClientRecord> providedClientRecords) {
        this.clientRecords = providedClientRecords;
    }


    // Receive a JSONObject msg labeled "CLIENT_REGISTRY" and has a field "registry" & use it to update existing records

    /**
     *
     *
     * Assume:
     *  - Has a "command" field labeled "CLIENT_REGISTRY"
     *  - Has a "registry" field with a valid JSONArray (this is ensured anyway)
     *  - TCP Ensures error-free data transfer, so we can assume all messages are well formed (as we created them)
     * @param registry Contains all client records another server has, which have been passed to us upon authentication
     */
    public void updateRecords(JSONArray registry) {

        // For comparing registries later
        ConcurrentHashMap<String, ClientRecord> givenRegistry = new ConcurrentHashMap<String, ClientRecord>();

        // Iterate through Array
        registry.forEach((clientRecordObject) -> {

            // Convert each record into a JSONObject
            JSONObject clientRecordJson = (JSONObject) clientRecordObject;
            String username = clientRecordJson.get("username").toString();
            ClientRecord givenRecord = new ClientRecord(clientRecordJson);
            givenRegistry.put(username, givenRecord);

            // Update existing record
            if (userExists(username)) {
                ClientRecord oldClientRecord = clientRecords.get(username);
                Integer loginToken = oldClientRecord.getLoggedInToken();
                if (MessageProcessor.isAnonymous(username) && loginToken >= 3) {
                    removeUser(username);
                }
                else {
                    // Update if the record has the correct secret
                    if (oldClientRecord.sameSecret(clientRecordJson)) {
                        oldClientRecord.updateRecord(clientRecordJson);
                    }
                    // Conflicting ClientRecord username & secret combination. Conflict created during network partition.
                    // We delete both.
                    else {
                        clientRecords.remove(username);
                    }
                }
            }
            // Or create a new record
            else {
                if (!givenRecord.anonToDelete()) {
                    addRecord(username, givenRecord);
                }
            }
        });
        // If we have an anon record that the given registry does not have & the user isn't logged in locally, send an
        // ANON_CHECK & delete the record
        clientRecords.forEach((user, record) -> {
            if (MessageProcessor.isAnonymous(user) && !givenRegistry.containsKey(user) && record.getLoggedInToken() > 1
                    && !SessionManager.getInstance().clientLoggedInLocally(user, record.getSecret())) {

                // Convert the record into a JSONObject
                String recordJsonString = MessageProcessor.getGson().toJson(record);
                JSONObject recordJson = MessageProcessor.toJson(recordJsonString, false, "");
                String anonCheckMsg = MessageProcessor.getAnonCheck(recordJson);

                // Bcast ANON_CHECK & delete user from the registry (added back if & when we get an ANON_SUCCESS msg)
                SessionManager.getInstance().serverBroadcast(anonCheckMsg);
                removeUser(user);
            }
        });
    }

    /** Adds a ClientRecord some other server in the network has, which we do not have in local storage yet
     * @param user The username of the client
     * @param clientRecord The ClientRecord to be added to storage */
    public void addRecord(String user, ClientRecord clientRecord) {
        clientRecords.put(user, clientRecord);
        System.out.println("            Added " + user + " to the registry: " + clientRecords);
    }

    /**
     * Retrieve the server's Client Registry in the form of a JSONArray.
     *
     * Use GSON to convert ClientRecords to JSON
     * https://github.com/google/gson/blob/master/UserGuide.md#TOC-Object-Examples
     *
     * @return JSONObject - containing the converted Client Registry. Null if GSON library broken?
     */
    public JSONObject getRecordsJson() {

        // Place ClientRecords into an array
        ArrayList<ClientRecord> recordArray = new ArrayList<ClientRecord>();
        clientRecords.forEach((username, record) -> recordArray.add(record));

        // Convert ArrayList into JSONArray String
        String jsonArrayString = MessageProcessor.getGson().toJson(recordArray);

        // Returns a JSONObject
        return MessageProcessor.toJson(jsonArrayString, true, "registry");
    }

    /** A new client has initiated a direct connection with this server --> create a new record and add to registry
     * @param username The client's username
     * @param secret The client's secret */
    public void addFreshClient(String username, String secret) {
        ClientRecord record = new ClientRecord(username, secret);
        addRecord(username, record);
    }

    /** Check if a client's secret matches what we have stored locally
     * @param username The client's username
     * @param secret The client's secret
     * @return true if records match, false if record does not exist or records do not match */
    public boolean secretCorrect(String username, String secret) {
        // Check record exists for given username
        if (!userExists(username)) {
            return false;
        }
        // Compare secret against local storage
        ClientRecord record = getClientRecord(username);
        return record.sameSecret(secret);
    }

    /** Login or logout a user, and update record accordingly
     * @param in True if user logging in, false if logging out
     * @param user The user who's record we are updating
     * @param secret Client's secret
     * @param loginContext Used for debugging */
    public synchronized Integer logUser(boolean in, String user, String secret, String loginContext,
                                        Integer optionalToken) {
        if (in) {
            return loginUser(user, secret, loginContext, optionalToken);
        }
        else {
            return logoutUser(user, secret, loginContext, optionalToken);
        }
    }

    /** Login a given user and update tokens, if required
     * @param user Username of client being logged into network
     * @param secret User's secret
     * @param loginContext Used for debugging
     * @param optionalToken If user is a registered user, update token to indicate logged in */
    public Integer loginUser(String user, String secret, String loginContext, Integer optionalToken) {
        int tokenSent = setLogin(user, loginContext, true, optionalToken);
        System.out.println("Trying to login: " + user + ". token: " + tokenSent);
        System.out.println("CONTEXT: " + loginContext);
        if (tokenSent == Integer.MIN_VALUE) {
            System.out.println("Invalid Login Attempt. Unsuccessful.");
        }
        return tokenSent;
    }

    /** Logout a given user and update tokens, if required
     * @param user Username of client being logged out of network
     * @param secret User's secret
     * @param loginContext Used for debugging
     * @param optionalToken If user is a registered user, update token to indicate logged out */
    public Integer logoutUser(String user, String secret, String loginContext, Integer optionalToken) {
        int tokenSent = setLogin(user, loginContext, false, optionalToken);
        System.out.println("Trying to logout: " + user + ". token: " + tokenSent);
        System.out.println("CONTEXT: " + loginContext);
        if (tokenSent == Integer.MIN_VALUE) {
            System.out.println("Invalid Logout Attempt. Unsuccessful.");
        }
        return tokenSent;
    }

    /** Set the login status of a given client
     * @param user Username of client being logged out of network
     * @param loginContext Used for debugging
     * @param optionalToken If user is a registered user, update token to indicate current status */
    private Integer setLogin(String user, String loginContext, boolean login, Integer optionalToken) {
        int tokenUsed;
        // Check if user is registered or anonymous, based on token value
        if (optionalToken != Integer.MIN_VALUE) {
            tokenUsed = optionalToken;
        }
        else {
            // Set token to value that indicated client is logged in
            tokenUsed = getLoginToken(user, login) + 1;
        }
        ClientRecord userRecord = getClientRecord(user);
        if (userRecord != null) {
            return userRecord.updateLoggedIn(tokenUsed, loginContext);
        }
        return Integer.MIN_VALUE;
    }

    /** Get's the current token for a user
     * @param user The user who's token we are retrieving
     * @param login Indicates whether user is logging into, or logging out of the network
     * @return The current token for the user */
    private Integer getLoginToken(String user, Boolean login) {
        ClientRecord clientRecord = getClientRecord(user);
        boolean loggedIn = clientRecord.loggedIn();
        try {
            // Set the client's token to indicate they have logged in/logged out of the network
            if ((!loggedIn && login) || (loggedIn && !login)) {
                return clientRecord.getLoggedInToken();
            }
            else {
                // Conflicting information - client trying to log in but already logged in, or trying to logout
                // but are already marked as logged out
                String status = (loggedIn ? "Status: Logged in. " : "Status: Logged out. ");
                String update = (login ? "Update: Login attempt. " : "Update: Logout attempt. ");
                throw new LoginException("User: " + user + ". " + status + update);
            }
        }
        catch (LoginException e) {
            e.printStackTrace();
            System.exit(1);
            // Error performing required task
            return UPDATE_FAILED;
        }
    }

    /**
     * Retrieve a list of all of the ClientRecords that are "logged in"
     * @return ArrayList<String> An ArrayList of Strings, each representing a user that is logged in, according to this
     * ClientRegistry instance.
     */
    public ArrayList<String> getLoggedInUsers() {
        ArrayList<String> loggedInUsers = new ArrayList<String>();
        this.clientRecords.forEach((username, clientRecord) -> {
            if (clientRecord.loggedIn()) {
                loggedInUsers.add(username);
            }
        });

        return loggedInUsers;
    }

    /** Check a user exists in local storage
     * @param user The username of the client we are searching for
     * @return true if client exists in storage, false otherwise */
    public boolean userExists(String user) {
        return clientRecords.containsKey(user);
    }


    /**
     * Just ensures that the record assigned to the named user is no longer in our clientRecords. Doesn't matter if the
     * user isn't in our clientRecords when this is called.
     * @param username The username of the client
     * @return true if client removed from records, false otherwise (client record did not exist)
     */
    public boolean removeUser(String username) {
        if (clientRecords.containsKey(username)) {
            if (!clientRecords.get(username).hasMessagesToDeliver()) {
                System.out.println("REMOVING ANON CLIENTRECORD ->" + username);
                clientRecords.remove(username);
                return true;
            }
            else {
                clientRecords.get(username).deleteAfterMsgsDelivered();
            }
        }
        return false;
    }

    /**
     * Retrieves a map of usernames and passwords, for comparison, for all usernames provided
     * @param userList The list of usernames for which we are returning the credentials.
     * @return A mapping from clients' usernames to their secret.
     */
    public HashMap<String, String> getClientCredentials(ArrayList<String> userList) {
        HashMap<String, String> clientCredentials = new HashMap<String, String>();

        userList.forEach((user) -> {
            ClientRecord record = getClientRecord(user);
            String secret = record.getSecret();
            clientCredentials.put(user, secret);
        });
        return clientCredentials;
    }

    // ------------------------------ MESSAGE HANDLING ------------------------------

    /** Adds a message to the registry
     * @param sender The client who sent the message
     * @param activityMsg The message to be delivered
     * @param loggedInUsers The current list of all user's logged into the system at the time the message was sent
     * @return The token number of the message */
    public Integer addMsgToRegistry(String sender, JSONObject activityMsg, ArrayList<String> loggedInUsers) {
        return getClientRecord(sender).createAndAddMessage(activityMsg, loggedInUsers);
    }

    /** Adds a message to the registry
     * @param msg The message to be added/stored
     * @param user The client to have the message stored against */
    public void addMessageToRegistry(Message msg, String user) {
        getClientRecord(user).addMessage(msg);
    }

    /** Sends any messages queued for a given client
     * @param con The connection to send the messages on
     * @param recipient The client who the messages are to be sent to
     * @return An ArrayList of message acknowledgements to be broadcast across the network, indicating the messages
     * have been delivered. */
    public ArrayList<JSONObject> messageFlush(Connection con, String recipient) {

        ArrayList<JSONObject> ackMessages = new ArrayList<JSONObject>();

        // Send any messages available for delivery to the client, and generate an acknowledgement message
        // if delivered
        clientRecords.forEach((sender, senderRecord) -> {
            JSONObject ackMessage = sendWaitingMessages(con, recipient, sender);
            if (ackMessage != null) {
                // Add the acknowledgement messages to the array list
                ackMessages.add(ackMessage);
            }
        });
        return ackMessages;
    }

    /** Get the token number of a particular client
     * @param client The client we want the token number of
     * @return The token number */
    public Integer getClientToken(ConnectedClient client) {
        ClientRecord tmp = clientRecords.get(client.getUsername());
        return tmp.getToken();
    }

    /** Send any messages queue to a client
     * @param con The connection to send the messages on
     * @param recipient The username of the client to recieve the messages
     * @param sender The username of the client who sent the messages */
    public JSONObject sendWaitingMessages(Connection con, String recipient, String sender) {
        ClientRecord senderRecord = getClientRecord(sender);

        // Store any acknowlegement messages generated, to be broadcast across the network
        HashMap<Integer, ArrayList<String>> acks = sendWaitingMessages(con, recipient, sender, senderRecord);

        // Register Sent Message and add ACK messages, if any
        if (!acks.isEmpty()) {
            registerAcks(acks, sender);
            JSONObject ackMessage = MessageProcessor.getStartAckMsg(sender);
            ackMessage.put("messages", acks);
            return ackMessage;
        }
        return null;
    }

    /** Send any waiting messages queued for a client
     * @param con The connection to send the messages on
     * @param recipient The username of the client to receive the messages
     * @param sender The username of the client who sent the messages
     * @param senderRecord The ClientRecord of the client who sent the messages
     * @return A hashmap containing all acknowlegment messages generated */
    public HashMap<Integer, ArrayList<String>> sendWaitingMessages(Connection con, String recipient,
                                                                   String sender, ClientRecord senderRecord) {
        HashMap<Integer, ArrayList<String>> acks = new HashMap<Integer, ArrayList<String>>();

        boolean allDelivered;

        // While there are messages that can be sent, send them!
        Message m = senderRecord.getNextMessage(recipient);
        while (m != null) {

            // Send the message
            JSONObject activityBroadcastMsg = MessageProcessor.cleanClientMessage(m.getClientMessage());
            Integer token = m.getToken();
            con.writeMsg(activityBroadcastMsg.toString());

            if (SessionManager.getInstance().clientStillConnected(con)) {

                // Record the message as sent
                m.receivedMessage(recipient);

                // Add an ACK
                ArrayList<String> users = new ArrayList<String>();
                users.add(recipient);
                acks.put(token, users);
            }
            // Attempt to retrieve another message
            m = senderRecord.getNextMessage(recipient);

            if (senderRecord.deleteAfterDelivered()) {
                removeUser(sender);
            }
        }
        // Return our messages to acknowledge
        return acks;
    }


    /**
     * Send messages to clients marked as recipients
     * Note: MessageFlush is only called with a 'sender' that is in the registry (its username and secret have been
     * checked. Thus, we do not need to check if senderRecord is null, we can assume its existence.
     * @param clientConnections The connections to send the messages to
     * @param sender The username of the client who sent the message
     * @return A JSONObject containing all acknowlegement messages created by this process
     */
    public JSONObject messageFlush(HashMap<String, Connection> clientConnections, String sender) {

        // To collect tokens and clients who received messages of that token number from the sender
        HashMap<Integer, ArrayList<String>> acks = new HashMap<Integer, ArrayList<String>>();

        // See JavaDoc note as to why we can assume existence.
        ClientRecord senderRecord = getClientRecord(sender);

        clientConnections.forEach((user, con) -> {

            boolean allDelivered;

            // Send all possible messages to client
            Message m = senderRecord.getNextMessage(user);
            while (m != null) {

                // Send the message
                JSONObject activityBroadcastMsg = MessageProcessor.cleanClientMessage(m.getClientMessage());
                Integer token = m.getToken();

                con.writeMsg(activityBroadcastMsg.toString());
                System.out.println("Just wrote " + activityBroadcastMsg.toString() + "to " + user);
                if (con.isOpen()) {

                    // Record the message as sent
                    m.receivedMessage(user);

                    // Add an ACK
                    if (!acks.containsKey(token)) {
                        ArrayList<String> users = new ArrayList<String>();
                        users.add(user);
                        acks.put(token, users);
                    }
                    else {
                        acks.get(token).add(user);
                    }
                    m = senderRecord.getNextMessage(user);
                    if (senderRecord.deleteAfterDelivered()) {
                        removeUser(sender);
                    }
                }
                // If the connection is closed, don't send
                else {
                    m = null;
                }
            }
        });

        // Return null if no messages sent
        if (acks.isEmpty()) {
            return null;
        }

        // Report the messages as having been sent
        registerAcks(acks, sender);

        // Return the ACKs, to send to servers!
        JSONObject ackMessage = MessageProcessor.getStartAckMsg(sender);
        ackMessage.put("messages", acks);

        return ackMessage;
    }

    /**
     * Called by messageFlush directly above (sender exists), by sendWaitingMessages used by the first messageFlush
     * (sender exists prior to call), and by Responder's MSG_ACKS, which MAY include a sender that isn't yet in our
     * registry. In this case we must ignore MSG_ACKS and allow SERVER_ANNOUNCE to update the Message's recipient list.
     * @param acks The HashMap of all acknowlegement messages to be sent
     * @param sender The username of the client who sent all the messages that were delivered
     */
    public void registerAcks(HashMap<Integer, ArrayList<String>> acks, String sender) {

        System.out.println("REGISTERING ACKS FROM " + sender);

        // Report the messages as having been sent
        if (userExists(sender)) {
            System.out.println(sender + " exists!");
            ClientRecord senderRecord = getClientRecord(sender);
            acks.forEach((token, recipients) -> senderRecord.receivedMessage(recipients, token));
            System.out.println("FINISHED senderRecord.receivedMessage(...)");
            if (senderRecord.deleteAfterDelivered()) {
                System.out.println("Deleting " + sender + " after having delivered messages!");
                removeUser(sender);
            }
        }
    }

    /** Remove a given client from any messages that have marked them as a recipient
     * @param user The username of the client to be removed */
    public void clearRecipientFromAllMsgs(String user) {
        clientRecords.forEach((sender, senderRecord) -> {
           senderRecord.clearRecipientFromAllMsgs(user);
        });
    }

    // ------------------------------ GENERAL GETTERS & SETTERS ------------------------------

    /** Gets a specific ClientRecord
     * @param user The username of the client who's record we want to retrieve
     * @return The ClientRecord, if exists, otherwise null */
    public synchronized ClientRecord getClientRecord(String user) {
        if (!clientRecords.containsKey(user)) {
            return null;
        }
        return clientRecords.get(user);
    }

    @Override
    /** Converts a JSONObject to a string */
    public String toString() {
        return getRecordsJson().toString();
    }
}
