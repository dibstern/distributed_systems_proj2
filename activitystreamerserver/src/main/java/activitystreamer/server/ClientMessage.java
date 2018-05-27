package activitystreamer.server;

import org.json.simple.JSONObject;

import java.util.ArrayList;

public class ClientMessage extends Message {


    private Integer token;


    public ClientMessage(Integer token, JSONObject clientMessage, ArrayList<String> recipients, Integer numAnonRecipients) {
        super(token, clientMessage, recipients, numAnonRecipients);
        this.token = token;
    }

    // Uses a server's Json Message to construct a Message
    public ClientMessage(JSONObject jsonMessage) {
        super(jsonMessage);
        this.token = ((Long) jsonMessage.get("token")).intValue();
    }




    // ------------------------------ COMPARING MESSAGES ------------------------------
    public Integer getToken() {
        return this.token;
    }

    @Override
    public int compareTo(Message anotherMessage) {
        ClientMessage otherMessage = (ClientMessage) anotherMessage;
        return otherMessage.getToken().compareTo(this.token);
    }

}
