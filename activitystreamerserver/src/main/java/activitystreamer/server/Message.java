package activitystreamer.server;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.util.ArrayList;

public class Message implements Comparable<Message> {
    private ArrayList<String> recipients;
    private JSONObject clientMessage;
    private JSONObject serverMessage;
    private Integer token;


    // ------------------------------ OBJECT CREATION ------------------------------
    public Message(Integer token, JSONObject clientMessageJson, ArrayList<String> recipients) {
        this.clientMessage = MessageProcessor.cleanClientMessage(clientMessageJson);
        this.recipients = new ArrayList<String>(recipients);
        this.token = token;

        // Provided client version of the message -> Create server message
        String recipientsJsonString = MessageProcessor.getGson().toJson(recipients);
        JSONObject addedServerInfo = MessageProcessor.toJson(recipientsJsonString, true, "recipients");
        addedServerInfo.put("token", token);
        this.serverMessage = new JSONObject();
        this.serverMessage.putAll(this.clientMessage);
        this.serverMessage.putAll(addedServerInfo);
    }

    // Uses a server's Json Message to construct a Message
    public Message(JSONObject serverMessage) {

        this.recipients = new ArrayList<String>(toRecipientsArrayList(serverMessage.get("recipients")));
        this.serverMessage = serverMessage;
        this.token = ((Long) serverMessage.get("token")).intValue();

        // Provided server version of the message -> Create client message
        this.clientMessage = MessageProcessor.cleanClientMessage(MessageProcessor.serverToClientJson(serverMessage));
    }

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
        users.forEach((user) -> {
            receivedMessage(user);
        });
        if (this.recipients.size() == 0) {
            return true;
        }
        return false;
    }
    // Is this just the same function as the one above?
    public boolean updateRecipients(ArrayList<String> receivedRecipients) {
        this.recipients.removeIf(m -> !receivedRecipients.contains(m));
        return this.recipients.isEmpty();
    }

    /**
     * Notes that a particular user received this message, removing the user from the remaining users
     * @param user
     * @return true if this Message instance should be deleted. false, otherwise.
     */
    public boolean receivedMessage(String user) {
        this.recipients.remove(user);
        return this.recipients.isEmpty();
    }

    public JSONObject getClientMessage() {
        return this.clientMessage;
    }

    public boolean addressedTo(String user) {
        return recipients.contains(user);
    }

    public ArrayList<String> getRemainingRecipients() {
        return this.recipients;
    }


    // ------------------------------ COMPARING MESSAGES ------------------------------

    public Integer getToken() {
        return this.token;
    }

    @Override
    public int compareTo(Message anotherMessage) {
        return anotherMessage.getToken().compareTo(this.token);
    }

    @Override
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
    public JSONObject getServerMessage() {
        return this.serverMessage;
    }

    public String getJsonString() {
        return MessageProcessor.getGson().toJson(this);
    }

}
