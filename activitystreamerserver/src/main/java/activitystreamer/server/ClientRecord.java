package activitystreamer.server;

import com.google.gson.reflect.TypeToken;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** This class represents a record of a Client on the network. It stores all required information about the client,
 * including its token number and messages waiting to be processed. */
public class ClientRecord {

    // Fields we want to keep track of
    private String username;
    private String secret;
    private Integer next_token;
    private Integer logged_in;
    private Integer received_up_to;
    private ArrayList<Message> messages;
    private ArrayList<Message> undeliverable_messages;
    private Boolean delete_after_delivering;


    /** Creates a new client record
     * @param username The client's username
     * @param secret The client's secret */
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

    /** Creates a new ClientRecord from a JSONOBject - this client is therefore connected to some other server in
     * the network, but we need to add it to local storage
     * @param clientRecordJson The JSONObject representing the client record */
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
     * @param receivedRecord The record received from some other server on the network
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

    /** Updates the messages that are to be delivered to clients
     * @param receivedMessages An ArrayList of messages to be delivered*/
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

    /** Update */
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
     * @return true if secret matches what is stored locally, false otherwise
     */
    public boolean sameSecret(String secret) {
        return this.secret.equals(secret);
    }

    /** Return the secret for a given client
     * @return the client's secret*/
    public String getSecret() {
        return this.secret;
    }

    /**
     * A client has logged in/loggout out from the network and needs it's login status updated
     * @param newLoggedIn the value to update the login field with
     * @param loginContext used for debugging purposes
     *  @return client's new login value
     */
    public Integer updateLoggedIn(Integer newLoggedIn, String loginContext) {

        // Client's login value is maximum integer --> reset back to 1
        if (this.logged_in == Integer.MAX_VALUE) {
            this.logged_in = 2;
            System.out.println("Logging In " + this.username);
            System.out.println(loginContext + "; this.logged_in = " + this.logged_in);
            return this.logged_in;
        }
        // Determine if client is logging in or logging out, update appropriately
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
     * Check's if a given client is logged in to the network
     * @return if logged_in positive, client has logged in, otherwise client is logged out
     */
    public boolean loggedIn() {
        return this.logged_in % 2 == 0;
    }

    /** Returns the current logged_in token for a client
     * @return client's logged_in token value */
    public Integer getLoggedInToken() {
        return this.logged_in;
    }

    // ----------------------------------- MESSAGING -----------------------------------


    /** Create a new message from a JSONObject and add it to local storage
     * @param msg The JSONObject to be converted into a message and added
     * @param recipients The list of recipients to receive the message
     * @return The token number of the message*/
    public Integer createAndAddMessage(JSONObject msg, ArrayList<String> recipients) {
        // Increment the token of the sender
        Integer token = getNextTokenAndIncrement();
        // Create message and add to storage
        Message message = new Message(token, msg, recipients);
        addMessage(message);
        return token;
    }

    /** Get a client's token and increment it
     * @return client's new message token*/
    public Integer getNextTokenAndIncrement() {
        // Token is the maximum integer --> reset to 1
        if (this.next_token.equals(Integer.MAX_VALUE)) {
            this.next_token = 1;
        }
        else {
            this.next_token += 1;
        }
        int token = this.next_token - 1;
        return token;
    }

    /** Add a message to local storage
     * @param msg The message to be added */
    public void addMessage(Message msg) {
        Integer token = msg.getToken();

        // Reset the token for the sender of the message as has reached the maximum integer
        if (this.received_up_to.equals(Integer.MAX_VALUE) && token.equals(1)) {
            this.received_up_to = 1;
            messages.add(msg);
        }

        // Check if the token matches the token we are expecting next
        else if (this.received_up_to + 1 == token) {
            // Message is next in line to be delivered, update our deliverable_messages queue
            messages.add(msg);
            updateDeliverableMsgs(token);
        }
        else {
            // Token indicates message has arrived out of order, add to undeliverable_messages queue
            undeliverable_messages.add(msg);
            Collections.sort(undeliverable_messages);
        }
    }

    /**
     * Check that we haven't already received messages with higher tokens -> update if we have
     * @param deliverableToken The token number of the message we can deliver next
     */
    private void updateDeliverableMsgs(Integer deliverableToken) {
        received_up_to = deliverableToken;
        ArrayList<Integer> tokensToMove = new ArrayList<Integer>();
        // Check if we can send previously undeliverable messages
        for (Message m : this.undeliverable_messages) {
            if (m.getToken() <= received_up_to) {
                tokensToMove.add(m.getToken());
                this.messages.add(m);
            }
        }
        // Resort the message queue
        this.undeliverable_messages.removeIf((m) -> tokensToMove.contains(m.getToken()));
        Collections.sort(this.messages);
    }

    /** Check if a message has been received by all intended recipients and delete from storage if so
     * @param receivers List of recipients a message should be delivered to
     * @param token The token number of the message
     * */
    public void receivedMessage(ArrayList<String> receivers, Integer token) {
        System.out.println("RECORDING RECEIVED MESSAGE FOR " + username);
        boolean allDelivered = false;
        // Check if message needs to be delivered to any recipients
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

    /** Gets the next token number for a client */
    public Integer getToken()
    {
        return this.next_token;
    }

    /** Deletes a given message from local storage
     * @param token The message token to be deleted */
    public void deleteMessage(Integer token) {
        messages.removeIf(m -> m.getToken().equals(token));
    }

    /** Deletes a series of messages from local storage
     * @param tokens The tokens of the messages to be deleted */
    private void deleteMessages(ArrayList<Integer> tokens) {
        messages.removeIf(m -> tokens.contains(m.getToken()));
    }

    /** Deletes a series undeliverable messages from local storage
     * @param tokens The tokens of messages to be deleted */
    private void deleteUndeliverableMessages(ArrayList<Integer> tokens) {
        undeliverable_messages.removeIf(m -> tokens.contains(m.getToken()));
    }

    /**
     * Used to get the next valid message available for the recipient.
     * @param recipient The recipient we want the next valid message for
     * @return Returns the next valid message
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

    /** Want to remove a particular client as a recipient from all messages (client was an anonymous user, and will
     * not rejoin network at a later stage).
     * @param user The user to remove from all messages */
    public void clearRecipientFromAllMsgs(String user) {
        // Find all of the messages that have given client listed as a recipient
        ArrayList<Integer> messagesToRemove = new ArrayList<Integer>();
        messages.forEach((m) -> {
            boolean allDelivered = m.receivedMessage(user);
            if (allDelivered) {
                messagesToRemove.add(m.getToken());
            }
        });
        // Remove messages from local storage
        deleteMessages(messagesToRemove);
        messagesToRemove.clear();
        // Find all undeliverable messages queued to be sent to the user and remove
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
    /** Compares if one ClientRecord is equal to another ClientRecord
     * @param obj The ClientRecord to be compared
     * @return true if the same record, false otherwise */
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
