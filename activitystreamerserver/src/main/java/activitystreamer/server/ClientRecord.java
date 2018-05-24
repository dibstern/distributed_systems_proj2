package activitystreamer.server;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRecord {

    private String username;
    private String secret;
    private Integer next_token_num;
    private Integer logged_in;

    // Maps usernames to the next expected message token (the token of the next message that the user has not received)
    private ConcurrentHashMap<String, PriorityQueue<Integer>> expected_tokens;

    // Maps tokens to JSONObject messages
    private ConcurrentHashMap<Integer, JSONObject> messages;


    public ClientRecord(String username, String secret) {
        this.username = username;
        this.secret = secret;
        this.next_token_num = 1;
        this.logged_in = 1;
        this.expected_tokens = new ConcurrentHashMap<String, PriorityQueue<Integer>>();
        this.messages = new ConcurrentHashMap<Integer, JSONObject>();
    }

    public ClientRecord(JSONObject clientRecordJson) {
        this.username = clientRecordJson.get("username").toString();
        this.secret = clientRecordJson.get("secret").toString();
        this.next_token_num = ((Long) clientRecordJson.get("next_token_num")).intValue();
        this.logged_in = ((Long) clientRecordJson.get("logged_in")).intValue();
        this.expected_tokens = toTokenDeliveryMap((JSONObject) clientRecordJson.get("expected_tokens"));
        this.messages = toSentMessagesHashMap((JSONObject) clientRecordJson.get("messages"));
    }

    /**
     *
     * @param receivedMessagesRecord
     */
    public void updateMessages(ConcurrentHashMap<Integer, JSONObject> receivedMessagesRecord) {
        receivedMessagesRecord.forEach((token, jsonMsg) -> {
            if (!this.messages.containsKey(token)) {
                this.messages.put(token, jsonMsg);
            }
        });
    }

    /**
     *
     * @param registryObject
     */
    public void updateRecord(JSONObject registryObject) {

        // Update token num
        Integer token = ((Long) registryObject.get("next_token_num")).intValue();
        if (token > this.next_token_num) {
            this.next_token_num = token;
        }

        // Update Logged In Status
        updateLoggedIn(((Long) registryObject.get("logged_in")).intValue());

        // Update Tokens
        JSONObject expectedTokensJson = (JSONObject) registryObject.get("expected_tokens");
        ConcurrentHashMap<String, PriorityQueue<Integer>> receivedTokenMap = toTokenDeliveryMap(expectedTokensJson);
        updateTokens(receivedTokenMap);

        // Update sent messages (MUST be updated after tokens)
        JSONObject sentMessagesJson = (JSONObject) registryObject.get("messages");
        ConcurrentHashMap<Integer, JSONObject> newSentMessages = toSentMessagesHashMap(sentMessagesJson);
        updateSentMessages(newSentMessages);
    }

    /**
     *
     * @param sentMessagesJson
     * @return
     */
    public ConcurrentHashMap<Integer, JSONObject> toSentMessagesHashMap(JSONObject sentMessagesJson) {
        ConcurrentHashMap<Integer, JSONObject> sentMessagesHashMap = new ConcurrentHashMap<Integer, JSONObject>();
        sentMessagesJson.forEach((tokenObj, msgObj) -> {
            Integer token = ((Long) tokenObj).intValue();
            JSONObject msg = (JSONObject) msgObj;
            sentMessagesHashMap.put(token, msg);
        });
        return sentMessagesHashMap;
    }

    /**
     *
     * @param sentMessages
     */
    private void updateSentMessages(ConcurrentHashMap<Integer, JSONObject> sentMessages) {
        sentMessages.forEach((token, msg) -> {
            if (!this.messages.containsKey(token)) {
                this.messages.put(token, msg);
            }
        });
    }

    /**
     *
     * @param expectedTokensJson
     * @return
     */
    public ConcurrentHashMap<String, PriorityQueue<Integer>> toTokenDeliveryMap(JSONObject expectedTokensJson) {

        // Data Structure we're building
        ConcurrentHashMap<String, PriorityQueue<Integer>> tokenDeliveryMap =
                new ConcurrentHashMap<String, PriorityQueue<Integer>>();

        // De-serialise JSON and insert into the Data Structure
        expectedTokensJson.forEach((userObj, tokenListObj) -> {
            String user = userObj.toString();
            JSONArray tokenListJson = (JSONArray) tokenListObj;
            tokenListJson.forEach((tokenObj) -> {
                Integer token = ((Long) tokenObj).intValue();
                tokenDeliveryMap.get(user).add(token);
            });
        });
        return tokenDeliveryMap;
    }

    /**
     *
     * A higher token means a more recent message was acknowledged, so we update our client registry with the next token
     *
     * @param receivedTokenMap
     */
    private void updateTokens(ConcurrentHashMap<String, PriorityQueue<Integer>> receivedTokenMap) {

        receivedTokenMap.forEach((user, tokenList) -> {

            // Update list of tokens, if we have one for the user
            if (this.expected_tokens.containsKey(user)) {
                PriorityQueue<Integer> currentUserTokens = this.expected_tokens.get(user);

                // If neither our list nor our messages have the token, it's a new msg, so add it.
                tokenList.forEach((token) -> {
                    if (!currentUserTokens.contains(token) && !this.messages.containsKey(token)) {
                        currentUserTokens.add(token);
                    }
                });
                // If our list & messages have a token, but it's not in the new list, the msg was sent, delete it
                currentUserTokens.forEach((token) -> {
                    if (!tokenList.contains(token) && this.messages.containsKey(token)) {
                        currentUserTokens.remove(token);
                    }
                });
            }
            // If we don't have the user in our ClientRegistry, add it!
            else {
                this.expected_tokens.put(user, tokenList);
            }
        });
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

    public JSONObject getMessage(Integer token) {
        if (messages.containsKey(token)) {
            return messages.get(token);
        }
        else {
            return null;
        }
    }

    /**
     * Adds the message to messages and adds the receiving users, all at the same time.
     * @param msg The message to be recorded in the ClientRecord
     * @param receivingUsers The users who should be recorded as the intended recipients of this message
     * @return The token of the message that has been added to the ClientRecord.
     */
    public Integer addMessageToRecord(JSONObject msg, ArrayList<String> receivingUsers) {

        // Update next_token_num and messages
        Integer new_token = getTokenAndIncrement();
        this.messages.put(new_token, msg);

        // Update expected_tokens with new delivery instructions for each user
        receivingUsers.forEach((user) -> {
            this.expected_tokens.get(user).add(new_token);
        });
        return new_token;
    }

    /**
     * Increment the current token and return the previous token. The previous token is the one assigned to a new msg.
     * @return Integer representing the token being assigned to a new message.
     */
    public Integer getTokenAndIncrement() {
        this.next_token_num += 1;
        int token_given = this.next_token_num - 1;
        return token_given;
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

    /**
     * Returns all users that are to receive the message indicated by the token, and who have already received all other
     * messages they are due to receive, preceding this message.
     *
     * Checks if:
     *  - Prior messages have been sent
     *  -
     *
     * @param token Indicates the message being sent
     * @return The users that are due to be delivered the message with the provided token.
     */
    public ArrayList<String> getReceivingUsers(Integer token) {
        ArrayList<String> receivingUsers = new ArrayList<String>();
        this.expected_tokens.forEach((user, deliveryList) -> {

            // If the first token in the list is the token, then
            if (deliveryList.peek().equals(token) && this.expected_tokens.containsKey(token)) {
                receivingUsers.add(user);
            }
        });
        return receivingUsers;
    }


    /**
     * Update the record with all of the users who have been sent the message indicated by token by deleting their
     * tokens from their expected_tokens list.
     *
     * @param receivedUsers A list of the users whom received the message indicated by token.
     * @param token Indicates a message in the message
     */
    public void updateSentMessages(ArrayList<String> receivedUsers, Integer token) {
        receivedUsers.forEach((user) -> {
            if (this.expected_tokens.containsKey(user) && this.messages.containsKey(token)) {
                this.expected_tokens.get(user).remove(token);
            }
            else {
                System.out.println("ERROR: Trying to update sent messages for a user not included in expected_tokens");
                System.out.println("User: " + user + ", token: " + token);
                System.out.println("messages: " + this.messages);
                System.exit(1);
            }
        });
    }

}
