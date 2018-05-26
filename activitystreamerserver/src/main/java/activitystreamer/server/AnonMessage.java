package activitystreamer.server;

import org.json.simple.JSONObject;

/**
 * INSERT STUFF HERE
 */
public class AnonMessage implements Comparable<AnonMessage> {

    private Integer messageToken;
    private JSONObject msg;
    private int numAnonClientsToReceive;

    public AnonMessage(JSONObject jsonMessage)
    {
        this.messageToken = ((Long) jsonMessage.get("token")).intValue();
        this.msg = serverToAnonJson(jsonMessage);
        this.numAnonClientsToReceive = (int) jsonMessage.get("num_anon");
    }

    // TODO CHECK THIS OKAY FIELDS
    public JSONObject serverToAnonJson(JSONObject serverJsonMessage) {
        JSONObject anonJsonMsg = new JSONObject();
        anonJsonMsg.put("command", serverJsonMessage.get("command"));
        anonJsonMsg.put("activity", serverJsonMessage.get("activity"));
        anonJsonMsg.put("username", serverJsonMessage.get("username"));
        return anonJsonMsg;
    }

    public int getNumAnonClientsToReceive()
    {
        return numAnonClientsToReceive;
    }

    public boolean decrementNumAnonClientsToReceive()
    {
        numAnonClientsToReceive--;
        if (numAnonClientsToReceive == 0)
        {
            // Correct number of anon clients have received message, can be deleted
            return true;
        }
        return false;
    }

    // ------------------ COMPARING MESSAGES ------------------
    public Integer getToken() {
        return this.messageToken;
    }

    @Override
    public int compareTo(AnonMessage anotherMessage) {
        return anotherMessage.getToken().compareTo(this.messageToken);
    }
}
