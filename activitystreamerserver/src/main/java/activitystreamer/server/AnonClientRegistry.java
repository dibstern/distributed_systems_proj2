package activitystreamer.server;

import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * INSERT STUFF HERE
 */
public class AnonClientRegistry {

    private int numAnonUsersOnNetwork;
    private boolean anonLoggedOn;
    private ArrayList<AnonMessage> messages;

    public AnonClientRegistry() {
        numAnonUsersOnNetwork = 0;
        anonLoggedOn = false;
        messages = new ArrayList<AnonMessage>();
    }

    public void addAnonMessage(AnonMessage msg) {
     messages.add(msg);
     Collections.sort(messages);
    }

    public int getNumAnonUsersOnNetwork() {
        return numAnonUsersOnNetwork;
    }

    public boolean anonsLoggedOn() {
        return anonLoggedOn;
    }

    public void anonLogin() {
        this.numAnonUsersOnNetwork++;
        if (numAnonUsersOnNetwork > 0) {
            setLoggedStatus(true);
        }
    }

    public void anonLogout() {
        this.numAnonUsersOnNetwork--;
        if (numAnonUsersOnNetwork == 0) {
            setLoggedStatus(false);
        }
    }

    private void setLoggedStatus(boolean var) {
        anonLoggedOn = var;
    }

    public void wasReceived(AnonMessage m) {
        boolean allDelivered = m.wasReceived();
        if (allDelivered) {
            messages.remove(m);
        }
    }

    public void wasReceived(AnonMessage m, Integer numUsers) {
        boolean allDelivered = m.wasReceived(numUsers);
        if (allDelivered) {
            messages.remove(m);
        }
    }

    public AnonMessage getNextMessage() {
        if (!messages.isEmpty()) {
            return messages.get(0);
        }
        return null;
    }

    public void flushMessages(Connection con) {
        //
    }

}
