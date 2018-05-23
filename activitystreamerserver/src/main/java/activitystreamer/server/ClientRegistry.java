package activitystreamer.server;

import com.google.gson.Gson;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.util.ArrayList;
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

    /**
     *
     *
     *
     * @param username
     * @param token
     * @return Null if client or message not in registry. Otherwise returns the JSONObject message.
     */
    public JSONObject getMessage(String username, Integer token) {
        if (clientRecords.containsKey(username)) {
            ClientRecord record = clientRecords.get(username);
            return record.getMessage(token);
        }
        else {
            return null;
        }
    }

    public boolean userExists(String user) {
        return clientRecords.containsKey(user);
    }

    public void removeUser(String username) {
        clientRecords.remove(username);
    }


    /**
     * Add message and its expected recipients to ClientRegistry and retrieve the allocated token
     * @param user The user sending the activity message.
     * @param msg The authenticated activity message.
     * @return Integer representing the token assigned to the newly received message
     */
    public Integer registerSentMessage(String user, JSONObject msg, ArrayList<String> loggedInUsers) {
        ClientRecord record = clientRecords.get(user);
        return record.addSentMessage(msg, loggedInUsers);
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
}
