package activitystreamer.server;

import activitystreamer.util.LoginException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRegistry {

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
     * @param registry ...
     */
    public void updateRecords(JSONArray registry) {

        // Iterate through Array
        registry.forEach((clientRecordObject) -> {

            // Convert each record into a JSONObject
            JSONObject clientRecordJson = (JSONObject) clientRecordObject;
            String username = clientRecordJson.get("username").toString();

            // Update existing record
            if (clientRecords.containsKey(username)) {
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

    private void addRecord(String user, ClientRecord clientRecord) {
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
        JSONObject clientRegistryJsonObj = MessageProcessor.toJson(jsonArrayString, true, "registry");
        return clientRegistryJsonObj;
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

    public Integer loginUser(String user, String secret, String loginContext, Integer optionalToken) {
        int tokenSent = setLogin(user, loginContext, true, optionalToken);
        System.out.println("Trying to login: " + user + ". token: " + tokenSent);
        System.out.println("CONTEXT: " + loginContext);
        return tokenSent;
    }

    public Integer logoutUser(String user, String secret, String loginContext, Integer optionalToken) {
        int tokenSent = setLogin(user, loginContext, false, optionalToken);
        System.out.println("Trying to logout: " + user + ". token: " + tokenSent);
        System.out.println("CONTEXT: " + loginContext);
        if (tokenSent != -4 && tokenSent != -2) {
            SessionManager.getInstance().serverBroadcast(MessageProcessor.getLogoutBroadcast(user, secret, tokenSent));
        }
        return tokenSent;
    }

    // TODO: Get rid of Magic Numbers
    private Integer setLogin(String user, String loginContext, boolean login, Integer optionalToken) {
        int tokenUsed;
        if (optionalToken != -2) {
            tokenUsed = optionalToken;
        }
        else {
            tokenUsed = getLoginToken(user, login) + 1;
        }
        int validToken = getClientRecord(user).updateLoggedIn(tokenUsed, loginContext);
        if (validToken == -4) {
            return null;
        }
        return validToken;
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
        clientRecords.remove(username);
    }

    /**
     * Retrieves a map of usernames and passwords, for comparison, for all usernames provided
     * @param userList The list of usernames for which we are returning the credentials.
     * @return A mapping from clients' usernames to their secret.
     */
    public HashMap<String, String> getClientCredentials(ArrayList<String> userList) {
        HashMap<String, String> clientCredentials = new HashMap<String, String>();

        userList.forEach((user) -> {
            if (clientRecords.containsKey(user)) {
                String secret = clientRecords.get(user).getSecret();
                clientCredentials.put(user, secret);
            }
        });
        return clientCredentials;
    }


    // ------------------------------ MESSAGE HANDLING ------------------------------

    public Integer addClientMsgToRegistry(String sender, JSONObject activityMsg, ArrayList<String> loggedInUsers) {
        return getClientRecord(sender).addMessage(activityMsg, loggedInUsers);
    }

    public void addMessageToRegistry(Message msg, String user) {
        getClientRecord(user).addMessage(msg);
    }

    public Message getMessage(String sender, Integer token) {
        return getClientRecord(sender).getMessage(token);
    }

    public void receivedMessage(String sender, ArrayList<String> receivers, Integer token) {
        getClientRecord(sender).receivedMessage(receivers, token);
    }


    public JSONObject messageFlush(HashMap<String, Connection> clientConnections, String sender) {

        boolean noMessagesSent = true;

        // To collect tokens and clients who received messages of that token number from the sender
        HashMap<Integer, ArrayList<String>> acks = new HashMap<Integer, ArrayList<String>>();

        ClientRecord senderRecord = getClientRecord(sender);

        clientConnections.forEach((user, con) -> {

            // Send all possible messages to client
            Message m = senderRecord.getNextMessage(user);
            while (m != null) {

                // Send the message
                JSONObject activityBroadcastMsg = m.getClientMessage();
                Integer token = m.getToken();
                con.writeMsg(activityBroadcastMsg.toString());

                // Add an ACK
                if (acks.containsKey(token)) {
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
        if (!acks.isEmpty()) {
            noMessagesSent = false;
        }

        // Report the messages as having been sent
        JSONObject theMessages = registerAcks(acks, sender);

        // Return the ACKs, to send to servers!
        JSONObject ackMessage = MessageProcessor.getStartAckMsg(sender);
        ackMessage.put("messages", theMessages);

        if (noMessagesSent) {
            return null;
        }
        return ackMessage;
    }

    public JSONObject registerAcks(HashMap<Integer, ArrayList<String>> acks, String sender) {

        JSONObject theMessages = new JSONObject();

        ClientRecord senderRecord = getClientRecord(sender);

        // Report the messages as having been sent
        acks.forEach((token, recipients) -> {
            senderRecord.receivedMessage(recipients, token);
            theMessages.put(token, recipients);
        });
        return theMessages;
    }






    // ------------------------------ GENERAL GETTERS & SETTTERS ------------------------------

    private ClientRecord getClientRecord(String user) {
        if (!clientRecords.containsKey(user)) {
            System.out.println("ERROR: - getClientRecord in clientRegistry - user: " + user +
                    " not in clientRecords" + clientRecords);
            System.exit(1);
            return null;
        }
        return clientRecords.get(user);
    }

}
