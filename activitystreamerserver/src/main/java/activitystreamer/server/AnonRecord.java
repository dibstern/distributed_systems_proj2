package activitystreamer.server;

import com.google.gson.reflect.TypeToken;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class AnonRecord extends Record {

    private Integer logged_in;
    private ArrayList<Message> messages;

    public AnonRecord(String username) {
        super(username);
        this.logged_in = 0;
        this.messages = new ArrayList<Message>();
    }

    public AnonRecord(JSONObject clientRecordJson) {
        super(clientRecordJson);
        this.logged_in = ((Long) clientRecordJson.get("logged_in")).intValue();

        Type collectionType = new TypeToken<ArrayList<Message>>(){}.getType();
        this.messages = MessageProcessor.getGson().fromJson(
                ((JSONArray) clientRecordJson.get("messages")).toJSONString(),
                collectionType);
    }

    public Integer updateLoggedIn(Integer newLoggedIn, String loginContext) {

        // TODO: Figure out how we synchronise logged in numbers

        return this.logged_in;
    }

    public Integer login(String loginContext) {
        if (this.logged_in < Integer.MAX_VALUE) {
            this.logged_in += 1;
        }
        return this.logged_in;
    }

    public Integer logout(String logoutContext) {
        if (this.logged_in > 0) {
            this.logged_in -= 1;
            return this.logged_in;
        }
        else {
            return Integer.MIN_VALUE;
        }
    }

    // TODO: Same exact function. First name makes sense. Replace?
    public Integer getNumLoggedIn() {
        return this.logged_in;
    }

    public boolean loggedIn() {
        return this.logged_in > 0;
    }


    // ----------------------------------- MESSAGING -----------------------------------

    public Integer createAndAddMessage(JSONObject msg, ArrayList<String> recipients, Integer numAnonRecipients) {
        Message message = new Message(msg, recipients, numAnonRecipients);
        addMessage(message);
        return Integer.MAX_VALUE;
    }

    public void addMessage(Message msg) {
        messages.add(msg);
    }

    /**
     * Used to get the next valid message available for the recipient.
     * @param recipient
     * @return
     */
    public Message getNextMessage(String recipient) {
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
