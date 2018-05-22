package activitystreamer.server;

import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRecord {

    private String username;
    private String secret;
    private Integer next_token_num;
    private Integer logged_in;

    // Maps usernames to the next expected message token (the token of the next message that the user has not received)
    public static enum DELIVERY { SEND, SENT, NO_SEND
    };
    private ConcurrentHashMap<String, ConcurrentHashMap<Integer, DELIVERY>> expected_tokens;

    // Maps tokens to JSONObject messages
    private ConcurrentHashMap<Integer, JSONObject> sent_messages;


    public ClientRecord(String username, String secret) {
        this.username = username;
        this.secret = secret;
        this.next_token_num = 1;
        this.logged_in = 1;
        this.expected_tokens = new ConcurrentHashMap<String, ConcurrentHashMap<Integer, DELIVERY>>();
        this.sent_messages = new ConcurrentHashMap<Integer, JSONObject>();
    }

    public ClientRecord(JSONObject clientRecordJson) {
        this.username = clientRecordJson.get("username").toString();
        this.secret = clientRecordJson.get("secret").toString();
        this.next_token_num = ((Long) clientRecordJson.get("next_token_num")).intValue();
        this.logged_in = ((Long) clientRecordJson.get("logged_in")).intValue();
        this.expected_tokens = toTokenDeliveryMap((JSONObject) clientRecordJson.get("expected_tokens"));
        this.sent_messages = toSentMessagesHashMap((JSONObject) clientRecordJson.get("sent_messages"));
    }

    /**
     *
     * @param sent_messages_record
     */
    public void updateMessagesSent(ConcurrentHashMap<Integer, JSONObject> sent_messages_record) {
        sent_messages_record.forEach((token, jsonMsg) -> {
            if (!this.sent_messages.containsKey(token)) {
                this.sent_messages.put(token, jsonMsg);
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
        ConcurrentHashMap<String, ConcurrentHashMap<Integer, DELIVERY>> newExpectedTokens =
                toTokenDeliveryMap(expectedTokensJson);
        updateTokens(newExpectedTokens);

        // Update sent messages
        JSONObject sentMessagesJson = (JSONObject) registryObject.get("sent_messages");
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
            if (!this.sent_messages.containsKey(token)) {
                this.sent_messages.put(token, msg);
            }
        });
    }

    /**
     *
     * @param expectedTokensJson
     * @return
     */
    public ConcurrentHashMap<String, ConcurrentHashMap<Integer, DELIVERY>> toTokenDeliveryMap(
            JSONObject expectedTokensJson) {

        // Data Structure we're building
        ConcurrentHashMap<String, ConcurrentHashMap<Integer, DELIVERY>> tokenDeliveryMap =
                new ConcurrentHashMap<String, ConcurrentHashMap<Integer, DELIVERY>>();

        // De-serialise JSON and insert into the Data Structure
        expectedTokensJson.forEach((userObj, msgObj) -> {
            String user = userObj.toString();
            JSONObject tokenInstruction = (JSONObject) msgObj;
            tokenInstruction.forEach((tokenObj, deliveryInstruction) -> {
                Integer token = ((Long) tokenObj).intValue();
                DELIVERY delivery = (DELIVERY) deliveryInstruction;
                tokenDeliveryMap.get(user).put(token, delivery);
            });
        });
        return tokenDeliveryMap;
    }

    /**
     *
     * A higher token means a more recent message was acknowledged, so we update our client registry with the next token
     *
     * @param newTokens
     */
    private void updateTokens(ConcurrentHashMap<String, ConcurrentHashMap<Integer, DELIVERY>> newTokens) {
        newTokens.forEach((user, tokenInstruction) -> {

            // Update instructions if we have current instructions
            if (this.expected_tokens.containsKey(user)) {

                // Look at each tokenInstruction and update if current is SEND and received SENT
                tokenInstruction.forEach((token, delivery) -> {
                    ConcurrentHashMap<Integer, DELIVERY> userDeliveryInstructions = this.expected_tokens.get(user);
                    DELIVERY updatedDelivery = updateDelivery(delivery, userDeliveryInstructions.get(token));
                    userDeliveryInstructions.put(token, updatedDelivery);
                });
            }
            // If we don't have the user in our ClientRegistry, add it!
            else {
                this.expected_tokens.put(user, tokenInstruction);
            }
        });
    }

    /**
     * Used to update currentDelivery, and indicates when invalid updates are attempted.
     *
     * @param currentDelivery
     * @param receivedDelivery
     * @return
     */
    private DELIVERY updateDelivery(DELIVERY currentDelivery, DELIVERY receivedDelivery) {
        switch (currentDelivery) {
            case NO_SEND:
                if (receivedDelivery != DELIVERY.NO_SEND) {
                    System.out.println("ERROR! Attempting to update NO_SEND");
                    System.exit(1);
                }
                return DELIVERY.NO_SEND;
            case SEND:
                if (receivedDelivery == DELIVERY.SENT) {
                    return DELIVERY.SENT;
                }
                else if (receivedDelivery == DELIVERY.SEND) {
                    return DELIVERY.SEND;
                }
                System.out.println("ERROR! Attempting to update SEND to NO_SEND");
                System.exit(1);
            default:
                if (receivedDelivery == DELIVERY.NO_SEND) {
                    System.out.println("ERROR! Attempting to update SENT to NO_SEND");
                    System.exit(1);
                }
                return DELIVERY.SENT;
        }
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
        if (sent_messages.containsKey(token)) {
            return sent_messages.get(token);
        }
        else {
            return null;
        }
    }

    public Integer addSentMessage(JSONObject msg, ArrayList<String> loggedInUsers) {

        // Update next_token_num and sent_messages
        Integer new_token = getTokenAndIncrement();
        this.sent_messages.put(new_token, msg);

        // Update expected_tokens with new delivery instructions for each user
        loggedInUsers.forEach((user) -> {
            this.expected_tokens.get(user).put(new_token, DELIVERY.SEND);
        });
        this.expected_tokens.forEach((user, deliveryInstructionsMap) -> {
            if (!deliveryInstructionsMap.containsKey(new_token)) {
                deliveryInstructionsMap.put(new_token, DELIVERY.NO_SEND);
            }
        });
        return new_token;
    }

    /**
     * Increment the current token and return the previous token. The previous token is the one assigned to a new msg.
     * @return Integer representing the token being assigned to a new message.
     */
    public Integer getTokenAndIncrement() {
        this.next_token_num += 1;
        return this.next_token_num - 1;
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

    public void updateLoggedIn(Integer newLoggedIn) {
        if (newLoggedIn > this.logged_in || this.logged_in == Integer.MAX_VALUE) {
            this.logged_in = newLoggedIn;
        }
    }

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

    public void incrementLoggedIn() {
        if (this.logged_in == Integer.MAX_VALUE) {
            this.logged_in = 2;
        }
        else {
            this.logged_in += 1;
        }
    }

    public boolean loggedIn() {
        return this.logged_in % 2 == 0;
    }


}
