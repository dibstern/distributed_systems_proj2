package activitystreamer.server;

import com.google.gson.reflect.TypeToken;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Record {

    private String username;

    public Record(String username) {
        this.username = username;
    }

    public Record(JSONObject clientRecordJson) {
        this.username = clientRecordJson.get("username").toString();
    }


    // ------------------------------ LOGIN MANAGEMENT ------------------------------

    public String getUsername() {
        return this.username;
    }

    /**
     *
     * @param newLoggedIn
     */
    public abstract Integer updateLoggedIn(Integer newLoggedIn, String loginContext) ;

    public abstract boolean loggedIn();

    // ------------------------------ MESSAGE CREATION ------------------------------

    public void createAndAddServerMessage(JSONObject msg, ArrayList<String> recipients, Integer token, Integer numAnonRecipients) {
        Message message = new Message(token, msg, recipients, numAnonRecipients);
        addMessage(message);
    }

    public abstract Integer createAndAddMessage(JSONObject msg, ArrayList<String> recipients, Integer numAnonRecipients);

    public abstract void addMessage(Message msg);
}
