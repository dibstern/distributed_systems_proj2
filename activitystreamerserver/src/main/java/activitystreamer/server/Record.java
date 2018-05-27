package activitystreamer.server;

import com.google.gson.reflect.TypeToken;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Record {

    private String username;
    private ArrayList<Message> messages;
    private ArrayList<Message> undeliverable_messages;
    private Integer next_token;
    private Integer received_up_to;

    public Record(String username) {
        this.username = username;
        this.next_token = 1;
        this.received_up_to = 0;
        this.messages = new ArrayList<Message>();
    }

    public Record(JSONObject clientRecordJson) {
        this.username = clientRecordJson.get("username").toString();
        this.next_token = ((Long) clientRecordJson.get("next_token")).intValue();
        this.received_up_to = ((Long) clientRecordJson.get("received_up_to")).intValue();

        Type collectionType = new TypeToken<ArrayList<Message>>(){}.getType();
        this.messages = MessageProcessor.getGson().fromJson(
                ((JSONArray) clientRecordJson.get("messages")).toJSONString(),
                collectionType);
        this.undeliverable_messages = MessageProcessor.getGson().fromJson(
                ((JSONArray) clientRecordJson.get("undeliverable_messages")).toJSONString(),
                collectionType);
    }

    /**
     * Synchronise this record, updating its values if the received record contains updated information.
     * @param receivedRecord
     */
    public void updateRecord(JSONObject receivedRecord) {

        // Update Logged In Status
        updateLoggedIn(((Long) receivedRecord.get("logged_in")).intValue(), "Updating Record");
        updateReceivedUpTo(((Long) receivedRecord.get("received_up_to")).intValue());

        // Update Messages
        Type collectionType = new TypeToken<ArrayList<Message>>(){}.getType();
        ArrayList<Message> receivedMessages = MessageProcessor.getGson().fromJson(
                ((JSONArray) receivedRecord.get("messages")).toJSONString(),
                collectionType);
        updateMessages(receivedMessages);
    }

    private void updateMessages(ArrayList<Message> receivedMessages) {

        receivedMessages.forEach((msg) -> {
            boolean messageFound;

            // Attempt to update deliverable messages if it's deliverable
            if (msg.getToken() <= this.received_up_to) {
                messageFound = updateDeliverableMessages(msg);
            }
            // Otherwise attempt to update undeliverable messages
            else {
                messageFound = updateUndeliverableMessages(msg);
            }
            // If we haven't found the message in our records, it's a new message we haven't yet received. Add it!
            if (!messageFound) {
                addMessage(msg);
            }
        });
    }

    private boolean updateDeliverableMessages(Message m) {
        AtomicBoolean messageFound = new AtomicBoolean(false);
        this.messages.forEach((message) -> {
            if (message.equals(m)) {
                messageFound.set(true);
                boolean allDelivered = message.updateRecipients(m.getRemainingRecipients());
                if (allDelivered) {
                    messages.remove(message);
                }
            }
        });
        return messageFound.get();
    }

    private boolean updateUndeliverableMessages(Message m) {
        AtomicBoolean messageFound = new AtomicBoolean(false);
        this.undeliverable_messages.forEach((message) -> {
            if (message.equals(m)) {
                messageFound.set(true);
                boolean allDelivered = message.updateRecipients(m.getRemainingRecipients());
                if (allDelivered) {
                    this.undeliverable_messages.remove(message);
                }
            }
        });
        return messageFound.get();
    }

    private void updateReceivedUpTo(Integer latestReceivedUpTo) {

        if (latestReceivedUpTo.equals(Integer.MAX_VALUE) || latestReceivedUpTo < 0) {
            this.received_up_to = 1;
        }
        else if (this.received_up_to < latestReceivedUpTo) {
            this.received_up_to = latestReceivedUpTo;
        }
    }

    // ------------------------------ LOGIN MANAGEMENT ------------------------------

    public String getUsername() {
        return this.username;
    }

    /**
     *
     * @param newLoggedIn
     */
    public abstract Integer updateLoggedIn(Integer newLoggedIn, String loginContext) ;

    public abstract boolean loggedIn();

    public abstract Integer getLoggedInToken();


    public Integer getNextTokenAndIncrement() {
        this.next_token += 1;
        int token = this.next_token - 1;
        return token;
    }

    // ------------------------------ MESSAGE CREATION ------------------------------

    public void createAndAddServerMessage(JSONObject msg, ArrayList<String> recipients, Integer token) {
        Message message = new Message(token, msg, recipients);
        addMessage(message);
    }

    public Integer createAndAddMessage(JSONObject msg, ArrayList<String> recipients) {
        Integer token = getNextTokenAndIncrement();
        Message message = new Message(token, msg, recipients);
        addMessage(message);
        return token;
    }

    // TODO: Implement as non-abstract here, in Record.java
    // public Integer createAndAddAnonMessage(JSONObject msg, ArrayList<String> recipients) {}

    public void addMessage(Message msg) {

        Integer token = msg.getToken();
        if (this.received_up_to + 1 == token) {
            messages.add(msg);
            Collections.sort(messages);
            this.received_up_to = token;
            updateDeliverableMessages();
        }
        else {
            undeliverable_messages.add(msg);
            Collections.sort(undeliverable_messages);
        }
    }

    // TODO: Deal with AnonMessage
    /**
     * Check that we haven't already received messages with higher tokens -> update if we have
     */
    public void updateDeliverableMessages() {
        ArrayList<Message> nextMessages = getMessagesWithTokensAbove(this.received_up_to);
        for (Message m : nextMessages) {
            if (m.getToken() == this.received_up_to + 1) {
                this.received_up_to += 1;
                undeliverable_messages.remove(m);
                messages.add(m);
            }
            else {
                break;
            }
        }
    }

    public ArrayList<Message> getMessagesWithTokensAbove(Integer largerThan) {
        ArrayList<Message> returnMessages = new ArrayList<Message>();
        for (Message m : messages) {
            if (m.getToken() > largerThan) {
                returnMessages.add(m);
            }
        }
        return returnMessages;
    }

    public void receivedMessage(ArrayList<String> receivers, Integer token) {
        boolean allDelivered = false;
        for (Message message : messages) {
            if (message.getToken().equals(token)) {
                allDelivered = message.receivedMessages(receivers);
            }
        }
        if (allDelivered) {
            // TODO: Anon Message Deletion
            deleteMessage(token);
        }
    }

    private void deleteMessage(Integer token) {
        messages.removeIf(m -> m.getToken().equals(token));
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
                return m;
            }
        }
        return null;
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
