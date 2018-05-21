package activitystreamer.server;

import org.json.simple.JSONObject;

import java.util.concurrent.ConcurrentHashMap;

public class ClientRecord {

    private String username;
    private String secret;
    private Integer next_token_num;

    // Maps usernames to the next expected message token (the token of the next message that the user has not received)
    private ConcurrentHashMap<String, Integer> expected_tokens;

    // Maps tokens to JSONObject messages
    private ConcurrentHashMap<Integer, JSONObject> sent_messages;


    public ClientRecord(String username, String secret) {
        this.username = username;
        this.secret = secret;
        this.next_token_num = 1;
        this.expected_tokens = new ConcurrentHashMap<String, Integer>();
        this.sent_messages = new ConcurrentHashMap<Integer, JSONObject>();
    }

    public ClientRecord(JSONObject clientRecordJson) {
        this.username = clientRecordJson.get("username").toString();
        this.secret = clientRecordJson.get("secret").toString();
        this.next_token_num = ((Long) clientRecordJson.get("next_token_num")).intValue();
        this.expected_tokens = toTokenHashMap((JSONObject) clientRecordJson.get("expected_tokens"));
        this.sent_messages = toSentMessagesHashMap((JSONObject) clientRecordJson.get("sent_messages"));
    }

    public void updateMessagesSent(ConcurrentHashMap<Integer, JSONObject> sent_messages_record) {
        sent_messages_record.forEach((token, jsonMsg) -> {
            if (!this.sent_messages.containsKey(token)) {
                this.sent_messages.put(token, jsonMsg);
            }
            // TODO: Either here, or write another function called immediately after this, that sends messages to other
            // clients if they are expecting this new token. Maybe a function in ClientRegistry.
        });

    }

    public void updateRecord(JSONObject registryObject) {
        String username = registryObject.get("username").toString();
        String secret = registryObject.get("secret").toString();

        // Update token num
        Integer token = ((Long) registryObject.get("next_token_num")).intValue();
        if (token > this.next_token_num) {
            this.next_token_num = token;
        }

        // Update Tokens
        JSONObject expectedTokensJson = (JSONObject) registryObject.get("expected_tokens");
        ConcurrentHashMap<String, Integer> newExpectedTokens = toTokenHashMap(expectedTokensJson);
        updateTokens(newExpectedTokens);

        // Update sent messages
        JSONObject sentMessagesJson = (JSONObject) registryObject.get("sent_messages");
        ConcurrentHashMap<Integer, JSONObject> newSentMessages = toSentMessagesHashMap(sentMessagesJson);
        updateSentMessages(newSentMessages);
    }

    public ConcurrentHashMap<Integer, JSONObject> toSentMessagesHashMap(JSONObject sentMessagesJson) {
        ConcurrentHashMap<Integer, JSONObject> sentMessagesHashMap = new ConcurrentHashMap<Integer, JSONObject>();
        sentMessagesJson.forEach((tokenObj, msgObj) -> {
            Integer token = ((Long) tokenObj).intValue();
            JSONObject msg = (JSONObject) msgObj;
            sentMessagesHashMap.put(token, msg);
        });
        return sentMessagesHashMap;
    }

    private void updateSentMessages(ConcurrentHashMap<Integer, JSONObject> sentMessages) {
        sentMessages.forEach((token, msg) -> {
            if (!this.sent_messages.containsKey(token)) {
                this.sent_messages.put(token, msg);
            }
        });
    }


    public ConcurrentHashMap<String, Integer> toTokenHashMap(JSONObject expectedTokensJson) {
        ConcurrentHashMap<String, Integer> tokenHashMap = new ConcurrentHashMap<String, Integer>();
        expectedTokensJson.forEach((userObj, tokenObj) -> {
            String user = userObj.toString();
            Integer token = ((Long) tokenObj).intValue();
            tokenHashMap.put(user, token);
        });
        return tokenHashMap;
    }

    private void updateTokens(ConcurrentHashMap<String, Integer> newTokens) {
        newTokens.forEach((user, token) -> {

            // Update if it contains the user
            if (this.expected_tokens.contains(user)) {
                Integer currToken = this.expected_tokens.get(user);

                // If the token provided is > the current recorded token, a msg has been sent, this is the ACK
                if (token > currToken) {
                    this.expected_tokens.put(user, token);
                }
            }
            // Add new entry if entry is missing
            else {
                this.expected_tokens.put(user, token);
            }
        });
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
     *
     * Must only be called when we _know_ with certainty that the client has received the next message from the client
     * @param other_user
     */
    public void updateToken(String other_user) {
        Integer current_token = expected_tokens.get(other_user);
        expected_tokens.put(other_user, current_token + 1);
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

    public boolean sameSecret(String secret) {
        return this.secret.equals(secret);
    }

    public JSONObject getMessage(Integer token) {
        if (sent_messages.containsKey(token)) {
            return sent_messages.get(token);
        }
        else {
            return null;
        }
    }

}
