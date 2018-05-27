package activitystreamer.server;

import org.json.simple.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;

public class ClientRecord extends Record {


    private String secret;
    private Integer next_token;
    private Integer logged_in;

    public ClientRecord(String username, String secret) {
        super(username);
        this.logged_in = 1;
        this.secret = secret;
        this.next_token = 1;
    }

    public ClientRecord(JSONObject clientRecordJson) {
        super(clientRecordJson);
        this.logged_in = ((Long) clientRecordJson.get("logged_in")).intValue();
        this.secret = clientRecordJson.get("secret").toString();
        this.next_token = ((Long) clientRecordJson.get("next_token")).intValue();
    }

    /**
     * Synchronise this record, updating its values if the received record contains updated information.
     * @param receivedRecord
     */
    @Override
    public void updateRecord(JSONObject receivedRecord) {
        super.updateRecord(receivedRecord);

        // Update next_token!!
        updateNextToken(((Long) receivedRecord.get("next_token")).intValue());
    }


    private void updateNextToken(Integer receivedNextToken) {
        if (receivedNextToken.equals(Integer.MAX_VALUE) || receivedNextToken < 1) {
            next_token = 1;
        }
        else if (next_token < receivedNextToken) {
            next_token = receivedNextToken;
        }
    }

    // ------------------------------ LOGIN MANAGEMENT ------------------------------

    /**
     * Checks whether or not the JSONObject representing a client record has the same secret as this ClientRecord
     * @param clientRecord
     * @return
     */
    public boolean sameSecret(JSONObject clientRecord) {
        if (clientRecord.containsKey("secret")){
            return this.secret.equals(clientRecord.get("secret").toString());
        }
        return false;
    }

    /**
     * Checks whether or not the provided secret is the same as the secret of this client record.
     * @param secret
     * @return
     */
    public boolean sameSecret(String secret) {
        return this.secret.equals(secret);
    }

    public String getSecret() {
        return this.secret;
    }

    /**
     *
     * @param newLoggedIn
     */
    public Integer updateLoggedIn(Integer newLoggedIn, String loginContext) {

        if (this.logged_in == Integer.MAX_VALUE) {
            this.logged_in = 2;
            System.out.println("Logging In " + super.getUsername());
            System.out.println(loginContext + "; this.logged_in = " + this.logged_in);
            return this.logged_in;
        }
        else if (newLoggedIn > this.logged_in && newLoggedIn > 0) {
            this.logged_in = newLoggedIn;

            if (this.logged_in % 2 == 0) {
                System.out.println("Logging In " + super.getUsername());
            }
            else {
                System.out.println("Logging Out " + super.getUsername());
            }
            System.out.println(loginContext + "; this.logged_in = " + this.logged_in);

            return this.logged_in;
        }
        // Invalid login attempt
        return Integer.MIN_VALUE;
    }

    /**
     *
     * @return
     */
    public boolean loggedIn() {
        return this.logged_in % 2 == 0;
    }

    public Integer getLoggedInToken() {
        return this.logged_in;
    }


    public Integer getNextTokenAndIncrement() {
        this.next_token += 1;
        int token = this.next_token - 1;
        return token;
    }


    // ----------------------------------- Archived Methods (Unused but possibly useful in the future) ----------------
    /**
     * Returns a HashMap of <Username, Message> Pairs, the message to send to each user.
     * @param connectedClients A client currently connected to the server.
     * @return a HashMap of <Username, Message> Pairs, so each user has a message (if any) the server can send it.
     *         May return an empty HashMap if no messages are yet ready to send.
     */
    public HashMap<String, Message> getNextMessages(ArrayList<String> connectedClients) {
        HashMap<String, Message> nextMessages = new HashMap<String, Message>();

        // Get the next message for each user, and if it's not null, store it!
        connectedClients.forEach((user) -> {
            Message msg = getNextMessage(user);
            if (msg != null) {
                nextMessages.put(user, msg);
            }
        });
        return nextMessages;
    }

}
