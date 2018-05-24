package activitystreamer.server;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.util.ArrayList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Message implements Comparable<Message> {
    private ArrayList<String> all_recipients;
    private ArrayList<String> remaining_recipients;
    private JSONObject clientMessage;
    private JSONObject serverMessage;
    private Integer token;


    // ------------------ OBJECT CREATION ------------------
    public Message(Integer token, JSONObject clientMessage, ArrayList<String> recipients) {
        this.token = token;
        this.clientMessage = clientMessage;
        this.all_recipients = new ArrayList<String>(recipients);
        this.remaining_recipients = new ArrayList<String>(recipients);

        // Create server message
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
        this.clientMessage = serverToClientJson(jsonMessage);
        this.all_recipients = new ArrayList<String>(toRecipientsArrayList(jsonMessage.get("all_recipients")));
        this.remaining_recipients = new ArrayList<String>(toRecipientsArrayList(jsonMessage.get("remaining_recipients")));
        this.serverMessage = jsonMessage;
    }

    public JSONObject serverToClientJson(JSONObject serverJsonMessage) {
        JSONObject clientJsonMsg = new JSONObject();
        clientJsonMsg.put("command", serverJsonMessage.get("command"));
        clientJsonMsg.put("activity", serverJsonMessage.get("activity"));
        clientJsonMsg.put("username", serverJsonMessage.get("username"));
        clientJsonMsg.put("secret", serverJsonMessage.get("secret"));
        clientJsonMsg.put("authenticated_user", serverJsonMessage.get("authenticated_user"));
        return clientJsonMsg;
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



    // ------------------ FUNCTIONALITY ------------------

    /**
     * Notes that a particular user received this message, removing the user from the remaining users
     * @param users A list of users who have received this message
     * @return true if this Message instance should be deleted. false, otherwise.
     */
    public boolean receivedMessages(ArrayList<String> users) {
        users.forEach((user) -> {
            receivedMessage(user);
        });
        if (this.remaining_recipients.size() == 0) {
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
        this.remaining_recipients.remove(user);
        if (this.remaining_recipients.size() == 0) {
            return true;
        }
        return false;
    }

    public JSONObject getClientMessage() {
        return this.clientMessage;
    }

    public JSONObject getServerMessage() {
        return this.serverMessage;
    }

    public String getJsonString() {
        return MessageProcessor.getGson().toJson(this);
    }

    public boolean addressedTo(String user) {
        return remaining_recipients.contains(user);
    }



    // ------------------ COMPARING MESSAGES ------------------
    public Integer getToken() {
        return this.token;
    }

    @Override
    public int compareTo(Message anotherMessage) {
        return anotherMessage.getToken().compareTo(this.token);
    }

}
