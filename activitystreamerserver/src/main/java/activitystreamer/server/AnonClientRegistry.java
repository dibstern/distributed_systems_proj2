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

    // TODO THIS WILL BE CALLED WHEN DECREMENTING NUMBER OF ANON CLIENTS TO RECEIVE MESSAGE EQUALS ZERO
    private void removeAnonMessage(AnonMessage msg) {
        messages.remove(msg);
        Collections.sort(messages);
    }

    public int getNumAnonUsersOnNetwork() {
        return numAnonUsersOnNetwork;
    }

    public boolean checkAnonLoggedOn() {
        return anonLoggedOn;
    }

    public void incrementNumAnonClients() {
        if (numAnonUsersOnNetwork == 0) {
            setLoggedStatus(true);
        }
        numAnonUsersOnNetwork++;
    }

    public void decrementNumAnonClients() {
        if (numAnonUsersOnNetwork == 1) {
            setLoggedStatus(false);
        }
        numAnonUsersOnNetwork--;
    }

    private void setLoggedStatus(boolean var) {
        anonLoggedOn = var;
    }

}
