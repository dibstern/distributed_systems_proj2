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
        return anotherMessage.getToken().compareTo(this.token);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!AnonMessage.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        final AnonMessage other = (AnonMessage) obj;
        if ((this.token == null) ? (other.token != null) : !this.token.equals(other.token)) {
            return false;
        }
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
}
