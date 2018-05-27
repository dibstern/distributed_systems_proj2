package activitystreamer.server;

import org.json.simple.JSONObject;

public class AnonRecord extends Record {

    private Integer anon_users;
    private Integer logged_in;

    public AnonRecord(String username) {
        super(username);
        this.anon_users = 0;
        this.logged_in = 0;
    }

    public AnonRecord(JSONObject clientRecordJson) {
        super(clientRecordJson);
        this.anon_users = ((Long) clientRecordJson.get("anon_users")).intValue();
        this.logged_in = ((Long) clientRecordJson.get("logged_in")).intValue();
    }

    public Integer updateLoggedIn(Integer newLoggedIn, String loginContext) {

        // TODO: Figure out how we synchronise logged in numbers

        return this.logged_in;
    }

    public Integer login(String loginContext) {
        if (this.logged_in < Integer.MAX_VALUE) {
            this.logged_in += 1;
        }
        return this.logged_in;
    }

    public Integer logout(String logoutContext) {
        if (this.logged_in > 0) {
            this.logged_in -= 1;
            return this.logged_in;
        }
        else {
            return Integer.MIN_VALUE;
        }
    }


    // TODO: Same exact function. First name makes sense. Replace?
    public Integer getNumLoggedIn() {
        return this.logged_in;
    }
    public Integer getLoggedInToken() {
        return this.logged_in;
    }

    public boolean loggedIn() {
        return this.logged_in > 0;
    }




}
