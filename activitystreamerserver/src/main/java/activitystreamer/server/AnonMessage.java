package activitystreamer.server;

import org.json.simple.JSONObject;

/**
 * INSERT STUFF HERE
 */
public class AnonMessage implements Comparable<AnonMessage> {

    private Integer token;
    private JSONObject msg;
    private int numAnonClientsToReceive;

    public AnonMessage(JSONObject jsonMessage) {
        this.token = ((Long) jsonMessage.get("token")).intValue();
        this.msg = MessageProcessor.serverMsgToAnonMsg(jsonMessage);
        this.numAnonClientsToReceive = (int) jsonMessage.get("num_anon");
    }

    public int getNumAnonClientsToReceive() {
        return numAnonClientsToReceive;
    }

    public boolean wasReceived(Integer numAnonClients) {
        this.numAnonClientsToReceive -= numAnonClients;
        return (numAnonClientsToReceive <= 0);
    }


    public boolean wasReceived() {

        numAnonClientsToReceive--;

        // Correct number of anon clients have received message, can be deleted
        return (numAnonClientsToReceive == 0);
    }

    public JSONObject getMessage() {
        return msg;
    }

    // ------------------ COMPARING MESSAGES ------------------
    public Integer getToken() {
        return this.token;
    }

    @Override
    public int compareTo(AnonMessage anotherMessage) {
        return anotherMessage.getToken().compareTo(this.messageToken);
    }
}
