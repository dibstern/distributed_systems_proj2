package activitystreamer.server;

import activitystreamer.util.LoginException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRegistry {

    private ConcurrentHashMap<String, ClientRecord> clientRecords;
    private AnonRecord anonRecord;

    // Client Records can either start empty, or they can be provided
    public ClientRegistry() {
        this.clientRecords = new ConcurrentHashMap<String, ClientRecord>();
        this.anonRecord = new AnonRecord("anonymous");
    }

    public ClientRegistry(ConcurrentHashMap<String, ClientRecord> providedClientRecords, AnonRecord providedAnonRecord) {
        this.clientRecords = providedClientRecords;
        this.anonRecord = providedAnonRecord;
    }


    // Receive a JSONObject msg labeled "CLIENT_REGISTRY" and has a field "registry" & use it to update existing records

    /**
     *
     *
     * Assume:
     *  - Has a "command" field labeled "CLIENT_REGISTRY"
     *  - Has a "registry" field with a valid JSONArray (this is ensured anyway)
     *  - TCP Ensures error-free data transfer, so we can assume all messages are well formed (as we created them)
     * @param registry ...
     */
    public void updateRecords(JSONArray registry) {

        // Iterate through Array
        registry.forEach((clientRecordObject) -> {

            // Convert each record into a JSONObject
            JSONObject clientRecordJson = (JSONObject) clientRecordObject;
            String username = clientRecordJson.get("username").toString();

            // Update existing record
            if (userExists(username)) {
                ClientRecord oldClientRecord = clientRecords.get(username);

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


            // Or create a new record
            else {
                addRecord(username, new ClientRecord(clientRecordJson));
            }
        });
    }

    public void updateAnonRecord(JSONObject anonRecordJson) {
        // anonRecord.updateRecord(anonRecordJson);
    }

    public Integer loginAnonUser() {
        String loginContext = "Context: in loginAnonUser in ClientRegistry";
        return anonRecord.login(loginContext);
    }

    public Integer logoutAnonUser() {
        String logoutContext = "Context: in logoutAnonUser in ClientRegistry";
        return anonRecord.logout(logoutContext);
    }



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

    public void addFreshClient(String username, String secret) {
        ClientRecord record = new ClientRecord(username, secret);
        addRecord(username, record);
    }

    public boolean secretCorrect(String username, String secret) {
        if (!userExists(username)) {
            return false;
        }
        ClientRecord record = getClientRecord(username);
        return record.sameSecret(secret);
    }

    public void loginAnonUser(String loginContext) {
        anonRecord.login(loginContext);
    }

    public void logoutAnonUser(String username) {

        clientRecords.remove(username);
    }


    public Integer loginUser(String user, String secret, String loginContext, Integer optionalToken) {
        int tokenSent = setLogin(user, loginContext, true, optionalToken);
        System.out.println("Trying to login: " + user + ". token: " + tokenSent);
        System.out.println("CONTEXT: " + loginContext);
        if (tokenSent == Integer.MIN_VALUE) {
            System.out.println("Invalid Login Attempt. Unsuccessful.");
        }
        return tokenSent;
    }

    public Integer logoutUser(String user, String secret, String loginContext, Integer optionalToken) {
        int tokenSent = setLogin(user, loginContext, false, optionalToken);
        System.out.println("Trying to logout: " + user + ". token: " + tokenSent);
        System.out.println("CONTEXT: " + loginContext);
        if (tokenSent == Integer.MIN_VALUE) {
            System.out.println("Invalid Logout Attempt. Unsuccessful.");
        }
        return tokenSent;
    }

    // TODO: Get rid of Magic Numbers
    private Integer setLogin(String user, String loginContext, boolean login, Integer optionalToken) {
        int tokenUsed;
        if (optionalToken != Integer.MIN_VALUE) {
            tokenUsed = optionalToken;
        }
        else {
            tokenUsed = getLoginToken(user, login) + 1;
        }
        return getClientRecord(user).updateLoggedIn(tokenUsed, loginContext);
    }

    private Integer getLoginToken(String user, Boolean login) {
        ClientRecord clientRecord = getClientRecord(user);
        boolean loggedIn = clientRecord.loggedIn();
        try {
            if ((!loggedIn && login) || (loggedIn && !login)) {
                return clientRecord.getLoggedInToken();
            }
            else {
                String status = (loggedIn ? "Status: Logged in. " : "Status: Logged out. ");
                String update = (login ? "Update: Login attempt. " : "Update: Logout attempt. ");
                throw new LoginException("User: " + user + ". " + status + update);
            }
        }
        catch (LoginException e) {
            e.printStackTrace();
            System.exit(1);

            // Return Integer.MAX_VALUE instead?
            return -2;
        }
    }

    /**
     * Retrieve a list of all of the ClientRecords that are "logged in"
     *
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

    public boolean userExists(String user) {
        return clientRecords.containsKey(user);
    }


    public void removeUser(String username) {
        if (clientRecords.containsKey(username))
        {
            clientRecords.remove(username);
        }
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

    public Integer addClientMsgToRegistry(String sender, JSONObject activityMsg, ArrayList<String> loggedInUsers, Integer numAnonUsers) {
        return getClientRecord(sender).createAndAddMessage(activityMsg, loggedInUsers, numAnonUsers);
    }

    public void addAnonMsgToRegistry(JSONObject activityMsg, ArrayList<String> loggedInUsers, Integer numAnonUsers) {
        anonRecord.createAndAddMessage(activityMsg, loggedInUsers, numAnonUsers);
    }

    public void addClientMessageToRegistry(Message msg, String user) {
        getClientRecord(user).addMessage(msg);
    }

    public void addAnonMessageToRegistry(Message msg) {
        anonRecord.addMessage(msg);
    }

    public void receivedMessage(String sender, ArrayList<String> receivers, Integer token, Integer numAnonReceivers) {

    }


    public void receivedClientMessage(String sender, ArrayList<String> receivers, Integer token, Integer numAnonReceivers) {
        getClientRecord(sender).receivedMessage(receivers, token, numAnonReceivers);
    }

    // NOTE: Cannot have a receivedAnonMessage equivalent, due to the inability to identify messages via tokens.



    // TODO: A version that looks for any messages to deliver for a particular user, sends them, returns a MSG_ACKS,
    // TODO: To be used when a user logs in!
    public ArrayList<JSONObject> messageFlush(Connection con, String recipient) {

        ArrayList<JSONObject> ackMessages = new ArrayList<JSONObject>();

        clientRecords.forEach((sender, senderRecord) -> {
            JSONObject ackMessage = sendWaitingMessages(con, recipient, sender);
            if (ackMessage != null) {
                ackMessages.add(ackMessage);
            }
        });
        return ackMessages;
    }



    public JSONObject sendWaitingMessages(Connection con, String recipient, String sender) {
        ClientRecord senderRecord = getClientRecord(sender);

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


    public HashMap<Integer, ArrayList<String>> sendWaitingMessages(Connection con, String recipient,
                                                                   String sender, ClientRecord senderRecord) {
        HashMap<Integer, ArrayList<String>> acks = new HashMap<Integer, ArrayList<String>>();

        // While there are messages that can be sent, send them!
        ClientMessage m = senderRecord.getNextMessage(recipient);
        while (m != null) {

            // Send the message
            JSONObject activityBroadcastMsg = m.getClientMessage();
            Integer token = m.getToken();
            con.writeMsg(activityBroadcastMsg.toString());

            // Record the message as sent
            m.receivedMessage(recipient);

            // Add an ACK
            ArrayList<String> users = new ArrayList<String>();
            users.add(recipient);
            acks.put(token, users);

            // Attempt to retrieve another message
            m = senderRecord.getNextMessage(recipient);
        }
        // Return our messages to acknowledge
        return acks;
    }



    public JSONObject messageFlush(HashMap<String, Connection> clientConnections, String sender) {

        // To collect tokens and clients who received messages of that token number from the sender
        HashMap<Integer, ArrayList<String>> acks = new HashMap<Integer, ArrayList<String>>();

        ClientRecord senderRecord = getClientRecord(sender);

        clientConnections.forEach((user, con) -> {

            // Send all possible messages to client
            ClientMessage m = senderRecord.getNextMessage(user);
            while (m != null) {

                // Send the message
                JSONObject activityBroadcastMsg = m.getClientMessage();
                Integer token = m.getToken();
                con.writeMsg(activityBroadcastMsg.toString());

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

    public void registerAcks(HashMap<Integer, ArrayList<String>> acks, String sender) {

        // TODO: What if we receive a MSG_ACKS for a user we haven't yet registered?
        if (!userExists(sender)) {
            // System.out.print("");
        }
        else {
            // Report the messages as having been sent
            ClientRecord senderRecord = getClientRecord(sender);
            // TODO -> Build in numAnonReceivers
            acks.forEach((token, recipients) -> senderRecord.receivedMessage(recipients, token, 0));
        }
    }




    // ------------------------------ GENERAL GETTERS & SETTERS ------------------------------

    public ClientRecord getClientRecord(String user) {
        if (!clientRecords.containsKey(user)) {
            SessionManager.logDebug("ERROR: - getClientRecord in clientRegistry - user: " + user +
                    " not in clientRecords" + clientRecords);
            System.exit(1);
            return null;
        }
        return clientRecords.get(user);
    }

}
