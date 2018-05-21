package activitystreamer.server;

import org.json.simple.JSONObject;

import java.util.HashMap;

public class ClientRecord {

    private String username;
    private String secret;
    private Integer next_token;

    // Maps usernames to the next expected message token (the token of the next message that the user has not received)
    private HashMap<String, Integer> expected_tokens;

    // Maps tokens to JSONObject messages
    private HashMap<Integer, JSONObject> sent_messages;


    public ClientRecord(String username, String secret) {
        this.username = username;
        this.secret = secret;
        this.next_token = 1;
        this.expected_tokens = new HashMap<String, Integer>();
        this.sent_messages = new HashMap<Integer, JSONObject>();
    }

    public ClientRecord(String username, String secret, Integer next_expected_token,
                        HashMap<String, Integer> expected_tokens, HashMap<Integer, JSONObject> sent_messages) {
        this.username = username;
        this.secret = secret;
        this.next_token = next_expected_token;
        this.expected_tokens = expected_tokens;
        this.sent_messages = sent_messages;
    }

    public void updateMessagesSent(HashMap<Integer, JSONObject> sent_messages_record) {
        sent_messages_record.forEach((token, jsonMsg) -> {
            if (!sent_messages.containsKey(token)) {
                sent_messages.put(token, jsonMsg);
            }
            // TODO: Either here, or write another function called immediately after this, that sends messages to other
            // clients if they are expecting this new token. Maybe a function in ClientRegistry.
        });

    }



    // Update


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
     *
     * Must only be called when we _know_ with certainty that the client has received the next message from the client
     * @param other_user
     */
    public void updateToken(String other_user) {
        Integer current_token = expected_tokens.get(other_user);
        expected_tokens.put(other_user, current_token + 1);
    }

}
