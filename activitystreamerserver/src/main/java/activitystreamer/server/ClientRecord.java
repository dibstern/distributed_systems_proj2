package activitystreamer.server;

import com.google.gson.reflect.TypeToken;
import org.json.simple.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;

public class ClientRecord {

    private String username;
    private String secret;
    private ArrayList<Message> messages;
    private Integer logged_in;
    private Integer next_token;
    private Integer last_message_cleared;

    public ClientRecord(String username, String secret) {
        this.username = username;
        this.secret = secret;
        this.logged_in = 1;
        this.next_token = 1;
        this.last_message_cleared = 0;
        this.messages = new ArrayList<Message>();
    }

    public ClientRecord(JSONObject clientRecordJson) {
        this.username = clientRecordJson.get("username").toString();
        this.secret = clientRecordJson.get("secret").toString();
        this.logged_in = ((Long) clientRecordJson.get("logged_in")).intValue();
        this.next_token = ((Long) clientRecordJson.get("next_token")).intValue();
        this.last_message_cleared = ((Long) clientRecordJson.get("last_message_cleared")).intValue();

        // TODO: Check that this works!!
        Type collectionType = new TypeToken<ArrayList<Message>>(){}.getType();
        this.messages = MessageProcessor.getGson().fromJson(
                ((JSONObject) clientRecordJson.get("messages")).toJSONString(),
                collectionType);
    }


    /**
     *
     * @param registryObject
     */
    public void updateRecord(JSONObject registryObject) {

        // Update Logged In Status
        updateLoggedIn(((Long) registryObject.get("logged_in")).intValue());

        updateLastMessageCleared(((Long) registryObject.get("last_message_cleared")).intValue());
        // Update Messages
//        JSONObject expectedTokensJson = (JSONObject) registryObject.get("expected_tokens");
//        ConcurrentHashMap<String, ArrayList<Integer>> receivedTokenMap = toTokenDeliveryMap(expectedTokensJson);
//        updateTokens(receivedTokenMap);
    }

    private void updateLastMessageCleared(Integer lastMessageCleared) {
        if (this.last_message_cleared < lastMessageCleared) {
            this.last_message_cleared = lastMessageCleared;
        }
        // TODO: Delete the associated cleared message, if it exists?
        // Would be simply: deleteMessage(lastMessageCleared);
    }


    /**
     * Checks whether or not the JSONObject representing a client record has the same secret as this ClientRecord
     * @param clientRecord
     * @return
     */
    public boolean sameSecret(JSONObject clientRecord) {
        if (clientRecord.containsKey("secret")){
            return this.secret.equals(clientRecord.get("secret").toString());
        }
        return false;
    }

    /**
     * Checks whether or not the provided secret is the same as the secret of this client record.
     * @param secret
     * @return
     */
    public boolean sameSecret(String secret) {
        return this.secret.equals(secret);
    }



    /*
     * Getters and Setters
     */
    public String getUsername() {
        return this.username;
    }

    public String getSecret() {
        return this.secret;
    }

    /**
     *
     * @param newLoggedIn
     */
    public void updateLoggedIn(Integer newLoggedIn) {
        if (newLoggedIn > this.logged_in || this.logged_in == Integer.MAX_VALUE) {
            this.logged_in = newLoggedIn;
        }
    }

    /**
     *
     * @param now_loggedIn
     */
    public void setLoggedIn(boolean now_loggedIn) {
        boolean currentlyLoggedIn = loggedIn();
        if (now_loggedIn && !currentlyLoggedIn) {
            incrementLoggedIn();
        }
        else if (!now_loggedIn && currentlyLoggedIn) {
            incrementLoggedIn();
        }
        else {
            System.out.println("ERROR: Attempting to set logged_in to same value.");
            System.exit(1);
        }
    }

    /**
     *
     */
    public void incrementLoggedIn() {
        if (this.logged_in == Integer.MAX_VALUE) {
            this.logged_in = 2;
        }
        else {
            this.logged_in += 1;
        }
    }

    /**
     *
     * @return
     */
    public boolean loggedIn() {
        return this.logged_in % 2 == 0;
    }


    public Integer getNextTokenAndIncrement() {
        this.next_token += 1;
        int token = this.next_token - 1;
        return token;
    }


    public Integer addMessage(JSONObject msg, ArrayList<String> recipients) {
        Integer token = getNextTokenAndIncrement();
        messages.add(new Message(token, msg, recipients));
        Collections.sort(messages);
        return token;
    }

    public Message getMessage(Integer token) {
        for (Message message : messages ) {
            if (message.getToken().equals(token)) {
                return message;
            }
        }
        return null;
    }

    public void receivedMessage(ArrayList<String> receivers, Integer token) {
        boolean allDelivered = false;
        for (Message message : messages) {
            if (message.getToken().equals(token)) {
                allDelivered = message.receivedMessages(receivers);
            }
        }
        if (allDelivered) {
            deleteMessage(token);
        }
    }

    public boolean containsMessage(Integer token) {
        for (Message m : messages) {
            if (m.getToken().equals(token)) {
                return true;
            }
        }
        return false;
    }

    private void deleteMessage(Integer token) {
        messages.removeIf(m -> m.getToken().equals(token));
        if (this.last_message_cleared < token) {
            this.last_message_cleared = token;
        }
    }

    /**
     * Returns a HashMap of <Username, Message> Pairs, the message to send to each user.
     * @param connectedClients A client currently connected to the server.
     * @return a HashMap of <Username, Message> Pairs, so each user has a message (if any) the server can send it.
     *         May return an empty HashMap if no messages are yet ready to send.
     */
    public HashMap<String, Message> getNextMessages(ArrayList<String> connectedClients) {
        HashMap<String, Message> nextMessages = new HashMap<String, Message>();

        // Get the next message for each user, and if it's not null, store it!
        connectedClients.forEach((user) -> {
            Message msg = getNextMessage(user);
            if (msg != null) {
                nextMessages.put(user, msg);
            }
        });
        return nextMessages;
    }

    /**
     * Used to get the next valid message available for the recipient.
     * @param recipient
     * @return
     */
    public Message getNextMessage(String recipient) {

        // Make sure the first message we iterate over is the first addressed to the recipient
        Collections.sort(messages);
        for (Message m : messages) {
            if (m.addressedTo(recipient)) {
                if (okayToSend(m.getToken())) {
                    return m;
                }
                else {
                    return null;
                }
            }
        }
        return null;
    }

    // public HashMap<Integer, ArrayList<String>> sen

    private Integer getLastTokenCleared() {
        return this.last_message_cleared;
    }


    public boolean okayToSend(Integer token) {
        Integer lastCleared = getLastTokenCleared();
        return (lastCleared.equals(token-1) || lastCleared > token || containsMessage(token-1) || token == 1);
    }

}
