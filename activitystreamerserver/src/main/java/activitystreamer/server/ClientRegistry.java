package activitystreamer.server;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
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
     * @param ...
     */
    public void updateRecords(JSONArray registry) {

        // Iterate through Array
        registry.forEach((clientRecordObject) -> {

            // TODO: UPDATE THIS (ALL BELOW)

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
                clientRecords.put(username, new ClientRecord(clientRecordJson));
            }
        });
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
        clientRecords.put(username, new ClientRecord(username, secret));
    }

    public boolean secretCorrect(String username, String secret) {
        if (!userExists(username)) {
            return false;
        }
        ClientRecord record = clientRecords.get(username);
        return record.sameSecret(secret);
    }

    public void logInUser(String user) {
        // TODO: Login Broadcast
        clientRecords.get(user).setLoggedIn(true);
    }

    public void logOutUser(String user) {
        // TODO: Logout Broadcast
        clientRecords.get(user).setLoggedIn(false);
    }




    public boolean userExists(String user) {
        return clientRecords.containsKey(user);
    }

    public void removeUser(String username) {
        clientRecords.remove(username);
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

    public Integer addMessageToRegistry(String sender, JSONObject activityMsg, ArrayList<String> loggedInUsers) {
        Integer token = -1;
        if (clientRecords.containsKey(sender)) {
            token = clientRecords.get(sender).addMessage(activityMsg, loggedInUsers);
        }
        if (token != -1) {
            return token;
        }
        else {
            System.out.println("ERROR - addMessageToRegistry - " + sender + " not in clientRecords: " + clientRecords);
            System.exit(1);
            return token;
        }
    }

    public Message getMessage(String sender, Integer token) {
        if (clientRecords.containsKey(sender)) {
            return clientRecords.get(sender).getMessage(token);
        }
        else {
            System.out.println("ERROR - getMessage - " + sender + " not in clientRecords: " + clientRecords);
            System.exit(1);
        }
        return null;
    }

    public void receivedMessage(String sender, ArrayList<String> receivers, Integer token) {
        if (clientRecords.containsKey(sender)) {
            clientRecords.get(sender).receivedMessage(receivers, token);
        }
        else {
            System.out.println("ERROR - receivedMessage - " + sender + " not in clientRecords: " + clientRecords);
            System.exit(1);
        }
    }


    public JSONObject messageFlush(HashMap<String, Connection> clientConnections, String sender) {

        // Prepare to send ACK message
        JSONObject ackMessage = new JSONObject();
        JSONObject theMessages = new JSONObject();
        ackMessage.put("command", "MSG_ACKS");
        ackMessage.put("sender", sender);
        HashMap<Integer, ArrayList<String>> acks = new HashMap<Integer, ArrayList<String>>();

        if (!clientRecords.containsKey(sender)) {
            System.out.println("ERROR: - messageFlush in clientRegistry - sender: " + sender +
                               " not in clientRecords" + clientRecords);
            System.exit(1);
            ackMessage.put("status", "failure");
            return ackMessage;
        }
        else {
            ClientRecord senderRecord = clientRecords.get(sender);

            clientConnections.forEach((user, con) -> {
                Message m;
                // Send all possible messages to client
                do {
                    m = senderRecord.getNextMessage(user);
                    if (m != null) {

                        // Send the message
                        JSONObject activityBroadcastMsg = m.getClientMessage();
                        Integer token = m.getToken();
                        con.writeMsg(activityBroadcastMsg.toString());

                        // Add an ACK
                        if (acks.containsKey(token)) {
                            ArrayList<String> users = new ArrayList<String>();
                            users.add(user);
                            acks.put(token, users);
                        } else {
                            acks.get(token).add(user);
                        }
                    }
                } while (m != null);
            });

            // Report the messages as having been sent
            acks.forEach((token, recipients) -> {
                senderRecord.receivedMessage(recipients, token);
                theMessages.put(token, recipients);
            });

            // Return the ACKs, to send to servers!
            ackMessage.put("messages", theMessages);
            return ackMessage;
        }
    }

}
