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
    public Message(Integer token, JSONObject clientMessage, ArrayList<String> recipients) {
        this.token = token;
        this.clientMessage = clientMessage;
        this.recipients = new ArrayList<String>(recipients);

        // Provided client version of the message -> Create server message
        String recipientsJsonString = MessageProcessor.getGson().toJson(recipients);
        JSONObject addedServerInfo = MessageProcessor.toJson(recipientsJsonString, true, "recipients");
        addedServerInfo.put("token", token);
        this.serverMessage = new JSONObject();
        this.serverMessage.putAll(this.clientMessage);
        this.serverMessage.putAll(addedServerInfo);
    }

    // Uses a server's Json Message to construct a Message
    public Message(JSONObject jsonMessage) {
        this.token = ((Long) jsonMessage.get("token")).intValue();
        this.recipients = new ArrayList<String>(toRecipientsArrayList(jsonMessage.get("recipients")));
        this.serverMessage = jsonMessage;

        // Provided server version of the message -> Create client message
        this.clientMessage = MessageProcessor.serverToClientJson(jsonMessage);
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

    /**
     * Notes that a particular user received this message, removing the user from the remaining users
     * @param user
     * @return true if this Message instance should be deleted. false, otherwise.
     */
    public boolean receivedMessage(String user) {
        this.recipients.remove(user);
        if (this.recipients.size() == 0) {
            return true;
        }
        return false;
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

    // ------------------------------ UNUSED METHODS ------------------------------
    public JSONObject getServerMessage() {
        return this.serverMessage;
    }

    public String getJsonString() {
        return MessageProcessor.getGson().toJson(this);
    }

}
