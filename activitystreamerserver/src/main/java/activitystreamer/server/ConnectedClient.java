package activitystreamer.server;

/** This class holds information about a given client the server is directly connected to */

public class ConnectedClient {
    private String username;
    private String secret;
    private int lockRequestServerCount;
    private boolean registered;
    private boolean loggedIn;

    /** Stores basic information about a particular client
     * @param username The client's username
     * @param password The client's password */
    public ConnectedClient(String username, String password) {
        this.username = username;
        this.secret = password;
        this.registered = true;
        this.loggedIn = true;
    }

    /** Stores basic information about a particular client
     * @param username The client's username
     * @param password The client's password
     * @param numKnownServers  The number of servers a given server currently knows about */
    public ConnectedClient(String username, String password, int numKnownServers) {
        this.username = username;
        this.secret = password;
        this.lockRequestServerCount = numKnownServers;
        if (numKnownServers == 0) {
            this.registered = true;
        }
        else {
            this.registered = false;
        }
        this.loggedIn = false;
    }

    /** Increments the number of LOCK_ALLOWED messages received */
    public boolean receivedLockAllowed() {
        if (isRegistered()) {
            return false;
        }
        this.lockRequestServerCount -= 1;
        if (this.lockRequestServerCount == 0) {
            register();
        }
        return isRegistered();
    }

    /*
     * Getters and Setters
     */

    public String getUsername() {
        return this.username;
    }

    public String getSecret() {
        return this.secret;
    }

    public boolean isClient(String username, String secret) {
        return this.username.equals(username) && this.secret.equals(secret);
    }

    public void register() {
        this.registered = true;
    }

    public boolean isRegistered() {
        return this.registered;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }


}
