package activitystreamer.server;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.util.ArrayList;

/** This class represents a Message that has been received/sent across the network.
 * */
public class Message implements Comparable<Message> {
    private ArrayList<String> recipients;
    private JSONObject clientMessage;
    private JSONObject serverMessage;
    private Integer token;


    // ------------------------------ OBJECT CREATION ------------------------------
    /** Creates a new client message
     * @param token The message's token number
     * @param clientMessageJson The message a client sent
     * @param recipients The clients who are to receive the given message */
    public Message(Integer token, JSONObject clientMessageJson, ArrayList<String> recipients) {
        this.clientMessage = MessageProcessor.cleanClientMessage(clientMessageJson);
        this.recipients = new ArrayList<String>(recipients);
        this.token = token;

        // Provided client version of the message -> Create server message
        String recipientsJsonString = MessageProcessor.getGson().toJson(recipients);
        JSONObject addedServerInfo = MessageProcessor.toJson(recipientsJsonString, true, "recipients");
        addedServerInfo.put("token", token);
        // Add necessary fields to client message so it can be sent across the network
        this.serverMessage = new JSONObject();
        this.serverMessage.putAll(this.clientMessage);
        this.serverMessage.putAll(addedServerInfo);
    }

    /** Use a server's JSON message to constuct a new message
     * @param serverMessage The message received from another server, which we are creating a new message from */
    public Message(JSONObject serverMessage) {

        this.recipients = new ArrayList<String>(toRecipientsArrayList(serverMessage.get("recipients")));
        this.serverMessage = serverMessage;
        this.token = ((Long) serverMessage.get("token")).intValue();

        // Provided server version of the message -> Create client message
        this.clientMessage = MessageProcessor.cleanClientMessage(MessageProcessor.serverToClientJson(serverMessage));
    }

    /** Converts the list of recipients from object form into an array list
     * @param recipientsObj The object containing all of a message's recipients
     * @return the arraylist of all clients to receive a message */
    public ArrayList<String> toRecipientsArrayList(Object recipientsObj) {
        ArrayList<String> recipientsList = new ArrayList<String>();
        JSONArray recipientsJSONArray = (JSONArray) recipientsObj;
        recipientsJSONArray.forEach((recipientObj) -> {
            String recipient = recipientObj.toString();
            recipientsList.add(recipient);
        });
        return recipientsList;
    }

    // ------------------------------ FUNCTIONALITY ------------------------------

    /**
     * Notes that a particular user received this message, removing the user from the remaining users
     * @param users A list of users who have received this message
     * @return true if this Message instance should be deleted. false, otherwise.
     */
    public boolean receivedMessages(ArrayList<String> users) {
        users.forEach(this::receivedMessage);
        if (this.recipients.size() == 0) {
            // Message has been recieved by all recipients
            return true;
        }
        return false;
    }
    /** A list of users have received a message, removes the users from the recipients lists
     * @param receivedRecipients The recipients who received the message
     * @return true if message has been delivered to all recipients, false otherwise */
    public boolean updateRecipients(ArrayList<String> receivedRecipients) {
        this.recipients.removeIf(m -> !receivedRecipients.contains(m));
        return this.recipients.isEmpty();
    }

    /**
     * Notes that a particular user received this message, removing the user from the remaining users
     * @param user The user who received the message
     * @return true if this Message instance should be deleted. false, otherwise.
     */
    public boolean receivedMessage(String user) {
        this.recipients.remove(user);
        return this.recipients.isEmpty();
    }

    /** Gets a client message
     * @return the JSONObject representation of the client message */
    public JSONObject getClientMessage() {
        return this.clientMessage;
    }

    /** Checks if a message should be delivered to a given user
     * @param user The username of the client
     * @return true if the user should receive the message, false otherwise */
    public boolean addressedTo(String user) {
        return recipients.contains(user);
    }

    /** Retrieves all recipients that are yet to receive a message
     * @return The arraylist of all recipients to receive a message */
    public ArrayList<String> getRemainingRecipients() {
        return this.recipients;
    }


    // ------------------------------ COMPARING MESSAGES ------------------------------

    /** Gets the token of a message
     * @return The message token*/
    public Integer getToken() {
        return this.token;
    }

    @Override
    /** Compares to messages to see if the same
     * @param anotherMessage the message to be compared to
     * @return if the messages match or not */
    public int compareTo(Message anotherMessage) {
        return anotherMessage.getToken().compareTo(this.token);
    }

    @Override
    /** Compares to messages to see if equal
     * @return obj The message we are being compared with
     * @return true if messages are the same, false otherwise */
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!Message.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        final Message other = (Message) obj;
        if ((this.token == null) ? (other.token != null) : !this.token.equals(other.token)) {
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
        hash = 53 * hash + (this.token != null ? this.token.hashCode() : 0);
        // Used if we implement a sender field
        // hash = 53 * hash + this.sender;
        return hash;
    }


    // ------------------------------ UNUSED METHODS ------------------------------
    /** Retrieves a server message
     * @return the server message */
    public JSONObject getServerMessage() {
        return this.serverMessage;
    }

    /** Converts the message to a string
     * @return the message in string format */
    public String getJsonString() {
        return MessageProcessor.getGson().toJson(this);
    }

}
