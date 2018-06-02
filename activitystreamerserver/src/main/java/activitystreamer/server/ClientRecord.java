package activitystreamer.server;

import com.google.gson.reflect.TypeToken;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientRecord {


    private String username;
    private String secret;
    private Integer next_token;
    private Integer logged_in;
    private Integer received_up_to;
    private ArrayList<Message> messages;
    private ArrayList<Message> undeliverable_messages;
    private Boolean delete_after_delivering;


    public ClientRecord(String username, String secret) {
        this.username = username;
        this.logged_in = 1;
        this.secret = secret;
        this.next_token = 1;
        this.received_up_to = 0;
        this.messages = new ArrayList<Message>();
        this.undeliverable_messages = new ArrayList<Message>();
        delete_after_delivering = false;
    }

    public ClientRecord(JSONObject clientRecordJson) {
        this.username = clientRecordJson.get("username").toString();
        this.logged_in = ((Long) clientRecordJson.get("logged_in")).intValue();
        this.secret = clientRecordJson.get("secret").toString();
        this.next_token = ((Long) clientRecordJson.get("next_token")).intValue();
        this.received_up_to = ((Long) clientRecordJson.get("received_up_to")).intValue();

        Type collectionType = new TypeToken<ArrayList<Message>>(){}.getType();
        this.messages = MessageProcessor.getGson().fromJson(
                ((JSONArray) clientRecordJson.get("messages")).toJSONString(),
                collectionType);
        this.undeliverable_messages = MessageProcessor.getGson().fromJson(
                ((JSONArray) clientRecordJson.get("undeliverable_messages")).toJSONString(),
                collectionType);
        delete_after_delivering = (boolean) clientRecordJson.get("delete_after_delivering");
    }

    /**
     * Synchronise this record, updating its values if the received record contains updated information.
     * @param receivedRecord
     */
    public void updateRecord(JSONObject receivedRecord) {
        updateLoggedIn(((Long) receivedRecord.get("logged_in")).intValue(), "Updating Record");
        updateNextToken(((Long) receivedRecord.get("next_token")).intValue());

        // Update Messages
        Type collectionType = new TypeToken<ArrayList<Message>>(){}.getType();
        ArrayList<Message> receivedDeliverableMessages = MessageProcessor.getGson().fromJson(
                ((JSONArray) receivedRecord.get("messages")).toJSONString(),
                collectionType);
        if (receivedDeliverableMessages != null) {
            updateMessages(receivedDeliverableMessages);
        }
        ArrayList<Message> receivedUndeliverableMessages = MessageProcessor.getGson().fromJson(
                ((JSONArray) receivedRecord.get("undeliverable_messages")).toJSONString(),
                collectionType);
        if (receivedUndeliverableMessages != null) {
            updateMessages(receivedUndeliverableMessages);
        }

        boolean new_delete = (boolean) receivedRecord.get("delete_after_delivering");
        if (!delete_after_delivering) {
            delete_after_delivering = new_delete;
        }
    }

    private void updateMessages(ArrayList<Message> receivedMessages) {
        // 1. If we don't have a message and its token is > this.received_up_to, add it to our messages
        // 2. Update recipients lists

        receivedMessages.forEach((msg) -> {
            if (!undeliverable_messages.contains(msg) && msg.getToken() > this.received_up_to) {
                addMessage(msg);
            }
            // Otherwise, we have the message (unless we have already sent it to all recipients) and we can update its
            // recipients list (deleting recipients not included on the received message)
            else {
                updateMessage(msg);
            }
        });
    }

    private boolean updateMessage(Message msg) {
        AtomicBoolean messageFound = new AtomicBoolean(false);
        ArrayList<Message> messagesToRemove = new ArrayList<Message>();
        this.messages.forEach((message) -> {
            if (message.equals(msg)) {
                messageFound.set(true);
                boolean allDelivered = message.updateRecipients(msg.getRemainingRecipients());
                if (allDelivered) {
                    messagesToRemove.add(message);
                }
            }
        });
        this.messages.removeIf((m) -> messagesToRemove.contains(m));
        return messageFound.get();
    }

    private void updateNextToken(Integer receivedNextToken) {
        if (receivedNextToken.equals(Integer.MAX_VALUE) || receivedNextToken < 1) {
            next_token = 1;
        }
        else if (next_token < receivedNextToken) {
            next_token = receivedNextToken;
        }
    }

    // ------------------------------ LOGIN MANAGEMENT ------------------------------

    public String getUsername() {
        return this.username;
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

    public String getSecret() {
        return this.secret;
    }

    /**
     *
     * @param newLoggedIn
     */
    public Integer updateLoggedIn(Integer newLoggedIn, String loginContext) {

        if (this.logged_in == Integer.MAX_VALUE) {
            this.logged_in = 2;
            System.out.println("Logging In " + this.username);
            System.out.println(loginContext + "; this.logged_in = " + this.logged_in);
            return this.logged_in;
        }
        else if (newLoggedIn > this.logged_in && newLoggedIn > 0) {
            this.logged_in = newLoggedIn;

            if (this.logged_in % 2 == 0) {
                System.out.println("Logging In " + this.username);
            }
            else {
                System.out.println("Logging Out " + this.username);
            }
            System.out.println(loginContext + "; this.logged_in = " + this.logged_in);

            return this.logged_in;
        }
        // Invalid login attempt
        return Integer.MIN_VALUE;
    }

    /**
     *
     * @return
     */
    public boolean loggedIn() {
        return this.logged_in % 2 == 0;
    }

    public Integer getLoggedInToken() {
        return this.logged_in;
    }

    // ----------------------------------- MESSAGING -----------------------------------


    public Integer createAndAddMessage(JSONObject msg, ArrayList<String> recipients) {
        Integer token = getNextTokenAndIncrement();
        Message message = new Message(token, msg, recipients);
        addMessage(message);
        return token;
    }

    public Integer getNextTokenAndIncrement() {
        if (this.next_token.equals(Integer.MAX_VALUE)) {
            this.next_token = 1;
        }
        else {
            this.next_token += 1;
        }
        int token = this.next_token - 1;
        return token;
    }

    public void addMessage(Message msg) {
        Integer token = msg.getToken();

        if (this.received_up_to.equals(Integer.MAX_VALUE) && token.equals(1)) {
            this.received_up_to = 1;
            messages.add(msg);
            // TODO: Something else here?
        }

        else if (this.received_up_to + 1 == token) {
            messages.add(msg);
            updateDeliverableMsgs(token);
        }
        else {
            undeliverable_messages.add(msg);
            Collections.sort(undeliverable_messages);
        }
    }

    /**
     * Check that we haven't already received messages with higher tokens -> update if we have
     */
    private void updateDeliverableMsgs(Integer deliverableToken) {
        received_up_to = deliverableToken;
        ArrayList<Integer> tokensToMove = new ArrayList<Integer>();
        for (Message m : this.undeliverable_messages) {
            if (m.getToken() <= received_up_to) {
                tokensToMove.add(m.getToken());
                this.messages.add(m);
            }
        }
        this.undeliverable_messages.removeIf((m) -> tokensToMove.contains(m.getToken()));
        Collections.sort(this.messages);
    }

    public void receivedMessage(ArrayList<String> receivers, Integer token) {
        System.out.println("RECORDING RECEIVED MESSAGE FOR " + username);
        boolean allDelivered = false;
        for (Message message : messages) {
            if (message.getToken().equals(token)) {
                if (allDelivered) {
                    allDelivered = message.receivedMessages(receivers);
                    allDelivered = true;
                }
                else {
                    allDelivered = message.receivedMessages(receivers);
                }
            }
        }
        if (allDelivered) {
            System.out.println("ALL DELIVERED -> DELETING MESSAGE!");
            deleteMessage(token);
        }
    }

    public Integer getToken()
    {
        return this.next_token;
    }

    public void deleteMessage(Integer token) {
        messages.removeIf(m -> m.getToken().equals(token));
    }

    private void deleteMessages(ArrayList<Integer> tokens) {
        messages.removeIf(m -> tokens.contains(m.getToken()));
    }

    private void deleteUndeliverableMessages(ArrayList<Integer> tokens) {
        undeliverable_messages.removeIf(m -> tokens.contains(m.getToken()));
    }

    /**
     * Used to get the next valid message available for the recipient.
     * @param recipient
     * @return
     */
    public Message getNextMessage(String recipient) {

        // Make sure the first message we iterate over is the first addressed to the recipient
        if (!messages.isEmpty()) {
            Collections.sort(messages);
            for (Message m : messages) {
                if (m.addressedTo(recipient)) {
                    return m;
                }
            }
        }
        return null;
    }


    public void clearRecipientFromAllMsgs(String user) {
        ArrayList<Integer> messagesToRemove = new ArrayList<Integer>();
        messages.forEach((m) -> {
            boolean allDelivered = m.receivedMessage(user);
            if (allDelivered) {
                messagesToRemove.add(m.getToken());
            }
        });
        deleteMessages(messagesToRemove);
        messagesToRemove.clear();
        undeliverable_messages.forEach((m) -> {
            boolean allDelivered = m.receivedMessage(user);
            if (allDelivered) {
                messagesToRemove.add(m.getToken());
            }
        });
        deleteUndeliverableMessages(messagesToRemove);
    }


    public boolean hasMessagesToDeliver() {
        return (messages.size() > 0 || undeliverable_messages.size() > 0);
    }


    // ------------------------------ COMPARING RECORDS ------------------------------
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!ClientRecord.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        final ClientRecord other = (ClientRecord) obj;
        if ((this.username == null) ? (other.username != null) : !this.username.equals(other.username)) {
            return false;
        }
        // Used if we implement a sender field
        // if (!this.sender.equals(other.sender)) {
        //     return false;
        // }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + (this.username != null ? this.username.hashCode() : 0);
        // Used if we implement a sender field
        // hash = 53 * hash + this.sender;
        return hash;
    }

    public void deleteAfterMsgsDelivered() {
        this.delete_after_delivering = true;
    }

    public boolean deleteAfterDelivered() {
        return this.delete_after_delivering && !hasMessagesToDeliver();
    }

    public boolean anonToDelete() {
        return this.delete_after_delivering;
    }




    // ----------------------------------- Archived Methods (Unused but possibly useful in the future) ----------------
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
}
